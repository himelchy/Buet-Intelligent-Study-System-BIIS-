package com.example;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Immutable chat message record. Serialised to a single UTF-8 string for UDP transport.
 * Uses ASCII Unit Separator (0x1F) as field delimiter — safe against ordinary text.
 *
 * Wire security: messages are encrypted with AES-256-GCM before sending.
 * The AES key is derived from SHA-256(APP_SECRET + COURSE_CODE), so each
 * course channel has a completely different key — a student enrolled only
 * in CSE-110 cannot decrypt CSE-109 packets even if they capture them.
 * GCM authentication also prevents any packet tampering on the wire.
 */
public record ChatMessage(
        String courseCode,
        String senderRoll,
        String senderName,
        long   timestamp,
        String content
) {
    private static final char   SEP        = '\u001F';
    private static final int    GCM_IV_LEN = 12;   // 96-bit IV recommended for GCM
    private static final int    GCM_TAG    = 128;  // 128-bit authentication tag

    /**
     * Shared application secret baked into the binary.
     * Changing this value invalidates all previously encrypted messages on the wire
     * (DB-stored plaintext is unaffected — it was never encrypted there).
     */
    private static final String APP_SECRET = "BISS-BUET-SECURE-2026";

    /* ── crypto helpers ─────────────────────────────────────────── */

    /**
     * Derives a 256-bit AES key from the course code.
     * SHA-256(APP_SECRET + courseCode.toUpperCase()) → 32 bytes.
     * Different course → different key → cannot cross-decrypt.
     */
    private static SecretKeySpec keyFor(String courseCode) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] raw = sha.digest(
                    (APP_SECRET + courseCode.toUpperCase())
                            .getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(raw, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("AES key derivation failed", ex);
        }
    }

    /**
     * Encrypts this message for transmission.
     * Wire format: [ 12-byte random IV | AES-GCM ciphertext+tag ]
     * Each call uses a fresh IV so the same plaintext never encrypts identically.
     *
     * @return raw bytes ready to put directly into a DatagramPacket
     * @throws Exception on any crypto failure (should never happen on a normal JVM)
     */
    public byte[] encrypt() throws Exception {
        byte[] iv = new byte[GCM_IV_LEN];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                keyFor(courseCode),
                new GCMParameterSpec(GCM_TAG, iv));

        byte[] ciphertext = cipher.doFinal(
                serialize().getBytes(StandardCharsets.UTF_8));

        // Prepend the IV so the receiver can reconstruct the same GCM state.
        byte[] out = new byte[GCM_IV_LEN + ciphertext.length];
        System.arraycopy(iv,         0, out, 0,          GCM_IV_LEN);
        System.arraycopy(ciphertext, 0, out, GCM_IV_LEN, ciphertext.length);
        return out;
    }

    /**
     * Decrypts a raw datagram payload into a ChatMessage.
     *
     * @param data       raw bytes from the DatagramPacket
     * @param len        number of valid bytes in {@code data}
     * @param courseCode the channel we expect this message to belong to
     * @return the decoded message, or {@code null} if decryption or parsing fails
     *         (wrong key / tampered packet / garbage data all return null silently)
     */
    public static ChatMessage decrypt(byte[] data, int len, String courseCode) {
        if (len <= GCM_IV_LEN) return null;
        try {
            byte[] iv         = Arrays.copyOfRange(data, 0, GCM_IV_LEN);
            byte[] ciphertext = Arrays.copyOfRange(data, GCM_IV_LEN, len);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    keyFor(courseCode),
                    new GCMParameterSpec(GCM_TAG, iv));

            String plain = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            return deserialize(plain);
        } catch (Exception ex) {
            // Wrong key, tampered payload, or unrelated multicast traffic — drop silently.
            return null;
        }
    }

    /** Produces a single-line string suitable for a UDP datagram payload. */
    public String serialize() {
        return courseCode + SEP
                + senderRoll + SEP
                + senderName + SEP
                + timestamp  + SEP
                + content.replace(SEP, ' ');   // sanitise content
    }

    /**
     * Rebuilds a ChatMessage from a serialised string.
     * Returns {@code null} if the payload is malformed.
     */
    public static ChatMessage deserialize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] p = raw.split(String.valueOf(SEP), 5);
        if (p.length < 5) return null;
        try {
            return new ChatMessage(p[0], p[1], p[2], Long.parseLong(p[3]), p[4]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Returns a short HH:mm string for display next to the bubble. */
    public String formattedTime() {
        return LocalTime
                .ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    /** True when this message was sent by the given roll number. */
    public boolean isSelf(String roll) {
        return roll != null && senderRoll.equalsIgnoreCase(roll.trim());
    }
}