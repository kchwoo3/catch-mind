package com.catchmind.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SessionManager {
    private final Map<String, ClientHandler> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    public synchronized int registerUser(String nickname, ClientHandler handler) {
        if (nickname == null || nickname.isBlank() || activeSessions.containsKey(nickname)) {
            return -1;
        }
        int userId = idGenerator.getAndIncrement();
        activeSessions.put(nickname, handler);
        return userId;
    }

    public void removeUser(String nickname) {
        if (nickname != null) {
            activeSessions.remove(nickname);
            System.out.println("[SessionManager] 세션 정리 완료: " + nickname + " (현재 접속자: " + activeSessions.size() + "명)");
        }
    }

    public void broadcast(String message) {
        for (ClientHandler client : activeSessions.values()) {
            client.sendMessage(message);
        }
    }

    // 대기실 우측 접속자 목록 갱신 (LOBBY_USERS|닉네임1|닉네임2...)
    public void broadcastLobbyUsers() {
        StringBuilder sb = new StringBuilder("LOBBY_USERS");
        for (ClientHandler client : activeSessions.values()) {
            if (client.getCurrentRoomId() == -1 && client.getNickname() != null) {
                sb.append("|").append(client.getNickname());
            }
        }
        String msg = sb.toString();
        for (ClientHandler client : activeSessions.values()) {
            if (client.getCurrentRoomId() == -1) {
                client.sendMessage(msg);
            }
        }
    }

    // 대기실에 있는 모든 유저에게 최신 방 목록 전송
    public void broadcastRoomsToLobby(String roomsMessage) {
        for (ClientHandler client : activeSessions.values()) {
            if (client.getCurrentRoomId() == -1) {
                client.sendMessage(roomsMessage);
            }
        }
    }
}