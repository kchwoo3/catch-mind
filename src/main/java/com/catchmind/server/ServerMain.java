package com.catchmind.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerMain {
    private static final int PORT = 5000;

    public static void main(String[] args) {
        SessionManager sessionManager = new SessionManager();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Server] 캐치마인드 서버가 포트 " + PORT + "에서 시작되었습니다.");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] 새 클라이언트 연결: " + clientSocket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(clientSocket, sessionManager);
                Thread clientThread = new Thread(handler);
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("[Server] 서버 소켓 오류: " + e.getMessage());
        }
    }
}