package com.catchmind.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class ClientMain {
	private record RoomInfo(String id, String name) {
		@Override
		public String toString() {
			return name;
		}
	}

	private static void handleGameRoomMessage(GameRoomPanel gameRoom, String[] parts) {
		try {
			switch (parts[0]) {
			case "SCORES" -> handleScores(gameRoom, parts);
			case "GAME_END" -> handleGameEnd(gameRoom, parts);
			default -> handleRoundMessage(gameRoom, parts);
			}
		} catch (NumberFormatException ex) {
			System.out.println("잘못된 게임 메시지 = " + Arrays.toString(parts));
		}
	}

	private static void handleRoundMessage(GameRoomPanel gameRoom, String[] parts) {
		String type = parts[0];
		int expectedLength = switch (type) {
		case "ROUND_START", "CORRECT_NOTIFY" -> 5;
		case "ROUND_END", "ROUND_CANCEL" -> 4;
		case "DRAW_NOTIFY" -> 8;
		case "CLEAR_NOTIFY" -> 2;
		default -> 3;
		};

		if (parts.length != expectedLength) {
			return;
		}

		int roundId = Integer.parseInt(parts[1]);

		if ("ROUND_START".equals(type)) {
			int roundNumber = Integer.parseInt(parts[2]);
			int roundSeconds = Integer.parseInt(parts[4]);
			gameRoom.handleRoundStart(roundId, roundNumber, parts[3], roundSeconds);
			return;
		}

		if (!gameRoom.isCurrentRound(roundId)) {
			return;
		}

		switch (type) {
		case "WORD" -> gameRoom.showWord(parts[2]);
		case "WORD_LENGTH" -> gameRoom.showWordLength(Integer.parseInt(parts[2]));
		case "ROUND_TIME" -> gameRoom.updateRemainingTime(Integer.parseInt(parts[2]));
		case "DRAW_NOTIFY" -> handleDrawingSegment(gameRoom, parts);
		case "CLEAR_NOTIFY" -> gameRoom.showDrawingCleared();
		case "CORRECT_NOTIFY" ->
			gameRoom.showCorrectAnswer(parts[2], Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
		case "ROUND_END" -> {
			if ("ALL_CORRECT".equals(parts[2]) || "TIMEOUT".equals(parts[2])) {
				gameRoom.handleRoundEnd(parts[2], parts[3]);
			}
		}
		case "ROUND_CANCEL" -> {
			if ("DRAWER_LEFT".equals(parts[2])) {
				gameRoom.showRoundCancelled(Integer.parseInt(parts[3]));
			}
		}
		}
	}

	private static void handleDrawingSegment(GameRoomPanel gameRoom, String[] parts) {
	    String colorCode = parts[6];
	    if (colorCode.length() != 7 || colorCode.charAt(0) != '#') {
	        return;
	    }

	    Color color = Color.decode(colorCode);
	    int width = Integer.parseInt(parts[7]);
	    if (width != 2 && width != 4 && width != 8
	            && !(width == 16 && Color.WHITE.equals(color))) {
	        return;
	    }

	    gameRoom.showDrawingSegment(
	            Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
	            Integer.parseInt(parts[4]), Integer.parseInt(parts[5]),
	            color, width);
	}
	
	private static void handleScores(GameRoomPanel gameRoom, String[] parts) {
		if (parts.length % 2 == 0) {
			return;
		}

		Map<String, Integer> scores = new HashMap<>();
		for (int index = 1; index < parts.length; index += 2) {
			scores.put(parts[index], Integer.parseInt(parts[index + 1]));
		}

		gameRoom.updateScores(scores);
	}

	private static void handleGameEnd(GameRoomPanel gameRoom, String[] parts) {
		if (!gameRoom.isGameInProgress() || parts.length < 2) {
			return;
		}

		if (parts.length == 2 && "NOT_ENOUGH_PLAYERS".equals(parts[1])) {
			gameRoom.showGameAbortedForTooFewPlayers();
			return;
		}

		if (!"COMPLETED".equals(parts[1]) || parts.length < 5 || (parts.length - 2) % 3 != 0) {
			return;
		}

		List<GameRoomPanel.FinalStanding> standings = new ArrayList<>();
		for (int index = 2; index < parts.length; index += 3) {
			standings.add(new GameRoomPanel.FinalStanding(parts[index], Integer.parseInt(parts[index + 1]),
					Integer.parseInt(parts[index + 2])));
		}

		gameRoom.handleGameCompleted(standings);
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
				String nickname = nicknameField.getText().trim();

				System.out.println("서버 IP = " + ip);
				System.out.println("포트 = " + port);
				System.out.println("닉네임 = " + nickname);

				JList<RoomInfo> roomList = new JList<>();
				JList<String> lobbyUserList = new JList<>();

				JButton refreshButton = new JButton("새로고침");
				refreshButton.setEnabled(false);

				new Thread(() -> {
					try (ExecutorService chatSendExecutor = Executors.newSingleThreadExecutor();
							Socket socket = new Socket()) {
						socket.connect(new InetSocketAddress(ip, port), 3000);
						System.out.println("TCP 연결 성공");

						socket.setSoTimeout(3000);

						BufferedReader reader = new BufferedReader(
								new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
						BufferedWriter writer = new BufferedWriter(
								new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

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
									lobbyTitle
											.setIcon(new ImageIcon(icon.getScaledInstance(44, 44, Image.SCALE_SMOOTH)));
									lobbyTitle.setIconTextGap(10);
								}

								JPanel lobbyHeader = new JPanel(new BorderLayout());
								lobbyHeader.setBackground(new Color(0xDDF3FF));
								lobbyHeader.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
								lobbyHeader.add(lobbyTitle, BorderLayout.WEST);
								lobbyHeader.add(new JLabel("내 닉네임 : " + nickname), BorderLayout.EAST);
								lobbyPanel.add(lobbyHeader, BorderLayout.NORTH);

								JPanel roomsPanel = new JPanel(new BorderLayout(0, 8));
								roomsPanel.setBackground(new Color(0xDDF3FF));
								roomsPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
								roomsPanel.add(new JLabel("방 목록"), BorderLayout.NORTH);
								roomsPanel.add(new JScrollPane(roomList), BorderLayout.CENTER);
								lobbyPanel.add(roomsPanel, BorderLayout.CENTER);

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
								frame.setTitle("낙서한판 대기실 - " + nickname);
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
								if (roomResponse.startsWith("CHAT|")) {
									String[] chat = roomResponse.split("\\|", 3);

									if (chat.length == 3) {
										SwingUtilities.invokeLater(() -> {
											if (frame.getContentPane() instanceof GameRoomPanel gameRoom) {
												gameRoom.appendChatMessage(chat[1], chat[2]);
											}
										});
									}

									continue;
								}

								if (roomResponse.startsWith("CHAT_NOTIFY|")) {
									String[] notice = roomResponse.split("\\|", 2);

									if (notice.length == 2) {
										SwingUtilities.invokeLater(() -> {
											if (frame.getContentPane() instanceof GameRoomPanel gameRoom) {
												gameRoom.appendSystemMessage(notice[1]);
											}
										});
									}

									continue;
								}
								String[] parts = roomResponse.split("\\|", -1);
								if ("GAME_STARTED".equals(parts[0]) && parts.length == 2) {
									try {
										int totalRounds = Integer.parseInt(parts[1]);

										if (totalRounds > 0) {
											SwingUtilities.invokeLater(() -> {
												if (frame.getContentPane() instanceof GameRoomPanel gameRoom) {
													gameRoom.handleGameStarted(totalRounds);
												}
											});
										}
									} catch (NumberFormatException ex) {
										System.out.println("잘못된 게임 시작 응답 = " + roomResponse);
									}

									continue;
								}

								if ("ERROR".equals(parts[0]) && parts.length == 2) {
									String errorCode = parts[1];
									String message = switch (errorCode) {
									case "NOT_HOST" -> "방장만 게임을 시작할 수 있습니다.";
									case "NOT_ENOUGH_PLAYERS" -> "참가자가 3명 이상이어야 합니다.";
									case "GAME_IN_PROGRESS" -> "이미 게임이 진행 중입니다.";
									case "NOT_IN_ROOM" -> "게임방에 입장한 상태에서 시작할 수 있습니다.";
									case "UNKNOWN_COMMAND" -> "현재 서버에서 게임 시작 요청을 처리하지 못했습니다.";
									case "ROOM_FULL" -> "게임방의 정원이 찼습니다.";
									case "ROOM_NOT_FOUND" -> "입장할 게임방을 찾을 수 없습니다.";
									default -> null;
									};

									if (message != null) {
										SwingUtilities.invokeLater(() -> {
											if (frame.getContentPane() instanceof GameRoomPanel gameRoom) {
												gameRoom.showStartGameFailure(message);
											} else if ("ROOM_FULL".equals(errorCode)
													|| "ROOM_NOT_FOUND".equals(errorCode)
													|| "GAME_IN_PROGRESS".equals(errorCode)) {
												JOptionPane.showMessageDialog(frame, message, "입장 실패",
														JOptionPane.WARNING_MESSAGE);
											}
										});
									}
									continue;
								}
								boolean gameRoomMessage = switch (parts[0]) {
								case "ROUND_START", "WORD", "WORD_LENGTH", "ROUND_TIME", "CORRECT_NOTIFY", "SCORES",
						        "ROUND_END", "ROUND_CANCEL", "GAME_END", "DRAW_NOTIFY", "CLEAR_NOTIFY" -> true;
								default -> false;
								};

								if (gameRoomMessage) {
									SwingUtilities.invokeLater(() -> {
										if (frame.getContentPane() instanceof GameRoomPanel gameRoom) {
											handleGameRoomMessage(gameRoom, parts);
										}
									});
									continue;
								}
								if ("PLAYERS".equals(parts[0])) {
									String[] players = Arrays.copyOfRange(parts, 1, parts.length);

									SwingUtilities.invokeLater(() -> {
										if (frame.getContentPane() instanceof GameRoomPanel gameRoom) {
											gameRoom.updatePlayers(players);
										}
									});

									continue;
								}
								if ("LOBBY_USERS".equals(parts[0])) {
									String[] users = Arrays.copyOfRange(parts, 1, parts.length);

									SwingUtilities.invokeLater(() -> lobbyUserList.setListData(users));
									continue;
								}
								boolean enteringRoom = "ROOM_CREATED".equals(parts[0]) || "JOIN_OK".equals(parts[0]);
								if (enteringRoom && parts.length == 2 && !parts[1].isBlank()) {
									String roomId = parts[1];

									SwingUtilities.invokeLater(() -> {
										GameRoomPanel gameRoom = new GameRoomPanel(roomId, nickname);

										Function<String, CompletableFuture<Void>> sendRoomCommand = command -> {
											try {
												return CompletableFuture.runAsync(() -> {
													try {
														writer.write(command + "\n");
														writer.flush();
													} catch (IOException ex) {
														throw new CompletionException(ex);
													}
												}, chatSendExecutor);
											} catch (RejectedExecutionException ex) {
												return CompletableFuture.failedFuture(ex);
											}
										};

										gameRoom.setChatSender(body -> sendRoomCommand.apply("CHAT|" + body));
										
										gameRoom.setDrawingSender(command -> sendRoomCommand.apply(command).exceptionally(error -> {
										    System.out.println("그림 전송 실패 = " + error.getMessage());
										    return null;
										}));
										
										gameRoom.setStartGameAction(() -> {
											gameRoom.setStartGamePending(true);

											sendRoomCommand.apply("START_GAME").whenComplete((result, error) -> {
												if (error != null) {
													SwingUtilities.invokeLater(() -> gameRoom
															.showStartGameFailure("게임 시작 요청을 보내지 못했습니다."));
												}
											});
										});
										frame.setContentPane(gameRoom);
										frame.setTitle("낙서한판 - 방 " + roomId + " - " + nickname);
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
						} else {
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
				illustration.setIcon(new ImageIcon(image.getScaledInstance(230, 230, Image.SCALE_SMOOTH)));
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
