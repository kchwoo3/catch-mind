package com.catchmind.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RoomManager {
    public enum JoinResult {
        SUCCESS,
        ROOM_NOT_FOUND,
        ROOM_FULL,
        GAME_IN_PROGRESS
    }

    private final Map<Integer, Room> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger nextRoomId = new AtomicInteger(1);

    public Room createRoom(String roomName, ClientHandler host) {
        int id = nextRoomId.getAndIncrement();
        Room room = new Room(id, roomName);
        room.addPlayer(host);
        rooms.put(id, room);
        return room;
    }

    public JoinResult joinRoom(int roomId, ClientHandler player) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return JoinResult.ROOM_NOT_FOUND;
        }
        synchronized (room) {
            if (room.isPlaying()) {
                return JoinResult.GAME_IN_PROGRESS;
            }
            if (room.isFull()) {
                return JoinResult.ROOM_FULL;
            }
            room.addPlayer(player);
            player.setCurrentRoomId(roomId);
            player.sendMessage("JOIN_OK|" + roomId);
            room.broadcast(room.buildPlayersMessage());
            room.broadcast(room.buildScoresMessage());
            room.broadcast("CHAT_NOTIFY|" + player.getNickname() + "님이 입장했습니다.");
            return JoinResult.SUCCESS;
        }
    }

    public void leaveRoom(int roomId, ClientHandler player) {
        Room room = rooms.get(roomId);
        if (room == null) return;

        boolean becameEmpty;
        synchronized (room) {
            ClientHandler prevHost = room.getHost();
            room.removePlayer(player);
            becameEmpty = room.isEmpty();

            if (!becameEmpty) {
                ClientHandler newHost = room.getHost();
                boolean hostChanged = (prevHost != null && prevHost.equals(player) && newHost != null);

                room.broadcast(room.buildPlayersMessage());
                if (hostChanged) {
                    room.broadcast("CHAT_NOTIFY|" + newHost.getNickname() + "님이 새로운 방장이 되었습니다.");
                }
                room.broadcast(room.buildScoresMessage());
                room.broadcast("CHAT_NOTIFY|" + player.getNickname() + "님이 퇴장했습니다.");
                room.onPlayerLeftPostNotify();
            }
        }

        if (becameEmpty) {
            rooms.remove(roomId);
        }
    }

    public Room getRoom(int roomId) {
        return rooms.get(roomId);
    }

    public String buildRoomsMessage() {
        if (rooms.isEmpty()) {
            return "ROOMS";
        }
        StringBuilder sb = new StringBuilder("ROOMS");
        for (Room room : rooms.values()) {
            sb.append("|").append(room.getRoomId()).append("|").append(room.getRoomName());
        }
        return sb.toString();
    }
}