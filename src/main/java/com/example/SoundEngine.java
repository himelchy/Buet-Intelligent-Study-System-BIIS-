package com.example;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages ambient background sounds for the Study Space.
 *
 * Sound files must be placed in:
 *   src/main/resources/com/example/sounds/
 *
 * Required files (looping MP3 clips, 30-60 seconds each):
 *   rain.mp3, storm.mp3, cafe.mp3, fireplace.mp3,
 *   ocean.mp3, library.mp3, snow.mp3, whitenoise.mp3
 *
 * If a file is missing the button is simply disabled — no crash.
 */
public final class SoundEngine {

    public record SoundTrack(String id, String label, String emoji, String resourcePath) {}

    /** All available ambient tracks in display order. */
    public static final SoundTrack[] TRACKS = {
        new SoundTrack("rain",       "Rain",       "🌧",  "sounds/rain.mp3"),
        new SoundTrack("storm",      "Storm",      "⛈",  "sounds/storm.mp3"),
        new SoundTrack("cafe",       "Café",       "☕",  "sounds/cafe.mp3"),
        new SoundTrack("fireplace",  "Fireplace",  "🔥",  "sounds/fireplace.mp3"),
        new SoundTrack("ocean",      "Ocean",      "🌊",  "sounds/ocean.mp3"),
        new SoundTrack("library",    "Library",    "📚",  "sounds/library.mp3"),
        new SoundTrack("snow",       "Snow",       "❄",   "sounds/snow.mp3"),
        new SoundTrack("whitenoise", "Focus",      "〰",  "sounds/whitenoise.mp3"),
    };

    private final Map<String, MediaPlayer> players = new LinkedHashMap<>();
    private String  activId   = null;
    private double  volume    = 0.6;

    public SoundEngine() {
        for (SoundTrack t : TRACKS) {
            URL url = getClass().getResource(t.resourcePath());
            if (url == null) continue;          // file not bundled — skip silently
            try {
                MediaPlayer mp = new MediaPlayer(new Media(url.toExternalForm()));
                mp.setVolume(volume);
                mp.setCycleCount(MediaPlayer.INDEFINITE);   // loop forever
                mp.setOnError(() ->
                    System.err.println("[Sound] Error playing " + t.id() + ": "
                            + mp.getError().getMessage()));
                players.put(t.id(), mp);
            } catch (Exception ex) {
                System.err.println("[Sound] Cannot load " + t.resourcePath()
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
        return activId;
    }

    /**
     * Starts the named track, stopping whatever was playing before.
     * Calling with the already-active ID stops it (toggle behaviour).
     */
    public void play(String id) {
        if (id.equals(activId)) {
            stop();
            return;
        }
        stop();
        MediaPlayer mp = players.get(id);
        if (mp == null) return;
        mp.seek(Duration.ZERO);
        mp.play();
        activId = id;
    }

    /** Stops the current track. */
    public void stop() {
        if (activId != null) {
            MediaPlayer mp = players.get(activId);
            if (mp != null) mp.stop();
            activId = null;
        }
    }

    /** Sets volume 0.0–1.0 across all players. */
    public void setVolume(double v) {
        volume = Math.max(0.0, Math.min(1.0, v));
        players.values().forEach(mp -> mp.setVolume(volume));
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