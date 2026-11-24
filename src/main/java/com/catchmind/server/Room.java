package com.catchmind.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

public class Room {
    public static final int MAX_PLAYERS = 4;
    public static final int MIN_PLAYERS_TO_START = 3;
    public static final int TOTAL_ROUNDS = 12;
    public static final int ROUND_DURATION_SECONDS = 140;
    public static final int ROUND_TRANSITION_DELAY_MS = 3000;

    private record PlayerScore(String nickname, int score) {}

    private final int roomId;
    private final String roomName;
    private final List<ClientHandler> players = new CopyOnWriteArrayList<>();

    private volatile boolean playing = false;
    private int currentRound = 0;
    private int currentRoundId = 0;
    private int drawerIndex = -1;
    private ClientHandler currentDrawer = null;
    private String currentWord = null;
    private int remainingSeconds = 0;
    private boolean roundActive = false;

    private final List<String> wordPool = new ArrayList<>();
    private final Map<String, Integer> scores = new LinkedHashMap<>();
    private final List<String> correctOrder = new ArrayList<>();
    private final Set<String> correctNicknames = new HashSet<>();

    private Timer gameTimer;

    public Room(int roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
    }

    public int getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public boolean isPlaying() {
        return playing;
    }

    public int getCurrentRound() {
        return currentRoundId;
    }

    public boolean isCurrentDrawer(ClientHandler client) {
        return client != null && client.equals(currentDrawer);
    }

    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public ClientHandler getHost() {
        return players.isEmpty() ? null : players.get(0);
    }

    public boolean isHost(ClientHandler client) {
        return client != null && client.equals(getHost());
    }

    public synchronized void addPlayer(ClientHandler player) {
        if (!players.contains(player)) {
            players.add(player);
            if (player.getNickname() != null) {
                scores.putIfAbsent(player.getNickname(), 0);
            }
        }
    }

    public synchronized void removePlayer(ClientHandler player) {
        int removedIdx = players.indexOf(player);
        if (removedIdx == -1) return;

        boolean wasDrawer = playing && roundActive && player.equals(currentDrawer);
        players.remove(removedIdx);

        if (removedIdx <= drawerIndex) {
            drawerIndex--;
        }

        if (!playing) {
            if (player.getNickname() != null) {
                scores.remove(player.getNickname());
            }
            return;
        }

        if (players.size() < MIN_PLAYERS_TO_START) {
            cancelGameDueToLackOfPlayers();
            return;
        }

        if (wasDrawer) {
            handleDrawerLeft();
            return;
        }

        if (roundActive && areAllRemainingGuessersCorrect()) {
            finishRound("ALL_CORRECT");
        }
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public List<ClientHandler> getPlayers() {
        return new ArrayList<>(players);
    }

    public synchronized String startGame(ClientHandler requester) {
        if (playing) {
            return "ERROR|GAME_ALREADY_STARTED";
        }
        if (!isHost(requester)) {
            return "ERROR|NOT_HOST";
        }
        if (players.size() < MIN_PLAYERS_TO_START) {
            return "ERROR|NOT_ENOUGH_PLAYERS";
        }

        playing = true;
        currentRound = 0;
        currentRoundId = 0;
        drawerIndex = -1;
        scores.clear();
        for (ClientHandler p : players) {
            if (p.getNickname() != null) {
                scores.put(p.getNickname(), 0);
            }
        }

        wordPool.clear();
        wordPool.addAll(WordBank.pickWords(TOTAL_ROUNDS + 1));

        broadcast("GAME_STARTED|" + TOTAL_ROUNDS);
        broadcast(buildScoresMessage());

        startNextRound();
        return null;
    }

    private synchronized void startNextRound() {
        if (!playing) return;

        currentRound++;
        if (currentRound > TOTAL_ROUNDS) {
            completeGame();
            return;
        }

        int nextRoundId = currentRoundId + 1;
        prepareAndBeginRound(nextRoundId, currentRound);
    }

    private synchronized void prepareAndBeginRound(int roundId, int roundNumber) {
        cancelTimer();

        if (players.isEmpty()) {
            playing = false;
            return;
        }

        currentRoundId = roundId;
        drawerIndex = (drawerIndex + 1) % players.size();
        currentDrawer = players.get(drawerIndex);
        currentWord = drawNextWord();
        remainingSeconds = ROUND_DURATION_SECONDS;
        roundActive = true;
        correctOrder.clear();
        correctNicknames.clear();

        String drawerName = currentDrawer.getNickname();
        broadcast("ROUND_START|" + roundId + "|" + roundNumber + "|" + drawerName + "|" + ROUND_DURATION_SECONDS);
        broadcast("CLEAR_NOTIFY|" + drawerName + "|" + roundId);

        for (ClientHandler p : players) {
            if (p.equals(currentDrawer)) {
                p.sendMessage("WORD|" + roundId + "|" + currentWord);
            } else {
                p.sendMessage("WORD_LENGTH|" + roundId + "|" + currentWord.length());
            }
        }

        broadcast("ROUND_TIME|" + roundId + "|" + remainingSeconds);
        startRoundTimer(roundId);
    }

    private String drawNextWord() {
        if (!wordPool.isEmpty()) {
            return wordPool.remove(0);
        }
        List<String> extra = WordBank.pickWords(5);
        for (String w : extra) {
            if (!w.equals(currentWord)) {
                return w;
            }
        }
        return extra.isEmpty() ? "무지개" : extra.get(0);
    }

    private void startRoundTimer(int roundId) {
        gameTimer = new Timer(true);
        gameTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                onTimerTick(roundId);
            }
        }, 1000L, 1000L);
    }

    private synchronized void onTimerTick(int roundId) {
        if (!playing || !roundActive || currentRoundId != roundId) {
            return;
        }

        remainingSeconds--;
        if (remainingSeconds > 0) {
            broadcast("ROUND_TIME|" + roundId + "|" + remainingSeconds);
        } else {
            broadcast("ROUND_TIME|" + roundId + "|0");
            finishRound("TIMEOUT");
        }
    }

    public synchronized boolean handleChatGuess(ClientHandler sender, String rawMessage) {
        if (!playing || !roundActive || currentWord == null || sender == null || sender.getNickname() == null) {
            return false;
        }

        String trimmedGuess = rawMessage.trim();
        if (!trimmedGuess.equals(currentWord)) {
            return false;
        }

        String nick = sender.getNickname();

        if (sender.equals(currentDrawer) || correctNicknames.contains(nick)) {
            return true;
        }

        correctNicknames.add(nick);
        correctOrder.add(nick);
        int rank = correctOrder.size();

        int guesserPoints = switch (rank) {
            case 1 -> 100;
            case 2 -> 70;
            case 3 -> 50;
            default -> 30;
        };
        int drawerBonus = (rank == 1) ? 30 : 10;

        scores.merge(nick, guesserPoints, Integer::sum);
        if (currentDrawer != null && currentDrawer.getNickname() != null) {
            scores.merge(currentDrawer.getNickname(), drawerBonus, Integer::sum);
        }

        broadcast("CORRECT_NOTIFY|" + currentRoundId + "|" + nick + "|" + rank + "|" + guesserPoints);
        broadcast(buildScoresMessage());

        if (areAllRemainingGuessersCorrect()) {
            finishRound("ALL_CORRECT");
        }

        return true;
    }

    private boolean areAllRemainingGuessersCorrect() {
        int remainingGuessers = 0;
        for (ClientHandler p : players) {
            if (!p.equals(currentDrawer) && p.getNickname() != null) {
                remainingGuessers++;
                if (!correctNicknames.contains(p.getNickname())) {
                    return false;
                }
            }
        }
        return remainingGuessers > 0;
    }

    private synchronized void finishRound(String reason) {
        if (!roundActive) return;
        roundActive = false;
        cancelTimer();

        int finishedRoundId = currentRoundId;
        int finishedRoundNum = currentRound;
        broadcast("ROUND_END|" + finishedRoundId + "|" + reason + "|" + currentWord);

        if (finishedRoundNum >= TOTAL_ROUNDS) {
            completeGame();
        } else {
            scheduleNextRound(finishedRoundId);
        }
    }

    private void scheduleNextRound(int finishedRoundId) {
        cancelTimer();
        gameTimer = new Timer(true);
        gameTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (Room.this) {
                    if (playing && currentRoundId == finishedRoundId) {
                        startNextRound();
                    }
                }
            }
        }, ROUND_TRANSITION_DELAY_MS);
    }

    private synchronized void handleDrawerLeft() {
        roundActive = false;
        cancelTimer();

        int canceledRoundId = currentRoundId;
        int nextRoundId = currentRoundId + 1;
        broadcast("ROUND_CANCEL|" + canceledRoundId + "|DRAWER_LEFT|" + nextRoundId);

        prepareAndBeginRound(nextRoundId, currentRound);
    }

    private synchronized void cancelGameDueToLackOfPlayers() {
        roundActive = false;
        playing = false;
        currentDrawer = null;
        currentWord = null;
        cancelTimer();

        broadcast("GAME_END|NOT_ENOUGH_PLAYERS");
        broadcast(buildScoresMessage());
    }

    private synchronized void completeGame() {
        roundActive = false;
        playing = false;
        currentDrawer = null;
        currentWord = null;
        cancelTimer();

        List<PlayerScore> ranking = new ArrayList<>();
        for (ClientHandler p : players) {
            String nick = p.getNickname();
            if (nick != null) {
                ranking.add(new PlayerScore(nick, scores.getOrDefault(nick, 0)));
            }
        }
        ranking.sort(Comparator.comparingInt(PlayerScore::score).reversed());

        StringBuilder sb = new StringBuilder("GAME_END|COMPLETED");
        int rank = 0;
        int prevScore = Integer.MIN_VALUE;
        for (int i = 0; i < ranking.size(); i++) {
            PlayerScore entry = ranking.get(i);
            if (entry.score() != prevScore) {
                rank = i + 1;
                prevScore = entry.score();
            }
            sb.append("|").append(entry.nickname())
              .append("|").append(entry.score())
              .append("|").append(rank);
        }

        broadcast(sb.toString());
    }

    private void cancelTimer() {
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer.purge();
            gameTimer = null;
        }
    }

    public void broadcast(String message) {
        for (ClientHandler player : players) {
            player.sendMessage(message);
        }
    }

    public String buildPlayersMessage() {
        StringBuilder sb = new StringBuilder("PLAYERS");
        for (ClientHandler player : players) {
            if (player.getNickname() != null) {
                sb.append("|").append(player.getNickname());
            }
        }
        return sb.toString();
    }

    public String buildScoresMessage() {
        StringBuilder sb = new StringBuilder("SCORES");
        for (ClientHandler player : players) {
            String nick = player.getNickname();
            if (nick != null) {
                sb.append("|").append(nick).append("|").append(scores.getOrDefault(nick, 0));
            }
        }
        return sb.toString();
    }
}