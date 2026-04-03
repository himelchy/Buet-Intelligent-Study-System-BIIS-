package com.example;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.function.Consumer;

public final class TeacherChatView {
    private static final String BG_APP = "#1f2330";
    private static final String BG_PANEL = "#2b3144";
    private static final String BG_INPUT = "#3a4159";
    private static final String BG_ME = "#5865f2";
    private static final String BG_OTHER = "#2f364c";
    private static final String TEXT_PRIMARY = "#f3f4f8";
    private static final String TEXT_MUTED = "#9ca3b7";
    private static final String TEXT_ACCENT = "#7dd3fc";
    private static final String ACCENT = "#5865f2";
    private static final int HISTORY_LIMIT = 200;

    private final AppState state;
    private final Chatservice chatService;
    private final String channelId;
    private final String channelTitle;
    private final String channelSubtitle;
    private final String selfId;
    private final String selfName;

    private VBox messageBox;
    private ScrollPane messageScroll;
    private TextField inputField;
    private boolean showingPlaceholder;

    private final Consumer<ChatMessage> messageListener = this::onMessage;

    public TeacherChatView(AppState state,
            Chatservice chatService,
            String channelId,
            String channelTitle,
            String channelSubtitle,
            String selfId,
            String selfName) {
        this.state = state;
        this.chatService = chatService;
        this.channelId = channelId;
        this.channelTitle = channelTitle;
        this.channelSubtitle = channelSubtitle;
        this.selfId = selfId;
        this.selfName = selfName;
    }

    public Stage buildStage(Window owner) {
        chatService.joinCourse(channelId);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Chat - " + channelTitle);
        stage.setScene(new Scene(buildRoot(), 760, 620));
        stage.setMinWidth(620);
        stage.setMinHeight(460);
        stage.setOnCloseRequest(event -> chatService.removeListener(messageListener));

        loadHistory();
        chatService.addListener(messageListener);
        return stage;
    }

    private BorderPane buildRoot() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:" + BG_APP + ";");
        root.setTop(buildHeader());
        root.setCenter(buildMessagePane());
        root.setBottom(buildInputBar());
        return root;
    }

    private HBox buildHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 18, 16, 18));
        header.setStyle("-fx-background-color:" + BG_PANEL
                + ";-fx-border-color:transparent transparent #202536 transparent"
                + ";-fx-border-width:0 0 1 0;");

        Label title = new Label(channelTitle);
        title.setStyle("-fx-text-fill:" + TEXT_PRIMARY + ";-fx-font-size:18px;-fx-font-weight:bold;");

        Label subtitle = new Label(channelSubtitle);
        subtitle.setStyle("-fx-text-fill:" + TEXT_MUTED + ";-fx-font-size:12px;");

        VBox texts = new VBox(3, title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label("Signed in as " + selfName + " (" + selfId + ")");
        badge.setStyle("-fx-text-fill:" + TEXT_ACCENT
                + ";-fx-font-size:11px;-fx-font-weight:bold"
                + ";-fx-background-color:#1e3a4a;-fx-background-radius:5"
                + ";-fx-padding:4 8 4 8;");

        header.getChildren().addAll(texts, spacer, badge);
        return header;
    }

    private ScrollPane buildMessagePane() {
        messageBox = new VBox(8);
        messageBox.setPadding(new Insets(16));
        messageBox.setStyle("-fx-background-color:" + BG_APP + ";");

        messageScroll = new ScrollPane(messageBox);
        messageScroll.setFitToWidth(true);
        messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        messageScroll.setStyle("-fx-background:" + BG_APP
                + ";-fx-background-color:" + BG_APP
                + ";-fx-border-color:transparent;");
        return messageScroll;
    }

    private HBox buildInputBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 16, 16, 16));
        bar.setStyle("-fx-background-color:" + BG_APP + ";");

        HBox wrap = new HBox();
        wrap.setAlignment(Pos.CENTER_LEFT);
        wrap.setStyle("-fx-background-color:" + BG_INPUT
                + ";-fx-background-radius:8;-fx-padding:10 14 10 14;");
        HBox.setHgrow(wrap, Priority.ALWAYS);

        inputField = new TextField();
        inputField.setPromptText("Message " + channelTitle + "...");
        inputField.setStyle("-fx-background-color:transparent"
                + ";-fx-text-fill:" + TEXT_PRIMARY
                + ";-fx-font-size:14px"
                + ";-fx-prompt-text-fill:" + TEXT_MUTED
                + ";-fx-border-color:transparent"
                + ";-fx-highlight-fill:" + ACCENT + ";");
        inputField.setOnAction(event -> trySend());
        HBox.setHgrow(inputField, Priority.ALWAYS);
        wrap.getChildren().add(inputField);

        Button sendButton = new Button("Send");
        sendButton.setStyle("-fx-background-color:" + ACCENT
                + ";-fx-text-fill:white;-fx-font-size:13px;-fx-font-weight:bold"
                + ";-fx-background-radius:6;-fx-cursor:hand"
                + ";-fx-border-color:transparent;-fx-padding:9 20 9 20;");
        sendButton.setOnAction(event -> trySend());

        bar.getChildren().addAll(wrap, sendButton);
        return bar;
    }

    private void loadHistory() {
        messageBox.getChildren().clear();
        List<ChatMessage> history = state.loadChatMessages(channelId, HISTORY_LIMIT);
        showingPlaceholder = history.isEmpty();
        if (showingPlaceholder) {
            addSystemMessage("No messages yet. Start the conversation here.");
        } else {
            history.forEach(message -> messageBox.getChildren().add(buildBubble(message)));
        }
        scrollToBottom();
    }

    private void onMessage(ChatMessage message) {
        if (!channelId.equalsIgnoreCase(message.courseCode())) {
            return;
        }

        Platform.runLater(() -> {
            if (showingPlaceholder) {
                messageBox.getChildren().clear();
                showingPlaceholder = false;
            }
            messageBox.getChildren().add(buildBubble(message));
            scrollToBottom();
        });
    }

    private void trySend() {
        if (inputField == null) {
            return;
        }

        String text = inputField.getText().trim();
        if (text.isBlank()) {
            return;
        }

        inputField.clear();
        chatService.send(channelId, text);
    }

    private Node buildBubble(ChatMessage message) {
        boolean self = message.isSelf(selfId);

        HBox row = new HBox(10);
        row.setPadding(new Insets(2, 0, 2, 0));
        row.setAlignment(self ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        if (self) {
            Label timeLabel = buildLabel(message.formattedTime(), TEXT_MUTED, 10, false);

            Label content = new Label(message.content());
            content.setWrapText(true);
            content.setMaxWidth(460);
            content.setStyle("-fx-background-color:" + BG_ME
                    + ";-fx-text-fill:#eef2ff;-fx-font-size:14px"
                    + ";-fx-background-radius:16 16 4 16"
                    + ";-fx-padding:9 14 9 14;");

            VBox bubble = new VBox(4, timeLabel, content);
            bubble.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().add(bubble);
            return row;
        }

        StackPane avatar = buildAvatar(message.senderName(), 36);
        Label senderLabel = buildLabel(message.senderName(), TEXT_ACCENT, 13, true);
        Label timeLabel = buildLabel(message.formattedTime(), TEXT_MUTED, 10, false);
        HBox meta = new HBox(8, senderLabel, timeLabel);
        meta.setAlignment(Pos.CENTER_LEFT);

        Label content = new Label(message.content());
        content.setWrapText(true);
        content.setMaxWidth(460);
        content.setStyle("-fx-background-color:" + BG_OTHER
                + ";-fx-text-fill:" + TEXT_PRIMARY + ";-fx-font-size:14px"
                + ";-fx-background-radius:4 16 16 16"
                + ";-fx-padding:9 14 9 14;");

        VBox bubble = new VBox(4, meta, content);
        row.getChildren().addAll(avatar, bubble);
        return row;
    }

    private void addSystemMessage(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill:" + TEXT_MUTED + ";-fx-font-size:12px;-fx-font-style:italic;");

        HBox wrap = new HBox(label);
        wrap.setPadding(new Insets(4, 0, 14, 4));
        messageBox.getChildren().add(wrap);
    }

    private StackPane buildAvatar(String name, double size) {
        Circle circle = new Circle(size / 2.0);
        circle.setStyle("-fx-fill:" + avatarColor(name) + ";");

        Label initials = new Label(initials(name));
        initials.setStyle("-fx-text-fill:white;-fx-font-size:"
                + (int) (size * 0.36) + "px;-fx-font-weight:bold;");

        StackPane pane = new StackPane(circle, initials);
        pane.setPrefSize(size, size);
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);
        return pane;
    }

    private String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        }
        return name.length() >= 2 ? name.substring(0, 2).toUpperCase() : name.toUpperCase();
    }

    private String avatarColor(String name) {
        String[] colors = {
                "#5865f2", "#57f287", "#fee75c", "#eb459e", "#ed4245",
                "#3ba55d", "#faa61a", "#00b0f4", "#7289da", "#43b581"
        };
        return colors[Math.abs(name.hashCode()) % colors.length];
    }

    private Label buildLabel(String text, String color, int size, boolean bold) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill:" + color + ";-fx-font-size:" + size + "px;"
                + (bold ? "-fx-font-weight:bold;" : ""));
        return label;
    }

    private void scrollToBottom() {
        Platform.runLater(() -> messageScroll.setVvalue(1.0));
    }
}
