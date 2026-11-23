package com.catchmind.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Room {
    public static final int MAX_PLAYERS = 4;
    public static final int MIN_PLAYERS_TO_START = 3;
    public static final int TOTAL_ROUNDS = 12;
    public static final int ROUND_DURATION_SECONDS = 140;

    private static final AtomicInteger roundIdGenerator = new AtomicInteger(1);

    private final int roomId;
    private final String roomName;
    private final List<ClientHandler> players = new CopyOnWriteArrayList<>();

    private volatile boolean playing = false;
    private volatile int currentRound = 0;       // 현재 유효 roundId (0이면 라운드 미진행)
    private volatile int currentRoundNo = 0;     // 1 ~ 12 라운드 번호
    private volatile ClientHandler currentDrawer = null;
    private volatile String currentWord = null;
    private volatile int remainingSeconds = 0;
    private volatile boolean roundActive = false;

    private int drawerRotationIndex = 0;
    private List<String> wordQueue = new ArrayList<>();
    private final Map<String, Integer> scores = new HashMap<>();
    private final Map<String, Integer> roundStartScores = new HashMap<>();
    private final List<String> roundCorrectOrder = new ArrayList<>();
    private final Set<String> roundCorrectSet = new HashSet<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "RoomScheduler-" + System.nanoTime());
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> roundTask;
    private ScheduledFuture<?> transitionTask;

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

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public ClientHandler getCurrentDrawer() {
        return currentDrawer;
    }

    public void setCurrentDrawer(ClientHandler currentDrawer) {
        this.currentDrawer = currentDrawer;
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

    public void addPlayer(ClientHandler player) {
        if (!players.contains(player)) {
            players.add(player);
            if (player.getNickname() != null) {
                scores.putIfAbsent(player.getNickname(), 0);
            }
        }
    }

    public boolean removePlayer(ClientHandler player) {
        return players.remove(player);
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public List<ClientHandler> getPlayers() {
        return new ArrayList<>(players);
    }

    public synchronized String startGame(ClientHandler requester) {
        if (!isHost(requester)) {
            return "ERROR|NOT_HOST";
        }
        if (playing) {
            return "ERROR|GAME_IN_PROGRESS";
        }
        if (players.size() < MIN_PLAYERS_TO_START) {
            return "ERROR|NOT_ENOUGH_PLAYERS";
        }

        cancelScheduledTasks();
        playing = true;
        currentRoundNo = 0;
        drawerRotationIndex = 0;
        wordQueue = WordBank.pickWords(TOTAL_ROUNDS);

        scores.clear();
        for (ClientHandler p : players) {
            if (p.getNickname() != null) {
                scores.put(p.getNickname(), 0);
            }
        }

        broadcast("GAME_STARTED|" + TOTAL_ROUNDS);
        broadcast(buildScoresMessage());
        startNextRound();
        return null;
    }

    private synchronized void startNextRound() {
        if (!playing) return;
        if (players.size() < MIN_PLAYERS_TO_START) {
            endGameNotEnoughPlayers();
            return;
        }
        if (currentRoundNo >= TOTAL_ROUNDS) {
            finishGameNormally();
            return;
        }

        currentRoundNo++;
        ClientHandler drawer = players.get(drawerRotationIndex % players.size());
        drawerRotationIndex++;
        beginRound(currentRoundNo, drawer);
    }

    private synchronized void beginRound(int roundNo, ClientHandler drawer) {
        cancelScheduledTasks();

        int roundId = roundIdGenerator.getAndIncrement();
        this.currentRound = roundId;
        this.currentDrawer = drawer;
        this.currentWord = wordQueue.isEmpty() ? "사과" : wordQueue.remove(0);
        this.remainingSeconds = ROUND_DURATION_SECONDS;
        this.roundActive = true;

        roundCorrectOrder.clear();
        roundCorrectSet.clear();
        roundStartScores.clear();
        for (ClientHandler p : players) {
            if (p.getNickname() != null) {
                roundStartScores.put(p.getNickname(), scores.getOrDefault(p.getNickname(), 0));
            }
        }

        String drawerName = drawer.getNickname();
        broadcast("ROUND_START|" + roundId + "|" + roundNo + "|" + drawerName + "|" + ROUND_DURATION_SECONDS);

        for (ClientHandler p : players) {
            if (p.equals(drawer)) {
                p.sendMessage("WORD|" + roundId + "|" + currentWord);
            } else {
                p.sendMessage("WORD_LENGTH|" + roundId + "|" + currentWord.length());
            }
        }

        roundTask = scheduler.scheduleAtFixedRate(() -> {
            synchronized (Room.this) {
                if (!playing || !roundActive || currentRound != roundId) {
                    return;
                }
                remainingSeconds--;
                if (remainingSeconds > 0) {
                    broadcast("ROUND_TIME|" + roundId + "|" + remainingSeconds);
                } else {
                    broadcast("ROUND_TIME|" + roundId + "|0");
                    completeRound("TIME_UP");
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    // 채팅 입력 시 정답 판정 (정답 처리되었으면 true, 일반 채팅으로 보낼 경우 false)
    public synchronized boolean handleChatGuess(ClientHandler sender, String rawMessage) {
        if (!playing || !roundActive || currentWord == null) {
            return false;
        }
        if (isCurrentDrawer(sender)) {
            return false;
        }
        String nick = sender.getNickname();
        if (nick == null || roundCorrectSet.contains(nick)) {
            return false;
        }

        String normalizedGuess = rawMessage.replaceAll("\\s+", "");
        String normalizedAnswer = currentWord.replaceAll("\\s+", "");
        if (!normalizedGuess.equalsIgnoreCase(normalizedAnswer)) {
            return false;
        }

        roundCorrectOrder.add(nick);
        roundCorrectSet.add(nick);
        int rank = roundCorrectOrder.size();

        int earned = switch (rank) {
            case 1 -> 100;
            case 2 -> 80;
            case 3 -> 60;
            default -> 40;
        };

        scores.put(nick, scores.getOrDefault(nick, 0) + earned);

        int drawerBonus = 0;
        String drawerNick = (currentDrawer != null) ? currentDrawer.getNickname() : null;
        if (drawerNick != null && scores.containsKey(drawerNick)) {
            drawerBonus = 20;
            scores.put(drawerNick, scores.getOrDefault(drawerNick, 0) + drawerBonus);
        }

        broadcast("CORRECT_NOTIFY|" + currentRound + "|" + nick + "|" + rank + "|" + earned + "|" + drawerBonus);
        broadcast(buildScoresMessage());

        long guessersCount = players.stream().filter(p -> !p.equals(currentDrawer)).count();
        if (roundCorrectSet.size() >= guessersCount) {
            completeRound("ALL_CORRECT");
        }
        return true;
    }

    private synchronized void completeRound(String reason) {
        if (!roundActive) return;
        roundActive = false;
        if (roundTask != null) {
            roundTask.cancel(false);
            roundTask = null;
        }

        int finishedRoundId = currentRound;
        String answer = currentWord != null ? currentWord : "";
        currentRound = 0;
        currentDrawer = null;
        currentWord = null;

        broadcast("ROUND_END|" + finishedRoundId + "|" + reason + "|" + answer);

        if (currentRoundNo >= TOTAL_ROUNDS) {
            transitionTask = scheduler.schedule(this::finishGameNormally, 2, TimeUnit.SECONDS);
        } else {
            transitionTask = scheduler.schedule(this::startNextRound, 2, TimeUnit.SECONDS);
        }
    }

    public synchronized void onPlayerRemoved(ClientHandler leftPlayer, boolean wasDrawer) {
        String leftNick = leftPlayer.getNickname();
        if (leftNick != null) {
            scores.remove(leftNick);
            roundStartScores.remove(leftNick);
        }

        if (!playing) {
            return;
        }

        // 최우선 규칙: 진행 중 인원이 2명 이하가 되면 즉시 점수 0 초기화 후 게임 종료
        if (players.size() < MIN_PLAYERS_TO_START) {
            endGameNotEnoughPlayers();
            return;
        }

        // 3명 이상 남은 상태에서 출제자가 퇴장한 경우 3초 대기 후 동일 roundNo 재개시
        if (wasDrawer && roundActive) {
            roundActive = false;
            if (roundTask != null) {
                roundTask.cancel(false);
                roundTask = null;
            }

            int cancelledRoundId = currentRound;
            currentRound = 0;
            currentDrawer = null;
            currentWord = null;

            // 라운드 시작 시점 점수로 복원
            for (ClientHandler p : players) {
                String nick = p.getNickname();
                if (nick != null) {
                    scores.put(nick, roundStartScores.getOrDefault(nick, 0));
                }
            }

            broadcast("ROUND_CANCEL|" + cancelledRoundId + "|DRAWER_LEFT|3");
            broadcast(buildScoresMessage());

            final int retryRoundNo = currentRoundNo;
            transitionTask = scheduler.schedule(() -> {
                synchronized (Room.this) {
                    if (!playing) return;
                    if (players.size() < MIN_PLAYERS_TO_START) {
                        endGameNotEnoughPlayers();
                        return;
                    }
                    ClientHandler nextDrawer = players.get(drawerRotationIndex % players.size());
                    drawerRotationIndex++;
                    beginRound(retryRoundNo, nextDrawer);
                }
            }, 3, TimeUnit.SECONDS);
        } else if (roundActive && currentDrawer != null) {
            // 일반 참가자 퇴장 시 남은 추리자가 모두 정답을 맞힌 상태라면 즉시 라운드 종료
            long remainingGuessers = players.stream().filter(p -> !p.equals(currentDrawer)).count();
            long correctRemaining = players.stream()
                    .filter(p -> !p.equals(currentDrawer) && roundCorrectSet.contains(p.getNickname()))
                    .count();
            if (remainingGuessers > 0 && correctRemaining >= remainingGuessers) {
                completeRound("ALL_CORRECT");
            }
        }
    }

    private synchronized void endGameNotEnoughPlayers() {
        cancelScheduledTasks();
        resetGameState();
        for (ClientHandler p : players) {
            if (p.getNickname() != null) {
                scores.put(p.getNickname(), 0);
            }
        }
        broadcast(buildScoresMessage());
        broadcast("GAME_END|NOT_ENOUGH_PLAYERS");
    }

    private synchronized void finishGameNormally() {
        cancelScheduledTasks();
        String finalResult = buildGameEndMessage();
        resetGameState();
        broadcast(finalResult);
    }

    private void resetGameState() {
        playing = false;
        roundActive = false;
        currentRound = 0;
        currentRoundNo = 0;
        currentDrawer = null;
        currentWord = null;
        remainingSeconds = 0;
        roundCorrectOrder.clear();
        roundCorrectSet.clear();
    }

    private void cancelScheduledTasks() {
        if (roundTask != null) {
            roundTask.cancel(false);
            roundTask = null;
        }
        if (transitionTask != null) {
            transitionTask.cancel(false);
            transitionTask = null;
        }
    }

    public void shutdownScheduler() {
        cancelScheduledTasks();
        scheduler.shutdownNow();
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

 // GAME_END|COMPLETED|<순위>|<닉네임>|<점수>|... (동점자 공동 순위 산출)
    private String buildGameEndMessage() {
        List<String[]> ranking = new ArrayList<>();
        for (ClientHandler player : players) {
            String nick = player.getNickname();
            if (nick != null) {
                ranking.add(new String[]{ nick, String.valueOf(scores.getOrDefault(nick, 0)) });
            }
        }
        ranking.sort((a, b) -> Integer.compare(Integer.parseInt(b[1]), Integer.parseInt(a[1])));

        StringBuilder sb = new StringBuilder("GAME_END|COMPLETED");
        int prevScore = Integer.MIN_VALUE;
        int currentRank = 0;
        for (int i = 0; i < ranking.size(); i++) {
            int score = Integer.parseInt(ranking.get(i)[1]);
            if (score != prevScore) {
                currentRank = i + 1;
                prevScore = score;
            }
            sb.append("|").append(currentRank)
              .append("|").append(ranking.get(i)[0])
              .append("|").append(score);
        }
        return sb.toString();
    }
}