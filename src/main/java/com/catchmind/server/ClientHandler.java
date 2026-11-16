package com.catchmind.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private static final RoomManager roomManager = new RoomManager();

    private final Socket socket;
    private final SessionManager sessionManager;
    private final ServerMain serverGui;
    private BufferedReader in;
    private PrintWriter out;

    private int userId = -1;
    private String nickname = null;
    private int currentRoomId = -1; // -1이면 대기실 상태

    public ClientHandler(Socket socket, SessionManager sessionManager, ServerMain serverGui) {
        this.socket = socket;
        this.sessionManager = sessionManager;
        this.serverGui = serverGui;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[Recv from " + (nickname != null ? nickname : socket.getRemoteSocketAddress()) + "] " + line);
                handleMessage(line.trim());
            }
        } catch (IOException e) {
            System.out.println("[Server] 클라이언트 연결 끊김: " + (nickname != null ? nickname : socket.getRemoteSocketAddress()));
        } finally {
            cleanup();
        }
    }

    private void handleMessage(String message) {
        if (message.isEmpty()) return;

        String[] tokens = message.split("\\|", 2);
        String command = tokens[0];

        switch (command) {
            case "LOGIN" -> {
                if (tokens.length < 2 || tokens[1].isBlank()) {
                    sendMessage("ERROR|INVALID_NICKNAME");
                    return;
                }
                if (this.nickname != null) {
                    sendMessage("ERROR|ALREADY_LOGGED_IN");
                    return;
                }

                String requestedNickname = tokens[1].trim();
                int assignedId = sessionManager.registerUser(requestedNickname, this);

                if (assignedId == -1) {
                    sendMessage("ERROR|NICKNAME_TAKEN");
                } else {
                    this.userId = assignedId;
                    this.nickname = requestedNickname;

                    sendMessage("LOGIN_OK");
                    sessionManager.broadcastLobbyUsers(); // 대기실 접속자 목록 갱신

                    if (serverGui != null) {
                        serverGui.appendLog(nickname + "님이 입장했습니다.");
                    }
                    System.out.println("[Server] 로그인 성공: ID=" + userId + ", Nickname=" + nickname);
                }
            }

            case "LIST_ROOMS" -> {
                sendMessage(roomManager.buildRoomsMessage());
                sessionManager.broadcastLobbyUsers();
            }

            case "CREATE_ROOM" -> {
                if (this.nickname == null) {
                    sendMessage("ERROR|NOT_LOGGED_IN");
                    return;
                }
                if (tokens.length < 2 || tokens[1].isBlank()) {
                    sendMessage("ERROR|INVALID_ROOM_NAME");
                    return;
                }
                if (this.currentRoomId != -1) {
                    sendMessage("ERROR|ALREADY_IN_ROOM");
                    return;
                }

                String roomName = tokens[1].trim();
                Room created = roomManager.createRoom(roomName, this);
                this.currentRoomId = created.getRoomId();

                // 방 생성 응답 및 참가자 목록 전송
                sendMessage("ROOM_CREATED|" + created.getRoomId());
                created.broadcast(created.buildPlayersMessage());

                // 대기실 유저들에게 방 목록 및 접속자 목록 실시간 갱신
                sessionManager.broadcastRoomsToLobby(roomManager.buildRoomsMessage());
                sessionManager.broadcastLobbyUsers();

                if (serverGui != null) {
                    serverGui.appendLog(nickname + "님이 방 생성: [" + created.getRoomId() + "] " + roomName);
                }
            }

            case "JOIN_ROOM" -> {
                if (this.nickname == null) {
                    sendMessage("ERROR|NOT_LOGGED_IN");
                    return;
                }
                if (tokens.length < 2 || tokens[1].isBlank()) {
                    sendMessage("ERROR|ROOM_NOT_FOUND");
                    return;
                }
                if (this.currentRoomId != -1) {
                    sendMessage("ERROR|ALREADY_IN_ROOM");
                    return;
                }

                int targetRoomId;
                try {
                    targetRoomId = Integer.parseInt(tokens[1].trim());
                } catch (NumberFormatException e) {
                    sendMessage("ERROR|ROOM_NOT_FOUND");
                    return;
                }

                Room room = roomManager.getRoom(targetRoomId);
                if (room == null) {
                    sendMessage("ERROR|ROOM_NOT_FOUND");
                    return;
                }

                room.addPlayer(this);
                this.currentRoomId = targetRoomId;

                sendMessage("JOIN_OK|" + targetRoomId);
                room.broadcast(room.buildPlayersMessage());
                room.broadcast("CHAT_NOTIFY|SYSTEM|" + this.nickname + "님이 입장했습니다.");
                sessionManager.broadcastLobbyUsers();

                if (serverGui != null) {
                    serverGui.appendLog(nickname + "님이 [" + room.getRoomName() + "] 방에 입장했습니다.");
                }
            }

            case "LEAVE_ROOM" -> {
                if (this.currentRoomId == -1) {
                    sendMessage("ERROR|NOT_IN_ROOM");
                    return;
                }
                int prevRoomId = this.currentRoomId;
                this.currentRoomId = -1;
                roomManager.leaveRoom(prevRoomId, this);
                Room leftRoom = roomManager.getRoom(prevRoomId);
                if (leftRoom != null) {
                    leftRoom.broadcast("CHAT_NOTIFY|SYSTEM|" + this.nickname + "님이 퇴장했습니다.");
                }

                sendMessage("LEAVE_OK");
                sessionManager.broadcastRoomsToLobby(roomManager.buildRoomsMessage());
                sessionManager.broadcastLobbyUsers();
            }

            case "CHAT" -> {
                if (this.currentRoomId == -1) {
                    sendMessage("ERROR|NOT_IN_ROOM");
                    return;
                }
                if (tokens.length < 2 || tokens[1].isBlank()) return;
                Room room = roomManager.getRoom(this.currentRoomId);
                if (room != null) {
                    room.broadcast("CHAT_NOTIFY|" + this.nickname + "|" + tokens[1]);
                }
            }

            default -> sendMessage("ERROR|UNKNOWN_COMMAND");
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    public int getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public int getCurrentRoomId() {
        return currentRoomId;
    }

    private void cleanup() {
    	if (this.currentRoomId != -1) {
    	    int prevRoomId = this.currentRoomId;
    	    roomManager.leaveRoom(prevRoomId, this);
    	    this.currentRoomId = -1;
    	    Room leftRoom = roomManager.getRoom(prevRoomId);
    	    if (leftRoom != null) {
    	        leftRoom.broadcast("CHAT_NOTIFY|SYSTEM|" + this.nickname + "님이 퇴장했습니다.");
    	    }
    	    sessionManager.broadcastRoomsToLobby(roomManager.buildRoomsMessage());
    	}
        if (this.nickname != null) {
            sessionManager.removeUser(this.nickname);
            sessionManager.broadcastLobbyUsers();
            if (serverGui != null) {
                serverGui.appendLog(this.nickname + "님이 퇴장했습니다.");
            }
        }
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[Server] 리소스 정리 중 오류: " + e.getMessage());
        }
    }
}