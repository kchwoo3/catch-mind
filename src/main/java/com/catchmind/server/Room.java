package com.catchmind.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Room {
    private final int roomId;
    private final String roomName;
    private final List<ClientHandler> players = new CopyOnWriteArrayList<>();

    public Room(int roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
    }

    public int getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void addPlayer(ClientHandler player) {
        if (!players.contains(player)) {
            players.add(player);
        }
    }

    public void removePlayer(ClientHandler player) {
        players.remove(player);
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public List<ClientHandler> getPlayers() {
        return new ArrayList<>(players);
    }

    // 방 안의 모든 유저에게 메시지 전송 (예: PLAYERS|닉네임1|닉네임2, CHAT|...)
    public void broadcast(String message) {
        for (ClientHandler player : players) {
            player.sendMessage(message);
        }
    }

    // 방 참가자 목록 프로토콜 문자열 생성
    public String buildPlayersMessage() {
        StringBuilder sb = new StringBuilder("PLAYERS");
        for (ClientHandler player : players) {
            if (player.getNickname() != null) {
                sb.append("|").append(player.getNickname());
            }
        }
        return sb.toString();
    }
}