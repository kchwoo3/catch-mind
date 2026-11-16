package com.catchmind.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RoomManager {
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

    // 방 퇴장 및 빈 방 자동 정리
    public void leaveRoom(int roomId, ClientHandler player) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.removePlayer(player);
            if (room.isEmpty()) {
                rooms.remove(roomId);
            } else {
                room.broadcast(room.buildPlayersMessage());
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