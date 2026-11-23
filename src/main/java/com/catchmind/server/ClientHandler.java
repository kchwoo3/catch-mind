package com.catchmind.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class ClientHandler implements Runnable {
    private static final RoomManager roomManager = new RoomManager();

    private static final int CANVAS_MAX_X = 800;
    private static final int CANVAS_MAX_Y = 500;
    private static final Set<String> ALLOWED_COLORS = Set.of(
        "#FF0000", "#FFA500", "#FFFF00",
        "#008000", "#0000FF", "#000080",
        "#800080", "#FFFFFF", "#000000"
    );
    private static final Set<Integer> ALLOWED_PEN_WIDTHS = Set.of(2, 4, 8);
    private static final int ERASER_WIDTH = 16;
    private static final String ERASER_COLOR = "#FFFFFF";

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
                    sessionManager.broadcastLobbyUsers();

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
                

                sendMessage("ROOM_CREATED|" + created.getRoomId());
                created.broadcast(created.buildPlayersMessage());

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

                RoomManager.JoinResult joinResult = roomManager.joinRoom(targetRoomId, this);
                if (joinResult != RoomManager.JoinResult.SUCCESS) {
                    sendMessage("ERROR|" + joinResult.name());
                    return;
                }

                Room room = roomManager.getRoom(targetRoomId);
                this.currentRoomId = targetRoomId;

                sendMessage("JOIN_OK|" + targetRoomId);
                if (room != null) {
                    room.broadcast(room.buildPlayersMessage());
                    room.broadcast("CHAT_NOTIFY|" + nickname + "님이 입장했습니다.");
                }
                sessionManager.broadcastLobbyUsers();

                if (serverGui != null && room != null) {
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

                sendMessage("LEAVE_OK");
                sessionManager.broadcastRoomsToLobby(roomManager.buildRoomsMessage());
                sessionManager.broadcastLobbyUsers();
            }

            case "CHAT" -> {
                if (this.currentRoomId == -1) {
                    sendMessage("ERROR|NOT_IN_ROOM");
                    return;
                }
                if (tokens.length < 2) return;
                Room room = roomManager.getRoom(this.currentRoomId);
                if (room != null) {
                    room.broadcast("CHAT|" + this.nickname + "|" + tokens[1]);
                }
            }

            case "DRAW" -> handleDraw(message);

            case "CLEAR" -> handleClear(message);

            default -> sendMessage("ERROR|UNKNOWN_COMMAND");
        }
    }

    // DRAW|<roundId>|<x1>|<y1>|<x2>|<y2>|<색상>|<굵기>
    private void handleDraw(String message) {
        if (this.currentRoomId == -1) {
            sendMessage("ERROR|NOT_IN_ROOM");
            return;
        }
        Room room = roomManager.getRoom(this.currentRoomId);
        if (room == null) {
            sendMessage("ERROR|NOT_IN_ROOM");
            return;
        }

        String[] parts = message.split("\\|");
        if (parts.length != 8) {
            sendMessage("ERROR|INVALID_DRAW_REQUEST");
            return;
        }

        int roundId, x1, y1, x2, y2, width;
        try {
            roundId = Integer.parseInt(parts[1].trim());
            x1 = Integer.parseInt(parts[2].trim());
            y1 = Integer.parseInt(parts[3].trim());
            x2 = Integer.parseInt(parts[4].trim());
            y2 = Integer.parseInt(parts[5].trim());
            width = Integer.parseInt(parts[7].trim());
        } catch (NumberFormatException e) {
            sendMessage("ERROR|INVALID_DRAW_REQUEST");
            return;
        }

        String color = parts[6].trim().toUpperCase();

        // 게임 진행 상태, 라운드 번호, 현재 출제자 일치 여부 검증
        if (!room.isPlaying() || roundId != room.getCurrentRound() || !room.isCurrentDrawer(this)) {
            sendMessage("ERROR|INVALID_DRAW_REQUEST");
            return;
        }

        // 가상 좌표 범위 검증 (0~800, 0~500)
        if (x1 < 0 || x1 > CANVAS_MAX_X || x2 < 0 || x2 > CANVAS_MAX_X
                || y1 < 0 || y1 > CANVAS_MAX_Y || y2 < 0 || y2 > CANVAS_MAX_Y) {
            sendMessage("ERROR|INVALID_DRAW_REQUEST");
            return;
        }

        // 색상 9종 및 펜 굵기(2, 4, 8) / 지우개(#FFFFFF + 16) 조합 검증
        boolean validPen = ALLOWED_COLORS.contains(color) && ALLOWED_PEN_WIDTHS.contains(width);
        boolean validEraser = ERASER_COLOR.equals(color) && width == ERASER_WIDTH;
        if (!validPen && !validEraser) {
            sendMessage("ERROR|INVALID_DRAW_REQUEST");
            return;
        }

        room.broadcast("DRAW_NOTIFY|" + roundId + "|" + x1 + "|" + y1 + "|" + x2 + "|" + y2 + "|" + color + "|" + width);
    }

    // CLEAR|<roundId>
    private void handleClear(String message) {
        if (this.currentRoomId == -1) {
            sendMessage("ERROR|NOT_IN_ROOM");
            return;
        }
        Room room = roomManager.getRoom(this.currentRoomId);
        if (room == null) {
            sendMessage("ERROR|NOT_IN_ROOM");
            return;
        }

        String[] parts = message.split("\\|");
        if (parts.length != 2) {
            sendMessage("ERROR|INVALID_DRAW_REQUEST");
            return;
        }

        int roundId;
        try {
            roundId = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            sendMessage("ERROR|INVALID_DRAW_REQUEST");
            return;
        }

        if (!room.isPlaying() || roundId != room.getCurrentRound() || !room.isCurrentDrawer(this)) {
            sendMessage("ERROR|INVALID_DRAW_REQUEST");
            return;
        }

        room.broadcast("CLEAR_NOTIFY|" + roundId);
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
            roomManager.leaveRoom(this.currentRoomId, this);
            this.currentRoomId = -1;
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