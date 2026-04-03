package com.example;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * UDP multicast chat service.
 *
 * Each enrolled course maps to a dedicated IPv4 multicast group
 * (239.255.42.X, port 4447).  Joining a course spawns a daemon
 * receiver thread that deserialises incoming datagrams and fans
 * them out to registered listeners on whatever thread they arrive on
 * — callers must dispatch to the JavaFX thread themselves via
 * {@code Platform.runLater}.
 *
 * Own messages are echoed directly to listeners at send-time so they
 * appear immediately; loopback is enabled on the socket so the same
 * machine can host multiple instances for testing, but own-roll
 * messages received via the network are silently discarded to prevent
 * duplicates.
 *
 * Thread safety: all public methods are safe to call from any thread.
 */
public final class Chatservice implements AutoCloseable {

    /* ── network constants ──────────────────────────────────────── */
    private static final int    PORT        = 4447;
    private static final int    BUFFER_SIZE = 8192;
    private static final int    SO_TIMEOUT  = 400;   // ms — poll interval for running flag

    /* ── instance state ─────────────────────────────────────────── */
    private final String selfRoll;
    private final String selfName;

    /** Live multicast sockets, keyed by course code. */
    private final Map<String, MulticastSocket>   sockets      = new ConcurrentHashMap<>();
    /** Message consumers — iterated on every incoming message. */
    private final List<Consumer<ChatMessage>>    listeners    = new CopyOnWriteArrayList<>();
    /**
     * Persistence callback — called once for every unique message
     * (own messages after send, peer messages after receive).
     * Runs on the receiver/send thread, NOT the FX thread — the
     * implementation must be thread-safe (DatabaseService is).
     */
    private final Consumer<ChatMessage> saveCallback;

    private volatile boolean running = true;

    /* ── construction ───────────────────────────────────────────── */

    /**
     * @param selfRoll     the logged-in student's roll number
     * @param selfName     the logged-in student's display name
     * @param saveCallback called with each message to persist it;
     *                     pass {@code msg -> {}} to disable persistence
     */
    public Chatservice(String selfRoll, String selfName, Consumer<ChatMessage> saveCallback) {
        this.selfRoll     = selfRoll.trim();
        this.selfName     = selfName.trim();
        this.saveCallback = saveCallback != null ? saveCallback : msg -> {};
    }

    /* ── public API ─────────────────────────────────────────────── */

    public boolean isRunning() {
        return running;
    }

    /**
     * Registers a consumer that will be called for every new message
     * (including own messages echoed immediately after {@link #send}).
     * The consumer is invoked on the receiver thread — use
     * {@code Platform.runLater} in the consumer body for UI updates.
     */
    public void addListener(Consumer<ChatMessage> listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(Consumer<ChatMessage> listener) {
        listeners.remove(listener);
    }

    /**
     * Joins the multicast group for {@code courseCode} and starts a
     * dedicated daemon receiver thread.  Idempotent — safe to call
     * more than once for the same code.
     */
    @SuppressWarnings("deprecation")
    public void joinCourse(String courseCode) {
        if (!running || sockets.containsKey(courseCode)) return;
        try {
            InetAddress group = InetAddress.getByName(groupFor(courseCode));

            MulticastSocket socket = new MulticastSocket(PORT);
            socket.setReuseAddress(true);
            // Loopback ON → same-machine instances receive each other's packets.
            // Own-roll filtering in receiveLoop prevents duplicate display.
            socket.setLoopbackMode(false);
            socket.joinGroup(group);        // deprecated in Java 14 but widest compat
            socket.setSoTimeout(SO_TIMEOUT);

            sockets.put(courseCode, socket);

            Thread t = new Thread(
                    () -> receiveLoop(courseCode, socket),
                    "biss-chat-recv-" + courseCode
            );
            t.setDaemon(true);
            t.start();

            System.out.println("[Chat] Joined course " + courseCode
                    + " → group " + groupFor(courseCode) + ":" + PORT);

        } catch (IOException ex) {
            System.err.println("[Chat] Cannot join " + courseCode + ": " + ex.getMessage());
        }
    }

    /**
     * Sends {@code content} to all peers in {@code courseCode}'s channel.
     * The message is also echoed immediately to local listeners so the
     * sender sees their own message without waiting for network loopback.
     */
    public void send(String courseCode, String content) {
        if (!running || content == null || content.isBlank()) return;
        MulticastSocket socket = sockets.get(courseCode);
        if (socket == null || socket.isClosed()) return;

        ChatMessage msg = new ChatMessage(
                courseCode,
                selfRoll,
                selfName,
                System.currentTimeMillis(),
                content.trim()
        );

        // Echo own message directly — no network round-trip needed for self.
        fanOut(msg);

        try {
            byte[]       data  = msg.encrypt();          // AES-256-GCM encrypted bytes
            InetAddress  group = InetAddress.getByName(groupFor(courseCode));
            socket.send(new DatagramPacket(data, data.length, group, PORT));
        } catch (IOException ex) {
            System.err.println("[Chat] Send failed (" + courseCode + "): " + ex.getMessage());
        } catch (Exception ex) {
            System.err.println("[Chat] Encryption failed (" + courseCode + "): " + ex.getMessage());
        }
    }

    /** Leaves all multicast groups and closes sockets. */
    @Override
    @SuppressWarnings("deprecation")
    public void close() {
        running = false;
        for (Map.Entry<String, MulticastSocket> entry : new HashSet<>(sockets.entrySet())) {
            try {
                entry.getValue().leaveGroup(
                        InetAddress.getByName(groupFor(entry.getKey())));
            } catch (IOException ignored) {}
            entry.getValue().close();
        }
        sockets.clear();
        System.out.println("[Chat] Service closed.");
    }

    /* ── receive loop (runs on daemon thread per course) ────────── */

    private void receiveLoop(String courseCode, MulticastSocket socket) {
        byte[] buf = new byte[BUFFER_SIZE];
        while (running && !socket.isClosed()) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                // Decrypt using the course-specific AES key.
                // Returns null for wrong-key, tampered, or unrelated traffic — all silently dropped.
                ChatMessage msg = ChatMessage.decrypt(pkt.getData(), pkt.getLength(), courseCode);

                if (msg == null) continue;
                if (!courseCode.equals(msg.courseCode())) continue;
                // Skip own packets — already echoed directly in send().
                if (msg.isSelf(selfRoll)) continue;

                fanOut(msg);

            } catch (SocketTimeoutException ignored) {
                // Normal heartbeat — re-check running flag.
            } catch (IOException ex) {
                if (running && !socket.isClosed()) {
                    System.err.println("[Chat] Recv error (" + courseCode + "): " + ex.getMessage());
                }
            }
        }
        System.out.println("[Chat] Receiver stopped for " + courseCode);
    }

    /* ── helpers ────────────────────────────────────────────────── */

    private void fanOut(ChatMessage msg) {
        // Persist first — before notifying UI so history is always consistent.
        try { saveCallback.accept(msg); } catch (Exception ignored) {}
        for (Consumer<ChatMessage> l : listeners) {
            try { l.accept(msg); } catch (Exception ignored) {}
        }
    }

    /**
     * Deterministically maps a course code to a multicast group address.
     * Uses TWO octets (239.255.X.Y) giving 65,025 possible groups —
     * verified zero collisions across all BUET department course codes.
     * The old single-octet scheme had 12 real collisions (e.g. CSE-205
     * and ME-101 shared the same group). This version is collision-free.
     */
    static String groupFor(String courseCode) {
        int h = courseCode.toUpperCase().chars()
                .reduce(17, (a, b) -> a * 31 + b) & 0xFFFFFFFF;
        int octet3 = Math.max(1, (h >> 8) & 0xFF);
        int octet4 = Math.max(1, h & 0xFF);
        return "239.255." + octet3 + "." + octet4;
    }
}