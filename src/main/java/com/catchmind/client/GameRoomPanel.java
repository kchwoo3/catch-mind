package com.catchmind.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

public class GameRoomPanel extends JPanel {
	record FinalStanding(String nickname, int score, int rank) {
	}
	private final JTextArea chatHistory = new JTextArea();
	private final JButton startGameButton = new JButton("게임 시작");
	private final String localNickname;
	private final JTable playerTable;
	private final JLabel roundLabel;
	private final JLabel wordLabel;
	private final JLabel timeLabel;
	private final JProgressBar timeBar;
	private final DrawingPanel canvasPanel;
	private final JLabel canvasNoticeLabel = new JLabel();
	private final JPanel drawingTools;
	private boolean waitingForGame = true;
	private boolean startGamePending;
	private int totalRounds;
	private String currentDrawerNickname;
	private int currentRoundNumber;
	private int currentRoundId;
	private String lastRoundAnswer = "—";
	private Runnable startGameAction;
	private Function<String, CompletableFuture<Void>> chatSender = body -> {
	    System.out.println("CHAT|" + body);
	    return CompletableFuture.completedFuture(null);
	};

	void setChatSender(Function<String, CompletableFuture<Void>> chatSender) {
	    this.chatSender = chatSender;
	}

	public GameRoomPanel(String roomId, String localNickname) {
	    this(roomId, "라운드 —", 0, new Object[0][0], "", localNickname);
	    setDrawingAllowed(false);
	}

	private GameRoomPanel(String roomId, String roundText, int remainingSeconds,
	        Object[][] players, String chatText, String localNickname) {
	    super(new BorderLayout(14, 14));
	    this.localNickname = localNickname;
		setBackground(new Color(0xDDF3FF));
		setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

		JPanel statusPanel = new JPanel(new GridLayout(1, 4, 12, 0));
		statusPanel.setBackground(new Color(0xBFE7FF));
		statusPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

		JLabel roomLabel = new JLabel("방 번호 " + roomId, JLabel.CENTER);
		roundLabel = new JLabel(roundText, JLabel.CENTER);
		wordLabel = new JLabel("제시어 —", JLabel.CENTER);

		for (JLabel label : new JLabel[] { roomLabel, roundLabel, wordLabel }) {
		    label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));
		    label.setForeground(new Color(0x16366D));
		    canvasNoticeLabel.setOpaque(true);
		    canvasNoticeLabel.setBackground(Color.WHITE);
		    canvasNoticeLabel.setBorder(BorderFactory.createCompoundBorder(
		            BorderFactory.createLineBorder(new Color(0xBFE7FF)),
		            BorderFactory.createEmptyBorder(12, 18, 12, 18)));
		    statusPanel.add(label);
		}

		String timeText = remainingSeconds == 0
		        ? "남은 시간 —"
		        : "남은 시간 " + remainingSeconds + "초";

		timeLabel = new JLabel(timeText, JLabel.CENTER);
		timeLabel.setFont(timeLabel.getFont().deriveFont(Font.BOLD, 14f));
		timeLabel.setForeground(new Color(0x16366D));

		timeBar = new JProgressBar();
		timeBar.setValue(remainingSeconds);

		JPanel timerPanel = new JPanel(new BorderLayout(0, 4));
		timerPanel.setOpaque(false);
		timerPanel.add(timeLabel, BorderLayout.NORTH);
		timerPanel.add(timeBar, BorderLayout.CENTER);

		statusPanel.add(timerPanel);

		add(statusPanel, BorderLayout.NORTH);

		canvasPanel = new DrawingPanel();
		canvasPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
		canvasPanel.setLayout(new GridBagLayout());
		canvasNoticeLabel.setFont(canvasNoticeLabel.getFont().deriveFont(Font.BOLD, 18f));
		canvasNoticeLabel.setForeground(new Color(0x16366D));
		canvasNoticeLabel.setVisible(false);
		canvasPanel.add(canvasNoticeLabel);

		drawingTools = canvasPanel.createToolsPanel();
		drawingTools.setOpaque(false);

		JPanel drawingArea = new JPanel(new BorderLayout());
		drawingArea.setOpaque(false);
		drawingArea.add(canvasPanel, BorderLayout.CENTER);
		drawingArea.add(drawingTools, BorderLayout.SOUTH);

		add(drawingArea, BorderLayout.CENTER);

		String[] columns = { "닉네임", "점수", "역할" };
		playerTable = new JTable(new DefaultTableModel(players, columns));

		if (playerTable.getRowCount() > 0) {
		    playerTable.setValueAt("방장", 0, 2);
		}
		playerTable.setDefaultEditor(Object.class, null);
		playerTable.setRowHeight(24);
		playerTable.setFillsViewportHeight(true);

		JPanel playersPanel = new JPanel(new BorderLayout());
		playersPanel.setBackground(Color.WHITE);
		playersPanel.setBorder(BorderFactory.createTitledBorder("참가자 목록"));
		playersPanel.add(new JScrollPane(playerTable), BorderLayout.CENTER);
		startGameButton.addActionListener(event -> startGameAction.run());
		playersPanel.add(startGameButton, BorderLayout.SOUTH);
		updateStartButton();
		playersPanel.setPreferredSize(new Dimension(260, 160));

		JPanel sidePanel = new JPanel(new BorderLayout(0, 10));
		sidePanel.setPreferredSize(new Dimension(260, 0));
		sidePanel.setOpaque(false);
		sidePanel.add(playersPanel, BorderLayout.NORTH);

		chatHistory.setText(chatText);
		chatHistory.setEditable(false);
		chatHistory.setLineWrap(true);
		chatHistory.setWrapStyleWord(true);

		JPanel chatInputPanel = new JPanel(new BorderLayout(4, 0));
		chatInputPanel.setOpaque(false);
		JTextField chatInput = new JTextField();
		JButton chatSendButton = new JButton("전송");

		JLabel chatError = new JLabel("전송 실패. 연결을 확인하세요.");
		chatError.setForeground(new Color(0xA32121));
		chatError.setVisible(false);
		chatInputPanel.add(chatError, BorderLayout.SOUTH);

		ActionListener sendChat = event -> {
		    String body = chatInput.getText().strip();
		    if (body.isEmpty() || !chatSendButton.isEnabled()) {
		        return;
		    }

		    chatError.setVisible(false);
		    chatSendButton.setEnabled(false);
		    chatInput.setEditable(false);

		    chatSender.apply(body).whenComplete((result, error) -> {
		        SwingUtilities.invokeLater(() -> {
		            if (error == null) {
		                chatInput.setText("");
		            } else {
		                chatError.setVisible(true);
		            }

		            chatSendButton.setEnabled(true);
		            chatInput.setEditable(true);
		            chatInput.requestFocusInWindow();
		        });
		    });
		};

		chatInput.addActionListener(sendChat);
		chatSendButton.addActionListener(sendChat);

		chatInputPanel.add(chatInput, BorderLayout.CENTER);
		chatInputPanel.add(chatSendButton, BorderLayout.EAST);

		JPanel chatPanel = new JPanel(new BorderLayout(0, 4));
		chatPanel.setBackground(Color.WHITE);
		chatPanel.setBorder(BorderFactory.createTitledBorder("채팅"));
		chatPanel.add(new JScrollPane(chatHistory), BorderLayout.CENTER);
		chatPanel.add(chatInputPanel, BorderLayout.SOUTH);

		sidePanel.add(chatPanel, BorderLayout.CENTER);

		add(sidePanel, BorderLayout.EAST);
	}
	
	void updatePlayers(String[] nicknames) {
	    Map<String, Integer> currentScores = new HashMap<>();

	    for (int row = 0; row < playerTable.getRowCount(); row++) {
	        String nickname = (String) playerTable.getValueAt(row, 0);
	        int score = (Integer) playerTable.getValueAt(row, 1);
	        currentScores.put(nickname, score);
	    }

	    DefaultTableModel playerModel = (DefaultTableModel) playerTable.getModel();
	    playerModel.setRowCount(0);

	    for (String nickname : nicknames) {
	        playerModel.addRow(new Object[] {
	            nickname, currentScores.getOrDefault(nickname, 0), ""
	        });
	    }

	    updatePlayerRoles();
	    updateStartButton();
	}
	
	void setStartGameAction(Runnable startGameAction) {
	    this.startGameAction = startGameAction;
	    updateStartButton();
	}

	void setWaitingForGame(boolean waitingForGame) {
	    this.waitingForGame = waitingForGame;
	    updateStartButton();
	}
	
	void setStartGamePending(boolean startGamePending) {
	    this.startGamePending = startGamePending;
	    updateStartButton();
	}

	void handleGameStarted(int totalRounds) {
	    this.totalRounds = totalRounds;
	    startGamePending = false;
	    setWaitingForGame(false);
	    roundLabel.setText("라운드 — / " + this.totalRounds);
	    appendSystemMessage("게임이 시작되었습니다.");
	}

	void showStartGameFailure(String message) {
	    if (!startGamePending) {
	        return;
	    }

	    setStartGamePending(false);
	    appendSystemMessage(message);
	}

	private void updateStartButton() {
	    boolean canStart = waitingForGame
	            && !startGamePending
	            && startGameAction != null
	            && playerTable.getRowCount() >= 3
	            && localNickname.equals(playerTable.getValueAt(0, 0));

	    startGameButton.setEnabled(canStart);
	}
	
	private void setDrawingAllowed(boolean allowed) {
	    canvasPanel.setDrawingAllowed(allowed);

	    for (Component tool : drawingTools.getComponents()) {
	        tool.setEnabled(allowed);
	    }
	}
	
	void handleRoundStart(int roundId, int roundNumber, String drawerNickname, int roundSeconds) {
	    if (waitingForGame || roundId <= currentRoundId || roundNumber < 1
	            || roundNumber > totalRounds || drawerNickname.isBlank() || roundSeconds <= 0) {
	        return;
	    }

	    currentRoundId = roundId;
	    startRound(roundNumber, totalRounds, drawerNickname, roundSeconds);
	}

	boolean isCurrentRound(int roundId) {
	    return !waitingForGame && currentDrawerNickname != null && currentRoundId == roundId;
	}
	
	void startRound(int roundNumber, int totalRounds, String drawerNickname, int roundSeconds) {
	    setWaitingForGame(false);
	    currentRoundNumber = roundNumber;
	    lastRoundAnswer = "—";
	    currentDrawerNickname = drawerNickname;
	    canvasNoticeLabel.setVisible(false);
	    canvasPanel.clearDrawing();
	    setDrawingAllowed(localNickname.equals(currentDrawerNickname));
	    wordLabel.setText("제시어 —");
	    roundLabel.setText("라운드 " + roundNumber + " / " + totalRounds);
	    timeBar.setMaximum(roundSeconds);
	    updateRemainingTime(roundSeconds);
	    updatePlayerRoles();
	}

	boolean isGameInProgress() {
	    return !waitingForGame;
	}

	void handleRoundEnd(String answer) {
	    showRoundEnd(answer, currentRoundNumber == totalRounds ? 0 : 3);
	}

	void handleGameCompleted(List<FinalStanding> standings) {
	    showGameCompleted(standings, lastRoundAnswer);
	}
	
	void showRoundEnd(String answer, int transitionSeconds) {
		lastRoundAnswer = answer;
	    currentDrawerNickname = null;
	    setDrawingAllowed(false);
	    updatePlayerRoles();
	    wordLabel.setText("정답 " + answer);
	    timeLabel.setText("라운드 종료");

	    String notice = transitionSeconds > 0
	            ? "라운드가 종료됐습니다. " + transitionSeconds + "초 후 다음 라운드가 시작됩니다."
	            : "라운드가 종료됐습니다.";

	    canvasNoticeLabel.setText(notice);
	    canvasNoticeLabel.setVisible(true);
	}

	private void updatePlayerRoles() {
	    for (int row = 0; row < playerTable.getRowCount(); row++) {
	        boolean isHost = row == 0;
	        boolean isDrawer = currentDrawerNickname != null
	                && currentDrawerNickname.equals(playerTable.getValueAt(row, 0));
	        String role = isHost ? "방장" : "";

	        if (isDrawer) {
	            role = isHost ? "방장 · 출제자" : "출제자";
	        }

	        playerTable.setValueAt(role, row, 2);
	    }
	}
	
	void showRoundCancelled(int transitionSeconds) {
	    currentDrawerNickname = null;
	    setDrawingAllowed(false);
	    updatePlayerRoles();
	    canvasPanel.clearDrawing();
	    wordLabel.setText("제시어 —");
	    timeLabel.setText("라운드 취소");
	    timeBar.setValue(0);
	    canvasNoticeLabel.setText("출제자가 퇴장했습니다. " + transitionSeconds
	            + "초 후 다음 출제자로 넘어갑니다.");
	    canvasNoticeLabel.setVisible(true);
	}
	
	void showWord(String word) {
	    if (localNickname.equals(currentDrawerNickname)) {
	        wordLabel.setText("제시어 " + word);
	    }
	}

	void showWordLength(int letterCount) {
	    if (currentDrawerNickname != null && !localNickname.equals(currentDrawerNickname)) {
	        wordLabel.setText("제시어 " + letterCount + "글자");
	    }
	}
	
	void updateRemainingTime(int remainingSeconds) {
	    timeBar.setValue(remainingSeconds);
	    timeLabel.setText("남은 시간 " + timeBar.getValue() + "초");
	}
	
	void showCorrectAnswer(String nickname, int rank, int earnedPoints) {
	    appendSystemMessage(nickname + "님이 " + rank + "번째로 정답을 맞혔습니다. (+"
	            + earnedPoints + "점)");
	}

	void updateScores(Map<String, Integer> scores) {
	    for (int row = 0; row < playerTable.getRowCount(); row++) {
	        String nickname = (String) playerTable.getValueAt(row, 0);
	        playerTable.setValueAt(scores.getOrDefault(nickname, 0), row, 1);
	    }
	}
	
	void showGameCompleted(List<FinalStanding> standings, String lastAnswer) {
	    Map<String, Integer> finalScores = new HashMap<>();
	    Map<Integer, Integer> rankCounts = new HashMap<>();

	    for (FinalStanding standing : standings) {
	        finalScores.put(standing.nickname(), standing.score());
	        rankCounts.merge(standing.rank(), 1, Integer::sum);
	    }

	    StringBuilder message = new StringBuilder("마지막 제시어 ")
	            .append(lastAnswer).append("\n\n");

	    for (FinalStanding standing : standings) {
	        if (rankCounts.get(standing.rank()) > 1) {
	            message.append("공동 ");
	        }

	        message.append(standing.rank()).append("위  ")
	                .append(standing.nickname()).append("  ")
	                .append(standing.score()).append("점\n");
	    }

	    updateScores(finalScores);
	    returnToWaiting();
	    JOptionPane.showMessageDialog(this, message.toString(), "최종 결과",
	            JOptionPane.INFORMATION_MESSAGE);
	}
	
	void showGameAbortedForTooFewPlayers() {
	    updateScores(Map.of());
	    canvasPanel.clearDrawing();
	    returnToWaiting();

	    JOptionPane.showMessageDialog(this,
	            "참가자가 2명 이하가 되어 이번 게임을 종료합니다.",
	            "게임 종료", JOptionPane.WARNING_MESSAGE);
	}

	private void returnToWaiting() {
	    currentRoundNumber = 0;
	    currentDrawerNickname = null;
	    canvasNoticeLabel.setVisible(false);
	    wordLabel.setText("제시어 —");
	    roundLabel.setText("라운드 —");
	    timeLabel.setText("남은 시간 —");
	    timeBar.setValue(0);
	    setDrawingAllowed(false);
	    updatePlayerRoles();
	    setWaitingForGame(true);
	}
	
	void appendChatMessage(String nickname, String body) {
		appendChatLine(nickname + ": " + body);
	}

	void appendSystemMessage(String body) {
		appendChatLine("[시스템] " + body);
	}

	private void appendChatLine(String line) {
		if (chatHistory.getDocument().getLength() > 0) {
			chatHistory.append("\n");
		}

		chatHistory.append(line);
		chatHistory.setCaretPosition(chatHistory.getDocument().getLength());
	}
	
	private List<FinalStanding> buildPreviewStandings() {
	    List<FinalStanding> orderedScores = new ArrayList<>();

	    for (int row = 0; row < playerTable.getRowCount(); row++) {
	        String nickname = (String) playerTable.getValueAt(row, 0);
	        int score = (Integer) playerTable.getValueAt(row, 1);
	        orderedScores.add(new FinalStanding(nickname, score, 0));
	    }

	    orderedScores.sort(Comparator.comparingInt(FinalStanding::score).reversed());

	    List<FinalStanding> standings = new ArrayList<>();
	    int rank = 1;

	    for (int index = 0; index < orderedScores.size(); index++) {
	        FinalStanding player = orderedScores.get(index);
	        if (index > 0 && player.score() != orderedScores.get(index - 1).score()) {
	            rank = index + 1;
	        }
	        standings.add(new FinalStanding(player.nickname(), player.score(), rank));
	    }

	    return standings;
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("낙서한판 - 게임방 미리보기");
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			Object[][] samplePlayers = {
				    { "그림왕", 0 },
				    { "스케치왕", 0 },
				    { "그림친구", 0 },
				    { "낙서왕", 0 }
				};
				String sampleChat = "그림왕: 안녕하세요!\n스케치왕: 반가워요!";

				String[] roleOptions = { "방장", "일반 참가자" };
				int selectedRole = JOptionPane.showOptionDialog(
				        null, "미리보기에서 확인할 역할을 선택하세요.", "게임방 미리보기",
				        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
				        null, roleOptions, roleOptions[0]);

				if (selectedRole == JOptionPane.CLOSED_OPTION) {
				    frame.dispose();
				    return;
				}

				String previewNickname = selectedRole == 0 ? "그림왕" : "그림친구";
				GameRoomPanel previewRoom =
				        new GameRoomPanel("1", "라운드 —", 0, samplePlayers, sampleChat, previewNickname);

				previewRoom.setDrawingAllowed(false);
				
				int totalRounds = 12;
				int roundSeconds = 140;
				List<String> previewWords = List.of(
				        "해바라기", "소방차", "아이스크림", "자전거", "비행기", "강아지", "무지개",
				        "눈사람", "우산", "카메라", "냉장고", "붕어빵", "시계");

				JButton previewAbortButton = new JButton("인원 부족 종료 미리보기");
				previewAbortButton.setEnabled(false);

				Timer previewTransitionTimer = new Timer(3000, null);
				previewTransitionTimer.setRepeats(false);

				Timer previewTimer = new Timer(1000, event -> {
				    Timer countdown = (Timer) event.getSource();
				    int roundNumber = previewRoom.currentRoundNumber;
				    int remainingSeconds = previewRoom.timeBar.getValue() - 1;
				    previewRoom.updateRemainingTime(remainingSeconds);

				    boolean allCorrect = roundNumber == 1 && remainingSeconds == 131;

				    if (roundNumber == 1) {
				        if (remainingSeconds == 137) {
				            previewRoom.showCorrectAnswer("스케치왕", 1, 100);
				            previewRoom.updateScores(Map.of("그림왕", 20, "스케치왕", 100));
				        } else if (remainingSeconds == 134) {
				            previewRoom.showCorrectAnswer("그림친구", 2, 80);
				            previewRoom.updateScores(Map.of("그림왕", 40, "스케치왕", 100, "그림친구", 80));
				        } else if (allCorrect) {
				            previewRoom.showCorrectAnswer("낙서왕", 3, 60);
				            previewRoom.updateScores(Map.of("그림왕", 60, "스케치왕", 100, "그림친구", 80, "낙서왕", 60));
				        }
				    } else if (roundNumber == 2 && "스케치왕".equals(previewRoom.currentDrawerNickname)) {
				        if (remainingSeconds == 137) {
				            previewRoom.showCorrectAnswer("그림왕", 1, 100);
				            previewRoom.updateScores(Map.of("그림왕", 160, "스케치왕", 120, "그림친구", 80, "낙서왕", 60));
				        } else if (remainingSeconds == 134) {
				            countdown.stop();
				            DefaultTableModel playerModel = (DefaultTableModel) previewRoom.playerTable.getModel();
				            playerModel.removeRow(1);

				            previewRoom.showRoundCancelled(3);
				            previewRoom.updateScores(Map.of("그림왕", 60, "그림친구", 80, "낙서왕", 60));
				            previewTransitionTimer.setActionCommand("retry");
				            previewTransitionTimer.restart();
				            return;
				        }
				    }

				    if (remainingSeconds <= 0 || allCorrect) {
				        countdown.stop();
				        String answer = event.getActionCommand();

				        if (roundNumber == totalRounds) {
				            previewAbortButton.setEnabled(false);
				            previewRoom.showRoundEnd(answer, 0);
				            previewRoom.showGameCompleted(previewRoom.buildPreviewStandings(), answer);
				        } else {
				            previewRoom.showRoundEnd(answer, 3);
				            previewTransitionTimer.setActionCommand("next");
				            previewTransitionTimer.restart();
				        }
				    }
				});

				previewTransitionTimer.addActionListener(event -> {
				    boolean retry = "retry".equals(event.getActionCommand());
				    int nextRound = previewRoom.currentRoundNumber + (retry ? 0 : 1);
				    int playerCount = previewRoom.playerTable.getRowCount();
				    int drawerRow = (nextRound - 1) % playerCount;
				    String drawer = (String) previewRoom.playerTable.getValueAt(drawerRow, 0);

				    int wordIndex = nextRound - 1;
				    if (playerCount < samplePlayers.length) {
				        wordIndex++;
				    }
				    String word = previewWords.get(wordIndex);

				    previewRoom.startRound(nextRound, totalRounds, drawer, roundSeconds);
				    if (previewNickname.equals(drawer)) {
				        previewRoom.showWord(word);
				    } else {
				        previewRoom.showWordLength(word.length());
				    }

				    previewTimer.setActionCommand(word);
				    previewTimer.restart();
				});

				frame.addWindowListener(new WindowAdapter() {
				    @Override
				    public void windowClosed(WindowEvent event) {
				        previewTimer.stop();
				        previewTransitionTimer.stop();
				    }
				});

				previewRoom.setStartGameAction(() -> {
				    previewAbortButton.setEnabled(true);
				    previewRoom.updateScores(Map.of());

				    String firstWord = previewWords.get(0);
				    previewRoom.startRound(1, totalRounds, "그림왕", roundSeconds);
				    previewRoom.showWord(firstWord);
				    previewTimer.setActionCommand(firstWord);
				    previewTimer.restart();
				});
				
			previewRoom.appendSystemMessage("그림친구님이 입장했습니다.");
			previewRoom.setChatSender(body -> {
				previewRoom.appendChatMessage(previewNickname, body);
			    return CompletableFuture.completedFuture(null);
			});

			JButton previewFinishButton = new JButton("12라운드 완료 미리보기");
			previewFinishButton.addActionListener(event -> {
				previewAbortButton.setEnabled(false);
			    previewTimer.stop();
			    previewTransitionTimer.stop();

			    DefaultTableModel playerModel = (DefaultTableModel) previewRoom.playerTable.getModel();
			    playerModel.setRowCount(0);
			    for (Object[] player : samplePlayers) {
			        playerModel.addRow(player);
			    }

			    previewRoom.startRound(12, 12, "그림왕", 140);
			    previewRoom.showRoundEnd("아이스크림", 0);
			    previewRoom.showGameCompleted(List.of(
			            new FinalStanding("그림왕", 720, 1),
			            new FinalStanding("스케치왕", 720, 1),
			            new FinalStanding("그림친구", 500, 3),
			            new FinalStanding("낙서왕", 500, 3)), "아이스크림");
			});

			previewAbortButton.addActionListener(event -> {
			    previewTimer.stop();
			    previewTransitionTimer.stop();
			    previewAbortButton.setEnabled(false);

			    DefaultTableModel playerModel =
			            (DefaultTableModel) previewRoom.playerTable.getModel();
			    playerModel.setRowCount(2);

			    previewRoom.showGameAbortedForTooFewPlayers();
			});

			JPanel previewControls = new JPanel();
			previewControls.add(previewFinishButton);
			previewControls.add(previewAbortButton);

			JPanel previewContent = new JPanel(new BorderLayout());
			previewContent.add(previewRoom, BorderLayout.CENTER);
			previewContent.add(previewControls, BorderLayout.SOUTH);
			frame.setContentPane(previewContent);
			frame.setSize(1080, 660);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}
}