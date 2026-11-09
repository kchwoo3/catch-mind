package com.catchmind.client;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.GridLayout;
import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ClientMain {
    public static void main(String[] args) {
    	SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("낙서한판 로그인");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(360, 220);
            frame.setLocationRelativeTo(null);
            JPanel panel = new JPanel(new GridLayout(3, 2, 15, 15));

            JTextField ipField = new JTextField("127.0.0.1", 12);
            JTextField portField = new JTextField("5000", 12);
            JTextField nicknameField = new JTextField(12);

            panel.add(new JLabel("서버 IP"));
            panel.add(ipField);
            panel.add(new JLabel("포트"));
            panel.add(portField);
            panel.add(new JLabel("닉네임"));
            panel.add(nicknameField);

            frame.add(panel);
            
            JButton connectButton = new JButton("접속");

            connectButton.addActionListener(event -> {
                String ip = ipField.getText();
                String portText = portField.getText().trim();
                int port;

                try {
                    port = Integer.parseInt(portText);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame, "포트를 정수로 입력하세요.");
                    return;
                }

                if (port < 1 || port > 65535) {
                    JOptionPane.showMessageDialog(frame, "포트는 1~65535 사이여야 합니다.");
                    return;
                }
                String nickname = nicknameField.getText();

                System.out.println("서버 IP = " + ip);
                System.out.println("포트 = " + port);
                System.out.println("닉네임 = " + nickname);
                
                new Thread(() -> {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(ip, port), 3000);
                        System.out.println("TCP 연결 성공");
                    } catch (IOException ex) {
                        System.out.println("TCP 연결 실패 = " + ex.getMessage());
                    }
                }).start();
            });

            frame.add(connectButton, BorderLayout.SOUTH);
            
            frame.setVisible(true);
        });
}
}
