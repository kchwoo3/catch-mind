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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import javax.swing.JList;
import javax.swing.JScrollPane;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ClientMain {
    public static void main(String[] args) {
    	SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("낙서한판 로그인");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(360, 220);
            frame.setLocationRelativeTo(null);
            JPanel panel = new JPanel(new GridLayout(3, 2, 15, 15));

            JTextField ipField = new JTextField("127.0.0.1", 12);
            JTextField portField = new JTextField("9999", 12);
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
                
                JList<String> roomList = new JList<>();

                JButton refreshButton = new JButton("새로고침");
                refreshButton.setEnabled(false);
                
                new Thread(() -> {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(ip, port), 3000);
                        System.out.println("TCP 연결 성공");
                        
                        socket.setSoTimeout(3000);

                        BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                        );
                        BufferedWriter writer = new BufferedWriter(
                        	    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                        	);

                        	writer.write("LOGIN|" + nickname + "\n");
                        	writer.flush();
                        String response = reader.readLine();
                        System.out.println("서버 응답 = " + response);
                        if ("LOGIN_OK".equals(response)) {
                            SwingUtilities.invokeLater(() -> {
                            	JPanel lobbyPanel = new JPanel(new BorderLayout());
                            	lobbyPanel.add(new JLabel(nickname + "님, 대기실입니다."), BorderLayout.NORTH);
                            	lobbyPanel.add(new JScrollPane(roomList), BorderLayout.CENTER);
                            	refreshButton.addActionListener(refreshEvent -> {
                            	    refreshButton.setEnabled(false);

                            	    new Thread(() -> {
                            	        try {
                            	            writer.write("LIST_ROOMS\n");
                            	            writer.flush();
                            	        } catch (IOException ex) {
                            	            System.out.println("방 목록 요청 실패 = " + ex.getMessage());
                            	        }
                            	    }).start();
                            	});

                            	lobbyPanel.add(refreshButton, BorderLayout.SOUTH);

                                frame.setContentPane(lobbyPanel);
                                frame.setTitle("낙서한판 대기실");
                                frame.addWindowListener(new WindowAdapter() {
                                    @Override
                                    public void windowClosing(WindowEvent event) {
                                        try {
                                            socket.close();
                                        } catch (IOException ex) {
                                            System.out.println("소켓 종료 중 오류 = " + ex.getMessage());
                                        }
                                    }
                                });
                                frame.revalidate();
                                frame.repaint();
                            });
                            writer.write("LIST_ROOMS\n");
                            writer.flush();

                            socket.setSoTimeout(0);

                            String roomResponse;
                            while ((roomResponse = reader.readLine()) != null) {
                                System.out.println("서버 메시지 = " + roomResponse);
                                String[] parts = roomResponse.split("\\|", -1);

                                if (!"ROOMS".equals(parts[0]) || parts.length % 2 == 0) {
                                    continue;
                                }

                                String[] roomNames = new String[(parts.length - 1) / 2];
                                for (int i = 0; i < roomNames.length; i++) {
                                    roomNames[i] = parts[i * 2 + 2];
                                }

                                SwingUtilities.invokeLater(() -> {
                                    roomList.setListData(roomNames);
                                    refreshButton.setEnabled(true);
                                });
                            }

                            System.out.println("서버 연결이 종료됐습니다.");
                        }
                    } catch (IOException ex) {
                        System.out.println("통신 실패 = " + ex.getMessage());
                    }
                }).start();
            });

            frame.add(connectButton, BorderLayout.SOUTH);
            
            frame.setVisible(true);
        });
}
}
