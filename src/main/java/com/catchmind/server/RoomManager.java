package com.catchmind.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RoomManager {
    public enum JoinResult {
        SUCCESS,
        ROOM_NOT_FOUND,
        GAME_IN_PROGRESS,
        ROOM_FULL
    }

    private final Map<Integer, Room> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger roomIdGenerator = new AtomicInteger(1);

    public Room createRoom(String roomName, ClientHandler creator) {
        int roomId = roomIdGenerator.getAndIncrement();
        Room room = new Room(roomId, roomName);
        room.addPlayer(creator);
        rooms.put(roomId, room);
        return room;
    }

    public Room getRoom(int roomId) {
        return rooms.get(roomId);
    }

    public JoinResult joinRoom(int roomId, ClientHandler player) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return JoinResult.ROOM_NOT_FOUND;
        }

        synchronized (room) {
            if (!rooms.containsKey(roomId)) {
                return JoinResult.ROOM_NOT_FOUND;
            }
            if (room.isPlaying()) {
                return JoinResult.GAME_IN_PROGRESS;
            }
            if (room.isFull()) {
                return JoinResult.ROOM_FULL;
            }

            room.addPlayer(player);
            return JoinResult.SUCCESS;
        }
    }

    public void leaveRoom(int roomId, ClientHandler player) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return;
        }

        synchronized (room) {
            ClientHandler previousHost = room.getHost();
            boolean wasDrawer = room.isCurrentDrawer(player);
            boolean removed = room.removePlayer(player);
            if (!removed) {
                return;
            }

            if (room.isEmpty()) {
                room.shutdownScheduler();
                rooms.remove(roomId);
                return;
            }

            ClientHandler newHost = room.getHost();
            room.broadcast(room.buildPlayersMessage());

            if (player.getNickname() != null) {
                room.broadcast("CHAT_NOTIFY|" + player.getNickname() + "님이 퇴장했습니다.");
            }

            if (previousHost != null && previousHost.equals(player) && newHost != null && newHost.getNickname() != null) {
                room.broadcast("CHAT_NOTIFY|" + newHost.getNickname() + "님이 새로운 방장이 되었습니다.");
            }

            room.onPlayerRemoved(player, wasDrawer);
        }
    }

    public String buildRoomsMessage() {
        StringBuilder sb = new StringBuilder("ROOMS");
        for (Room room : rooms.values()) {
            sb.append("|").append(room.getRoomId())
              .append("|").append(room.getRoomName());
        }
        return sb.toString();
    }
}