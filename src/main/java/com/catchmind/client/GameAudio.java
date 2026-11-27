package com.catchmind.client;

import java.awt.event.HierarchyEvent;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JToggleButton;

final class GameAudio implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "game-audio");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, Clip> clips = new HashMap<>();
    private volatile boolean muted;
    private String backgroundFile;
    private Clip playingBackground;

    boolean isMuted() {
        return muted;
    }
    
    JToggleButton createMuteButton() {
        JToggleButton button = new JToggleButton();
        bindMuteButton(button);
        return button;
    }

    void bindMuteButton(JToggleButton button) {
        updateMuteButton(button);
        button.setEnabled(true);

        button.addActionListener(event -> {
            setMuted(button.isSelected());
            updateMuteButton(button);
        });

        button.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                updateMuteButton(button);
            }
        });
    }

    private void updateMuteButton(JToggleButton button) {
        boolean muted = isMuted();
        button.setSelected(muted);
        button.setText(muted ? "소리 꺼짐" : "소리 켜짐");
    }

    void setMuted(boolean muted) {
        this.muted = muted;
        submit(() -> {
            if (muted) {
                for (Clip clip : clips.values()) {
                    if (clip != null) {
                        clip.stop();
                    }
                }
                playingBackground = null;
            } else {
                updateBackground();
            }
        });
    }

    void playLobbyMusic() {
        selectBackground("bgm-lobby.wav");
    }

    void playGameMusic() {
        selectBackground("bgm-game.wav");
    }

    void playNotice() {
        playEffect("sfx-notice.wav");
    }

    void playCorrect() {
        playEffect("sfx-correct.wav");
    }

    private void selectBackground(String file) {
        submit(() -> {
            backgroundFile = file;
            updateBackground();
        });
    }

    private void updateBackground() {
        if (muted || backgroundFile == null) {
            return;
        }

        Clip next = loadClip(backgroundFile);
        if (next == playingBackground) {
            return;
        }

        if (playingBackground != null) {
            playingBackground.stop();
        }

        playingBackground = next;
        if (next != null) {
            next.setFramePosition(0);
            next.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    private void playEffect(String file) {
        if (muted) {
            return;
        }

        submit(() -> {
            if (muted) {
                return;
            }

            Clip clip = loadClip(file);
            if (clip != null) {
                clip.stop();
                clip.setFramePosition(0);
                clip.start();
            }
        });
    }

    private Clip loadClip(String file) {
        if (clips.containsKey(file)) {
            return clips.get(file);
        }

        URL resource = GameAudio.class.getResource("/sounds/" + file);
        Clip clip = null;

        if (resource == null) {
            System.err.println("음원을 찾을 수 없습니다. " + file);
        } else {
            try (var input = AudioSystem.getAudioInputStream(resource)) {
                clip = AudioSystem.getClip();
                clip.open(input);
                clips.put(file, clip);
                return clip;
            } catch (IOException | UnsupportedAudioFileException | LineUnavailableException
                    | IllegalArgumentException ex) {
                if (clip != null) {
                    clip.close();
                }
                System.err.println("음원을 열 수 없습니다. " + file + " (" + ex.getMessage() + ")");
            }
        }

        clips.put(file, null);
        return null;
    }

    private void submit(Runnable task) {
        if (!worker.isShutdown()) {
            worker.execute(task);
        }
    }

    @Override
    public void close() {
        submit(() -> {
            for (Clip clip : clips.values()) {
                if (clip != null) {
                    clip.close();
                }
            }
            clips.clear();
            playingBackground = null;
        });
        worker.shutdown();
    }
}