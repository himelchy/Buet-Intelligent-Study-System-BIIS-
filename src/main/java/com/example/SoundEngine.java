package com.example;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages the bundled background sounds for Study Space.
 *
 * Sound files must be placed in:
 *   src/main/resources/com/example/sounds/
 *
 * Required files:
 *   fireplace.mp3, 40hz.mp3, rain.mp3, om.mp3, ocean.mp3
 *
 * If a file is missing the button is disabled.
 */
public final class SoundEngine {

    public record SoundTrack(String id, String label, String emoji, String resourcePath) {}

    /** All available ambient tracks in display order. */
    public static final SoundTrack[] TRACKS = {
        new SoundTrack("fireplace", "Fireplace", "\uD83D\uDD25", "sounds/fireplace.mp3"),
        new SoundTrack("40hz", "40Hz", "\u223F", "sounds/40hz.mp3"),
        new SoundTrack("rain", "Rain", "\uD83C\uDF27", "sounds/rain.mp3"),
        new SoundTrack("om", "Om", "\u0950", "sounds/om.mp3"),
        new SoundTrack("ocean", "Ocean", "\uD83C\uDF0A", "sounds/ocean.mp3"),
    };

    private final Map<String, MediaPlayer> players = new LinkedHashMap<>();
    private String activeId = null;
    private double volume = 0.6;

    public SoundEngine() {
        for (SoundTrack track : TRACKS) {
            URL url = getClass().getResource(track.resourcePath());
            if (url == null) {
                continue;
            }
            try {
                MediaPlayer mediaPlayer = new MediaPlayer(new Media(url.toExternalForm()));
                mediaPlayer.setVolume(volume);
                mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                mediaPlayer.setOnError(() ->
                        System.err.println("[Sound] Error playing " + track.id() + ": "
                                + mediaPlayer.getError().getMessage()));
                players.put(track.id(), mediaPlayer);
            } catch (Exception ex) {
                System.err.println("[Sound] Cannot load " + track.resourcePath()
                        + ": " + ex.getMessage());
            }
        }
    }

    /** Returns true if the given track ID has a usable MediaPlayer. */
    public boolean isAvailable(String id) {
        return players.containsKey(id);
    }

    /** Returns the currently playing track ID, or null if nothing is playing. */
    public String activeId() {
        return activeId;
    }

    /**
     * Starts the named track, stopping whatever was playing before.
     * Calling with the already-active ID stops it.
     */
    public void play(String id) {
        if (id.equals(activeId)) {
            stop();
            return;
        }
        stop();
        MediaPlayer mediaPlayer = players.get(id);
        if (mediaPlayer == null) {
            return;
        }
        mediaPlayer.seek(Duration.ZERO);
        mediaPlayer.play();
        activeId = id;
    }

    /** Stops the current track. */
    public void stop() {
        if (activeId != null) {
            MediaPlayer mediaPlayer = players.get(activeId);
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }
            activeId = null;
        }
    }

    /** Sets volume 0.0-1.0 across all players. */
    public void setVolume(double value) {
        volume = Math.max(0.0, Math.min(1.0, value));
        players.values().forEach(mediaPlayer -> mediaPlayer.setVolume(volume));
    }

    public double getVolume() {
        return volume;
    }

    /** Must be called when the Study Space window closes. */
    public void dispose() {
        stop();
        players.values().forEach(MediaPlayer::dispose);
        players.clear();
    }
}
