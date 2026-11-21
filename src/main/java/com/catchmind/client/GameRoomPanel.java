package com.catchmind.client;

import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import java.awt.Color;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import java.awt.Dimension;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.Font;
import javax.swing.JProgressBar;
import java.awt.event.ActionListener;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.table.DefaultTableModel;

public class GameRoomPanel extends JPanel {
	private final JTextArea chatHistory = new JTextArea();
	private final JButton startGameButton = new JButton("게임 시작");
	private final String localNickname;
	private final JTable playerTable;
	private boolean waitingForGame = true;
	private Runnable startGameAction;
	private Function<String, CompletableFuture<Void>> chatSender = body -> {
	    System.out.println("CHAT|" + body);
	    return CompletableFuture.completedFuture(null);
	};

	void setChatSender(Function<String, CompletableFuture<Void>> chatSender) {
	    this.chatSender = chatSender;
	}

	public GameRoomPanel(String roomId) {
	    this(roomId, "라운드 —", 0, new Object[0][0], "", "");
	}

	private GameRoomPanel(String roomId, String roundText, int remainingSeconds,
	        Object[][] players, String chatText, String localNickname) {
	    super(new BorderLayout(14, 14));
	    this.localNickname = localNickname;
		setBackground(new Color(0xDDF3FF));
		setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

		JPanel statusPanel = new JPanel(new GridLayout(1, 3));
		statusPanel.setBackground(new Color(0xBFE7FF));
		statusPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

		String[] statusTexts = { "방 번호 " + roomId, roundText, };

		for (String text : statusTexts) {
			JLabel label = new JLabel(text, JLabel.CENTER);
			label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));
			label.setForeground(new Color(0x16366D));
			statusPanel.add(label);
		}

		String timeText = remainingSeconds == 0 ? "남은 시간 —" : "남은 시간 " + remainingSeconds + "초";
		JLabel timeLabel = new JLabel(timeText, JLabel.CENTER);
		timeLabel.setFont(timeLabel.getFont().deriveFont(Font.BOLD, 14f));
		timeLabel.setForeground(new Color(0x16366D));

		JProgressBar timeBar = new JProgressBar(0, 60);
		timeBar.setValue(remainingSeconds);

		JPanel timerPanel = new JPanel(new BorderLayout(0, 4));
		timerPanel.setOpaque(false);
		timerPanel.add(timeLabel, BorderLayout.NORTH);
		timerPanel.add(timeBar, BorderLayout.CENTER);

		statusPanel.add(timerPanel);

		add(statusPanel, BorderLayout.NORTH);

		DrawingPanel canvasPanel = new DrawingPanel();
		canvasPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

		JPanel drawingTools = canvasPanel.createToolsPanel();
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
	
	void setStartGameAction(Runnable startGameAction) {
	    this.startGameAction = startGameAction;
	    updateStartButton();
	}

	void setWaitingForGame(boolean waitingForGame) {
	    this.waitingForGame = waitingForGame;
	    updateStartButton();
	}

	private void updateStartButton() {
	    boolean canStart = waitingForGame
	            && startGameAction != null
	            && playerTable.getRowCount() >= 3
	            && localNickname.equals(playerTable.getValueAt(0, 0));

	    startGameButton.setEnabled(canStart);
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

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("낙서한판 - 게임방 미리보기");
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			Object[][] samplePlayers = {
				    { "그림왕", 0 },
				    { "스케치왕", 0 },
				    { "그림친구", 0 }
				};
				String sampleChat = "그림왕: 안녕하세요!\n스케치왕: 반가워요!";

				GameRoomPanel previewRoom =
				        new GameRoomPanel("1", "라운드 —", 0, samplePlayers, sampleChat, "그림왕");

				previewRoom.setStartGameAction(() -> {
				    previewRoom.setWaitingForGame(false);
				    previewRoom.appendSystemMessage("미리보기에서 게임 시작 버튼을 눌렀습니다.");
				});
				
			previewRoom.appendSystemMessage("그림친구님이 입장했습니다.");
			previewRoom.setChatSender(body -> {
			    previewRoom.appendChatMessage("그림왕", body);
			    return CompletableFuture.completedFuture(null);
			});

			frame.setContentPane(previewRoom);
			frame.setSize(1080, 660);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}
}