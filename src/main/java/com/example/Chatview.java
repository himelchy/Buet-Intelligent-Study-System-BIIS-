package com.example;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Discord-inspired dark chat window.
 *
 * Layout:
 * ┌──────────────────────────────────────────┐
 * │ Sidebar (248 px) │ Chat panel │
 * │ ───────────── │ ─────────────────── │
 * │ BISS Chat header │ # channel header │
 * │ # cse-110 │ message feed │
 * │ # cse-111 … │ │
 * │ [user bar] │ [input bar] │
 * └──────────────────────────────────────────┘
 *
 * One course = one channel. Switching channels replays stored history.
 * Messages from other students arrive asynchronously from Chatservice
 * and are appended via Platform.runLater.
 */
public final class Chatview {

    /* ── Discord-inspired dark palette ─────────────────────────── */
    private static final String BG_DARKEST = "#1e1f22";
    private static final String BG_SIDEBAR = "#2b2d31";
    private static final String BG_CH_ACTIVE = "#404249";
    private static final String BG_CH_HOVER = "#35373c";
    private static final String BG_CHAT = "#313338";
    private static final String BG_INPUT_WRAP = "#383a40";
    private static final String BG_BUBBLE_ME = "#4752c4";
    private static final String BG_BUBBLE_THM = "#2b2d31";
    private static final String TEXT_PRIMARY = "#dbdee1";
    private static final String TEXT_MUTED = "#949ba4";
    private static final String TEXT_CH = "#96989d";
    private static final String ACCENT = "#5865f2";
    private static final String GREEN_ONLINE = "#23a55a";

    /** Avatar background colours cycled by name hash. */
    private static final String[] AVATAR_COLOURS = {
            "#5865f2", "#57f287", "#fee75c", "#eb459e", "#ed4245",
            "#3ba55d", "#faa61a", "#00b0f4", "#7289da", "#43b581"
    };

    /* ── state ──────────────────────────────────────────────────── */
    private final AppState state;
    private final Chatservice chatService;

    /** Per-channel message history (thread-safe list, mutated on FX thread). */
    private final Map<String, ObservableList<ChatMessage>> history = new ConcurrentHashMap<>();
    /** Sidebar channel buttons. */
    private final Map<String, Button> channelBtns = new LinkedHashMap<>();
    /**
     * The first (code) label inside each channel button — updated on activation.
     */
    private final Map<String, Label> channelLabels = new LinkedHashMap<>();
    /** Full course titles for header display. */
    private final Map<String, String> channelTitles = new LinkedHashMap<>();

    private String activeCourse;

    /* ── live UI refs (set during buildRoot) ────────────────────── */
    private VBox messageBox;
    private ScrollPane messageScroll;
    private TextField inputField;
    private Label headerTitle;
    private Label headerSub;

    /** Stored so we can remove it when the window closes. */
    private final Consumer<ChatMessage> messageListener = this::onMessage;

    /* ── construction ───────────────────────────────────────────── */

    public Chatview(AppState state, Chatservice chatService) {
        this.state = state;
        this.chatService = chatService;
    }

    /* ── public entry point ─────────────────────────────────────── */

    /**
     * Builds and returns a configured (but not yet shown) Stage.
     * Caller is responsible for calling {@code stage.show()}.
     */
    /** How many past messages to load from the DB per channel. */
    private static final int HISTORY_LIMIT = 200;

    public Stage buildStage(Window owner) {
        List<CourseSelection> enrolled = state.courseSelections().stream()
                .filter(CourseSelection::isSelected)
                .toList();

        // Register multicast groups — history is loaded from DB on channel switch.
        for (CourseSelection cs : enrolled) {
            history.put(cs.code(), FXCollections.observableArrayList());
            channelTitles.put(cs.code(), cs.title());
            chatService.joinCourse(cs.code());
        }

        chatService.addListener(messageListener);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("BISS Chat — Student Channels");
        stage.setScene(new Scene(buildRoot(enrolled), 980, 680));
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        stage.setOnCloseRequest(e -> chatService.removeListener(messageListener));

        if (!enrolled.isEmpty()) {
            Platform.runLater(() -> switchChannel(enrolled.getFirst().code()));
        }

        return stage;
    }

    /* ── root layout ────────────────────────────────────────────── */

    private BorderPane buildRoot(List<CourseSelection> enrolled) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_DARKEST + ";");
        root.setLeft(buildSidebar(enrolled));
        root.setCenter(buildChatPane());
        return root;
    }

    /* ── sidebar ────────────────────────────────────────────────── */

    private VBox buildSidebar(List<CourseSelection> enrolled) {
        VBox bar = new VBox();
        bar.setPrefWidth(248);
        bar.setMinWidth(190);
        bar.setMaxWidth(280);
        bar.setStyle("-fx-background-color: " + BG_SIDEBAR + ";");
        bar.getChildren().addAll(
                sidebarHeader(),
                channelList(enrolled),
                spacer(),
                userBar());
        return bar;
    }

    private HBox sidebarHeader() {
        HBox hdr = new HBox();
        hdr.setPadding(new Insets(16, 16, 14, 16));
        hdr.setAlignment(Pos.CENTER_LEFT);
        hdr.setStyle("-fx-background-color:" + BG_SIDEBAR
                + ";-fx-border-color:transparent transparent #1a1b1e transparent"
                + ";-fx-border-width:0 0 1 0;");

        VBox txt = new VBox(2);
        Label name = styledLabel("BISS Chat", TEXT_PRIMARY, 15, true);
        Label sub = styledLabel(state.profile().department() + " · " + state.profile().term(),
                TEXT_MUTED, 11, false);
        txt.getChildren().addAll(name, sub);
        hdr.getChildren().add(txt);
        return hdr;
    }

    private VBox channelList(List<CourseSelection> enrolled) {
        VBox list = new VBox(2);
        list.setPadding(new Insets(14, 8, 8, 8));

        Label sect = styledLabel("  TEXT CHANNELS", TEXT_MUTED, 11, true);
        sect.setPadding(new Insets(0, 0, 6, 8));
        list.getChildren().add(sect);

        for (CourseSelection cs : enrolled) {
            list.getChildren().add(buildChannelBtn(cs.code(), cs.title()));
        }
        return list;
    }

    private Button buildChannelBtn(String code, String title) {
        Button btn = new Button();
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPadding(new Insets(7, 10, 7, 10));
        btn.setStyle(styleInactive());

        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        Label hash = styledLabel("#", TEXT_MUTED, 18, false);
        hash.setStyle("-fx-text-fill:" + TEXT_MUTED + ";-fx-font-size:18px;-fx-min-width:16px;");

        Label codeLabel = styledLabel(code.toLowerCase(), TEXT_CH, 14, false);
        Label titleLabel = styledLabel(title, TEXT_MUTED, 11, false);
        VBox labels = new VBox(1, codeLabel, titleLabel);

        row.getChildren().addAll(hash, labels);
        btn.setGraphic(row);
        btn.setAlignment(Pos.CENTER_LEFT);

        channelBtns.put(code, btn);
        channelLabels.put(code, codeLabel);

        btn.setOnMouseEntered(e -> {
            if (!code.equals(activeCourse))
                btn.setStyle(styleHover());
        });
        btn.setOnMouseExited(e -> {
            if (!code.equals(activeCourse))
                btn.setStyle(styleInactive());
        });
        btn.setOnAction(e -> switchChannel(code));
        return btn;
    }

    private HBox userBar() {
        HBox bar = new HBox(10);
        bar.setPadding(new Insets(10, 12, 14, 12));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color:#232428;");

        String name = state.profile().name();
        String roll = state.profile().roll();

        StackPane av = buildAvatar(name, 34);
        Label nameLbl = styledLabel(name.length() > 20 ? name.substring(0, 17) + "…" : name,
                TEXT_PRIMARY, 13, true);
        Label rollLbl = styledLabel(roll, TEXT_MUTED, 11, false);
        VBox info = new VBox(1, nameLbl, rollLbl);
        Region sp = spacer();

        Circle dot = new Circle(5);
        dot.setStyle("-fx-fill:" + GREEN_ONLINE + ";");

        bar.getChildren().addAll(av, info, sp, dot);
        return bar;
    }

    /* ── chat panel ─────────────────────────────────────────────── */

    private BorderPane buildChatPane() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color:" + BG_CHAT + ";");
        pane.setTop(chatHeader());
        pane.setCenter(messageScrollPane());
        pane.setBottom(inputBar());
        return pane;
    }

    private HBox chatHeader() {
        HBox hdr = new HBox(10);
        hdr.setPadding(new Insets(14, 18, 14, 18));
        hdr.setAlignment(Pos.CENTER_LEFT);
        hdr.setStyle("-fx-background-color:" + BG_CHAT
                + ";-fx-border-color:transparent transparent #23242a transparent"
                + ";-fx-border-width:0 0 1 0;");

        Label hash = styledLabel("#", TEXT_MUTED, 22, false);

        headerTitle = styledLabel("Select a channel", TEXT_PRIMARY, 16, true);
        headerSub = styledLabel("Course text channel", TEXT_MUTED, 12, false);
        VBox titles = new VBox(2, headerTitle, headerSub);

        Region sp = spacer();

        Label badge = new Label("● UDP LIVE");
        badge.setStyle("-fx-text-fill:" + GREEN_ONLINE
                + ";-fx-font-size:11px;-fx-font-weight:bold"
                + ";-fx-background-color:#1a3a28;-fx-background-radius:4"
                + ";-fx-padding:3 8 3 8;");

        hdr.getChildren().addAll(hash, titles, sp, badge);
        return hdr;
    }

    private ScrollPane messageScrollPane() {
        messageBox = new VBox(6);
        messageBox.setPadding(new Insets(16));
        messageBox.setStyle("-fx-background-color:" + BG_CHAT + ";");

        messageScroll = new ScrollPane(messageBox);
        messageScroll.setFitToWidth(true);
        messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        messageScroll.setStyle("-fx-background:" + BG_CHAT
                + ";-fx-background-color:" + BG_CHAT
                + ";-fx-border-color:transparent;");
        return messageScroll;
    }

    private HBox inputBar() {
        HBox bar = new HBox(10);
        bar.setPadding(new Insets(0, 16, 16, 16));
        bar.setAlignment(Pos.CENTER);
        bar.setStyle("-fx-background-color:" + BG_CHAT + ";");

        HBox wrap = new HBox();
        wrap.setAlignment(Pos.CENTER_LEFT);
        wrap.setStyle("-fx-background-color:" + BG_INPUT_WRAP
                + ";-fx-background-radius:8;-fx-padding:10 14 10 14;");
        HBox.setHgrow(wrap, Priority.ALWAYS);

        inputField = new TextField();
        inputField.setPromptText("Type a message…");
        inputField.setStyle("-fx-background-color:transparent"
                + ";-fx-text-fill:" + TEXT_PRIMARY
                + ";-fx-font-size:14px"
                + ";-fx-prompt-text-fill:" + TEXT_MUTED
                + ";-fx-border-color:transparent"
                + ";-fx-highlight-fill:" + ACCENT + ";");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputField.setOnAction(e -> trySend());
        wrap.getChildren().add(inputField);

        Button sendBtn = new Button("Send");
        sendBtn.setStyle("-fx-background-color:" + ACCENT
                + ";-fx-text-fill:white;-fx-font-size:13px;-fx-font-weight:bold"
                + ";-fx-background-radius:6;-fx-cursor:hand"
                + ";-fx-border-color:transparent;-fx-padding:9 20 9 20;");
        sendBtn.setOnAction(e -> trySend());

        bar.getChildren().addAll(wrap, sendBtn);
        return bar;
    }

    /* ── channel switching ──────────────────────────────────────── */

    private void switchChannel(String code) {
        // Deactivate previous selection.
        if (activeCourse != null) {
            Button old = channelBtns.get(activeCourse);
            Label lbl = channelLabels.get(activeCourse);
            if (old != null)
                old.setStyle(styleInactive());
            if (lbl != null)
                lbl.setStyle("-fx-text-fill:" + TEXT_CH + ";-fx-font-size:14px;");
        }

        activeCourse = code;

        // Activate new selection.
        Button newBtn = channelBtns.get(code);
        Label newLbl = channelLabels.get(code);
        if (newBtn != null)
            newBtn.setStyle(styleActive());
        if (newLbl != null)
            newLbl.setStyle("-fx-text-fill:" + TEXT_PRIMARY
                    + ";-fx-font-size:14px;-fx-font-weight:bold;");

        String title = channelTitles.getOrDefault(code, code);
        headerTitle.setText(code.toLowerCase() + "  —  " + title);
        headerSub.setText("Course text channel  ·  UDP multicast  ·  LAN peers");

        // Load persisted history from DB and merge with any live messages
        // received since the window opened but before this channel was selected.
        java.util.List<ChatMessage> dbHistory = state.loadChatMessages(code, HISTORY_LIMIT);
        ObservableList<ChatMessage> liveList = history.get(code);

        // Build a deduplicated merged list: DB first (oldest), then any live
        // messages that arrived after the last DB timestamp.
        long lastDbTs = dbHistory.isEmpty() ? 0 : dbHistory.get(dbHistory.size() - 1).timestamp();
        java.util.List<ChatMessage> liveSince = liveList == null
                ? java.util.List.of()
                : liveList.stream()
                        .filter(m -> m.timestamp() > lastDbTs)
                        .toList();

        // Replace the in-memory list with the merged set so future appends stay
        // consistent.
        if (liveList != null) {
            liveList.setAll(dbHistory);
            liveList.addAll(liveSince);
        }

        // Rebuild message feed.
        messageBox.getChildren().clear();
        if (dbHistory.isEmpty() && liveSince.isEmpty()) {
            addSystemMsg("No messages yet in #" + code.toLowerCase()
                    + "  ·  Be the first to say something!");
        } else {
            addSystemMsg("Showing last " + (dbHistory.size() + liveSince.size())
                    + " messages  ·  #" + code.toLowerCase());
        }

        java.util.List<ChatMessage> all = new java.util.ArrayList<>(dbHistory);
        all.addAll(liveSince);
        all.forEach(m -> messageBox.getChildren().add(buildBubble(m)));

        inputField.setPromptText("Message #" + code.toLowerCase() + "…");
        inputField.requestFocus();
        scrollToBottom();
    }

    /* ── message handling ───────────────────────────────────────── */

    /** Called on the receiver thread — must dispatch to FX thread. */
    private void onMessage(ChatMessage msg) {
        Platform.runLater(() -> {
            ObservableList<ChatMessage> list = history.get(msg.courseCode());
            if (list == null)
                return;
            list.add(msg);
            if (msg.courseCode().equals(activeCourse)) {
                messageBox.getChildren().add(buildBubble(msg));
                scrollToBottom();
            }
        });
    }

    private void trySend() {
        if (activeCourse == null || inputField == null)
            return;
        String text = inputField.getText().trim();
        if (text.isBlank())
            return;
        inputField.clear();
        chatService.send(activeCourse, text);
    }

    /* ── bubble builder ─────────────────────────────────────────── */

    private Node buildBubble(ChatMessage msg) {
        boolean self = msg.isSelf(state.profile().roll());
        HBox row = new HBox(10);
        row.setPadding(new Insets(2, 0, 2, 0));

        if (self) {
            /* ── own message: right-aligned blurple bubble ── */
            row.setAlignment(Pos.CENTER_RIGHT);

            Label timeLbl = styledLabel(msg.formattedTime(), TEXT_MUTED, 10, false);

            Label content = new Label(msg.content());
            content.setWrapText(true);
            content.setMaxWidth(460);
            content.setStyle("-fx-background-color:" + BG_BUBBLE_ME
                    + ";-fx-text-fill:#e8e8ff;-fx-font-size:14px"
                    + ";-fx-background-radius:16 16 4 16"
                    + ";-fx-padding:9 14 9 14;");

            VBox bubble = new VBox(4, timeLbl, content);
            bubble.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().add(bubble);

        } else {
            /* ── peer message: left-aligned with coloured avatar ── */
            row.setAlignment(Pos.CENTER_LEFT);

            StackPane av = buildAvatar(msg.senderName(), 36);
            VBox.setMargin(av, new Insets(2, 0, 0, 0));

            Label senderLbl = styledLabel(msg.senderName(), avatarColour(msg.senderName()), 13, true);
            Label timeLbl = styledLabel(msg.formattedTime(), TEXT_MUTED, 10, false);
            HBox meta = new HBox(8, senderLbl, timeLbl);
            meta.setAlignment(Pos.CENTER_LEFT);

            Label content = new Label(msg.content());
            content.setWrapText(true);
            content.setMaxWidth(460);
            content.setStyle("-fx-background-color:" + BG_BUBBLE_THM
                    + ";-fx-text-fill:" + TEXT_PRIMARY + ";-fx-font-size:14px"
                    + ";-fx-background-radius:4 16 16 16"
                    + ";-fx-padding:9 14 9 14;");

            VBox bubble = new VBox(4, meta, content);
            row.getChildren().addAll(av, bubble);
        }

        return row;
    }

    private void addSystemMsg(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill:" + TEXT_MUTED + ";-fx-font-size:12px;-fx-font-style:italic;");
        lbl.setWrapText(true);
        HBox wrap = new HBox(lbl);
        wrap.setPadding(new Insets(4, 0, 14, 4));
        messageBox.getChildren().add(wrap);
    }

    /* ── avatar ─────────────────────────────────────────────────── */

    private StackPane buildAvatar(String name, double size) {
        Circle bg = new Circle(size / 2.0);
        bg.setStyle("-fx-fill:" + avatarColour(name) + ";");

        Label initials = new Label(initials(name));
        initials.setStyle("-fx-text-fill:white;-fx-font-size:"
                + (int) (size * 0.36) + "px;-fx-font-weight:bold;");

        // Clip to circle shape for a perfectly round avatar.
        StackPane pane = new StackPane(bg, initials);
        pane.setPrefSize(size, size);
        pane.setMaxSize(size, size);
        pane.setMinSize(size, size);
        pane.setClip(new Circle(size / 2.0, size / 2.0, size / 2.0));
        return pane;
    }

    private String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        }
        return name.length() >= 2 ? name.substring(0, 2).toUpperCase() : name.toUpperCase();
    }

    private String avatarColour(String name) {
        return AVATAR_COLOURS[Math.abs(name.hashCode()) % AVATAR_COLOURS.length];
    }

    /* ── style helpers ──────────────────────────────────────────── */

    private static String styleInactive() {
        return "-fx-background-color:transparent;-fx-background-radius:4"
                + ";-fx-border-color:transparent;-fx-cursor:hand;";
    }

    private static String styleHover() {
        return "-fx-background-color:" + BG_CH_HOVER
                + ";-fx-background-radius:4;-fx-border-color:transparent;-fx-cursor:hand;";
    }

    private static String styleActive() {
        return "-fx-background-color:" + BG_CH_ACTIVE
                + ";-fx-background-radius:4;-fx-border-color:transparent;-fx-cursor:hand;";
    }

    private static Label styledLabel(String text, String colour, int size, boolean bold) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + colour + ";-fx-font-size:" + size + "px;"
                + (bold ? "-fx-font-weight:bold;" : ""));
        return l;
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    private void scrollToBottom() {
        Platform.runLater(() -> messageScroll.setVvalue(1.0));
    }
}