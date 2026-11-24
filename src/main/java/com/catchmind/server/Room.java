package com.catchmind.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Room {
    public static final int MAX_PLAYERS = 4;

    private final int roomId;
    private final String roomName;
    private final List<ClientHandler> players = new CopyOnWriteArrayList<>();
    private volatile boolean playing = false;
    private volatile int currentRound = 0;
    private volatile ClientHandler currentDrawer = null;

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

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public ClientHandler getCurrentDrawer() {
        return currentDrawer;
    }

    public void setCurrentDrawer(ClientHandler currentDrawer) {
        this.currentDrawer = currentDrawer;
    }

    public boolean isCurrentDrawer(ClientHandler client) {
        return client != null && client.equals(currentDrawer);
    }

    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public ClientHandler getHost() {
        return players.isEmpty() ? null : players.get(0);
    }

    public boolean isHost(ClientHandler client) {
        return client != null && client.equals(getHost());
    }

    public void addPlayer(ClientHandler player) {
        if (!players.contains(player)) {
            players.add(player);
        }
    }

    public boolean removePlayer(ClientHandler player) {
        return players.remove(player);
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public List<ClientHandler> getPlayers() {
        return new ArrayList<>(players);
    }

    // 방 안의 모든 유저에게 메시지 전송
    public void broadcast(String message) {
        for (ClientHandler player : players) {
            player.sendMessage(message);
        }
    }

    // 방 참가자 목록 프로토콜 문자열 생성 (첫 번째 항목이 항상 방장)
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