package com.catchmind.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final SessionManager sessionManager;
    private BufferedReader in;
    private PrintWriter out;

    private int userId = -1;
    private String nickname = null;

    public ClientHandler(Socket socket, SessionManager sessionManager) {
        this.socket = socket;
        this.sessionManager = sessionManager;
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
                    sendMessage("LOGIN_OK|" + userId + "|" + nickname);
                    System.out.println("[Server] 로그인 성공: ID=" + userId + ", Nickname=" + nickname);
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

    private void cleanup() {
        sessionManager.removeUser(this.nickname);
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[Server] 리소스 정리 중 오류: " + e.getMessage());
        }
    }
}