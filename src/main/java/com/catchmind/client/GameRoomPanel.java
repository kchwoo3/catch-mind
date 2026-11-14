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

public class GameRoomPanel extends JPanel {
	public GameRoomPanel(String roomId) {
		this(roomId, "라운드 —", 0, new Object[0][0], "");
	}

	private GameRoomPanel(String roomId, String roundText, int remainingSeconds, Object[][] players, String chatText) {
		super(new BorderLayout(14, 14));
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

		JPanel canvasPanel = new JPanel();
		canvasPanel.setBackground(Color.WHITE);
		canvasPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

		JPanel drawingTools = new JPanel(new FlowLayout(FlowLayout.LEFT));
		drawingTools.setOpaque(false);
		drawingTools.add(new JLabel("색상"));
		drawingTools.add(new JComboBox<>(new String[] { "검정", "빨강", "파랑" }));
		drawingTools.add(new JLabel("선 굵기"));
		drawingTools.add(new JComboBox<>(new String[] { "2 px", "4 px", "8 px" }));
		drawingTools.add(new JButton("지우개"));
		drawingTools.add(new JButton("전체 지우기"));

		JPanel drawingArea = new JPanel(new BorderLayout());
		drawingArea.setOpaque(false);
		drawingArea.add(canvasPanel, BorderLayout.CENTER);
		drawingArea.add(drawingTools, BorderLayout.SOUTH);

		add(drawingArea, BorderLayout.CENTER);

		String[] columns = { "닉네임", "점수" };

		JTable playerTable = new JTable(players, columns);
		playerTable.setDefaultEditor(Object.class, null);
		playerTable.setRowHeight(24);
		playerTable.setFillsViewportHeight(true);

		JPanel playersPanel = new JPanel(new BorderLayout());
		playersPanel.setBackground(Color.WHITE);
		playersPanel.setBorder(BorderFactory.createTitledBorder("참가자 목록"));
		playersPanel.add(new JScrollPane(playerTable), BorderLayout.CENTER);
		playersPanel.setPreferredSize(new Dimension(260, 160));

		JPanel sidePanel = new JPanel(new BorderLayout(0, 10));
		sidePanel.setOpaque(false);
		sidePanel.add(playersPanel, BorderLayout.NORTH);

		JTextArea chatHistory = new JTextArea(chatText);
		chatHistory.setEditable(false);
		chatHistory.setLineWrap(true);
		chatHistory.setWrapStyleWord(true);

		JPanel chatInputPanel = new JPanel(new BorderLayout(4, 0));
		chatInputPanel.setOpaque(false);
		chatInputPanel.add(new JTextField(), BorderLayout.CENTER);
		chatInputPanel.add(new JButton("전송"), BorderLayout.EAST);

		JPanel chatPanel = new JPanel(new BorderLayout(0, 4));
		chatPanel.setBackground(Color.WHITE);
		chatPanel.setBorder(BorderFactory.createTitledBorder("채팅"));
		chatPanel.add(new JScrollPane(chatHistory), BorderLayout.CENTER);
		chatPanel.add(chatInputPanel, BorderLayout.SOUTH);

		sidePanel.add(chatPanel, BorderLayout.CENTER);

		add(sidePanel, BorderLayout.EAST);
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("낙서한판 - 게임방 미리보기");
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			Object[][] samplePlayers = { { "그림왕", 20 }, { "스케치왕", 10 } };
			String sampleChat = "그림왕: 안녕하세요!\n스케치왕: 반가워요!";

			frame.setContentPane(new GameRoomPanel("1", "라운드 1 / 5", 42, samplePlayers, sampleChat));
			frame.setSize(1080, 660);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}
}