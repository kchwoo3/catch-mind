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

public class GameRoomPanel extends JPanel {
    public GameRoomPanel(String roomId) {
        super(new BorderLayout());
        JPanel statusPanel = new JPanel(new GridLayout(1, 3));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        statusPanel.add(new JLabel("방 번호 " + roomId, JLabel.CENTER));
        statusPanel.add(new JLabel("라운드 1 / 5", JLabel.CENTER));
        statusPanel.add(new JLabel("남은 시간 60초", JLabel.CENTER));

        add(statusPanel, BorderLayout.NORTH);
        
        JPanel canvasPanel = new JPanel();
        canvasPanel.setBackground(Color.WHITE);
        canvasPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        JPanel drawingTools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        drawingTools.add(new JLabel("색상"));
        drawingTools.add(new JComboBox<>(new String[] { "검정", "빨강", "파랑" }));
        drawingTools.add(new JLabel("선 굵기"));
        drawingTools.add(new JComboBox<>(new String[] { "2 px", "4 px", "8 px" }));
        drawingTools.add(new JButton("지우개"));
        drawingTools.add(new JButton("전체 지우기"));

        JPanel drawingArea = new JPanel(new BorderLayout());
        drawingArea.add(canvasPanel, BorderLayout.CENTER);
        drawingArea.add(drawingTools, BorderLayout.SOUTH);

        add(drawingArea, BorderLayout.CENTER);
        
        String[] columns = { "닉네임", "점수" };
        Object[][] players = {
            { "그림왕", 20 },
            { "스케치왕", 10 }
        };

        JTable playerTable = new JTable(players, columns);
        playerTable.setDefaultEditor(Object.class, null);

        JPanel playersPanel = new JPanel(new BorderLayout());
        playersPanel.setBorder(BorderFactory.createTitledBorder("참가자 목록"));
        playersPanel.add(new JScrollPane(playerTable), BorderLayout.CENTER);
        playersPanel.setPreferredSize(new Dimension(260, 160));

        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.add(playersPanel, BorderLayout.NORTH);
        
        JTextArea chatHistory = new JTextArea(
        	    "그림왕: 안녕하세요!\n스케치왕: 반가워요!"
        	);
        	chatHistory.setEditable(false);
        	chatHistory.setLineWrap(true);
        	chatHistory.setWrapStyleWord(true);

        	JPanel chatInputPanel = new JPanel(new BorderLayout(4, 0));
        	chatInputPanel.add(new JTextField(), BorderLayout.CENTER);
        	chatInputPanel.add(new JButton("전송"), BorderLayout.EAST);

        	JPanel chatPanel = new JPanel(new BorderLayout(0, 4));
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
            frame.setContentPane(new GameRoomPanel("1"));
            frame.setSize(1080, 660);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}