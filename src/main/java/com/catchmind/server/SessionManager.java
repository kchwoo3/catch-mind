package com.catchmind.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SessionManager {
    private final Map<String, ClientHandler> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    /**
     * 닉네임 중복 검사 후 세션 등록.
     * 성공 시 발급된 유저 고유 ID 반환, 중복/유효하지 않으면 -1 반환.
     */
    public synchronized int registerUser(String nickname, ClientHandler handler) {
        if (nickname == null || nickname.isBlank() || activeSessions.containsKey(nickname)) {
            return -1;
        }
        int userId = idGenerator.getAndIncrement();
        activeSessions.put(nickname, handler);
        return userId;
    }

    /**
     * 클라이언트 연결 종료 시 세션 정리
     */
    public void removeUser(String nickname) {
        if (nickname != null) {
            activeSessions.remove(nickname);
            System.out.println("[SessionManager] 세션 정리 완료: " + nickname + " (현재 접속자: " + activeSessions.size() + "명)");
        }
    }

    /**
     * 접속 중인 모든 클라이언트에게 메시지 브로드캐스트
     */
    public void broadcast(String message) {
        for (ClientHandler client : activeSessions.values()) {
            client.sendMessage(message);
        }
    }
}