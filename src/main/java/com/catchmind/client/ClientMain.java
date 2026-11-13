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
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.net.URL;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import java.awt.FlowLayout;

public class ClientMain {
	private record RoomInfo(String id, String name) {
	    @Override
	    public String toString() {
	        return name;
	    }
	}
    public static void main(String[] args) {
    	SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("낙서한판 로그인");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(760, 420);
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
                
                JList<RoomInfo> roomList = new JList<>();

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
                            	JPanel lobbyPanel = new JPanel(new BorderLayout(14, 14));
                            	lobbyPanel.setBackground(new Color(0xDDF3FF));
                            	lobbyPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

                            	JLabel lobbyTitle = new JLabel("낙서한판 대기실");
                            	lobbyTitle.setFont(lobbyTitle.getFont().deriveFont(Font.BOLD, 24f));
                            	lobbyTitle.setForeground(new Color(0x16366D));

                            	URL lobbyIconUrl = ClientMain.class.getResource("/images/login-sketchbook.png");
                            	if (lobbyIconUrl != null) {
                            	    Image icon = new ImageIcon(lobbyIconUrl).getImage();
                            	    lobbyTitle.setIcon(new ImageIcon(
                            	        icon.getScaledInstance(44, 44, Image.SCALE_SMOOTH)
                            	    ));
                            	    lobbyTitle.setIconTextGap(10);
                            	}

                            	JPanel lobbyHeader = new JPanel(new BorderLayout());
                            	lobbyHeader.setBackground(new Color(0xDDF3FF));
                            	lobbyHeader.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
                            	lobbyHeader.add(lobbyTitle, BorderLayout.WEST);
                            	lobbyHeader.add(new JLabel(nickname + "님"), BorderLayout.EAST);
                            	lobbyPanel.add(lobbyHeader, BorderLayout.NORTH);

                            	JPanel roomsPanel = new JPanel(new BorderLayout(0, 8));
                            	roomsPanel.setBackground(new Color(0xDDF3FF));
                            	roomsPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
                            	roomsPanel.add(new JLabel("방 목록"), BorderLayout.NORTH);
                            	roomsPanel.add(new JScrollPane(roomList), BorderLayout.CENTER);
                            	lobbyPanel.add(roomsPanel, BorderLayout.CENTER);

                            	JList<String> lobbyUserList = new JList<>(new String[] { nickname });
                            	JPanel lobbyUsersPanel = new JPanel(new BorderLayout(0, 8));
                            	lobbyUsersPanel.setBackground(new Color(0xDDF3FF));
                            	lobbyUsersPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
                            	lobbyUsersPanel.add(new JLabel("접속자 목록"), BorderLayout.NORTH);
                            	lobbyUsersPanel.add(new JScrollPane(lobbyUserList), BorderLayout.CENTER);
                            	lobbyUsersPanel.setPreferredSize(new Dimension(220, 0));
                            	lobbyPanel.add(lobbyUsersPanel, BorderLayout.EAST);
                            	
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

                            	JButton createRoomButton = new JButton("방 만들기");

                            	createRoomButton.addActionListener(createEvent -> {
                            	    String input = JOptionPane.showInputDialog(frame, "방 제목을 입력하세요.");
                            	    if (input == null) {
                            	        return;
                            	    }

                            	    String roomName = input.strip();
                            	    if (roomName.isEmpty()) {
                            	        JOptionPane.showMessageDialog(frame, "방 제목을 입력하세요.");
                            	        return;
                            	    }

                            	    if (roomName.contains("|") || roomName.contains("\n") || roomName.contains("\r")) {
                            	        JOptionPane.showMessageDialog(frame, "방 제목에는 |와 줄바꿈을 넣을 수 없습니다.");
                            	        return;
                            	    }

                            	    String request = "CREATE_ROOM|" + roomName;
                            	    System.out.println("방 생성 요청 = " + request);
                            	    new Thread(() -> {
                            	        try {
                            	            writer.write(request + "\n");
                            	            writer.flush();
                            	        } catch (IOException ex) {
                            	            System.out.println("방 생성 요청 실패 = " + ex.getMessage());
                            	        }
                            	    }).start();
                            	});
                            	
                            	JButton quickJoinButton = new JButton("빠른 입장");
                            	quickJoinButton.addActionListener(joinEvent -> {
                            	    RoomInfo selectedRoom = roomList.getSelectedValue();

                            	    if (selectedRoom == null) {
                            	        JOptionPane.showMessageDialog(frame, "입장할 방을 선택하세요.");
                            	        return;
                            	    }

                            	    String request = "JOIN_ROOM|" + selectedRoom.id();
                            	    new Thread(() -> {
                            	        try {
                            	            writer.write(request + "\n");
                            	            writer.flush();
                            	            System.out.println("방 입장 요청 전송 = " + request);
                            	        } catch (IOException ex) {
                            	            System.out.println("방 입장 요청 실패 = " + ex.getMessage());
                            	        }
                            	    }).start();
                            	});
                            	
                            	JPanel lobbyButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                            	lobbyButtons.setOpaque(false);
                            	lobbyButtons.add(refreshButton);
                            	lobbyButtons.add(createRoomButton);
                            	lobbyButtons.add(quickJoinButton);
                            	lobbyPanel.add(lobbyButtons, BorderLayout.SOUTH);

                                frame.setContentPane(lobbyPanel);
                                frame.setTitle("낙서한판 대기실");
                                frame.setSize(1080, 660);
                                frame.setLocationRelativeTo(null);
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
                                boolean enteringRoom = "ROOM_CREATED".equals(parts[0]) || "JOIN_OK".equals(parts[0]);
                                if (enteringRoom && parts.length == 2 && !parts[1].isBlank()) {
                                    String roomId = parts[1];

                                    SwingUtilities.invokeLater(() -> {
                                        JPanel roomPanel = new JPanel(new BorderLayout());
                                        roomPanel.add(new JLabel("방 번호 " + roomId, JLabel.CENTER), BorderLayout.CENTER);

                                        frame.setContentPane(roomPanel);
                                        frame.setTitle("낙서한판 - 방 " + roomId);
                                        frame.setSize(1080, 660);
                                        frame.setLocationRelativeTo(null);
                                        frame.revalidate();
                                        frame.repaint();
                                    });

                                    continue;
                                }

                                if (!"ROOMS".equals(parts[0]) || parts.length % 2 == 0) {
                                    continue;
                                }

                                RoomInfo[] rooms = new RoomInfo[(parts.length - 1) / 2];

                                for (int i = 0; i < rooms.length; i++) {
                                    String id = parts[i * 2 + 1];
                                    String name = parts[i * 2 + 2];
                                    rooms[i] = new RoomInfo(id, name);
                                }

                                SwingUtilities.invokeLater(() -> {
                                    roomList.setListData(rooms);
                                    refreshButton.setEnabled(true);
                                });
                            }

                            System.out.println("서버 연결이 종료됐습니다.");
                        }
                        else {
                        	SwingUtilities.invokeLater(() -> {
                        	    JOptionPane.showMessageDialog(frame, "로그인에 실패했습니다.");
                        	});
                        }
                    } catch (IOException ex) {
                        System.out.println("통신 실패 = " + ex.getMessage());
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(frame, "서버 통신에 실패했습니다.");
                        });
                    }
                }).start();
            });

            JLabel illustration = new JLabel("", JLabel.CENTER);
            URL imageUrl = ClientMain.class.getResource("/images/login-sketchbook.png");
            if (imageUrl != null) {
                Image image = new ImageIcon(imageUrl).getImage();
                illustration.setIcon(new ImageIcon(
                    image.getScaledInstance(230, 230, Image.SCALE_SMOOTH)
                ));
            }
            illustration.setOpaque(true);
            illustration.setBackground(new Color(0xDDF3FF));

            JLabel title = new JLabel("낙서한판", JLabel.CENTER);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 32f));
            title.setForeground(new Color(0x16366D));

            panel.setOpaque(false);

            JPanel formCard = new JPanel(new BorderLayout(0, 18));
            formCard.setOpaque(false);
            formCard.add(title, BorderLayout.NORTH);
            formCard.add(panel, BorderLayout.CENTER);
            formCard.add(connectButton, BorderLayout.SOUTH);

            JPanel formSide = new JPanel(new GridBagLayout());
            formSide.setBackground(new Color(0xDDF3FF));
            formSide.add(formCard);

            JPanel loginScreen = new JPanel(new GridLayout(1, 2, 14, 0));
            loginScreen.setBackground(new Color(0xDDF3FF));
            loginScreen.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            loginScreen.add(illustration);
            loginScreen.add(formSide);
            frame.setContentPane(loginScreen);
            
            frame.setVisible(true);
        });
}
}
