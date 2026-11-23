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

    // 방 생성 및 생성자 자동 입장
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

    // 방 입장 검증 및 참가자 등록 (정원 4명 제한, 게임 진행 중 차단)
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

    // 방 퇴장, 빈 방 자동 정리 및 방장 위임 처리
    public void leaveRoom(int roomId, ClientHandler player) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return;
        }

        synchronized (room) {
            ClientHandler previousHost = room.getHost();
            boolean removed = room.removePlayer(player);
            if (!removed) {
                return;
            }

            if (room.isEmpty()) {
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
        }
    }

    // 대기실 방 목록 프로토콜 생성: ROOMS|방ID|방이름|... (방 없으면 ROOMS)
    public String buildRoomsMessage() {
        StringBuilder sb = new StringBuilder("ROOMS");
        for (Room room : rooms.values()) {
            sb.append("|").append(room.getRoomId())
              .append("|").append(room.getRoomName());
        }
        return sb.toString();
    }
}