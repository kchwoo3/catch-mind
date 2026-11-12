package com.catchmind.server;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ServerMain extends JFrame {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private JTextArea logArea;
    private JTextField portField;
    private JButton startButton;

    private ServerSocket serverSocket;
    private final SessionManager sessionManager = new SessionManager();

    public ServerMain() {
        setTitle("CatchMind Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 480);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // 1. 상단/중앙 로그 출력 영역
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        add(scrollPane, BorderLayout.CENTER);

        // 2. 하단 포트 입력 및 시작 버튼 영역
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        JPanel portPanel = new JPanel(new BorderLayout(15, 0));
        JLabel portLabel = new JLabel("Port Number");
        portLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        portField = new JTextField("5000");
        portField.setHorizontalAlignment(JTextField.CENTER);
        portField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        portField.setPreferredSize(new Dimension(220, 32));

        portPanel.add(portLabel, BorderLayout.WEST);
        portPanel.add(portField, BorderLayout.CENTER);

        startButton = new JButton("Server Start");
        startButton.setFont(new Font("SansSerif", Font.BOLD, 15));
        startButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        startButton.setPreferredSize(new Dimension(0, 45));
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        bottomPanel.add(portPanel);
        bottomPanel.add(Box.createVerticalStrut(10));
        bottomPanel.add(startButton);

        add(bottomPanel, BorderLayout.SOUTH);

        // 3. Server Start 버튼 이벤트
        startButton.addActionListener(e -> startServer());
    }

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "1~65535 사이의 올바른 포트 번호를 입력하세요.");
            return;
        }

        // UI 상태 변경 (입력칸 및 버튼 비활성화)
        portField.setEditable(false);
        portField.setEnabled(false);
        startButton.setEnabled(false);
        startButton.setText("CatchMind Server Running..");

        // 백그라운드 스레드에서 소켓 수락 루프 실행 (UI 멈춤 방지)
        Thread acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                appendLog("CatchMind Server Running.. (Port: " + port + ")");
                appendLog("Waiting clients ...");

                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    appendLog("Client connected: " + clientSocket.getRemoteSocketAddress());

                    ClientHandler handler = new ClientHandler(clientSocket, sessionManager);
                    Thread clientThread = new Thread(handler);
                    clientThread.start();
                }
            } catch (IOException ex) {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    appendLog("Server error: " + ex.getMessage());
                }
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    // 스레드 안전하게 로그창에 [HH:mm] 형식으로 출력
    public void appendLog(String message) {
        String time = LocalTime.now().format(TIME_FMT);
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + time + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServerMain().setVisible(true));
    }
}