package com.example;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import javafx.util.Duration;

/**
 * BISS Study Space — a full study environment window containing:
 *   • Multi-tab PDF / PPT / online book reader
 *   • Dark / Light / Sepia reading themes
 *   • Ambient sound bar (rain, café, fire…)
 *   • Per-course note taker with save, share, and send-to-chat
 *   • Bookshelf sidebar loaded from / saved to SQLite
 *   • Teacher uploaded slides accessible directly
 */
public final class StudySpaceView {

    // ── Themes ──────────────────────────────────────────────────────────
    public enum Theme {
        LIGHT ("#fafafa", "#1a1a1a", "#f0f0f0"),
        DARK  ("#1e1e2e", "#cdd6f4", "#2a2a3e"),
        SEPIA ("#f4ecd8", "#3b2f2f", "#e8dcc8");

        public final String bg, fg, sidebar;
        Theme(String bg, String fg, String sidebar) {
            this.bg = bg; this.fg = fg; this.sidebar = sidebar;
        }
    }

    private static final String ACCENT       = "#7c3aed";
    private static final String ACCENT_LIGHT = "#ede9fe";
    private static final String BTN_STYLE    =
            "-fx-background-color:#7c3aed;-fx-text-fill:white;-fx-font-size:12px;"
            + "-fx-background-radius:6;-fx-cursor:hand;-fx-padding:6 14 6 14;";
    private static final String GHOST_STYLE  =
            "-fx-background-color:transparent;-fx-text-fill:#7c3aed;-fx-font-size:12px;"
            + "-fx-background-radius:6;-fx-cursor:hand;-fx-padding:6 14 6 14;"
            + "-fx-border-color:#7c3aed;-fx-border-radius:6;-fx-border-width:1;";

    // ── State ────────────────────────────────────────────────────────────
    private final AppState    state;
    private final String      stylesheetUrl;
    private final BiConsumer<String, String> courseChatSender;
    private final SoundEngine sound;
    private final AiStudyCoachService aiCoach;
    private       Theme       theme = Theme.LIGHT;

    // Live UI roots updated on theme change
    private BorderPane  root;
    private VBox        sidebarBox;
    private TabPane     readerTabs;
    private TabPane     rightSidebarTabs;
    private VBox        notesPanel;
    private VBox        aiCoachPanel;
    private HBox        soundBar;

    // Notes state
    private String        notesCourse  = null;
    private StudyNote     editingNote  = null;
    private ComboBox<String> notesCourseBox;
    private TextField     notesSearchField;
    private ComboBox<NoteFilterMode> notesFilterBox;
    private TextField     noteTitleField;
    private TextArea      noteContentArea;
    private Button        notePinButton;
    private Label         myNotesHeader;
    private Label         sharedNotesHeader;
    private ListView<StudyNote> myNotesList;
    private ListView<StudyNote> sharedNotesList;
    private List<StudyNote> loadedMyNotes = List.of();
    private List<StudyNote> loadedSharedNotes = List.of();
    private boolean       draftNotePinned;
    private ListView<DatabaseService.BookEntry> bookList;
    private final Map<Tab, ReaderContext> readerContexts = new HashMap<>();

    // AI Coach state
    private ComboBox<AiContextMode> aiContextModeBox;
    private Label aiStatusLabel;
    private Label aiContextSummaryLabel;
    private TextArea aiPromptArea;
    private TextArea aiOutputArea;
    private ProgressIndicator aiBusyIndicator;
    private final List<Button> aiRequestButtons = new ArrayList<>();
    private final List<Button> aiResultButtons = new ArrayList<>();
    private AiStudyCoachRequest lastAiRequest;
    private AiStudyCoachResult lastAiResult;
    private boolean aiBusy;

    // Sound button map for toggle highlight
    private final Map<String, Button> soundBtns = new LinkedHashMap<>();

    // Focus timer state
    private Timeline        pomodoroTimeline;
    private PomodoroPhase   pomodoroPhase = PomodoroPhase.FOCUS;
    private int             pomodoroRemainingSeconds = 25 * 60;
    private int             completedFocusSessions;
    private Label           pomodoroPhaseLabel;
    private Label           pomodoroCountdownLabel;
    private Label           pomodoroSummaryLabel;
    private ComboBox<Integer> focusDurationBox;
    private ComboBox<Integer> breakDurationBox;
    private Button          pomodoroStartPauseButton;

    // ── Construction ─────────────────────────────────────────────────────

    public StudySpaceView(AppState state, String stylesheetUrl) {
        this(state, stylesheetUrl, null);
    }

    public StudySpaceView(AppState state, String stylesheetUrl, BiConsumer<String, String> courseChatSender) {
        this.state        = state;
        this.stylesheetUrl = stylesheetUrl;
        this.courseChatSender = courseChatSender;
        this.sound        = new SoundEngine();
        this.aiCoach      = new AiStudyCoachService();
    }

    public Stage buildStage(Window owner) {
        root = buildRoot();

        Scene scene = new Scene(root, 1200, 760);
        scene.getStylesheets().add(stylesheetUrl);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("BISS Study Space");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setOnCloseRequest(e -> {
            disposePomodoroTimer();
            sound.dispose();
            aiCoach.close();
        });

        applyTheme(Theme.LIGHT);

        // Restore bookshelf from DB
        loadBookshelfFromDb();

        return stage;
    }

    // ── Root layout ──────────────────────────────────────────────────────

    private BorderPane buildRoot() {
        root = new BorderPane();

        root.setTop(buildToolbar());
        root.setLeft(buildSidebar());
        root.setCenter(buildReaderArea());
        root.setRight(buildRightSidebar());
        root.setBottom(buildSoundBar());

        return root;
    }

    // ── Toolbar ──────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        HBox bar = new HBox(10);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("📖  BISS Study Space");
        title.setStyle("-fx-font-size:15px;-fx-font-weight:bold;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button addFileBtn = styledBtn("+ Add Book / PDF / PPT", true);
        addFileBtn.setOnAction(e -> openFileChooser());

        Button addUrlBtn = styledBtn("+ Online URL", false);
        addUrlBtn.setOnAction(e -> openUrlDialog());

        Button teacherBtn = styledBtn("Teacher Slides", false);
        teacherBtn.setOnAction(e -> openTeacherResourcesDialog());

        // Theme buttons
        ToggleGroup tg = new ToggleGroup();
        ToggleButton light = themeToggle("☀ Light", Theme.LIGHT, tg);
        ToggleButton dark  = themeToggle("🌙 Dark",  Theme.DARK,  tg);
        ToggleButton sepia = themeToggle("📜 Sepia", Theme.SEPIA, tg);
        light.setSelected(true);
        HBox themeBox = new HBox(0, light, dark, sepia);
        themeBox.setStyle("-fx-background-radius:6;-fx-border-radius:6;"
                + "-fx-border-color:#d0d0d0;-fx-border-width:1;");

        bar.getChildren().addAll(title, sp, addFileBtn, addUrlBtn, teacherBtn, themeBox);
        return bar;
    }

    // ── Left sidebar ─────────────────────────────────────────────────────

    private VBox buildSidebar() {
        sidebarBox = new VBox(0);
        sidebarBox.setPrefWidth(220);
        sidebarBox.setMinWidth(180);

        Label booksHdr = sidebarHeader("MY LIBRARY");
        bookList = new ListView<>();
        bookList.setId("book-list");
        bookList.setPrefHeight(220);
        bookList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(DatabaseService.BookEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                String icon = item.sourceType().equals("URL") ? "🌐"
                        : item.title().toLowerCase().endsWith(".pptx")
                        || item.title().toLowerCase().endsWith(".ppt") ? "📊" : "📄";
                HBox row = new HBox(6);
                Label lbl = new Label(icon + " " + truncate(item.title(), 22));
                lbl.setStyle("-fx-font-size:12px;");
                Button del = new Button("✕");
                del.setStyle("-fx-font-size:9px;-fx-background-color:transparent;"
                        + "-fx-text-fill:#cc0000;-fx-cursor:hand;-fx-padding:0 2 0 2;");
                del.setOnAction(e -> {
                    state.deleteBook(item.id());
                    bookList.getItems().remove(item);
                });
                Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS);
                row.getChildren().addAll(lbl, s, del);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });
        bookList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                DatabaseService.BookEntry entry = bookList.getSelectionModel().getSelectedItem();
                if (entry != null) openBookTab(entry);
            }
        });

        sidebarBox.getChildren().addAll(booksHdr, bookList);

        // Notes section in sidebar
        Label notesToolsHdr = sidebarHeader("FIND NOTES");
        notesSearchField = new TextField();
        notesSearchField.setPromptText("Search note title or text");
        notesSearchField.setStyle("-fx-font-size:12px;");
        notesSearchField.textProperty().addListener((obs, oldValue, newValue) -> applyNotesFilter());

        notesFilterBox = new ComboBox<>(FXCollections.observableArrayList(NoteFilterMode.values()));
        notesFilterBox.setMaxWidth(Double.MAX_VALUE);
        notesFilterBox.setValue(NoteFilterMode.ALL);
        notesFilterBox.setOnAction(e -> applyNotesFilter());

        myNotesHeader = sidebarHeader("MY NOTES");
        myNotesList = new ListView<>();
        myNotesList.setPrefHeight(160);
        myNotesList.setCellFactory(lv -> noteCell());
        myNotesList.setPlaceholder(new Label("No notes found."));
        myNotesList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                StudyNote n = myNotesList.getSelectionModel().getSelectedItem();
                if (n != null) loadNoteIntoEditor(n);
            }
        });

        sharedNotesHeader = sidebarHeader("SHARED NOTES");
        sharedNotesList = new ListView<>();
        sharedNotesList.setPrefHeight(140);
        sharedNotesList.setCellFactory(lv -> noteCell());
        sharedNotesList.setPlaceholder(new Label("No shared notes found."));
        sharedNotesList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                StudyNote n = sharedNotesList.getSelectionModel().getSelectedItem();
                if (n != null) loadNoteIntoEditor(n);
            }
        });

        sidebarBox.getChildren().addAll(
                notesToolsHdr,
                notesSearchField,
                notesFilterBox,
                myNotesHeader,
                myNotesList,
                sharedNotesHeader,
                sharedNotesList);
        return sidebarBox;
    }

    // ── Reader area (multi-tab) ──────────────────────────────────────────

    private StackPane buildReaderArea() {
        readerTabs = new TabPane();
        readerTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        readerTabs.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldTab, newTab) -> refreshAiContextSummary());

        Tab placeholder = new Tab("Welcome");
        placeholder.setClosable(false);
        VBox welcome = new VBox(18);
        welcome.setAlignment(Pos.CENTER);
        welcome.setPadding(new Insets(60));
        Label wl = new Label("📚  Open a book, PDF, PPT, or URL to start reading");
        wl.setStyle("-fx-font-size:16px;-fx-text-fill:#888;");
        Label wl2 = new Label("Double-click a library item or use the toolbar buttons above");
        wl2.setStyle("-fx-font-size:13px;-fx-text-fill:#aaa;");
        welcome.getChildren().addAll(wl, wl2);
        placeholder.setContent(welcome);
        readerTabs.getTabs().add(placeholder);

        StackPane wrap = new StackPane(readerTabs);
        return wrap;
    }

    private TabPane buildRightSidebar() {
        rightSidebarTabs = new TabPane();
        rightSidebarTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        rightSidebarTabs.setPrefWidth(340);
        rightSidebarTabs.setMinWidth(280);

        Tab notesTab = new Tab("Notes");
        notesTab.setContent(buildNotesPanel());

        Tab aiTab = new Tab("AI Coach");
        aiTab.setContent(buildAiCoachPanel());

        rightSidebarTabs.getTabs().addAll(notesTab, aiTab);
        return rightSidebarTabs;
    }

    // ── Notes panel (right side) ──────────────────────────────────────────

    private VBox buildNotesPanel() {
        notesPanel = new VBox(8);
        notesPanel.setPrefWidth(320);
        notesPanel.setMinWidth(260);
        notesPanel.setPadding(new Insets(12));

        Label hdr = new Label("📝  NOTES");
        hdr.setStyle("-fx-font-size:13px;-fx-font-weight:bold;");

        // Course picker
        List<String> courseCodes = state.courseSelections().stream()
                .filter(CourseSelection::isSelected)
                .map(CourseSelection::code)
                .toList();
        notesCourseBox = new ComboBox<>(
                FXCollections.observableArrayList(courseCodes));
        notesCourseBox.setMaxWidth(Double.MAX_VALUE);
        notesCourseBox.setPromptText("Select course...");
        if (!courseCodes.isEmpty()) {
        notesCourseBox.setValue(courseCodes.getFirst());
            notesCourse = courseCodes.getFirst();
            refreshNotesSidebar(notesCourse);
        }
        notesCourseBox.setOnAction(e -> {
            notesCourse = notesCourseBox.getValue();
            refreshNotesSidebar(notesCourse);
            refreshAiContextSummary();
        });

        // Note title
        noteTitleField = new TextField();
        noteTitleField.setPromptText("Note title…");
        noteTitleField.setStyle("-fx-font-size:13px;");

        // Note content
        noteContentArea = new TextArea();
        noteContentArea.setPromptText("Start writing your notes here…");
        noteContentArea.setWrapText(true);
        noteContentArea.setPrefRowCount(12);
        noteContentArea.setStyle("-fx-font-size:13px;-fx-font-family:'Courier New';");
        VBox.setVgrow(noteContentArea, Priority.ALWAYS);

        // Action row
        Button newBtn   = styledBtn("New", false);
        Button saveBtn  = styledBtn("Save", true);
        notePinButton   = styledBtn("Pin", false);
        Button shareBtn = styledBtn("Share", false);
        Button chatBtn  = styledBtn("-> Chat", false);
        chatBtn.setTooltip(new Tooltip("Send note summary to course chat"));

        newBtn.setOnAction(e  -> clearNoteEditor());
        saveBtn.setOnAction(e -> saveCurrentNote(notesCourseBox.getValue(), false));
        notePinButton.setOnAction(e -> toggleCurrentNotePin());
        shareBtn.setOnAction(e -> saveCurrentNote(notesCourseBox.getValue(), true));
        chatBtn.setOnAction(e -> sendNoteToChatChannel(notesCourseBox.getValue()));

        HBox row1 = new HBox(6, newBtn, saveBtn, notePinButton);
        HBox row2 = new HBox(6, shareBtn, chatBtn);

        Label shareHint = new Label("Share makes the note visible to classmates in this course.");
        shareHint.setStyle("-fx-font-size:10px;-fx-text-fill:#888;");
        shareHint.setWrapText(true);

        notesPanel.getChildren().addAll(
                hdr, notesCourseBox, noteTitleField, noteContentArea,
                row1, row2, shareHint);
        updateNotePinButton();
        return notesPanel;
    }

    private VBox buildAiCoachPanel() {
        aiCoachPanel = new VBox(10);
        aiCoachPanel.setPadding(new Insets(12));
        aiCoachPanel.setPrefWidth(320);
        aiCoachPanel.setMinWidth(260);

        Label title = new Label("AI STUDY COACH");
        title.setStyle("-fx-font-size:13px;-fx-font-weight:bold;");

        Label note = new Label(
                "Generate a summary, simple explanation, quiz, flashcards, or ask a custom question from your current note or active PDF page.");
        note.setStyle("-fx-font-size:11px;-fx-text-fill:#666;");
        note.setWrapText(true);

        aiBusyIndicator = new ProgressIndicator();
        aiBusyIndicator.setPrefSize(18, 18);
        aiBusyIndicator.setVisible(false);
        aiBusyIndicator.setManaged(false);

        aiStatusLabel = new Label(aiCoach.statusMessage());
        aiStatusLabel.setWrapText(true);
        aiStatusLabel.setStyle("-fx-font-size:11px;-fx-text-fill:#666;");

        HBox statusRow = new HBox(8, aiBusyIndicator, aiStatusLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        aiContextModeBox = new ComboBox<>(FXCollections.observableArrayList(AiContextMode.values()));
        aiContextModeBox.setMaxWidth(Double.MAX_VALUE);
        aiContextModeBox.setValue(AiContextMode.CURRENT_NOTE);
        aiContextModeBox.setOnAction(e -> refreshAiContextSummary());

        aiContextSummaryLabel = new Label();
        aiContextSummaryLabel.setWrapText(true);
        aiContextSummaryLabel.setStyle("-fx-font-size:11px;-fx-text-fill:#777;");

        aiPromptArea = new TextArea();
        aiPromptArea.setPromptText("Optional custom question, for example: Explain recursion from this page.");
        aiPromptArea.setWrapText(true);
        aiPromptArea.setPrefRowCount(3);
        aiPromptArea.setStyle("-fx-font-size:12px;");

        Button summarizeBtn = styledBtn("Summarize", true);
        Button explainBtn = styledBtn("Explain", false);
        Button quizBtn = styledBtn("MCQ Quiz", false);
        Button flashcardsBtn = styledBtn("Flashcards", false);
        Button askBtn = styledBtn("Ask AI", true);

        summarizeBtn.setOnAction(e -> runAiAction(AiStudyCoachAction.SUMMARIZE));
        explainBtn.setOnAction(e -> runAiAction(AiStudyCoachAction.EXPLAIN_SIMPLE));
        quizBtn.setOnAction(e -> runAiAction(AiStudyCoachAction.MCQ_QUIZ));
        flashcardsBtn.setOnAction(e -> runAiAction(AiStudyCoachAction.FLASHCARDS));
        askBtn.setOnAction(e -> runAiAction(AiStudyCoachAction.CUSTOM));

        aiRequestButtons.addAll(List.of(summarizeBtn, explainBtn, quizBtn, flashcardsBtn, askBtn));

        HBox actionRowOne = new HBox(6, summarizeBtn, explainBtn);
        HBox actionRowTwo = new HBox(6, quizBtn, flashcardsBtn, askBtn);

        Label outputLabel = new Label("AI Output");
        outputLabel.setStyle("-fx-font-size:12px;-fx-font-weight:bold;");

        aiOutputArea = new TextArea();
        aiOutputArea.setEditable(false);
        aiOutputArea.setWrapText(true);
        aiOutputArea.setPrefRowCount(14);
        aiOutputArea.setStyle("-fx-font-size:12px;-fx-font-family:'Consolas';");
        VBox.setVgrow(aiOutputArea, Priority.ALWAYS);

        Button useInNotesBtn = styledBtn("Use in Notes", false);
        Button saveAsNoteBtn = styledBtn("Save as Note", true);
        Button copyBtn = styledBtn("Copy", false);
        Button sendChatBtn = styledBtn("Send to Chat", false);
        Button regenerateBtn = styledBtn("Regenerate", false);

        useInNotesBtn.setOnAction(e -> loadAiResultIntoNoteEditor());
        saveAsNoteBtn.setOnAction(e -> saveAiResultAsNote());
        copyBtn.setOnAction(e -> copyAiResult());
        sendChatBtn.setOnAction(e -> sendAiResultToChat());
        regenerateBtn.setOnAction(e -> rerunLastAiRequest());

        aiResultButtons.addAll(List.of(useInNotesBtn, saveAsNoteBtn, copyBtn, sendChatBtn, regenerateBtn));

        HBox resultRowOne = new HBox(6, useInNotesBtn, saveAsNoteBtn);
        HBox resultRowTwo = new HBox(6, copyBtn, sendChatBtn, regenerateBtn);

        aiCoachPanel.getChildren().addAll(
                title,
                note,
                statusRow,
                aiContextModeBox,
                aiContextSummaryLabel,
                aiPromptArea,
                actionRowOne,
                actionRowTwo,
                outputLabel,
                aiOutputArea,
                resultRowOne,
                resultRowTwo);

        refreshAiContextSummary();
        updateAiButtonState();
        return aiCoachPanel;
    }

    // ── Sound bar (bottom) ───────────────────────────────────────────────

    private VBox buildSoundBar() {
        VBox footer = new VBox(6);
        footer.setStyle("-fx-background-color:#1a1a2e;");

        soundBar = new HBox(8);
        soundBar.setPadding(new Insets(8, 16, 0, 16));
        soundBar.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label("🔊");
        lbl.setStyle("-fx-font-size:16px;");

        for (SoundEngine.SoundTrack t : SoundEngine.TRACKS) {
            Button btn = new Button(t.emoji() + " " + t.label());
            btn.setStyle(soundBtnStyle(false));
            btn.setDisable(!sound.isAvailable(t.id()));
            if (btn.isDisable()) {
                btn.setTooltip(new Tooltip(
                    t.label() + ".mp3 not found in resources/com/example/sounds/"));
            }
            btn.setOnAction(e -> {
                sound.play(t.id());
                refreshSoundButtons();
            });
            soundBtns.put(t.id(), btn);
        }

        Button stopBtn = new Button("⏹ Stop");
        stopBtn.setStyle("-fx-background-color:#cc3333;-fx-text-fill:white;"
                + "-fx-font-size:12px;-fx-background-radius:6;-fx-cursor:hand;"
                + "-fx-padding:5 12 5 12;");
        stopBtn.setOnAction(e -> {
            sound.stop();
            refreshSoundButtons();
        });

        Slider volSlider = new Slider(0, 1, 0.6);
        volSlider.setPrefWidth(120);
        volSlider.setStyle("-fx-control-inner-background:#3a3a5e;");
        volSlider.valueProperty().addListener((obs, o, n) -> sound.setVolume(n.doubleValue()));
        Label volLbl = new Label("🔉 Vol");
        volLbl.setStyle("-fx-text-fill:#aaa;-fx-font-size:11px;");

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        soundBar.getChildren().add(lbl);
        soundBtns.values().forEach(b -> soundBar.getChildren().add(b));
        soundBar.getChildren().addAll(sp, volLbl, volSlider, stopBtn);
        footer.getChildren().addAll(soundBar, buildPomodoroBar());
        return footer;
    }

    private HBox buildPomodoroBar() {
        HBox bar = new HBox(18);
        bar.setPadding(new Insets(0, 16, 10, 16));
        bar.setAlignment(Pos.CENTER_LEFT);

        Label timerTitle = new Label("FOCUS TIMER");
        timerTitle.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:10px;-fx-font-weight:bold;");

        pomodoroPhaseLabel = new Label();
        pomodoroPhaseLabel.setStyle("-fx-font-size:11px;-fx-font-weight:bold;");

        pomodoroCountdownLabel = new Label();
        pomodoroCountdownLabel.setStyle("-fx-text-fill:white;-fx-font-size:24px;"
                + "-fx-font-weight:bold;-fx-font-family:'Consolas';");

        pomodoroSummaryLabel = new Label();
        pomodoroSummaryLabel.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:10px;");

        VBox infoBox = new VBox(2, timerTitle, pomodoroPhaseLabel, pomodoroCountdownLabel, pomodoroSummaryLabel);
        infoBox.setMinWidth(220);

        focusDurationBox = new ComboBox<>(FXCollections.observableArrayList(15, 25, 45, 50, 90));
        focusDurationBox.setValue(25);
        focusDurationBox.setPrefWidth(86);
        focusDurationBox.setStyle("-fx-font-size:11px;");

        breakDurationBox = new ComboBox<>(FXCollections.observableArrayList(5, 10, 15, 20));
        breakDurationBox.setValue(5);
        breakDurationBox.setPrefWidth(86);
        breakDurationBox.setStyle("-fx-font-size:11px;");

        focusDurationBox.setOnAction(e -> handlePomodoroPresetChange());
        breakDurationBox.setOnAction(e -> handlePomodoroPresetChange());

        Label focusLabel = new Label("Focus");
        focusLabel.setStyle("-fx-text-fill:#cbd5e1;-fx-font-size:11px;");
        Label breakLabel = new Label("Break");
        breakLabel.setStyle("-fx-text-fill:#cbd5e1;-fx-font-size:11px;");

        HBox presetRow = new HBox(6, focusLabel, focusDurationBox, breakLabel, breakDurationBox);
        presetRow.setAlignment(Pos.CENTER_LEFT);

        pomodoroStartPauseButton = styledBtn("Start Focus", true);
        Button resetButton = styledBtn("Reset", false);
        Button skipButton = styledBtn("Skip", false);

        pomodoroStartPauseButton.setOnAction(e -> togglePomodoro());
        resetButton.setOnAction(e -> resetPomodoro());
        skipButton.setOnAction(e -> skipPomodoroPhase());

        HBox buttonRow = new HBox(6, pomodoroStartPauseButton, resetButton, skipButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        VBox controlBox = new VBox(6, presetRow, buttonRow);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(infoBox, spacer, controlBox);

        pomodoroRemainingSeconds = durationForPhase(PomodoroPhase.FOCUS);
        updatePomodoroUi();
        return bar;
    }

    private void handlePomodoroPresetChange() {
        if (!isPomodoroRunning()) {
            pomodoroRemainingSeconds = durationForPhase(pomodoroPhase);
        }
        updatePomodoroUi();
    }

    private void togglePomodoro() {
        if (isPomodoroRunning()) {
            pausePomodoro();
            return;
        }
        startPomodoro();
    }

    private void startPomodoro() {
        if (pomodoroRemainingSeconds <= 0) {
            pomodoroRemainingSeconds = durationForPhase(pomodoroPhase);
        }
        ensurePomodoroTimeline();
        pomodoroTimeline.play();
        updatePomodoroUi();
    }

    private void pausePomodoro() {
        if (pomodoroTimeline != null) {
            pomodoroTimeline.pause();
        }
        updatePomodoroUi();
    }

    private void resetPomodoro() {
        disposePomodoroTimer();
        pomodoroPhase = PomodoroPhase.FOCUS;
        pomodoroRemainingSeconds = durationForPhase(PomodoroPhase.FOCUS);
        completedFocusSessions = 0;
        updatePomodoroUi();
    }

    private void skipPomodoroPhase() {
        boolean wasRunning = isPomodoroRunning();
        if (pomodoroTimeline != null) {
            pomodoroTimeline.pause();
        }
        pomodoroPhase = pomodoroPhase.next();
        pomodoroRemainingSeconds = durationForPhase(pomodoroPhase);
        if (wasRunning && pomodoroTimeline != null) {
            pomodoroTimeline.play();
        }
        updatePomodoroUi();
    }

    private void ensurePomodoroTimeline() {
        if (pomodoroTimeline != null) {
            return;
        }
        pomodoroTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> advancePomodoroTimer()));
        pomodoroTimeline.setCycleCount(Timeline.INDEFINITE);
    }

    private void advancePomodoroTimer() {
        if (pomodoroRemainingSeconds > 0) {
            pomodoroRemainingSeconds--;
        }

        if (pomodoroRemainingSeconds <= 0) {
            if (pomodoroPhase == PomodoroPhase.FOCUS) {
                completedFocusSessions++;
            }
            pomodoroPhase = pomodoroPhase.next();
            pomodoroRemainingSeconds = durationForPhase(pomodoroPhase);
            playPomodoroCue();
        }

        updatePomodoroUi();
    }

    private void disposePomodoroTimer() {
        if (pomodoroTimeline != null) {
            pomodoroTimeline.stop();
        }
    }

    private boolean isPomodoroRunning() {
        return pomodoroTimeline != null && pomodoroTimeline.getStatus() == Animation.Status.RUNNING;
    }

    private int durationForPhase(PomodoroPhase phase) {
        return switch (phase) {
            case FOCUS -> selectedMinutes(focusDurationBox, 25) * 60;
            case BREAK -> selectedMinutes(breakDurationBox, 5) * 60;
        };
    }

    private int selectedMinutes(ComboBox<Integer> box, int fallback) {
        Integer value = box == null ? null : box.getValue();
        return value == null ? fallback : Math.max(1, value);
    }

    private void updatePomodoroUi() {
        if (pomodoroPhaseLabel == null || pomodoroCountdownLabel == null || pomodoroSummaryLabel == null) {
            return;
        }

        boolean running = isPomodoroRunning();
        int phaseDuration = durationForPhase(pomodoroPhase);
        String accent = pomodoroPhase == PomodoroPhase.FOCUS ? "#c4b5fd" : "#86efac";
        String phaseText;
        if (running) {
            phaseText = pomodoroPhase.label() + " in progress";
        } else if (pomodoroRemainingSeconds < phaseDuration) {
            phaseText = pomodoroPhase.label() + " paused";
        } else {
            phaseText = pomodoroPhase.label() + " ready";
        }

        pomodoroPhaseLabel.setText(phaseText);
        pomodoroPhaseLabel.setStyle("-fx-text-fill:" + accent + ";-fx-font-size:11px;-fx-font-weight:bold;");
        pomodoroCountdownLabel.setText(formatPomodoroTime(pomodoroRemainingSeconds));
        pomodoroSummaryLabel.setText("Done: " + completedFocusSessions
                + " focus blocks  |  Focus " + selectedMinutes(focusDurationBox, 25)
                + "m  |  Break " + selectedMinutes(breakDurationBox, 5) + "m");

        if (pomodoroStartPauseButton != null) {
            String idleLabel = pomodoroRemainingSeconds < phaseDuration
                    ? "Resume"
                    : pomodoroPhase == PomodoroPhase.FOCUS ? "Start Focus" : "Start Break";
            pomodoroStartPauseButton.setText(running ? "Pause" : idleLabel);
        }
    }

    private void playPomodoroCue() {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Exception ignored) {
        }
    }

    private static String formatPomodoroTime(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int minutes = safeSeconds / 60;
        int seconds = safeSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // ── Open book tab ────────────────────────────────────────────────────

    private void openBookTab(DatabaseService.BookEntry entry) {
        // Check if already open
        for (Tab t : readerTabs.getTabs()) {
            if (String.valueOf(entry.id()).equals(t.getUserData())) {
                readerTabs.getSelectionModel().select(t);
                return;
            }
        }

        String lower = entry.sourcePath().toLowerCase();
        Tab tab = new Tab(truncate(entry.title(), 20));
        tab.setUserData(String.valueOf(entry.id()));
        ReaderContext readerContext = new ReaderContext(entry, ReaderKind.forEntry(entry));
        readerContexts.put(tab, readerContext);

        if (entry.sourceType().equals("URL")) {
            tab.setContent(buildWebTab(entry.sourcePath()));
        } else if (lower.endsWith(".pdf")) {
            tab.setContent(buildPdfTab(entry, tab, readerContext));
        } else if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) {
            tab.setContent(buildPptTab(entry.sourcePath()));
        } else {
            tab.setContent(buildExternalFileTab(entry.sourcePath()));
        }

        if (!lower.endsWith(".pdf")) {
            tab.setOnClosed(e -> {
                readerContexts.remove(tab);
                refreshAiContextSummary();
            });
        }

        readerTabs.getTabs().add(tab);
        readerTabs.getSelectionModel().select(tab);
        refreshAiContextSummary();
    }

    // ── PDF tab ──────────────────────────────────────────────────────────

    private BorderPane buildPdfTab(DatabaseService.BookEntry entry, Tab tab, ReaderContext readerContext) {
        BorderPane pane = new BorderPane();

        // State
        int[] pageState = {entry.lastPage()};
        int[] totalPages = {1};
        PDDocument[] docHolder = {null};

        // Image display
        ImageView pageView = new ImageView();
        pageView.setPreserveRatio(true);
        pageView.setFitWidth(700);

        ScrollPane scroll = new ScrollPane(pageView);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:transparent;");
        pane.setCenter(scroll);
        readerContext.setBookmarks(state.loadBookBookmarks(entry.id()));

        // Controls bar
        Label pageLabel = new Label("Page — / —");
        pageLabel.setStyle("-fx-font-size:13px;");

        Button prev  = styledBtn("◀ Prev", false);
        Button next  = styledBtn("Next ▶", true);
        Button zoomIn  = styledBtn("🔍+", false);
        Button zoomOut = styledBtn("🔍−", false);
        double[] zoom = {1.0};

        zoomIn.setOnAction(e  -> { zoom[0] = Math.min(3.0, zoom[0] + 0.2); renderPage(docHolder, pageState, pageView, zoom, pageLabel, totalPages); });
        zoomOut.setOnAction(e -> { zoom[0] = Math.max(0.4, zoom[0] - 0.2); renderPage(docHolder, pageState, pageView, zoom, pageLabel, totalPages); });

        prev.setOnAction(e -> {
            if (pageState[0] > 0) {
                pageState[0]--;
                readerContext.updatePageState(pageState[0], totalPages[0]);
                renderPage(docHolder, pageState, pageView, zoom, pageLabel, totalPages);
                state.updateBookProgress(entry.id(), pageState[0], theme.name());
                refreshAiContextSummary();
            }
        });
        next.setOnAction(e -> {
            if (pageState[0] < totalPages[0] - 1) {
                pageState[0]++;
                readerContext.updatePageState(pageState[0], totalPages[0]);
                renderPage(docHolder, pageState, pageView, zoom, pageLabel, totalPages);
                state.updateBookProgress(entry.id(), pageState[0], theme.name());
                refreshAiContextSummary();
            }
        });

        HBox controls = new HBox(10, prev, pageLabel, next, new Label("  "), zoomIn, zoomOut);
        controls.setPadding(new Insets(8, 14, 8, 14));
        controls.setAlignment(Pos.CENTER);
        pane.setBottom(controls);

        TextField pageJumpField = new TextField();
        pageJumpField.setPrefWidth(70);
        pageJumpField.setPromptText("Page");
        pageJumpField.setStyle("-fx-font-size:12px;");
        Button goBtn = styledBtn("Go", false);
        controls.getChildren().addAll(new Label("  "), new Label("Page"), pageJumpField, goBtn);

        VBox toolsPanel = new VBox(10);
        toolsPanel.setPrefWidth(250);
        toolsPanel.setPadding(new Insets(14));
        toolsPanel.setStyle("-fx-background-color:#f7f5ff;-fx-border-color:#ddd6fe;-fx-border-width:0 0 0 1;");

        Label toolsTitle = new Label("PDF Tools");
        toolsTitle.setStyle("-fx-font-size:13px;-fx-font-weight:bold;");
        Label jumpHintLabel = new Label("Jump to any page");
        jumpHintLabel.setWrapText(true);
        jumpHintLabel.setStyle("-fx-font-size:11px;-fx-text-fill:#666;");
        Button bookmarkCurrentBtn = styledBtn("Bookmark Current Page", true);
        ListView<DatabaseService.BookBookmark> bookmarkList = new ListView<>();
        bookmarkList.setPrefHeight(180);
        bookmarkList.setPlaceholder(new Label("No bookmarks yet."));
        Label recentLabel = new Label("Recently Visited");
        recentLabel.setStyle("-fx-font-size:12px;-fx-font-weight:bold;");
        FlowPane recentPagesPane = new FlowPane(6, 6);
        recentPagesPane.setPrefWrapLength(210);
        toolsPanel.getChildren().addAll(
                toolsTitle,
                jumpHintLabel,
                bookmarkCurrentBtn,
                bookmarkList,
                new Separator(),
                recentLabel,
                recentPagesPane);
        pane.setRight(toolsPanel);

        Runnable[] refreshPdfTools = new Runnable[1];
        IntConsumer[] goToPage = new IntConsumer[1];

        bookmarkList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(DatabaseService.BookBookmark item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label label = new Label("Page " + (item.pageIndex() + 1) + "  •  " + truncate(item.label(), 22));
                label.setStyle("-fx-font-size:11px;");
                Button remove = new Button("✕");
                remove.setStyle("-fx-font-size:9px;-fx-background-color:transparent;"
                        + "-fx-text-fill:#cc0000;-fx-cursor:hand;-fx-padding:0 2 0 2;");
                remove.setOnAction(e -> {
                    state.deleteBookBookmark(item.id());
                    readerContext.removeBookmark(item.id());
                    if (refreshPdfTools[0] != null) {
                        refreshPdfTools[0].run();
                    }
                });
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(6, label, spacer, remove);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });

        bookmarkList.setOnMouseClicked(e -> {
            if (e.getClickCount() < 2) return;
            DatabaseService.BookBookmark bookmark = bookmarkList.getSelectionModel().getSelectedItem();
            if (bookmark != null && goToPage[0] != null) {
                goToPage[0].accept(bookmark.pageIndex());
            }
        });

        goToPage[0] = requestedPage -> {
            if (requestedPage < 0 || requestedPage >= totalPages[0]) {
                new Alert(Alert.AlertType.WARNING,
                        "Choose a page between 1 and " + Math.max(1, totalPages[0]) + ".").show();
                if (refreshPdfTools[0] != null) {
                    refreshPdfTools[0].run();
                }
                return;
            }

            boolean changed = requestedPage != pageState[0];
            pageState[0] = requestedPage;
            readerContext.updatePageState(pageState[0], totalPages[0]);
            if (changed) {
                readerContext.recordRecentPage(pageState[0]);
                state.updateBookProgress(entry.id(), pageState[0], theme.name());
                refreshAiContextSummary();
            }
            if (docHolder[0] != null) {
                renderPage(docHolder, pageState, pageView, zoom, pageLabel, totalPages);
            }
            if (refreshPdfTools[0] != null) {
                refreshPdfTools[0].run();
            }
        };

        prev.setOnAction(e -> {
            if (pageState[0] > 0 && goToPage[0] != null) {
                goToPage[0].accept(pageState[0] - 1);
            }
        });
        next.setOnAction(e -> {
            if (pageState[0] < totalPages[0] - 1 && goToPage[0] != null) {
                goToPage[0].accept(pageState[0] + 1);
            }
        });

        Runnable jumpAction = () -> {
            String raw = pageJumpField.getText() == null ? "" : pageJumpField.getText().trim();
            if (raw.isBlank()) {
                return;
            }
            try {
                int requested = Integer.parseInt(raw) - 1;
                if (goToPage[0] != null) {
                    goToPage[0].accept(requested);
                }
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.WARNING, "Enter a valid page number.").show();
                if (refreshPdfTools[0] != null) {
                    refreshPdfTools[0].run();
                }
            }
        };

        pageJumpField.setOnAction(e -> jumpAction.run());
        goBtn.setOnAction(e -> jumpAction.run());

        bookmarkCurrentBtn.setOnAction(e -> {
            if (totalPages[0] <= 0) {
                new Alert(Alert.AlertType.WARNING, "Wait for the PDF to finish loading first.").show();
                return;
            }
            String defaultLabel = readerContext.bookmarkAt(pageState[0])
                    .map(DatabaseService.BookBookmark::label)
                    .orElse("Page " + (pageState[0] + 1));
            TextInputDialog dialog = new TextInputDialog(defaultLabel);
            dialog.setTitle("Bookmark Page");
            dialog.setHeaderText("Save a bookmark for page " + (pageState[0] + 1));
            dialog.setContentText("Label:");
            dialog.showAndWait().ifPresent(label -> {
                DatabaseService.BookBookmark saved = state.saveBookBookmark(entry.id(), pageState[0], label);
                if (saved != null) {
                    readerContext.upsertBookmark(saved);
                    if (refreshPdfTools[0] != null) {
                        refreshPdfTools[0].run();
                    }
                }
            });
        });

        refreshPdfTools[0] = () -> {
            pageJumpField.setText(String.valueOf(pageState[0] + 1));
            jumpHintLabel.setText("Jump to any page (1 - " + Math.max(1, totalPages[0]) + ")");
            bookmarkList.getItems().setAll(readerContext.bookmarks());
            recentPagesPane.getChildren().clear();
            for (int recentPage : readerContext.recentPages()) {
                Button recentButton = new Button("Pg " + (recentPage + 1));
                recentButton.setStyle(recentPage == pageState[0]
                        ? soundBtnStyle(true)
                        : soundBtnStyle(false));
                recentButton.setOnAction(e -> {
                    if (goToPage[0] != null) {
                        goToPage[0].accept(recentPage);
                    }
                });
                recentPagesPane.getChildren().add(recentButton);
            }
        };
        refreshPdfTools[0].run();

        // Load PDF on background thread
        Thread loader = new Thread(() -> {
            try {
                PDDocument doc = Loader.loadPDF(new File(entry.sourcePath()));
                docHolder[0] = doc;
                totalPages[0] = doc.getNumberOfPages();
                if (pageState[0] >= totalPages[0]) pageState[0] = 0;
                readerContext.updatePageState(pageState[0], totalPages[0]);
                readerContext.recordRecentPage(pageState[0]);
                Platform.runLater(() -> {
                    renderPage(docHolder, pageState, pageView, zoom, pageLabel, totalPages);
                    if (refreshPdfTools[0] != null) {
                        refreshPdfTools[0].run();
                    }
                    refreshAiContextSummary();
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                    pageLabel.setText("Error loading PDF: " + ex.getMessage()));
            }
        }, "biss-pdf-loader");
        loader.setDaemon(true);
        loader.start();

        // Close cleanup
        tab.setOnClosed(e -> {
            try { if (docHolder[0] != null) docHolder[0].close(); } catch (Exception ignored) {}
            readerContexts.remove(tab);
            refreshAiContextSummary();
        });

        return pane;
    }

    private void renderPage(PDDocument[] docHolder, int[] page,
                            ImageView view, double[] zoom,
                            Label pageLabel, int[] total) {
        PDDocument doc = docHolder[0];
        if (doc == null) return;
        Thread t = new Thread(() -> {
            try {
                PDFRenderer renderer = new PDFRenderer(doc);
                float dpi = (float)(96 * zoom[0]);
                BufferedImage img = renderer.renderImageWithDPI(page[0], dpi, ImageType.RGB);
                WritableImage fx  = SwingFXUtils.toFXImage(img, null);
                Platform.runLater(() -> {
                    view.setImage(fx);
                    view.setFitWidth(fx.getWidth());
                    pageLabel.setText("Page " + (page[0] + 1) + " / " + total[0]);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> pageLabel.setText("Render error: " + ex.getMessage()));
            }
        }, "biss-pdf-render");
        t.setDaemon(true);
        t.start();
    }

    // ── PPT tab ──────────────────────────────────────────────────────────

    private VBox buildPptTab(String path) {
        VBox box = new VBox(16);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        Label lbl = new Label("📊  PowerPoint file: " + new File(path).getName());
        lbl.setStyle("-fx-font-size:15px;-fx-font-weight:bold;");
        Label sub = new Label("Click below to open in your system's PowerPoint or LibreOffice viewer.");
        sub.setStyle("-fx-font-size:13px;-fx-text-fill:#888;");
        Button open = styledBtn("Open in System Viewer", true);
        open.setOnAction(e -> {
            try { java.awt.Desktop.getDesktop().open(new File(path)); }
            catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Cannot open: " + ex.getMessage()).show();
            }
        });
        box.getChildren().addAll(lbl, sub, open);
        return box;
    }

    // ── Web/URL tab ──────────────────────────────────────────────────────

    private VBox buildExternalFileTab(String path) {
        VBox box = new VBox(16);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        Label lbl = new Label("Open in system viewer: " + new File(path).getName());
        lbl.setStyle("-fx-font-size:15px;-fx-font-weight:bold;");
        Label sub = new Label("This file type is available from the teacher upload but is not previewed inside BISS.");
        sub.setStyle("-fx-font-size:13px;-fx-text-fill:#888;");
        sub.setWrapText(true);
        Button open = styledBtn("Open File", true);
        open.setOnAction(e -> {
            try { java.awt.Desktop.getDesktop().open(new File(path)); }
            catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Cannot open: " + ex.getMessage()).show();
            }
        });
        box.getChildren().addAll(lbl, sub, open);
        return box;
    }

    private WebView buildWebTab(String url) {
        WebView wv = new WebView();
        wv.getEngine().load(url);
        return wv;
    }

    // ── File chooser ─────────────────────────────────────────────────────

    private void openFileChooser() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open Book, PDF or Slides");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Supported files",
                        "*.pdf", "*.ppt", "*.pptx"),
                new FileChooser.ExtensionFilter("PDF", "*.pdf"),
                new FileChooser.ExtensionFilter("Slides", "*.ppt", "*.pptx"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );
        File f = fc.showOpenDialog(root.getScene().getWindow());
        if (f == null) return;

        String type = f.getName().toLowerCase().endsWith(".pdf") ? "FILE" : "PPT";
        long id = state.addBook(f.getName(), type, f.getAbsolutePath(), null);
        if (id < 0) return;

        DatabaseService.BookEntry entry = new DatabaseService.BookEntry(
                id, f.getName(), type, f.getAbsolutePath(), null, 0, theme.name());
        addToBookListSidebar(entry);
        openBookTab(entry);
    }

    // ── URL dialog ───────────────────────────────────────────────────────

    private void openUrlDialog() {
        TextInputDialog dlg = new TextInputDialog("https://");
        dlg.setTitle("Open Online Book / Resource");
        dlg.setHeaderText("Enter the URL of an online PDF, textbook, or study resource");
        dlg.setContentText("URL:");
        dlg.showAndWait().ifPresent(url -> {
            if (url.isBlank()) return;
            String title = url.length() > 40 ? url.substring(0, 37) + "…" : url;
            long id = state.addBook(title, "URL", url, null);
            DatabaseService.BookEntry entry = new DatabaseService.BookEntry(
                    id, title, "URL", url, null, 0, theme.name());
            addToBookListSidebar(entry);
            openBookTab(entry);
        });
    }

    // ── Teacher slides dialog ─────────────────────────────────────────────

    private void openTeacherResourcesDialog() {
        List<UploadedResource> resources = state.teacherResources();
        Stage dlg = new Stage();
        dlg.initOwner(root.getScene().getWindow());
        dlg.setTitle("Teacher Uploaded Slides & Resources");

        VBox box = new VBox(10);
        box.setPadding(new Insets(18));

        Label hdr = new Label("Available teacher resources — double-click to open");
        hdr.setStyle("-fx-font-size:13px;-fx-font-weight:bold;");

        ListView<UploadedResource> lv = new ListView<>(
                FXCollections.observableArrayList(resources));
        lv.setPrefHeight(300);
        lv.setCellFactory(l -> new ListCell<>() {
            @Override protected void updateItem(UploadedResource r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) { setText(null); return; }
                setText(r.courseProperty().get() + "  ·  "
                        + r.titleProperty().get() + "  [" + r.typeProperty().get() + "]"
                        + "  ← " + r.uploadedAtProperty().get());
            }
        });
        lv.setOnMouseClicked(e -> {
            if (e.getClickCount() < 2) return;
            UploadedResource r = lv.getSelectionModel().getSelectedItem();
            if (r == null) return;
            String path = resolveUploadedResourcePath(r);
            if (path == null) {
                new Alert(Alert.AlertType.WARNING,
                        "This teacher resource file is not available on disk right now.").show();
                return;
            }
            String type = inferLocalBookType(path);
            long id = state.addBook(r.titleProperty().get(), type, path,
                    r.courseProperty().get());
            if (id < 0) {
                new Alert(Alert.AlertType.ERROR, "Could not add the teacher resource to your library.").show();
                return;
            }
            DatabaseService.BookEntry entry = new DatabaseService.BookEntry(
                    id, r.titleProperty().get(), type, path,
                    r.courseProperty().get(), 0, theme.name());
            addToBookListSidebar(entry);
            openBookTab(entry);
            dlg.close();
        });

        if (resources.isEmpty()) {
            lv.setPlaceholder(new Label("No teacher resources shared yet."));
        }

        Button close = styledBtn("Close", false);
        close.setOnAction(e -> dlg.close());
        box.getChildren().addAll(hdr, lv, close);

        dlg.setScene(new Scene(box, 560, 380));
        dlg.show();
    }

    private void refreshAiContextSummary() {
        if (aiContextSummaryLabel == null || aiContextModeBox == null) {
            return;
        }

        try {
            AiContextDescriptor descriptor = describeAiContext(aiContextModeBox.getValue());
            aiContextSummaryLabel.setText(descriptor.summary());
        } catch (Exception ex) {
            aiContextSummaryLabel.setText(ex.getMessage());
        }
    }

    private AiContextDescriptor describeAiContext(AiContextMode mode) {
        if (mode == null || mode == AiContextMode.CURRENT_NOTE) {
            String title = noteTitleField == null ? "" : noteTitleField.getText().trim();
            String content = noteContentArea == null ? "" : noteContentArea.getText().trim();
            if (content.isBlank()) {
                return new AiContextDescriptor("Current note editor is empty.", null);
            }
            String summary = title.isBlank()
                    ? "Using the current note editor as context."
                    : "Using note: " + title;
            return new AiContextDescriptor(summary, resolveCourseForNotes(notesCourse));
        }

        ReaderContext context = activeReaderContext();
        if (context == null) {
            return new AiContextDescriptor("Open a PDF tab to use reader context.", null);
        }
        if (!context.supportsAiExtraction()) {
            return new AiContextDescriptor("The active reader tab is not a PDF.", null);
        }

        int start = Math.max(0, context.currentPage() - mode.pageRadius());
        int end = Math.max(start, context.currentPage() + mode.pageRadius());
        String pageText = start == end
                ? "page " + (start + 1)
                : "pages " + (start + 1) + "-" + (end + 1);
        String course = resolveCourseForNotes(context.entry().courseCode());
        String summary = "Using " + context.entry().title() + " (" + pageText + ")"
                + (course.isBlank() ? "" : " for " + course);
        return new AiContextDescriptor(summary, course);
    }

    private void runAiAction(AiStudyCoachAction action) {
        AiStudyCoachRequest request;
        try {
            request = buildAiRequest(action);
        } catch (Exception ex) {
            if (aiStatusLabel != null) {
                aiStatusLabel.setText(ex.getMessage());
            }
            return;
        }

        executeAiRequest(request, true);
    }

    private void rerunLastAiRequest() {
        if (lastAiRequest == null) {
            if (aiStatusLabel != null) {
                aiStatusLabel.setText("Run an AI action first.");
            }
            return;
        }
        executeAiRequest(lastAiRequest, false);
    }

    private void executeAiRequest(AiStudyCoachRequest request, boolean rememberRequest) {
        if (!aiCoach.isConfigured()) {
            if (aiStatusLabel != null) {
                aiStatusLabel.setText(aiCoach.statusMessage());
            }
            return;
        }

        if (rememberRequest) {
            lastAiRequest = request;
        }

        setAiBusy(true, request.action().label() + " in progress...");
        Task<AiStudyCoachResult> task = new Task<>() {
            @Override
            protected AiStudyCoachResult call() {
                return aiCoach.generate(request);
            }
        };

        task.setOnSucceeded(event -> {
            lastAiResult = task.getValue();
            aiOutputArea.setText(lastAiResult.content());
            setAiBusy(false, request.action().label() + " complete.");
        });
        task.setOnFailed(event -> {
            Throwable failure = task.getException();
            String message = failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
                    ? "AI request failed."
                    : failure.getMessage();
            setAiBusy(false, message);
        });

        Thread worker = new Thread(task, "biss-ai-coach");
        worker.setDaemon(true);
        worker.start();
    }

    private AiStudyCoachRequest buildAiRequest(AiStudyCoachAction action) throws Exception {
        AiContextMode mode = aiContextModeBox == null ? AiContextMode.CURRENT_NOTE : aiContextModeBox.getValue();
        AiContextPayload context = resolveAiContext(mode);
        String customPrompt = aiPromptArea == null ? "" : aiPromptArea.getText().trim();

        if (action == AiStudyCoachAction.CUSTOM && customPrompt.isBlank()) {
            throw new IllegalArgumentException("Enter a custom question first.");
        }

        return new AiStudyCoachRequest(
                action,
                context.label(),
                context.text(),
                context.courseCode(),
                customPrompt);
    }

    private AiContextPayload resolveAiContext(AiContextMode mode) throws Exception {
        if (mode == null || mode == AiContextMode.CURRENT_NOTE) {
            return buildNoteContext();
        }
        return buildPdfContext(mode);
    }

    private AiContextPayload buildNoteContext() {
        String title = noteTitleField == null ? "" : noteTitleField.getText().trim();
        String content = noteContentArea == null ? "" : noteContentArea.getText().trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("Write or load a note first.");
        }

        String label = title.isBlank() ? "Current note editor" : "Note: " + title;
        String text = title.isBlank()
                ? content
                : "Note title: " + title + System.lineSeparator() + System.lineSeparator() + content;
        return new AiContextPayload(label, text, resolveCourseForNotes(notesCourse));
    }

    private AiContextPayload buildPdfContext(AiContextMode mode) throws Exception {
        ReaderContext context = activeReaderContext();
        if (context == null) {
            throw new IllegalArgumentException("Open a PDF in Study Space first.");
        }
        if (!context.supportsAiExtraction()) {
            throw new IllegalArgumentException("The active reader tab is not a PDF.");
        }

        Path pdfPath = Path.of(context.entry().sourcePath());
        if (!Files.exists(pdfPath)) {
            throw new IllegalStateException("The active PDF file is not available on disk.");
        }

        int start = Math.max(0, context.currentPage() - mode.pageRadius());
        int end = Math.max(start, Math.min(context.totalPages() - 1, context.currentPage() + mode.pageRadius()));
        String text = PdfStudyContextExtractor.extractPageRange(pdfPath, start, end, 10_000);
        if (text.isBlank()) {
            throw new IllegalStateException("No readable text was found on the selected PDF page.");
        }

        String label = start == end
                ? context.entry().title() + " - Page " + (start + 1)
                : context.entry().title() + " - Pages " + (start + 1) + "-" + (end + 1);
        return new AiContextPayload(label, text, resolveCourseForNotes(context.entry().courseCode()));
    }

    private ReaderContext activeReaderContext() {
        if (readerTabs == null) {
            return null;
        }
        Tab selected = readerTabs.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return null;
        }
        return readerContexts.get(selected);
    }

    private void updateAiButtonState() {
        boolean allowRequests = aiCoach.isConfigured() && !aiBusy;
        aiRequestButtons.forEach(button -> button.setDisable(!allowRequests));

        boolean hasResult = lastAiResult != null && !lastAiResult.content().isBlank();
        boolean enableResultActions = hasResult && !aiBusy;
        aiResultButtons.forEach(button -> button.setDisable(!enableResultActions));
    }

    private void setAiBusy(boolean busy, String status) {
        aiBusy = busy;
        if (aiBusyIndicator != null) {
            aiBusyIndicator.setVisible(busy);
            aiBusyIndicator.setManaged(busy);
        }
        if (aiStatusLabel != null && status != null && !status.isBlank()) {
            aiStatusLabel.setText(status);
        }
        updateAiButtonState();
    }

    private void loadAiResultIntoNoteEditor() {
        if (lastAiResult == null) {
            return;
        }
        selectNotesCourse(lastAiResult.courseCode());
        editingNote = null;
        noteTitleField.setText(lastAiResult.title());
        noteContentArea.setText(lastAiResult.content());
        if (rightSidebarTabs != null) {
            rightSidebarTabs.getSelectionModel().select(0);
        }
        if (aiStatusLabel != null) {
            aiStatusLabel.setText("AI output moved into the note editor.");
        }
    }

    private void saveAiResultAsNote() {
        if (lastAiResult == null) {
            return;
        }
        String course = resolveCourseForNotes(lastAiResult.courseCode());
        if (course.isBlank()) {
            if (aiStatusLabel != null) {
                aiStatusLabel.setText("Select a course in Notes before saving AI output.");
            }
            return;
        }

        editingNote = null;
        StudyNote saved = persistNote(course, lastAiResult.title(), lastAiResult.content(), false);
        selectNotesCourse(course);
        loadNoteIntoEditor(saved);
        if (aiStatusLabel != null) {
            aiStatusLabel.setText("AI output saved as a note for " + course + ".");
        }
    }

    private void copyAiResult() {
        if (lastAiResult == null) {
            return;
        }
        copyToClipboard(lastAiResult.content());
        if (aiStatusLabel != null) {
            aiStatusLabel.setText("AI output copied to clipboard.");
        }
    }

    private void sendAiResultToChat() {
        if (lastAiResult == null) {
            return;
        }

        String course = resolveCourseForNotes(lastAiResult.courseCode());
        String message = lastAiResult.chatMessage();
        if (courseChatSender != null && notesCourseBox != null && notesCourseBox.getItems().contains(course)) {
            courseChatSender.accept(course, message);
            if (aiStatusLabel != null) {
                aiStatusLabel.setText("AI output sent to course chat: " + course + ".");
            }
            return;
        }

        copyToClipboard(message);
        if (aiStatusLabel != null) {
            aiStatusLabel.setText("AI output copied for chat. Open the course chat and paste it.");
        }
    }

    private void copyToClipboard(String text) {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent clipboardContent = new javafx.scene.input.ClipboardContent();
        clipboardContent.putString(text == null ? "" : text);
        clipboard.setContent(clipboardContent);
    }

    private void selectNotesCourse(String preferredCourse) {
        String course = resolveCourseForNotes(preferredCourse);
        if (course.isBlank() || notesCourseBox == null) {
            return;
        }
        notesCourseBox.setValue(course);
        notesCourse = course;
        refreshNotesSidebar(course);
    }

    private String resolveCourseForNotes(String preferredCourse) {
        String preferred = preferredCourse == null ? "" : preferredCourse.trim();
        if (notesCourseBox != null) {
            if (!preferred.isBlank() && notesCourseBox.getItems().contains(preferred)) {
                return preferred;
            }
            if (notesCourseBox.getValue() != null && !notesCourseBox.getValue().isBlank()) {
                return notesCourseBox.getValue();
            }
        }
        return preferred;
    }

    // ── Notes helpers ─────────────────────────────────────────────────────

    private void refreshNotesSidebar(String course) {
        if (course == null) return;
        loadedMyNotes = state.loadMyNotes(course);
        loadedSharedNotes = state.loadSharedNotes(course);
        applyNotesFilter();
    }

    private void clearNoteEditor() {
        editingNote = null;
        draftNotePinned = false;
        noteTitleField.clear();
        noteContentArea.clear();
        updateNotePinButton();
    }

    private void loadNoteIntoEditor(StudyNote n) {
        editingNote = n;
        draftNotePinned = n.isPinned();
        noteTitleField.setText(n.title());
        noteContentArea.setText(n.content());
        updateNotePinButton();
    }

    private void applyNotesFilter() {
        if (myNotesList == null || sharedNotesList == null) {
            return;
        }

        NoteFilterMode mode = notesFilterBox == null || notesFilterBox.getValue() == null
                ? NoteFilterMode.ALL
                : notesFilterBox.getValue();
        String query = notesSearchField == null ? "" : notesSearchField.getText();

        boolean showMy = mode != NoteFilterMode.SHARED_ONLY;
        boolean showShared = mode != NoteFilterMode.MY_ONLY;

        List<StudyNote> filteredMy = loadedMyNotes.stream()
                .filter(note -> noteMatchesSearch(note, query))
                .filter(note -> mode != NoteFilterMode.PINNED_ONLY || note.isPinned())
                .toList();
        List<StudyNote> filteredShared = loadedSharedNotes.stream()
                .filter(note -> noteMatchesSearch(note, query))
                .filter(note -> mode != NoteFilterMode.PINNED_ONLY || note.isPinned())
                .toList();

        myNotesList.getItems().setAll(filteredMy);
        sharedNotesList.getItems().setAll(filteredShared);

        setNodeVisible(myNotesHeader, showMy);
        setNodeVisible(myNotesList, showMy);
        setNodeVisible(sharedNotesHeader, showShared);
        setNodeVisible(sharedNotesList, showShared);
    }

    private boolean noteMatchesSearch(StudyNote note, String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return true;
        }
        String query = rawQuery.toLowerCase(Locale.ROOT).trim();
        return note.title().toLowerCase(Locale.ROOT).contains(query)
                || note.content().toLowerCase(Locale.ROOT).contains(query)
                || note.ownerRoll().toLowerCase(Locale.ROOT).contains(query);
    }

    private void setNodeVisible(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setManaged(visible);
        node.setVisible(visible);
    }

    private void toggleCurrentNotePin() {
        if (editingNote == null) {
            draftNotePinned = !draftNotePinned;
            updateNotePinButton();
            return;
        }
        toggleNotePin(editingNote);
    }

    private void toggleNotePin(StudyNote note) {
        if (note == null || note.id() <= 0) {
            draftNotePinned = !draftNotePinned;
            updateNotePinButton();
            return;
        }

        boolean pinned = !note.isPinned();
        state.setNotePinned(note.id(), pinned);
        if (editingNote != null && editingNote.id() == note.id()) {
            editingNote = copyNotePinned(editingNote, pinned);
            draftNotePinned = editingNote.isPinned();
        }
        selectNotesCourse(note.courseCode());
        updateNotePinButton();
    }

    private void updateNotePinButton() {
        if (notePinButton == null) {
            return;
        }

        boolean pinned = editingNote != null ? editingNote.isPinned() : draftNotePinned;
        notePinButton.setText(pinned ? "Unpin" : "Pin");
        notePinButton.setStyle(pinned ? BTN_STYLE : GHOST_STYLE);
        notePinButton.setTooltip(new Tooltip(
                editingNote == null
                        ? "Mark this draft to be pinned when you save it."
                        : "Pinned notes stay at the top of your note lists."));
    }

    private StudyNote copyNotePinned(StudyNote note, boolean pinned) {
        return new StudyNote(
                note.id(),
                note.ownerRoll(),
                note.courseCode(),
                note.title(),
                note.content(),
                note.isPublic(),
                pinned,
                note.createdAt(),
                note.updatedAt());
    }

    private void saveCurrentNote(String course, boolean makePublic) {
        if (course == null || course.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Select a course first.").show();
            return;
        }
        String title   = noteTitleField.getText().trim();
        String content = noteContentArea.getText().trim();
        if (title.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Give your note a title.").show();
            return;
        }

        persistNote(course, title, content, makePublic);

        new Alert(Alert.AlertType.INFORMATION,
                makePublic ? "Note saved and shared with " + course + " classmates!"
                           : "Note saved.").show();
    }

    private StudyNote persistNote(String course, String title, String content, boolean makePublic) {
        long now = System.currentTimeMillis();
        long id = editingNote != null ? editingNote.id() : 0;
        boolean pub = makePublic || (editingNote != null && editingNote.isPublic());
        boolean pinned = editingNote != null ? editingNote.isPinned() : draftNotePinned;

        StudyNote note = new StudyNote(
                id,
                state.profile().roll(),
                course,
                title,
                content,
                pub,
                pinned,
                now,
                now);
        StudyNote saved = state.saveNote(note);
        editingNote = saved;
        draftNotePinned = saved.isPinned();
        refreshNotesSidebar(course);
        updateNotePinButton();
        return saved;
    }

    private void sendNoteToChatChannel(String course) {
        if (course == null || noteContentArea.getText().isBlank()) return;
        String summary = "Note: " + noteTitleField.getText()
                + "\n" + noteContentArea.getText().substring(
                        0, Math.min(200, noteContentArea.getText().length()));
        if (courseChatSender != null && notesCourseBox != null && notesCourseBox.getItems().contains(course)) {
            courseChatSender.accept(course, summary);
            new Alert(Alert.AlertType.INFORMATION,
                    "Note summary sent to the course chat for " + course + ".").show();
            return;
        }
        copyToClipboard(summary);
        new Alert(Alert.AlertType.INFORMATION,
                "Note summary copied to clipboard.\nPaste it in the Chat window for " + course).show();
    }

    // ── Bookshelf helpers ─────────────────────────────────────────────────

    private void loadBookshelfFromDb() {
        if (bookList == null) return;
        bookList.getItems().setAll(state.loadBookshelf());
    }

    private void addToBookListSidebar(DatabaseService.BookEntry entry) {
        if (bookList == null) return;
        bookList.getItems().removeIf(e -> e.id() == entry.id());
        bookList.getItems().addFirst(entry);
    }

    // ── Theme ─────────────────────────────────────────────────────────────

    private String inferLocalBookType(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".ppt") || lower.endsWith(".pptx") ? "PPT" : "FILE";
    }

    private String resolveUploadedResourcePath(UploadedResource resource) {
        List<Path> candidates = new ArrayList<>();
        if (resource.sourcePath() != null && !resource.sourcePath().isBlank()) {
            candidates.add(Path.of(resource.sourcePath()));
        }

        String fileName = resource.fileNameProperty().get();
        if (fileName != null && !fileName.isBlank()) {
            candidates.add(Path.of(System.getProperty("user.dir"), "data", "uploads", fileName));
            candidates.add(Path.of(System.getProperty("user.dir"), "uploads", fileName));
            candidates.add(Path.of(fileName));
        }

        return candidates.stream()
                .map(Path::toAbsolutePath)
                .filter(Files::exists)
                .map(Path::toString)
                .findFirst()
                .orElse(null);
    }

    private void applyTheme(Theme t) {
        theme = t;
        String bg      = t.bg;
        String fg      = t.fg;
        String sideBar = t.sidebar;

        root.setStyle("-fx-background-color:" + bg + ";");
        if (sidebarBox != null)
            sidebarBox.setStyle("-fx-background-color:" + sideBar + ";");
        if (notesPanel != null)
            notesPanel.setStyle("-fx-background-color:" + sideBar
                    + ";-fx-border-color:#d0d0d0;-fx-border-width:0 0 0 1;");
        if (aiCoachPanel != null)
            aiCoachPanel.setStyle("-fx-background-color:" + sideBar
                    + ";-fx-border-color:#d0d0d0;-fx-border-width:0 0 0 1;");
        if (readerTabs != null)
            readerTabs.setStyle("-fx-background-color:" + bg + ";");

        // Propagate to note text area
        if (noteContentArea != null)
            noteContentArea.setStyle("-fx-control-inner-background:" + bg
                    + ";-fx-text-fill:" + fg + ";-fx-font-size:13px;"
                    + "-fx-font-family:'Courier New';");
        if (aiPromptArea != null)
            aiPromptArea.setStyle("-fx-control-inner-background:" + bg
                    + ";-fx-text-fill:" + fg + ";-fx-font-size:12px;");
        if (aiOutputArea != null)
            aiOutputArea.setStyle("-fx-control-inner-background:" + bg
                    + ";-fx-text-fill:" + fg + ";-fx-font-size:12px;"
                    + "-fx-font-family:'Consolas';");
    }

    // ── Sound buttons refresh ─────────────────────────────────────────────

    private void refreshSoundButtons() {
        String active = sound.activeId();
        soundBtns.forEach((id, btn) ->
                btn.setStyle(soundBtnStyle(id.equals(active))));
    }

    // ── ListCell for notes ────────────────────────────────────────────────

    private ListCell<StudyNote> noteCell() {
        return new ListCell<>() {
            @Override protected void updateItem(StudyNote n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) { setText(null); setGraphic(null); setContextMenu(null); return; }
                String icon = n.isPublic() ? "👥 " : "📝 ";
                if (n.isPinned()) {
                    icon = "[PIN] " + icon;
                }
                VBox vb = new VBox(2);
                Label t  = new Label(icon + truncate(n.title(), 22));
                t.setStyle("-fx-font-size:12px;-fx-font-weight:bold;");
                Label sub = new Label(n.preview());
                sub.setStyle("-fx-font-size:10px;-fx-text-fill:#888;");
                Label dt  = new Label(n.formattedDate());
                dt.setStyle("-fx-font-size:9px;-fx-text-fill:#aaa;");
                vb.getChildren().addAll(t, sub, dt);
                MenuItem pinItem = new MenuItem(n.isPinned() ? "Unpin note" : "Pin note");
                pinItem.setOnAction(e -> toggleNotePin(n));
                setContextMenu(new ContextMenu(pinItem));
                setGraphic(vb);
            }
        };
    }

    // ── Style utils ───────────────────────────────────────────────────────

    private enum NoteFilterMode {
        ALL("All Notes"),
        MY_ONLY("My Notes"),
        SHARED_ONLY("Shared Notes"),
        PINNED_ONLY("Pinned");

        private final String label;

        NoteFilterMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum PomodoroPhase {
        FOCUS("Focus"),
        BREAK("Break");

        private final String label;

        PomodoroPhase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public PomodoroPhase next() {
            return this == FOCUS ? BREAK : FOCUS;
        }
    }

    private enum AiContextMode {
        CURRENT_NOTE("Current Note", 0),
        ACTIVE_PDF_PAGE("Active PDF Page", 0),
        ACTIVE_PDF_SPREAD("Active PDF Page +/- 1", 1);

        private final String label;
        private final int pageRadius;

        AiContextMode(String label, int pageRadius) {
            this.label = label;
            this.pageRadius = pageRadius;
        }

        public int pageRadius() {
            return pageRadius;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record AiContextPayload(String label, String text, String courseCode) {
    }

    private record AiContextDescriptor(String summary, String courseCode) {
    }

    private enum ReaderKind {
        PDF,
        URL,
        PPT,
        FILE;

        private static ReaderKind forEntry(DatabaseService.BookEntry entry) {
            if (entry == null) {
                return FILE;
            }
            if ("URL".equalsIgnoreCase(entry.sourceType())) {
                return URL;
            }
            String lowerPath = entry.sourcePath() == null ? "" : entry.sourcePath().toLowerCase();
            if (lowerPath.endsWith(".pdf")) {
                return PDF;
            }
            if (lowerPath.endsWith(".ppt") || lowerPath.endsWith(".pptx")) {
                return PPT;
            }
            return FILE;
        }
    }

    private static final class ReaderContext {
        private final DatabaseService.BookEntry entry;
        private final ReaderKind kind;
        private final List<DatabaseService.BookBookmark> bookmarks = new ArrayList<>();
        private final Deque<Integer> recentPages = new ArrayDeque<>();
        private int currentPage;
        private int totalPages = 1;

        private ReaderContext(DatabaseService.BookEntry entry, ReaderKind kind) {
            this.entry = entry;
            this.kind = kind;
            this.currentPage = Math.max(0, entry == null ? 0 : entry.lastPage());
        }

        private DatabaseService.BookEntry entry() {
            return entry;
        }

        private int currentPage() {
            return currentPage;
        }

        private int totalPages() {
            return Math.max(1, totalPages);
        }

        private void updatePageState(int currentPage, int totalPages) {
            this.currentPage = Math.max(0, currentPage);
            this.totalPages = Math.max(1, totalPages);
        }

        private void setBookmarks(List<DatabaseService.BookBookmark> bookmarks) {
            this.bookmarks.clear();
            if (bookmarks != null) {
                this.bookmarks.addAll(bookmarks);
                this.bookmarks.sort(Comparator.comparingInt(DatabaseService.BookBookmark::pageIndex));
            }
        }

        private List<DatabaseService.BookBookmark> bookmarks() {
            return List.copyOf(bookmarks);
        }

        private Optional<DatabaseService.BookBookmark> bookmarkAt(int pageIndex) {
            return bookmarks.stream()
                    .filter(bookmark -> bookmark.pageIndex() == pageIndex)
                    .findFirst();
        }

        private void upsertBookmark(DatabaseService.BookBookmark bookmark) {
            if (bookmark == null) {
                return;
            }
            bookmarks.removeIf(existing -> existing.id() == bookmark.id()
                    || existing.pageIndex() == bookmark.pageIndex());
            bookmarks.add(bookmark);
            bookmarks.sort(Comparator.comparingInt(DatabaseService.BookBookmark::pageIndex));
        }

        private void removeBookmark(long bookmarkId) {
            bookmarks.removeIf(bookmark -> bookmark.id() == bookmarkId);
        }

        private void recordRecentPage(int pageIndex) {
            if (pageIndex < 0) {
                return;
            }
            recentPages.remove(pageIndex);
            recentPages.addFirst(pageIndex);
            while (recentPages.size() > 8) {
                recentPages.removeLast();
            }
        }

        private List<Integer> recentPages() {
            return List.copyOf(recentPages);
        }

        private boolean supportsAiExtraction() {
            return kind == ReaderKind.PDF;
        }
    }

    private Button styledBtn(String text, boolean primary) {
        Button b = new Button(text);
        b.setStyle(primary ? BTN_STYLE : GHOST_STYLE);
        return b;
    }

    private ToggleButton themeToggle(String text, Theme t, ToggleGroup tg) {
        ToggleButton tb = new ToggleButton(text);
        tb.setToggleGroup(tg);
        tb.setStyle("-fx-font-size:12px;-fx-cursor:hand;-fx-padding:5 12 5 12;"
                + "-fx-background-color:transparent;-fx-border-color:transparent;");
        tb.selectedProperty().addListener((obs, o, n) -> {
            if (n) {
                applyTheme(t);
                tb.setStyle("-fx-font-size:12px;-fx-cursor:hand;-fx-padding:5 12 5 12;"
                        + "-fx-background-color:#7c3aed;-fx-text-fill:white;"
                        + "-fx-border-color:transparent;");
            } else {
                tb.setStyle("-fx-font-size:12px;-fx-cursor:hand;-fx-padding:5 12 5 12;"
                        + "-fx-background-color:transparent;-fx-border-color:transparent;");
            }
        });
        return tb;
    }

    private static String soundBtnStyle(boolean active) {
        return active
                ? "-fx-background-color:#7c3aed;-fx-text-fill:white;-fx-font-size:11px;"
                  + "-fx-background-radius:20;-fx-cursor:hand;-fx-padding:5 12 5 12;"
                : "-fx-background-color:#2a2a4a;-fx-text-fill:#ccc;-fx-font-size:11px;"
                  + "-fx-background-radius:20;-fx-cursor:hand;-fx-padding:5 12 5 12;";
    }

    private static Label sidebarHeader(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:#888;"
                + "-fx-padding:10 10 4 10;");
        return l;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
