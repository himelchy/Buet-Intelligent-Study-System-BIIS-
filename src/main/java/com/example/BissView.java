package com.example;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

@SuppressWarnings({ "deprecation", "unchecked" })
public class BissView {
    private static final int CG_STAT_MIN_HUNDREDTHS = 220;
    private static final int CG_STAT_MAX_HUNDREDTHS = 400;
    private static final int CG_STAT_BUCKET_WIDTH_HUNDREDTHS = 20;
    private static final int STUDENT_ATTENDANCE_WARNING_THRESHOLD = 75;
    private static final int STUDENT_TAB_PLANNER_INDEX = 0;
    private static final int STUDENT_TAB_ATTENDANCE_INDEX = 5;
    private static final int STUDENT_TAB_NOTICES_INDEX = 7;
    private static final DateTimeFormatter DEADLINE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final DateTimeFormatter DEADLINE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter CALENDAR_HEADER_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM");
    private static final DateTimeFormatter CALENDAR_PERIOD_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter CALENDAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter CALENDAR_AGENDA_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM");

    private final AppState state;
    private final Scene scene;
    private final String stylesheetUrl;
    private final Map<String, Stage> courseStages = new HashMap<>();
    private final Map<String, Stage> teacherChatStages = new HashMap<>();
    private final Map<String, PrivateChatConversation> privateChatConversations = new HashMap<>();
    private final java.util.function.Consumer<ChatMessage> privateChatNotificationListener = this::onPrivateChatNotification;
    private final java.util.function.Consumer<ChatMessage> courseChatNotificationListener = this::onCourseChatNotification;
    private final Map<String, Integer> courseChatUnreadCounts = new HashMap<>();

    private Chatservice chatService;
    private Chatservice teacherChatService;
    private Stage chatStage;
    private Stage studyStage;
    private String courseChatActorKey;
    private String courseChatActorId;
    private String teacherChatActorKey;
    private String teacherChatActorId;
    private Button teacherDashboardChatButton;
    private Button studentCourseChatButton;
    private Button studentTeacherChatButton;
    private Runnable studentNotificationRefreshAction;

    public BissView(AppState state, Scene scene, String stylesheetUrl) {
        this.state = state;
        this.scene = scene;
        this.stylesheetUrl = stylesheetUrl;
    }

    public Parent buildLandingView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");

        HBox header = new HBox(16);
        header.getStyleClass().add("landing-header");
        header.setPadding(new Insets(24, 32, 16, 32));
        header.setAlignment(Pos.CENTER_LEFT);

        ImageView logo = buildLogoView(46);
        VBox brandText = new VBox(2);
        Label title = new Label("BUET Intelligent Study System");
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label("BISS - Intelligent study companion for BUET students");
        subtitle.getStyleClass().add("brand-subtitle");
        brandText.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button studentLoginButton = new Button("Student Login");
        studentLoginButton.getStyleClass().add("primary-button");
        studentLoginButton.setOnAction(event -> scene.setRoot(buildLoginView()));

        Button teacherLoginButton = new Button("Teacher Login");
        teacherLoginButton.getStyleClass().add("outline-button");
        teacherLoginButton.setOnAction(event -> scene.setRoot(buildTeacherLoginView()));

        header.getChildren().addAll(logo, brandText, spacer, studentLoginButton, teacherLoginButton);

        HBox hero = new HBox(28);
        hero.setPadding(new Insets(12, 32, 32, 32));
        hero.setAlignment(Pos.TOP_CENTER);

        VBox heroPanel = new VBox(16);
        heroPanel.getStyleClass().add("hero-panel");
        heroPanel.setPrefWidth(430);
        heroPanel.setMinWidth(430);

        Label heroBadge = new Label("Welcome to BISS");
        heroBadge.getStyleClass().add("hero-badge");

        Label heroTitle = new Label("There is always something exciting happening at BUET.");
        heroTitle.getStyleClass().add("hero-title");
        heroTitle.setWrapText(true);

        Label heroBody = new Label(
                "Let's make study from boring to something exciting !!!");
        heroBody.getStyleClass().add("hero-body");
        heroBody.setWrapText(true);

        HBox pillRow = new HBox(8);
        Label pillOne = new Label("Resources");
        pillOne.getStyleClass().addAll("hero-pill", "pill-gold");
        Label pillTwo = new Label("Enrollment");
        pillTwo.getStyleClass().addAll("hero-pill", "pill-teal");
        Label pillThree = new Label("Results");
        pillThree.getStyleClass().addAll("hero-pill", "pill-coral");
        pillRow.getChildren().addAll(pillOne, pillTwo, pillThree);
        pillRow.setAlignment(Pos.CENTER_LEFT);

        GridPane actionGrid = new GridPane();
        actionGrid.getStyleClass().add("hero-action-grid");
        actionGrid.setHgap(12);
        actionGrid.setVgap(12);

        ColumnConstraints actionColOne = new ColumnConstraints();
        actionColOne.setPercentWidth(50);
        actionColOne.setHgrow(Priority.ALWAYS);
        ColumnConstraints actionColTwo = new ColumnConstraints();
        actionColTwo.setPercentWidth(50);
        actionColTwo.setHgrow(Priority.ALWAYS);
        actionGrid.getColumnConstraints().addAll(actionColOne, actionColTwo);

        Button studentPortalButton = new Button("Student Portal");
        studentPortalButton.getStyleClass().addAll("landing-action-button", "landing-student-button");
        studentPortalButton.setOnAction(event -> scene.setRoot(buildLoginView()));
        Button teacherPortalButton = new Button("Teacher Portal");
        teacherPortalButton.getStyleClass().addAll("landing-action-button", "landing-teacher-button");
        teacherPortalButton.setOnAction(event -> scene.setRoot(buildTeacherLoginView()));

        Button studentRegistrationButton = new Button("Student Registration");
        studentRegistrationButton.getStyleClass().addAll("landing-action-button", "landing-student-register-button");
        studentRegistrationButton.setOnAction(event -> scene.setRoot(buildStudentRegistrationView()));
        Button teacherRegistrationButton = new Button("Teacher Registration");
        teacherRegistrationButton.getStyleClass().addAll("landing-action-button", "landing-teacher-register-button");
        teacherRegistrationButton.setOnAction(event -> scene.setRoot(buildTeacherRegistrationView()));

        configureLandingActionButton(studentPortalButton);
        configureLandingActionButton(teacherPortalButton);
        configureLandingActionButton(studentRegistrationButton);
        configureLandingActionButton(teacherRegistrationButton);

        actionGrid.add(studentPortalButton, 0, 0);
        actionGrid.add(teacherPortalButton, 1, 0);
        actionGrid.add(studentRegistrationButton, 0, 1);
        actionGrid.add(teacherRegistrationButton, 1, 1);

        GridPane.setHgrow(studentPortalButton, Priority.ALWAYS);
        GridPane.setHgrow(teacherPortalButton, Priority.ALWAYS);
        GridPane.setHgrow(studentRegistrationButton, Priority.ALWAYS);
        GridPane.setHgrow(teacherRegistrationButton, Priority.ALWAYS);

        heroPanel.getChildren().addAll(heroBadge, heroTitle, heroBody, pillRow, actionGrid);

        VBox campusGallery = buildCampusGallery();

        hero.getChildren().addAll(heroPanel, campusGallery);

        root.setTop(header);
        root.setCenter(hero);
        return root;
    }

    public Parent buildLoginView() {
        VBox loginCard = new VBox(16);
        loginCard.getStyleClass().add("card");
        loginCard.setPadding(new Insets(28));
        loginCard.setMaxWidth(420);

        Label formTitle = new Label("Student Login");
        formTitle.getStyleClass().add("card-title");
        Label formNote = new Label(
                "Use your BUET student credentials to access resources. Registrations are stored in the local database.");
        formNote.getStyleClass().add("muted");
        formNote.setWrapText(true);

        Label hint = new Label("Demo account: 2005107 / student123");
        hint.getStyleClass().add("muted");

        GridPane formGrid = new GridPane();
        formGrid.setHgap(12);
        formGrid.setVgap(12);

        Label idLabel = new Label("Student ID / Roll");
        idLabel.getStyleClass().add("field-label");
        TextField idField = new TextField();
        idField.setPromptText("e.g. 2105123");

        Label passLabel = new Label("Password");
        passLabel.getStyleClass().add("field-label");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Enter your password");

        formGrid.add(idLabel, 0, 0);
        formGrid.add(idField, 1, 0);
        formGrid.add(passLabel, 0, 1);
        formGrid.add(passField, 1, 1);
        GridPane.setHgrow(idField, Priority.ALWAYS);
        GridPane.setHgrow(passField, Priority.ALWAYS);

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Button loginButton = new Button("Login");
        loginButton.getStyleClass().add("primary-button");
        loginButton.setDefaultButton(true);

        loginButton.setOnAction(event -> {
            if (idField.getText().isBlank() || passField.getText().isBlank()) {
                message.setText("Please enter both student ID and password.");
                return;
            }

            StudentProfile profile = state.findStudentByRoll(idField.getText().trim());
            if (profile == null) {
                message.setText("Student account not found. Please register first.");
                return;
            }

            StudentProfile authenticated = state.authenticateStudent(idField.getText().trim(), passField.getText());
            if (authenticated == null) {
                message.setText("Incorrect student password.");
                return;
            }

            message.setText(" ");
            scene.setRoot(buildDashboardView());
        });

        Button registerButton = new Button("New Student Registration");
        registerButton.getStyleClass().add("outline-button");
        registerButton.setOnAction(event -> scene.setRoot(buildStudentRegistrationView()));

        Button teacherSwitchButton = new Button("Teacher Sign In");
        teacherSwitchButton.getStyleClass().add("outline-button");
        teacherSwitchButton.setOnAction(event -> scene.setRoot(buildTeacherLoginView()));

        HBox primaryActions = new HBox(10, loginButton, registerButton);
        primaryActions.setAlignment(Pos.CENTER_LEFT);

        VBox actionButtons = new VBox(10, primaryActions, teacherSwitchButton);
        VBox.setMargin(actionButtons, new Insets(6, 0, 0, 0));
        loginCard.getChildren().addAll(formTitle, formNote, hint, formGrid, message, actionButtons);

        return buildAuthView("BISS Student Portal", loginCard);
    }

    public Parent buildTeacherLoginView() {
        VBox loginCard = new VBox(16);
        loginCard.getStyleClass().add("card");
        loginCard.setPadding(new Insets(28));
        loginCard.setMaxWidth(460);

        Label formTitle = new Label("Teacher Login");
        formTitle.getStyleClass().add("card-title");
        Label formNote = new Label(
                "Sign in with teacher ID to manage routine, resources, and attendance. Registrations are stored in the local database.");
        formNote.getStyleClass().add("muted");
        formNote.setWrapText(true);

        Label hint = new Label("Seeded teacher IDs use password teacher123 (for example: TCH-1001, TCH-1002, TCH-1003).");
        hint.getStyleClass().add("muted");

        GridPane formGrid = new GridPane();
        formGrid.setHgap(12);
        formGrid.setVgap(12);

        Label idLabel = new Label("Teacher ID");
        idLabel.getStyleClass().add("field-label");
        TextField idField = new TextField();
        idField.setPromptText("e.g. TCH-1001");

        Label passLabel = new Label("Password");
        passLabel.getStyleClass().add("field-label");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Enter your password");

        formGrid.add(idLabel, 0, 0);
        formGrid.add(idField, 1, 0);
        formGrid.add(passLabel, 0, 1);
        formGrid.add(passField, 1, 1);
        GridPane.setHgrow(idField, Priority.ALWAYS);
        GridPane.setHgrow(passField, Priority.ALWAYS);

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Button loginButton = new Button("Login");
        loginButton.getStyleClass().add("primary-button");
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(event -> {
            if (idField.getText().isBlank() || passField.getText().isBlank()) {
                message.setText("Please enter teacher ID and password.");
                return;
            }

            TeacherProfile profile = state.findTeacherById(idField.getText().trim());
            if (profile == null) {
                message.setText("Teacher ID not found.");
                return;
            }

            TeacherWorkspace workspace = state.authenticateTeacher(idField.getText().trim(), passField.getText());
            if (workspace == null) {
                message.setText("Incorrect teacher password.");
                return;
            }

            message.setText(" ");
            scene.setRoot(buildTeacherDashboardView());
        });

        Button registerButton = new Button("New Teacher Registration");
        registerButton.getStyleClass().add("outline-button");
        registerButton.setOnAction(event -> scene.setRoot(buildTeacherRegistrationView()));

        Button studentSwitchButton = new Button("Student Sign In");
        studentSwitchButton.getStyleClass().add("outline-button");
        studentSwitchButton.setOnAction(event -> scene.setRoot(buildLoginView()));

        HBox primaryActions = new HBox(10, loginButton, registerButton);
        primaryActions.setAlignment(Pos.CENTER_LEFT);

        VBox actionButtons = new VBox(10, primaryActions, studentSwitchButton);
        VBox.setMargin(actionButtons, new Insets(6, 0, 0, 0));
        loginCard.getChildren().addAll(formTitle, formNote, hint, formGrid, message, actionButtons);

        return buildAuthView("BISS Teacher Portal", loginCard);
    }

    public Parent buildStudentRegistrationView() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(28));
        card.setMaxWidth(560);

        Label formTitle = new Label("New Student Registration");
        formTitle.getStyleClass().add("card-title");
        Label formNote = new Label(
                "Create a student account with the profile information that will appear on the dashboard profile card.");
        formNote.getStyleClass().add("muted");
        formNote.setWrapText(true);
        Label sessionNote = new Label(
                "This student account will be saved to the local database and reused for future logins.");
        sessionNote.getStyleClass().add("muted");
        sessionNote.setWrapText(true);

        GridPane formGrid = new GridPane();
        formGrid.setHgap(12);
        formGrid.setVgap(12);

        TextField nameField = new TextField();
        nameField.setPromptText("Full name");

        TextField rollField = new TextField();
        rollField.setPromptText("Student ID / roll");

        ComboBox<String> departmentBox = buildComboBox(List.of("CSE", "EEE", "CE", "ME", "ChE", "IPE", "Arch", "URP"),
                "CSE");
        ComboBox<String> termBox = buildComboBox(
                List.of("Level 1 Term 1", "Level 1 Term 2", "Level 2 Term 1", "Level 2 Term 2", "Level 3 Term 1",
                        "Level 3 Term 2", "Level 4 Term 1", "Level 4 Term 2"),
                "Level 1 Term 1");

        TextField cgpaField = new TextField();
        cgpaField.setPromptText("Current CGPA");

        TextField emailField = new TextField();
        emailField.setPromptText("student@buet.ac.bd");

        TextField phoneField = new TextField();
        phoneField.setPromptText("Mobile number");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Create password");

        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirm password");

        formGrid.add(buildFieldLabel("Full name"), 0, 0);
        formGrid.add(nameField, 1, 0);
        formGrid.add(buildFieldLabel("Roll"), 0, 1);
        formGrid.add(rollField, 1, 1);
        formGrid.add(buildFieldLabel("Department"), 0, 2);
        formGrid.add(departmentBox, 1, 2);
        formGrid.add(buildFieldLabel("Term"), 0, 3);
        formGrid.add(termBox, 1, 3);
        formGrid.add(buildFieldLabel("CGPA"), 0, 4);
        formGrid.add(cgpaField, 1, 4);
        formGrid.add(buildFieldLabel("Email"), 0, 5);
        formGrid.add(emailField, 1, 5);
        formGrid.add(buildFieldLabel("Phone"), 0, 6);
        formGrid.add(phoneField, 1, 6);
        formGrid.add(buildFieldLabel("Password"), 0, 7);
        formGrid.add(passField, 1, 7);
        formGrid.add(buildFieldLabel("Confirm"), 0, 8);
        formGrid.add(confirmField, 1, 8);

        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(rollField, Priority.ALWAYS);
        GridPane.setHgrow(departmentBox, Priority.ALWAYS);
        GridPane.setHgrow(termBox, Priority.ALWAYS);
        GridPane.setHgrow(cgpaField, Priority.ALWAYS);
        GridPane.setHgrow(emailField, Priority.ALWAYS);
        GridPane.setHgrow(phoneField, Priority.ALWAYS);
        GridPane.setHgrow(passField, Priority.ALWAYS);
        GridPane.setHgrow(confirmField, Priority.ALWAYS);

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Button registerButton = new Button("Create Student Account");
        registerButton.getStyleClass().add("primary-button");
        registerButton.setDefaultButton(true);
        registerButton.setOnAction(event -> {
            String name = clean(nameField.getText());
            String roll = clean(rollField.getText());
            String cgpa = clean(cgpaField.getText());
            String email = clean(emailField.getText());
            String phone = clean(phoneField.getText());
            String password = passField.getText();
            String confirmPassword = confirmField.getText();

            if (name.isBlank() || roll.isBlank() || cgpa.isBlank() || email.isBlank() || phone.isBlank()
                    || password.isBlank() || confirmPassword.isBlank()) {
                message.setText("Fill in all student registration fields.");
                return;
            }

            if (!isValidEmail(email)) {
                message.setText("Enter a valid student email address.");
                return;
            }

            if (!isValidCgpa(cgpa)) {
                message.setText("CGPA must be between 0.00 and 4.00.");
                return;
            }

            if (state.findStudentByRoll(roll) != null) {
                message.setText("A student account with this roll already exists.");
                return;
            }

            if (password.length() < 4) {
                message.setText("Student password must be at least 4 characters.");
                return;
            }

            if (!password.equals(confirmPassword)) {
                message.setText("Student passwords do not match.");
                return;
            }

            state.registerStudent(
                    new StudentProfile(
                            name,
                            roll,
                            departmentBox.getValue(),
                            termBox.getValue(),
                            cgpa,
                            email,
                            phone),
                    password);
            scene.setRoot(buildDashboardView());
        });

        Button backButton = new Button("Back to Student Login");
        backButton.getStyleClass().add("outline-button");
        backButton.setOnAction(event -> scene.setRoot(buildLoginView()));

        Button teacherSwitchButton = new Button("Teacher Registration");
        teacherSwitchButton.getStyleClass().add("outline-button");
        teacherSwitchButton.setOnAction(event -> scene.setRoot(buildTeacherRegistrationView()));

        HBox primaryActions = new HBox(10, registerButton, backButton);
        primaryActions.setAlignment(Pos.CENTER_LEFT);

        VBox actionButtons = new VBox(10, primaryActions, teacherSwitchButton);
        VBox.setMargin(actionButtons, new Insets(6, 0, 0, 0));

        card.getChildren().addAll(formTitle, formNote, sessionNote, formGrid, message, actionButtons);
        return buildAuthView("BISS Student Registration", card);
    }

    public Parent buildTeacherRegistrationView() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(28));
        card.setMaxWidth(580);

        Label formTitle = new Label("New Teacher Registration");
        formTitle.getStyleClass().add("card-title");
        Label formNote = new Label(
                "Create a teacher account with the profile information that will appear in the faculty workspace.");
        formNote.getStyleClass().add("muted");
        formNote.setWrapText(true);
        Label sessionNote = new Label(
                "This teacher account will be saved to the local database and reused for future logins.");
        sessionNote.getStyleClass().add("muted");
        sessionNote.setWrapText(true);

        GridPane formGrid = new GridPane();
        formGrid.setHgap(12);
        formGrid.setVgap(12);

        TextField nameField = new TextField();
        nameField.setPromptText("Full name");

        TextField idField = new TextField();
        idField.setPromptText("Teacher ID");

        ComboBox<String> departmentBox = buildComboBox(List.of("CSE", "EEE", "CE", "ME", "ChE", "IPE", "Arch", "URP"),
                "CSE");
        ComboBox<String> designationBox = buildComboBox(
                List.of("Lecturer", "Assistant Professor", "Associate Professor", "Professor"),
                "Lecturer");

        TextField emailField = new TextField();
        emailField.setPromptText("teacher@buet.ac.bd");

        TextField officeField = new TextField();
        officeField.setPromptText("Office room");

        TextField phoneField = new TextField();
        phoneField.setPromptText("Mobile number");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Create password");

        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirm password");

        formGrid.add(buildFieldLabel("Full name"), 0, 0);
        formGrid.add(nameField, 1, 0);
        formGrid.add(buildFieldLabel("Teacher ID"), 0, 1);
        formGrid.add(idField, 1, 1);
        formGrid.add(buildFieldLabel("Department"), 0, 2);
        formGrid.add(departmentBox, 1, 2);
        formGrid.add(buildFieldLabel("Designation"), 0, 3);
        formGrid.add(designationBox, 1, 3);
        formGrid.add(buildFieldLabel("Email"), 0, 4);
        formGrid.add(emailField, 1, 4);
        formGrid.add(buildFieldLabel("Office"), 0, 5);
        formGrid.add(officeField, 1, 5);
        formGrid.add(buildFieldLabel("Phone"), 0, 6);
        formGrid.add(phoneField, 1, 6);
        formGrid.add(buildFieldLabel("Password"), 0, 7);
        formGrid.add(passField, 1, 7);
        formGrid.add(buildFieldLabel("Confirm"), 0, 8);
        formGrid.add(confirmField, 1, 8);

        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(idField, Priority.ALWAYS);
        GridPane.setHgrow(departmentBox, Priority.ALWAYS);
        GridPane.setHgrow(designationBox, Priority.ALWAYS);
        GridPane.setHgrow(emailField, Priority.ALWAYS);
        GridPane.setHgrow(officeField, Priority.ALWAYS);
        GridPane.setHgrow(phoneField, Priority.ALWAYS);
        GridPane.setHgrow(passField, Priority.ALWAYS);
        GridPane.setHgrow(confirmField, Priority.ALWAYS);

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Button registerButton = new Button("Create Teacher Account");
        registerButton.getStyleClass().add("primary-button");
        registerButton.setDefaultButton(true);
        registerButton.setOnAction(event -> {
            String name = clean(nameField.getText());
            String teacherId = clean(idField.getText());
            String email = clean(emailField.getText());
            String office = clean(officeField.getText());
            String phone = clean(phoneField.getText());
            String password = passField.getText();
            String confirmPassword = confirmField.getText();

            if (name.isBlank() || teacherId.isBlank() || email.isBlank() || office.isBlank() || phone.isBlank()
                    || password.isBlank() || confirmPassword.isBlank()) {
                message.setText("Fill in all teacher registration fields.");
                return;
            }

            if (!isValidEmail(email)) {
                message.setText("Enter a valid teacher email address.");
                return;
            }

            if (state.findTeacherById(teacherId) != null) {
                message.setText("A teacher account with this ID already exists.");
                return;
            }

            if (password.length() < 4) {
                message.setText("Teacher password must be at least 4 characters.");
                return;
            }

            if (!password.equals(confirmPassword)) {
                message.setText("Teacher passwords do not match.");
                return;
            }

            state.registerTeacher(
                    new TeacherProfile(
                            teacherId,
                            name,
                            departmentBox.getValue(),
                            designationBox.getValue(),
                            email,
                            office,
                            phone),
                    password);
            scene.setRoot(buildTeacherDashboardView());
        });

        Button backButton = new Button("Back to Teacher Login");
        backButton.getStyleClass().add("outline-button");
        backButton.setOnAction(event -> scene.setRoot(buildTeacherLoginView()));

        Button studentSwitchButton = new Button("Student Registration");
        studentSwitchButton.getStyleClass().add("outline-button");
        studentSwitchButton.setOnAction(event -> scene.setRoot(buildStudentRegistrationView()));

        HBox primaryActions = new HBox(10, registerButton, backButton);
        primaryActions.setAlignment(Pos.CENTER_LEFT);

        VBox actionButtons = new VBox(10, primaryActions, studentSwitchButton);
        VBox.setMargin(actionButtons, new Insets(6, 0, 0, 0));

        card.getChildren().addAll(formTitle, formNote, sessionNote, formGrid, message, actionButtons);
        return buildAuthView("BISS Teacher Registration", card);
    }

    public Parent buildDashboardView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        teacherDashboardChatButton = null;
        studentCourseChatButton = null;
        ObservableList<StudentDeadlineTask> plannerItems = FXCollections.observableArrayList();
        refreshStudentDeadlineTasks(plannerItems);
        state.courseSelections().forEach(selection -> selection.selectedProperty().addListener((obs, oldVal, newVal) -> {
            refreshStudentDeadlineTasks(plannerItems);
            ensureStudentCourseChatEnvironment();
            updateCourseChatButton();
        }));

        HBox topBar = new HBox(16);
        topBar.getStyleClass().add("topbar");
        topBar.setPadding(new Insets(18, 28, 18, 28));

        HBox brandBlock = new HBox(12);
        brandBlock.setAlignment(Pos.CENTER_LEFT);
        ImageView navLogo = buildLogoView(34);
        VBox brandText = new VBox(2);
        Label brand = new Label("BUET Intelligent Study System (BISS)");
        brand.getStyleClass().add("brand-title");
        Label brandSubtitle = new Label("Student Portal");
        brandSubtitle.getStyleClass().add("brand-subtitle");
        brandText.getChildren().addAll(brand, brandSubtitle);
        brandBlock.getChildren().addAll(navLogo, brandText);
        Label page = new Label("Dashboard Overview");
        page.getStyleClass().add("page-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label welcome = new Label("Logged in as " + state.profile().name());
        welcome.getStyleClass().add("muted");

        /*
         * Button homeButton = new Button("Home");
         * homeButton.getStyleClass().add("outline-button");
         * homeButton.setOnAction(event -> goHome());
         * 
         * Button logout = new Button("Logout");
         * logout.getStyleClass().add("outline-button");
         * logout.setOnAction(event -> {
         * state.setLoggedInStudent(null);
         * scene.setRoot(buildLoginView());
         * });
         * 
         * topBar.getChildren().addAll(brandBlock, page, spacer, welcome, homeButton,
         * logout);
         */

        Button homeButton = new Button("Home");
        homeButton.getStyleClass().add("outline-button");
        homeButton.setOnAction(event -> goHome());

        Button chatButton = new Button("💬 Chat");
        chatButton.getStyleClass().add("primary-button");
        chatButton.setOnAction(event -> openChatWindow());
        studentCourseChatButton = chatButton;
        ensureStudentCourseChatEnvironment();
        updateCourseChatButton();

        Button studyButton = new Button("📖 Study Space");
        studyButton.getStyleClass().add("primary-button");
        studyButton.setOnAction(event -> openStudySpace());

        Button teacherChatButton = new Button("Teacher Chat");
        teacherChatButton.getStyleClass().add("primary-button");
        teacherChatButton.setOnAction(event -> openStudentTeacherConversationPicker());
        studentTeacherChatButton = teacherChatButton;
        ensureStudentPrivateChatEnvironment();
        updatePrivateChatButtons();

        Button logout = new Button("Logout");
        logout.getStyleClass().add("outline-button");
        logout.setOnAction(event -> {
            closeAllTransientWindows();
            state.setLoggedInStudent(null);
            scene.setRoot(buildLoginView());
        });

        topBar.getChildren().addAll(
                brandBlock, page, spacer, welcome,
                homeButton, chatButton, teacherChatButton, studyButton, logout);

        root.setTop(topBar);

        GridPane mainGrid = new GridPane();
        mainGrid.setHgap(22);
        mainGrid.setVgap(22);
        mainGrid.setPadding(new Insets(24, 28, 28, 28));

        ColumnConstraints leftCol = new ColumnConstraints();
        leftCol.setPercentWidth(35);
        ColumnConstraints rightCol = new ColumnConstraints();
        rightCol.setPercentWidth(65);
        mainGrid.getColumnConstraints().addAll(leftCol, rightCol);

        TabPane infoTabs = buildInfoTabs(plannerItems);
        VBox profileCard = buildProfileCard();
        VBox notificationCenter = buildStudentNotificationCenterCard(
                plannerItems,
                this::openChatWindow,
                this::openStudentTeacherConversationPicker,
                () -> infoTabs.getSelectionModel().select(STUDENT_TAB_PLANNER_INDEX),
                () -> infoTabs.getSelectionModel().select(STUDENT_TAB_ATTENDANCE_INDEX),
                () -> infoTabs.getSelectionModel().select(STUDENT_TAB_NOTICES_INDEX));
        VBox leftColumnBox = new VBox(22, profileCard, notificationCenter);
        VBox courseSection = buildCoursesSection();

        mainGrid.add(leftColumnBox, 0, 0);
        mainGrid.add(infoTabs, 1, 0);
        mainGrid.add(courseSection, 0, 1, 2, 1);

        ScrollPane scrollPane = new ScrollPane(mainGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("main-scroll");
        root.setCenter(scrollPane);

        return root;
    }

    public Parent buildTeacherDashboardView() {
        TeacherWorkspace workspace = activeTeacherWorkspace();

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        studentTeacherChatButton = null;
        studentCourseChatButton = null;

        HBox topBar = new HBox(16);
        topBar.getStyleClass().add("topbar");
        topBar.setPadding(new Insets(18, 28, 18, 28));

        HBox brandBlock = new HBox(12);
        brandBlock.setAlignment(Pos.CENTER_LEFT);
        ImageView navLogo = buildLogoView(34);
        VBox brandText = new VBox(2);
        Label brand = new Label("BUET Intelligent Study System (BISS)");
        brand.getStyleClass().add("brand-title");
        Label brandSubtitle = new Label("Teacher Portal");
        brandSubtitle.getStyleClass().add("brand-subtitle");
        brandText.getChildren().addAll(brand, brandSubtitle);
        brandBlock.getChildren().addAll(navLogo, brandText);
        Label page = new Label("Teacher Workspace");
        page.getStyleClass().add("page-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label welcome = new Label("Logged in as " + workspace.profile().name());
        welcome.getStyleClass().add("muted");

        Button homeButton = new Button("Home");
        homeButton.getStyleClass().add("outline-button");
        homeButton.setOnAction(event -> goHome());

        Button chatButton = new Button("Chat");
        chatButton.getStyleClass().add("primary-button");
        chatButton.setOnAction(event -> openTeacherConversationPicker(workspace));
        teacherDashboardChatButton = chatButton;
        ensureTeacherPrivateChatEnvironment(workspace);
        updatePrivateChatButtons();

        Button logout = new Button("Logout");
        logout.getStyleClass().add("outline-button");
        logout.setOnAction(event -> {
            closeAllTransientWindows();
            state.setLoggedInTeacher(null);
            scene.setRoot(buildTeacherLoginView());
        });

        topBar.getChildren().addAll(brandBlock, page, spacer, welcome, homeButton, chatButton, logout);
        root.setTop(topBar);

        GridPane mainGrid = new GridPane();
        mainGrid.setHgap(22);
        mainGrid.setVgap(22);
        mainGrid.setPadding(new Insets(24, 28, 28, 28));

        ColumnConstraints leftCol = new ColumnConstraints();
        leftCol.setPercentWidth(32);
        ColumnConstraints rightCol = new ColumnConstraints();
        rightCol.setPercentWidth(68);
        mainGrid.getColumnConstraints().addAll(leftCol, rightCol);

        VBox profileCard = buildTeacherProfileCard(workspace);
        TabPane teacherTabs = buildTeacherTabs(workspace);

        mainGrid.add(profileCard, 0, 0);
        mainGrid.add(teacherTabs, 1, 0);

        ScrollPane scrollPane = new ScrollPane(mainGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("main-scroll");
        root.setCenter(scrollPane);

        return root;
    }

    private VBox buildTeacherProfileCard(TeacherWorkspace workspace) {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(22));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Teacher Profile");
        title.getStyleClass().add("card-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button editButton = new Button("Edit Profile");
        editButton.getStyleClass().add("outline-button");
        editButton.setOnAction(event -> openTeacherProfileEditor(workspace));
        header.getChildren().addAll(title, spacer, editButton);

        GridPane infoGrid = new GridPane();
        infoGrid.setVgap(10);
        infoGrid.setHgap(12);
        infoGrid.add(buildFieldLabel("Name"), 0, 0);
        infoGrid.add(buildFieldValue(workspace.profile().name()), 1, 0);
        infoGrid.add(buildFieldLabel("Teacher ID"), 0, 1);
        infoGrid.add(buildFieldValue(workspace.profile().id()), 1, 1);
        infoGrid.add(buildFieldLabel("Department"), 0, 2);
        infoGrid.add(buildFieldValue(workspace.profile().department()), 1, 2);
        infoGrid.add(buildFieldLabel("Designation"), 0, 3);
        infoGrid.add(buildFieldValue(workspace.profile().designation()), 1, 3);
        infoGrid.add(buildFieldLabel("Email"), 0, 4);
        infoGrid.add(buildFieldValue(workspace.profile().email()), 1, 4);
        infoGrid.add(buildFieldLabel("Office"), 0, 5);
        infoGrid.add(buildFieldValue(workspace.profile().officeRoom()), 1, 5);
        infoGrid.add(buildFieldLabel("Phone"), 0, 6);
        infoGrid.add(buildFieldValue(workspace.profile().phone()), 1, 6);

        HBox tags = new HBox(8);
        tags.setPadding(new Insets(6, 0, 0, 0));
        Label status = new Label("Faculty Active");
        status.getStyleClass().add("tag");
        Label privateData = new Label("Private Workspace");
        privateData.getStyleClass().add("tag");
        tags.getChildren().addAll(status, privateData);

        card.getChildren().addAll(header, infoGrid, tags);
        return card;
    }

    private TabPane buildTeacherTabs(TeacherWorkspace workspace) {
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("info-tabs");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab routineTab = new Tab("My Routine");
        routineTab.setContent(buildTeacherRoutinePane(workspace));

        Tab calendarTab = new Tab("Calendar");
        calendarTab.setContent(buildTeacherCalendarPane(workspace));

        Tab plannerTab = new Tab("Deadline Planner");
        plannerTab.setContent(buildTeacherDeadlinePlannerPane(workspace));

        Tab submissionsTab = new Tab("Submissions");
        submissionsTab.setContent(buildTeacherSubmissionsPane(workspace));

        Tab uploadTab = new Tab("Upload Resources");
        uploadTab.setContent(buildTeacherUploadPane(workspace));

        Tab attendanceTab = new Tab("Attendance");
        attendanceTab.setContent(buildTeacherAttendancePane(workspace));

        Tab noticesTab = new Tab("Authority Notices");
        noticesTab.setContent(buildTeacherNoticesPane());

        tabPane.getTabs().addAll(routineTab, calendarTab, plannerTab, submissionsTab, uploadTab, attendanceTab,
                noticesTab);
        return tabPane;
    }

    private VBox buildTeacherRoutinePane(TeacherWorkspace workspace) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("Teacher Routine");
        title.getStyleClass().add("card-title");
        Label note = new Label("Routine for " + workspace.profile().name()
                + ". Rows show days and columns show one-hour periods.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        List<RoutineTimetableEntry> routineEntries = workspace.routineRows().stream()
                .map(row -> new RoutineTimetableEntry(
                        parseRoutineDay(row.dayProperty().get()),
                        parseRoutineStartTime(row.timeProperty().get()),
                        parseRoutineEndTime(row.timeProperty().get()),
                        clean(row.courseProperty().get()),
                        formatRoutineSubtitle(row.sectionProperty().get(), row.roomProperty().get()),
                        clean(row.timeProperty().get())))
                .filter(entry -> entry.dayOfWeek() != null)
                .toList();

        box.getChildren().addAll(title, note, buildRoutineTimetable(routineEntries, "No routine assigned."));
        return box;
    }

    private VBox buildTeacherCalendarPane(TeacherWorkspace workspace) {
        return buildCalendarPane(
                "Teacher Calendar",
                "See your weekly routine together with assignments, class tests, quizzes, and exams in week or month format.",
                (periodStart, periodEnd) -> buildTeacherCalendarEntries(workspace, periodStart, periodEnd));
    }

    private VBox buildTeacherUploadPane(TeacherWorkspace workspace) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("Upload Resources");
        title.getStyleClass().add("card-title");
        Label note = new Label("Upload files, PDFs, slides, and notes for students or private use.");
        note.getStyleClass().add("muted");

        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(10);

        TextField courseField = new TextField();
        courseField.setPromptText("Course code (e.g. CSE-110)");

        TextField titleField = new TextField();
        titleField.setPromptText("Resource title");

        TextField fileField = new TextField();
        fileField.setPromptText("No file selected");
        fileField.setEditable(false);
        final File[] selectedFile = new File[1];

        ComboBox<String> typeBox = new ComboBox<>(
                FXCollections.observableArrayList("PDF", "PPT", "DOC", "Sheet", "Other"));
        typeBox.setValue("PDF");
        typeBox.setMaxWidth(Double.MAX_VALUE);

        Button browseButton = new Button("Choose File");
        browseButton.getStyleClass().add("outline-button");
        browseButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Resource File");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("All Supported", "*.pdf", "*.ppt", "*.pptx", "*.doc", "*.docx",
                            "*.xls", "*.xlsx"),
                    new FileChooser.ExtensionFilter("PDF", "*.pdf"),
                    new FileChooser.ExtensionFilter("Slides", "*.ppt", "*.pptx"),
                    new FileChooser.ExtensionFilter("Documents", "*.doc", "*.docx"),
                    new FileChooser.ExtensionFilter("Spreadsheets", "*.xls", "*.xlsx"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
            File selected = chooser.showOpenDialog(scene.getWindow());
            if (selected != null) {
                selectedFile[0] = selected;
                fileField.setText(selected.getName());
                typeBox.setValue(inferResourceType(selected.getName()));
            }
        });

        CheckBox privateOnlyCheck = new CheckBox("Private (teacher only)");
        privateOnlyCheck.getStyleClass().add("muted");

        formGrid.add(buildFieldLabel("Course"), 0, 0);
        formGrid.add(courseField, 1, 0);
        formGrid.add(buildFieldLabel("Title"), 0, 1);
        formGrid.add(titleField, 1, 1);
        formGrid.add(buildFieldLabel("File"), 0, 2);
        formGrid.add(fileField, 1, 2);
        formGrid.add(browseButton, 2, 2);
        formGrid.add(buildFieldLabel("Type"), 0, 3);
        formGrid.add(typeBox, 1, 3);
        formGrid.add(privateOnlyCheck, 1, 4);
        GridPane.setHgrow(courseField, Priority.ALWAYS);
        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setHgrow(fileField, Priority.ALWAYS);
        GridPane.setHgrow(typeBox, Priority.ALWAYS);

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Button uploadButton = new Button("Upload Resource");
        uploadButton.getStyleClass().add("primary-button");
        uploadButton.setOnAction(event -> {
            if (courseField.getText().isBlank() || titleField.getText().isBlank() || fileField.getText().isBlank()) {
                message.setText("Please provide course, title, and file.");
                return;
            }
            if (selectedFile[0] == null) {
                message.setText("Choose a file to upload.");
                return;
            }

            String visibility = privateOnlyCheck.isSelected() ? "Private" : "Student";
            try {
                String storedPath = storeTeacherResource(selectedFile[0]);
                workspace.uploadedResources().addFirst(
                        new UploadedResource(
                                courseField.getText().trim(),
                                titleField.getText().trim(),
                                typeBox.getValue(),
                                fileField.getText().trim(),
                                visibility,
                                LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                                storedPath));
            } catch (IllegalStateException ex) {
                message.setText(ex.getMessage());
                return;
            }

            courseField.clear();
            titleField.clear();
            fileField.clear();
            selectedFile[0] = null;
            typeBox.setValue("PDF");
            privateOnlyCheck.setSelected(false);
            message.setText("Resource uploaded successfully.");
        });

        TableView<UploadedResource> uploadedTable = new TableView<>(workspace.uploadedResources());
        uploadedTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<UploadedResource, String> courseCol = new TableColumn<>("Course");
        courseCol.setCellValueFactory(data -> data.getValue().courseProperty());
        TableColumn<UploadedResource, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(data -> data.getValue().titleProperty());
        TableColumn<UploadedResource, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> data.getValue().typeProperty());
        TableColumn<UploadedResource, String> fileCol = new TableColumn<>("File");
        fileCol.setCellValueFactory(data -> data.getValue().fileNameProperty());
        TableColumn<UploadedResource, String> visibilityCol = new TableColumn<>("Visibility");
        visibilityCol.setCellValueFactory(data -> data.getValue().visibilityProperty());
        TableColumn<UploadedResource, String> uploadedAtCol = new TableColumn<>("Uploaded");
        uploadedAtCol.setCellValueFactory(data -> data.getValue().uploadedAtProperty());
        uploadedTable.getColumns().addAll(courseCol, titleCol, typeCol, fileCol, visibilityCol, uploadedAtCol);
        uploadedTable.setPlaceholder(new Label("No resources uploaded yet."));
        sizeTableToRows(uploadedTable);

        box.getChildren().addAll(title, note, formGrid, message, uploadButton, uploadedTable);
        return box;
    }

    private VBox buildTeacherAttendancePane(TeacherWorkspace workspace) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("Attendance by Student");
        title.getStyleClass().add("card-title");
        Label note = new Label(
                "Mark each class per student, monitor attendance percentage, and highlight students below the warning threshold.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        List<String> courseOptions = teacherCourseOptions(workspace);
        ComboBox<String> courseBox = buildComboBox(
                courseOptions.isEmpty() ? List.of("") : courseOptions,
                courseOptions.isEmpty() ? "" : courseOptions.getFirst());
        courseBox.setEditable(true);

        TextField sectionField = new TextField("A");
        sectionField.setPromptText("Section");

        DatePicker datePicker = new DatePicker(LocalDate.now());

        ComboBox<Integer> thresholdBox = new ComboBox<>(FXCollections.observableArrayList(60, 70, 75, 80, 85, 90));
        thresholdBox.setValue(75);
        thresholdBox.setMaxWidth(Double.MAX_VALUE);

        GridPane controls = new GridPane();
        controls.setHgap(10);
        controls.setVgap(10);
        controls.add(buildFieldLabel("Course"), 0, 0);
        controls.add(courseBox, 1, 0);
        controls.add(buildFieldLabel("Section"), 2, 0);
        controls.add(sectionField, 3, 0);
        controls.add(buildFieldLabel("Date"), 0, 1);
        controls.add(datePicker, 1, 1);
        controls.add(buildFieldLabel("Warning %"), 2, 1);
        controls.add(thresholdBox, 3, 1);
        GridPane.setHgrow(courseBox, Priority.ALWAYS);
        GridPane.setHgrow(sectionField, Priority.ALWAYS);
        GridPane.setHgrow(datePicker, Priority.ALWAYS);
        GridPane.setHgrow(thresholdBox, Priority.ALWAYS);

        Label summary = new Label();
        summary.getStyleClass().add("muted");
        summary.setWrapText(true);

        ObservableList<AttendanceMarkRow> studentRows = FXCollections.observableArrayList();
        TableView<AttendanceMarkRow> table = new TableView<>(studentRows);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(360);
        table.setPlaceholder(new Label("Choose a course to start marking attendance."));

        TableColumn<AttendanceMarkRow, Boolean> presentCol = new TableColumn<>("Present");
        presentCol.setCellValueFactory(data -> data.getValue().presentProperty());
        presentCol.setCellFactory(CheckBoxTableCell.forTableColumn(presentCol));
        presentCol.setEditable(true);

        TableColumn<AttendanceMarkRow, String> rollCol = new TableColumn<>("Roll");
        rollCol.setCellValueFactory(data -> Bindings.createStringBinding(() -> data.getValue().student().roll()));

        TableColumn<AttendanceMarkRow, String> nameCol = new TableColumn<>("Student");
        nameCol.setCellValueFactory(data -> Bindings.createStringBinding(() -> data.getValue().student().name()));

        TableColumn<AttendanceMarkRow, String> departmentCol = new TableColumn<>("Department");
        departmentCol.setCellValueFactory(data -> Bindings.createStringBinding(() -> data.getValue().student().department()));

        TableColumn<AttendanceMarkRow, String> attendedCol = new TableColumn<>("Attended");
        attendedCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> Integer.toString(data.getValue().previewAttended()),
                data.getValue().presentProperty()));

        TableColumn<AttendanceMarkRow, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> Integer.toString(data.getValue().previewTotal()),
                data.getValue().presentProperty()));

        TableColumn<AttendanceMarkRow, String> percentageCol = new TableColumn<>("Attendance %");
        percentageCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> formatAttendancePercentage(data.getValue().previewPercentage()),
                data.getValue().presentProperty()));

        TableColumn<AttendanceMarkRow, String> warningCol = new TableColumn<>("Warning");
        warningCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> attendanceWarningText(data.getValue().previewPercentage(), thresholdBox.getValue()),
                data.getValue().presentProperty(),
                thresholdBox.valueProperty()));

        table.getColumns().addAll(presentCol, rollCol, nameCol, departmentCol, attendedCol, totalCol, percentageCol,
                warningCol);

        ObservableList<AttendanceSessionSummary> historyItems = FXCollections.observableArrayList();
        TableView<AttendanceSessionSummary> historyTable = new TableView<>(historyItems);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        historyTable.setPrefHeight(190);
        historyTable.setPlaceholder(new Label("Marked class sessions for this course will appear here."));

        TableColumn<AttendanceSessionSummary, String> historyDateCol = new TableColumn<>("Date");
        historyDateCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::attendanceDate));

        TableColumn<AttendanceSessionSummary, String> historySectionCol = new TableColumn<>("Section");
        historySectionCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::section));

        TableColumn<AttendanceSessionSummary, String> historyPresentCol = new TableColumn<>("Present");
        historyPresentCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> Integer.toString(data.getValue().presentCount())));

        TableColumn<AttendanceSessionSummary, String> historyTotalCol = new TableColumn<>("Total");
        historyTotalCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> Integer.toString(data.getValue().totalCount())));

        TableColumn<AttendanceSessionSummary, String> historyPercentageCol = new TableColumn<>("Attendance %");
        historyPercentageCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> formatAttendancePercentage(data.getValue().percentage())));

        historyTable.getColumns().addAll(historyDateCol, historySectionCol, historyPresentCol, historyTotalCol,
                historyPercentageCol);

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Runnable refreshView = () -> {
            String courseCode = clean(courseBox.isEditable() ? courseBox.getEditor().getText() : courseBox.getValue())
                    .toUpperCase(Locale.ROOT);
            String section = clean(sectionField.getText()).toUpperCase(Locale.ROOT);
            LocalDate selectedDate = datePicker.getValue();
            List<StudentAttendanceRecord> teacherRecords = state.loadTeacherAttendanceRecords(workspace.profile().id());

            studentRows.setAll(buildAttendanceMarkRows(courseCode, section, selectedDate, teacherRecords));
            attachAttendanceSummaryListeners(studentRows, summary, thresholdBox);
            refreshTeacherAttendanceSummary(summary, studentRows, thresholdBox.getValue());
            historyItems.setAll(buildAttendanceSessionSummaries(courseCode, section, teacherRecords));
            table.refresh();
        };

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("outline-button");
        refreshButton.setOnAction(event -> {
            refreshView.run();
            message.setText("Attendance view refreshed.");
        });

        Button saveButton = new Button("Save Attendance");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setOnAction(event -> {
            String courseCode = clean(courseBox.isEditable() ? courseBox.getEditor().getText() : courseBox.getValue())
                    .toUpperCase(Locale.ROOT);
            String section = clean(sectionField.getText()).toUpperCase(Locale.ROOT);
            LocalDate selectedDate = datePicker.getValue();
            if (courseCode.isBlank()) {
                message.setText("Choose a course first.");
                return;
            }
            if (section.isBlank()) {
                message.setText("Enter a section before saving.");
                return;
            }
            if (selectedDate == null) {
                message.setText("Choose a class date first.");
                return;
            }

            List<StudentAttendanceRecord> records = studentRows.stream()
                    .map(row -> new StudentAttendanceRecord(
                            0L,
                            workspace.profile().id(),
                            courseCode,
                            section,
                            selectedDate.toString(),
                            row.student().roll(),
                            row.student().name(),
                            row.isPresent(),
                            System.currentTimeMillis()))
                    .toList();
            try {
                state.saveAttendanceRecords(records);
                refreshView.run();
                message.setText("Attendance saved for " + courseCode + " " + section + " on " + selectedDate + ".");
            } catch (IllegalArgumentException ex) {
                message.setText(ex.getMessage());
            } catch (IllegalStateException ex) {
                message.setText("Could not save attendance right now.");
            }
        });

        courseBox.setOnAction(event -> refreshView.run());
        sectionField.textProperty().addListener((obs, oldValue, newValue) -> refreshView.run());
        datePicker.valueProperty().addListener((obs, oldValue, newValue) -> refreshView.run());
        thresholdBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            refreshTeacherAttendanceSummary(summary, studentRows, thresholdBox.getValue());
            table.refresh();
        });

        HBox actions = new HBox(10, saveButton, refreshButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        refreshView.run();
        box.getChildren().addAll(title, note, controls, summary, table, message, actions, historyTable);
        return box;
    }

    private VBox buildTeacherDeadlinePlannerPane(TeacherWorkspace workspace) {
        workspace.deadlines().sort(Comparator.comparingLong(AcademicDeadline::dueAtEpochMillis));

        VBox box = new VBox(14);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("Assignment + Deadline Planner");
        title.getStyleClass().add("card-title");
        Label note = new Label(
                "Create assignments, class tests, quizzes, and exams with due dates. Students will see these items in their planner timeline and calendar.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        List<String> courseOptions = teacherCourseOptions(workspace);
        ComboBox<String> courseBox = buildComboBox(courseOptions.isEmpty() ? List.of("") : courseOptions,
                courseOptions.isEmpty() ? "" : courseOptions.getFirst());
        courseBox.setEditable(true);

        ComboBox<String> typeBox = buildComboBox(List.of("Assignment", "CT", "Quiz", "Exam"), "Assignment");
        TextField titleField = new TextField();
        titleField.setPromptText("e.g. Assignment 4 - Sorting");

        DatePicker dueDatePicker = new DatePicker(LocalDate.now().plusDays(3));
        TextField timeField = new TextField("23:59");
        timeField.setPromptText("HH:mm");

        TextArea detailsArea = new TextArea();
        detailsArea.setPromptText("Short instructions, topics, or submission notes");
        detailsArea.setPrefRowCount(3);
        detailsArea.setWrapText(true);

        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(10);
        formGrid.add(buildFieldLabel("Course"), 0, 0);
        formGrid.add(courseBox, 1, 0);
        formGrid.add(buildFieldLabel("Type"), 0, 1);
        formGrid.add(typeBox, 1, 1);
        formGrid.add(buildFieldLabel("Title"), 0, 2);
        formGrid.add(titleField, 1, 2);
        formGrid.add(buildFieldLabel("Due date"), 0, 3);
        formGrid.add(dueDatePicker, 1, 3);
        formGrid.add(buildFieldLabel("Due time"), 0, 4);
        formGrid.add(timeField, 1, 4);
        formGrid.add(buildFieldLabel("Details"), 0, 5);
        formGrid.add(detailsArea, 1, 5);
        GridPane.setHgrow(courseBox, Priority.ALWAYS);
        GridPane.setHgrow(typeBox, Priority.ALWAYS);
        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setHgrow(dueDatePicker, Priority.ALWAYS);
        GridPane.setHgrow(timeField, Priority.ALWAYS);
        GridPane.setHgrow(detailsArea, Priority.ALWAYS);

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Button publishButton = new Button("Publish Deadline");
        publishButton.getStyleClass().add("primary-button");
        publishButton.setOnAction(event -> {
            String courseCode = clean(courseBox.isEditable() ? courseBox.getEditor().getText() : courseBox.getValue())
                    .toUpperCase(Locale.ROOT);
            String itemTitle = clean(titleField.getText());
            if (courseCode.isBlank() || itemTitle.isBlank()) {
                message.setText("Course and title are required.");
                return;
            }
            if (dueDatePicker.getValue() == null) {
                message.setText("Choose a due date.");
                return;
            }

            LocalTime dueTime = parseDeadlineTime(timeField.getText());
            if (dueTime == null) {
                message.setText("Enter time in 24-hour HH:mm format.");
                return;
            }

            LocalDateTime dueDateTime = LocalDateTime.of(dueDatePicker.getValue(), dueTime);
            if (dueDateTime.isBefore(LocalDateTime.now())) {
                message.setText("Due date must be in the future.");
                return;
            }

            long dueAt = dueDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            try {
                AcademicDeadline saved = state.saveDeadline(new AcademicDeadline(
                        workspace.profile().id(),
                        workspace.profile().name(),
                        courseCode,
                        typeBox.getValue(),
                        itemTitle,
                        clean(detailsArea.getText()),
                        dueAt));
                workspace.deadlines().add(saved);
                workspace.deadlines().sort(Comparator.comparingLong(AcademicDeadline::dueAtEpochMillis));
                if (!courseBox.getItems().contains(courseCode)) {
                    courseBox.getItems().add(courseCode);
                }
                courseBox.setValue(courseCode);
                typeBox.setValue("Assignment");
                titleField.clear();
                dueDatePicker.setValue(LocalDate.now().plusDays(3));
                timeField.setText("23:59");
                detailsArea.clear();
                message.setText("Deadline published for students.");
            } catch (IllegalArgumentException ex) {
                message.setText(ex.getMessage());
            } catch (IllegalStateException ex) {
                message.setText("Could not save the deadline right now.");
            }
        });

        TableView<AcademicDeadline> table = new TableView<>(workspace.deadlines());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(280);

        TableColumn<AcademicDeadline, String> courseCol = new TableColumn<>("Course");
        courseCol.setCellValueFactory(data -> data.getValue().courseCodeProperty());
        TableColumn<AcademicDeadline, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> data.getValue().typeProperty());
        TableColumn<AcademicDeadline, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(data -> data.getValue().titleProperty());
        TableColumn<AcademicDeadline, String> dueCol = new TableColumn<>("Due");
        dueCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> formatDeadlineDateTime(data.getValue().dueAtEpochMillis()),
                data.getValue().dueAtEpochMillisProperty()));
        TableColumn<AcademicDeadline, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> teacherDeadlineStatus(data.getValue()),
                data.getValue().dueAtEpochMillisProperty()));
        table.getColumns().addAll(courseCol, typeCol, titleCol, dueCol, statusCol);
        table.setPlaceholder(new Label("No assignments, quizzes, or exams published yet."));

        Button deleteButton = new Button("Delete Selected");
        deleteButton.getStyleClass().add("outline-button");
        deleteButton.setOnAction(event -> {
            AcademicDeadline selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                message.setText("Select a deadline first.");
                return;
            }

            try {
                state.deleteDeadline(selected.id(), workspace.profile().id());
                workspace.deadlines().remove(selected);
                message.setText("Deadline deleted.");
            } catch (IllegalStateException ex) {
                message.setText("Could not delete the selected deadline.");
            }
        });

        HBox actions = new HBox(10, publishButton, deleteButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        box.getChildren().addAll(title, note, formGrid, message, actions, table);
        return box;
    }

    private VBox buildTeacherSubmissionsPane(TeacherWorkspace workspace) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("Assignment Submissions");
        title.getStyleClass().add("card-title");
        Label note = new Label(
                "Review submitted work, open attachments, and save grades with feedback for your published deadlines.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        List<String> courseOptions = new java.util.ArrayList<>();
        courseOptions.add("All courses");
        courseOptions.addAll(teacherCourseOptions(workspace));
        ComboBox<String> courseFilter = buildComboBox(courseOptions, "All courses");

        ComboBox<String> statusFilter = buildComboBox(
                List.of("All statuses", "Awaiting Review", "Graded"),
                "All statuses");

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("outline-button");

        HBox filters = new HBox(10, courseFilter, statusFilter, refreshButton);
        filters.setAlignment(Pos.CENTER_LEFT);

        Label summary = new Label();
        summary.getStyleClass().add("muted");
        summary.setWrapText(true);

        ObservableList<AssignmentSubmission> filteredItems = FXCollections.observableArrayList();
        TableView<AssignmentSubmission> table = new TableView<>(filteredItems);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(340);
        table.setPlaceholder(new Label("No submissions have been received yet."));

        TableColumn<AssignmentSubmission, String> courseCol = new TableColumn<>("Course");
        courseCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::courseCode));

        TableColumn<AssignmentSubmission, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::deadlineType));

        TableColumn<AssignmentSubmission, String> itemCol = new TableColumn<>("Item");
        itemCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::deadlineTitle));

        TableColumn<AssignmentSubmission, String> studentCol = new TableColumn<>("Student");
        studentCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> data.getValue().studentName() + " (" + data.getValue().studentRoll() + ")"));

        TableColumn<AssignmentSubmission, String> submittedCol = new TableColumn<>("Submitted");
        submittedCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::submittedAtText));

        TableColumn<AssignmentSubmission, String> attachmentCol = new TableColumn<>("Attachment");
        attachmentCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> data.getValue().hasAttachment() ? "File" : "Text only"));

        TableColumn<AssignmentSubmission, String> gradeCol = new TableColumn<>("Grade");
        gradeCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::gradeDisplay));

        TableColumn<AssignmentSubmission, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> Bindings.createStringBinding(() -> teacherSubmissionStatus(data.getValue())));

        table.getColumns().addAll(courseCol, typeCol, itemCol, studentCol, submittedCol, attachmentCol, gradeCol,
                statusCol);

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Runnable refreshView = () -> {
            List<AssignmentSubmission> allSubmissions = state.loadTeacherSubmissions(workspace.profile().id());
            String selectedCourse = clean(courseFilter.getValue());
            String selectedStatus = clean(statusFilter.getValue());

            List<AssignmentSubmission> visible = allSubmissions.stream()
                    .filter(submission -> selectedCourse.isBlank()
                            || "All courses".equalsIgnoreCase(selectedCourse)
                            || submission.courseCode().equalsIgnoreCase(selectedCourse))
                    .filter(submission -> {
                        if (selectedStatus.isBlank() || "All statuses".equalsIgnoreCase(selectedStatus)) {
                            return true;
                        }
                        if ("Graded".equalsIgnoreCase(selectedStatus)) {
                            return submission.isGraded();
                        }
                        return !submission.isGraded();
                    })
                    .toList();

            filteredItems.setAll(visible);

            long graded = allSubmissions.stream().filter(AssignmentSubmission::isGraded).count();
            long awaiting = allSubmissions.size() - graded;
            summary.setText(allSubmissions.isEmpty()
                    ? "Submissions will appear here after students turn in work."
                    : allSubmissions.size() + " submissions received. "
                            + awaiting + " awaiting review, " + graded + " graded.");
        };

        refreshButton.setOnAction(event -> {
            refreshView.run();
            message.setText("Submission list refreshed.");
        });
        courseFilter.setOnAction(event -> refreshView.run());
        statusFilter.setOnAction(event -> refreshView.run());

        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                AssignmentSubmission selected = table.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openTeacherSubmissionReviewDialog(workspace, selected, refreshView);
                }
            }
        });

        Button openButton = new Button("Open / Grade Selected");
        openButton.getStyleClass().add("primary-button");
        openButton.setOnAction(event -> {
            AssignmentSubmission selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                message.setText("Select a submission first.");
                return;
            }
            openTeacherSubmissionReviewDialog(workspace, selected, refreshView);
        });

        HBox actions = new HBox(10, openButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        refreshView.run();
        box.getChildren().addAll(title, note, filters, summary, table, message, actions);
        return box;
    }

    private VBox buildTeacherNoticesPane() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("Authority Notices");
        title.getStyleClass().add("card-title");
        Label note = new Label("Orders and instructions from university authority.");
        note.getStyleClass().add("muted");

        ListView<String> listView = new ListView<>(state.authorityNotices());
        listView.setPrefHeight(260);
        listView.setTooltip(new Tooltip("Authority instructions for faculty."));

        box.getChildren().addAll(title, note, listView);
        return box;
    }

    private TeacherWorkspace activeTeacherWorkspace() {
        TeacherWorkspace current = state.loggedInTeacher();
        if (current != null) {
            return current;
        }

        TeacherWorkspace fallback = new TeacherWorkspace(
                new TeacherProfile("N/A", "No Teacher", "N/A", "N/A", "N/A", "N/A", "N/A"),
                FXCollections.observableArrayList(),
                FXCollections.observableArrayList(),
                FXCollections.observableArrayList(),
                FXCollections.observableArrayList());
        return fallback;
    }

    private VBox buildProfileCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(22));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Student Profile");
        title.getStyleClass().add("card-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button editButton = new Button("Edit Profile");
        editButton.getStyleClass().add("outline-button");
        editButton.setOnAction(event -> openStudentProfileEditor());
        header.getChildren().addAll(title, spacer, editButton);

        GridPane infoGrid = new GridPane();
        infoGrid.setVgap(10);
        infoGrid.setHgap(12);
        infoGrid.add(buildFieldLabel("Name"), 0, 0);
        infoGrid.add(buildFieldValue(state.profile().name()), 1, 0);
        infoGrid.add(buildFieldLabel("Roll"), 0, 1);
        infoGrid.add(buildFieldValue(state.loggedInRoll()), 1, 1);
        infoGrid.add(buildFieldLabel("Department"), 0, 2);
        infoGrid.add(buildFieldValue(state.profile().department()), 1, 2);
        infoGrid.add(buildFieldLabel("Term"), 0, 3);
        infoGrid.add(buildFieldValue(state.profile().term()), 1, 3);
        infoGrid.add(buildFieldLabel("CGPA"), 0, 4);
        infoGrid.add(buildFieldValue(state.profile().cgpa()), 1, 4);
        infoGrid.add(buildFieldLabel("Email"), 0, 5);
        infoGrid.add(buildFieldValue(state.profile().email()), 1, 5);
        infoGrid.add(buildFieldLabel("Phone"), 0, 6);
        infoGrid.add(buildFieldValue(state.profile().phone()), 1, 6);

        HBox tags = new HBox(8);
        tags.setPadding(new Insets(6, 0, 0, 0));
        Label status = new Label("Active Student");
        status.getStyleClass().add("tag");
        Label scholarship = new Label("Merit Track");
        scholarship.getStyleClass().add("tag");
        tags.getChildren().addAll(status, scholarship);

        card.getChildren().addAll(header, infoGrid, tags);
        return card;
    }

    private VBox buildStudentNotificationCenterCard(ObservableList<StudentDeadlineTask> plannerItems,
            Runnable openCourseChatAction,
            Runnable openTeacherChatAction,
            Runnable openPlannerAction,
            Runnable openAttendanceAction,
            Runnable openNoticesAction) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(18));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Smart Notification Center");
        title.getStyleClass().add("card-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("outline-button");
        refreshButton.setOnAction(event -> {
            ensureStudentCourseChatEnvironment();
            refreshStudentDeadlineTasks(plannerItems);
            refreshStudentNotificationCenter();
        });

        header.getChildren().addAll(title, spacer, refreshButton);

        Label note = new Label(
                "Unread chats, due-soon items, overdue work, attendance risks, and teacher updates appear here.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        HBox metricRow = new HBox(10);
        VBox feedBox = new VBox(10);
        feedBox.getStyleClass().add("notification-feed");

        Runnable refreshAction = () -> populateStudentNotificationCenter(
                plannerItems,
                metricRow,
                feedBox,
                openCourseChatAction,
                openTeacherChatAction,
                openPlannerAction,
                openAttendanceAction,
                openNoticesAction);
        studentNotificationRefreshAction = refreshAction;
        observeStudentDeadlineTasks(plannerItems, refreshAction);
        refreshAction.run();

        card.getChildren().addAll(header, note, metricRow, feedBox);
        return card;
    }

    private void populateStudentNotificationCenter(ObservableList<StudentDeadlineTask> plannerItems,
            HBox metricRow,
            VBox feedBox,
            Runnable openCourseChatAction,
            Runnable openTeacherChatAction,
            Runnable openPlannerAction,
            Runnable openAttendanceAction,
            Runnable openNoticesAction) {
        int unreadTotal = totalCourseChatUnreadCount() + totalPrivateChatUnreadCount();
        long upcomingCount = plannerItems.stream()
                .filter(task -> !isResolvedStudentDeadlineTask(task))
                .filter(task -> isUpcoming(task.deadline()))
                .count();
        long overdueCount = plannerItems.stream()
                .filter(task -> !isResolvedStudentDeadlineTask(task))
                .filter(task -> isOverdue(task.deadline()))
                .count();

        metricRow.getChildren().setAll(
                buildStudentNotificationMetricCard(Integer.toString(unreadTotal), "Unread", "deadline-metric-completed"),
                buildStudentNotificationMetricCard(Long.toString(upcomingCount), "Due Soon", "deadline-metric-upcoming"),
                buildStudentNotificationMetricCard(Long.toString(overdueCount), "Overdue", "deadline-metric-overdue"));

        List<StudentDashboardNotification> notifications = List.of(
                buildUnreadChatNotification(openCourseChatAction, openTeacherChatAction),
                buildUpcomingDeadlineNotification(plannerItems, openPlannerAction),
                buildOverdueWorkNotification(plannerItems, openPlannerAction),
                buildAttendanceAlertNotification(openAttendanceAction),
                buildTeacherUpdateNotification(plannerItems, openPlannerAction, openNoticesAction));

        feedBox.getChildren().setAll(notifications.stream().map(this::buildStudentNotificationItem).toList());
    }

    private VBox buildStudentNotificationMetricCard(String value, String label, String accentStyleClass) {
        VBox card = new VBox(4);
        card.getStyleClass().addAll("deadline-metric-card", accentStyleClass);
        HBox.setHgrow(card, Priority.ALWAYS);

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("deadline-metric-value");

        Label textLabel = new Label(label);
        textLabel.getStyleClass().add("deadline-metric-label");

        card.getChildren().addAll(valueLabel, textLabel);
        return card;
    }

    private VBox buildStudentNotificationItem(StudentDashboardNotification notification) {
        VBox content = new VBox(6);
        content.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(content, Priority.ALWAYS);

        Label badge = new Label(notification.category());
        badge.getStyleClass().addAll("notification-badge", notification.badgeStyleClass());

        Label title = new Label(notification.title());
        title.getStyleClass().add("notification-item-title");
        title.setWrapText(true);

        Label detail = new Label(notification.detail());
        detail.getStyleClass().add("notification-item-detail");
        detail.setWrapText(true);

        content.getChildren().addAll(badge, title, detail);

        HBox row = new HBox(10);
        row.getStyleClass().addAll("notification-item", notification.itemStyleClass());
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(content);

        if (notification.action() != null && notification.actionLabel() != null && !notification.actionLabel().isBlank()) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button actionButton = new Button(notification.actionLabel());
            actionButton.getStyleClass().addAll("outline-button", "notification-action-button");
            actionButton.setOnAction(event -> notification.action().run());

            row.getChildren().addAll(spacer, actionButton);
        }

        VBox wrapper = new VBox(row);
        return wrapper;
    }

    private StudentDashboardNotification buildUnreadChatNotification(Runnable openCourseChatAction,
            Runnable openTeacherChatAction) {
        int courseUnread = totalCourseChatUnreadCount();
        int privateUnread = totalPrivateChatUnreadCount();
        int totalUnread = courseUnread + privateUnread;
        long courseChannels = courseChatUnreadCounts.values().stream().filter(count -> count > 0).count();
        long privateConversations = privateChatConversations.values().stream()
                .filter(conversation -> conversation.unreadCount() > 0)
                .count();

        if (totalUnread <= 0) {
            return new StudentDashboardNotification(
                    "Chat",
                    "All chats are caught up",
                    "No unread course or teacher messages are waiting right now.",
                    "notification-item-neutral",
                    "notification-badge-neutral",
                    "Open",
                    openCourseChatAction);
        }

        java.util.List<String> parts = new java.util.ArrayList<>();
        if (courseUnread > 0) {
            parts.add(courseUnread + " unread in " + courseChannels + " course chat"
                    + (courseChannels == 1 ? "" : "s"));
        }
        if (privateUnread > 0) {
            parts.add(privateUnread + " unread in " + privateConversations + " teacher chat"
                    + (privateConversations == 1 ? "" : "s"));
        }

        Runnable action = privateUnread > courseUnread ? openTeacherChatAction : openCourseChatAction;
        return new StudentDashboardNotification(
                "Chat",
                totalUnread + " unread chat message" + (totalUnread == 1 ? "" : "s"),
                String.join(" • ", parts) + ".",
                "notification-item-chat",
                "notification-badge-chat",
                "Open",
                action);
    }

    private StudentDashboardNotification buildUpcomingDeadlineNotification(ObservableList<StudentDeadlineTask> plannerItems,
            Runnable openPlannerAction) {
        List<StudentDeadlineTask> upcomingTasks = plannerItems.stream()
                .filter(task -> !isResolvedStudentDeadlineTask(task))
                .filter(task -> isUpcoming(task.deadline()))
                .sorted(Comparator.comparingLong(task -> task.deadline().dueAtEpochMillis()))
                .toList();

        if (upcomingTasks.isEmpty()) {
            return new StudentDashboardNotification(
                    "Due Soon",
                    "No deadlines due in the next 7 days",
                    "Your upcoming planner queue is clear for now.",
                    "notification-item-neutral",
                    "notification-badge-upcoming",
                    "Planner",
                    openPlannerAction);
        }

        StudentDeadlineTask nextTask = upcomingTasks.getFirst();
        String detail = nextTask.deadline().courseCode() + " • " + nextTask.deadline().title()
                + " • " + reminderText(nextTask);
        if (upcomingTasks.size() > 1) {
            detail += " • +" + (upcomingTasks.size() - 1) + " more queued";
        }

        return new StudentDashboardNotification(
                "Due Soon",
                upcomingTasks.size() + " deadline" + (upcomingTasks.size() == 1 ? "" : "s") + " need attention soon",
                detail,
                "notification-item-upcoming",
                "notification-badge-upcoming",
                "Planner",
                openPlannerAction);
    }

    private StudentDashboardNotification buildOverdueWorkNotification(ObservableList<StudentDeadlineTask> plannerItems,
            Runnable openPlannerAction) {
        List<StudentDeadlineTask> overdueTasks = plannerItems.stream()
                .filter(task -> !isResolvedStudentDeadlineTask(task))
                .filter(task -> isOverdue(task.deadline()))
                .sorted(Comparator.comparingLong(task -> task.deadline().dueAtEpochMillis()))
                .toList();

        if (overdueTasks.isEmpty()) {
            return new StudentDashboardNotification(
                    "Overdue",
                    "No overdue work right now",
                    "You do not have any unfinished assignments, quizzes, or exams past their due time.",
                    "notification-item-neutral",
                    "notification-badge-overdue",
                    "Planner",
                    openPlannerAction);
        }

        StudentDeadlineTask oldestTask = overdueTasks.getFirst();
        String detail = oldestTask.deadline().courseCode() + " • " + oldestTask.deadline().title()
                + " • " + reminderText(oldestTask);
        if (overdueTasks.size() > 1) {
            detail += " • +" + (overdueTasks.size() - 1) + " more overdue";
        }

        return new StudentDashboardNotification(
                "Overdue",
                overdueTasks.size() + " overdue item" + (overdueTasks.size() == 1 ? "" : "s"),
                detail,
                "notification-item-overdue",
                "notification-badge-overdue",
                "Review",
                openPlannerAction);
    }

    private StudentDashboardNotification buildAttendanceAlertNotification(Runnable openAttendanceAction) {
        List<StudentAttendanceRecord> records = state.loadMyAttendanceRecords();
        if (records.isEmpty()) {
            return new StudentDashboardNotification(
                    "Attendance",
                    "Attendance has not been marked yet",
                    "This card will flag low-attendance courses once teachers upload class records.",
                    "notification-item-neutral",
                    "notification-badge-attendance",
                    "Attendance",
                    openAttendanceAction);
        }

        List<AttendanceCourseSummary> summaries = buildAttendanceCourseSummaries(records);
        List<AttendanceCourseSummary> atRiskCourses = summaries.stream()
                .filter(summary -> summary.percentage() < STUDENT_ATTENDANCE_WARNING_THRESHOLD)
                .sorted(Comparator.comparingDouble(AttendanceCourseSummary::percentage))
                .toList();

        if (atRiskCourses.isEmpty()) {
            int totalClasses = records.size();
            int attendedClasses = (int) records.stream().filter(StudentAttendanceRecord::present).count();
            double overallPercentage = totalClasses == 0 ? 0.0 : (attendedClasses * 100.0) / totalClasses;
            return new StudentDashboardNotification(
                    "Attendance",
                    "Attendance is on track",
                    "Overall attendance is " + formatAttendancePercentage(overallPercentage)
                            + " across " + totalClasses + " marked class"
                            + (totalClasses == 1 ? "" : "es") + ".",
                    "notification-item-neutral",
                    "notification-badge-attendance",
                    "Attendance",
                    openAttendanceAction);
        }

        AttendanceCourseSummary lowestCourse = atRiskCourses.getFirst();
        String detail = lowestCourse.courseCode() + " is at "
                + formatAttendancePercentage(lowestCourse.percentage())
                + ". " + atRiskCourses.size() + " course"
                + (atRiskCourses.size() == 1 ? "" : "s")
                + " are below " + STUDENT_ATTENDANCE_WARNING_THRESHOLD + "%.";
        return new StudentDashboardNotification(
                "Attendance",
                "Low attendance alert",
                detail,
                "notification-item-attendance",
                "notification-badge-attendance",
                "Attendance",
                openAttendanceAction);
    }

    private StudentDashboardNotification buildTeacherUpdateNotification(ObservableList<StudentDeadlineTask> plannerItems,
            Runnable openPlannerAction,
            Runnable openNoticesAction) {
        AcademicDeadline latestTeacherUpdate = plannerItems.stream()
                .map(StudentDeadlineTask::deadline)
                .filter(Objects::nonNull)
                .max(Comparator.comparingLong(AcademicDeadline::createdAtEpochMillis))
                .orElse(null);

        if (latestTeacherUpdate != null) {
            String teacherName = clean(latestTeacherUpdate.teacherName()).isBlank()
                    ? "Your teacher"
                    : latestTeacherUpdate.teacherName();
            String detail = teacherName + " posted " + latestTeacherUpdate.type() + " in "
                    + latestTeacherUpdate.courseCode() + ": " + latestTeacherUpdate.title()
                    + " • Due " + formatDeadlineDateTime(latestTeacherUpdate.dueAtEpochMillis());
            return new StudentDashboardNotification(
                    "Update",
                    "Latest teacher update",
                    detail,
                    "notification-item-update",
                    "notification-badge-update",
                    "View",
                    openPlannerAction);
        }

        if (!state.authorityNotices().isEmpty()) {
            return new StudentDashboardNotification(
                    "Update",
                    "Latest official notice",
                    state.authorityNotices().get(0),
                    "notification-item-update",
                    "notification-badge-update",
                    "Notices",
                    openNoticesAction);
        }

        return new StudentDashboardNotification(
                "Update",
                "No new teacher updates",
                "Newly published assignments and official notices will appear here.",
                "notification-item-neutral",
                "notification-badge-update",
                "Notices",
                openNoticesAction);
    }

    private boolean isResolvedStudentDeadlineTask(StudentDeadlineTask task) {
        return task != null && (task.isCompleted() || task.submission() != null);
    }

    private int totalCourseChatUnreadCount() {
        return courseChatUnreadCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int totalPrivateChatUnreadCount() {
        return privateChatConversations.values().stream()
                .mapToInt(PrivateChatConversation::unreadCount)
                .sum();
    }

    private void refreshStudentNotificationCenter() {
        if (studentNotificationRefreshAction == null) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            studentNotificationRefreshAction.run();
        } else {
            Platform.runLater(studentNotificationRefreshAction);
        }
    }

    private TabPane buildInfoTabs(ObservableList<StudentDeadlineTask> plannerItems) {
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("info-tabs");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab plannerTab = new Tab("Planner");
        plannerTab.setContent(buildStudentPlannerPane(plannerItems));

        Tab calendarTab = new Tab("Calendar");
        calendarTab.setContent(buildStudentCalendarPane(plannerItems));

        Tab submissionsTab = new Tab("Submissions");
        submissionsTab.setContent(buildStudentSubmissionsPane(plannerItems));

        Tab resourcesTab = new Tab("Resources");
        resourcesTab.setContent(buildResourcesPane());

        Tab routineTab = new Tab("Routine");
        routineTab.setContent(buildRoutinePane());

        Tab attendanceTab = new Tab("Attendance");
        attendanceTab.setContent(buildStudentAttendancePane());

        Tab resultsTab = new Tab("Results");
        resultsTab.setContent(buildResultsPane());

        Tab noticesTab = new Tab("Authority Notices");
        noticesTab.setContent(buildNoticesPane());

        Tab enrollmentTab = new Tab("Term Enrollment");
        enrollmentTab.setContent(buildEnrollmentPane());

        tabPane.getTabs().addAll(plannerTab, calendarTab, submissionsTab, resourcesTab, routineTab, attendanceTab,
                resultsTab, noticesTab, enrollmentTab);
        return tabPane;
    }

    private VBox buildStudentPlannerPane(ObservableList<StudentDeadlineTask> plannerItems) {
        VBox box = new VBox(14);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("Assignment + Deadline Timeline");
        title.getStyleClass().add("card-title");
        Label note = new Label(
                "Track assignments, quizzes, and exams in time order. Mark items done to silence reminders and keep overdue alerts focused on unfinished work.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        HBox metricRow = new HBox(12);
        VBox upcomingBox = new VBox(8);
        upcomingBox.getStyleClass().add("deadline-panel");
        VBox overdueBox = new VBox(8);
        overdueBox.getStyleClass().add("deadline-panel");
        HBox highlightRow = new HBox(16, upcomingBox, overdueBox);
        HBox.setHgrow(upcomingBox, Priority.ALWAYS);
        HBox.setHgrow(overdueBox, Priority.ALWAYS);
        observeStudentDeadlineTasks(plannerItems,
                () -> populateStudentDeadlineHighlights(plannerItems, metricRow, upcomingBox, overdueBox, 4));

        TableView<StudentDeadlineTask> table = new TableView<>(plannerItems);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(320);
        table.setPlaceholder(new Label("No deadlines are available for your enrolled courses."));

        TableColumn<StudentDeadlineTask, Boolean> completedCol = new TableColumn<>("Done");
        completedCol.setCellValueFactory(data -> data.getValue().completedProperty());
        completedCol.setCellFactory(CheckBoxTableCell.forTableColumn(completedCol));
        completedCol.setEditable(true);

        TableColumn<StudentDeadlineTask, String> courseCol = new TableColumn<>("Course");
        courseCol.setCellValueFactory(data -> data.getValue().deadline().courseCodeProperty());
        TableColumn<StudentDeadlineTask, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> data.getValue().deadline().typeProperty());
        TableColumn<StudentDeadlineTask, String> titleCol = new TableColumn<>("Item");
        titleCol.setCellValueFactory(data -> data.getValue().deadline().titleProperty());
        TableColumn<StudentDeadlineTask, String> dueCol = new TableColumn<>("Due");
        dueCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> formatDeadlineDateTime(data.getValue().deadline().dueAtEpochMillis()),
                data.getValue().deadline().dueAtEpochMillisProperty()));
        TableColumn<StudentDeadlineTask, String> reminderCol = new TableColumn<>("Reminder");
        reminderCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> reminderText(data.getValue()),
                data.getValue().deadline().dueAtEpochMillisProperty(),
                data.getValue().completedProperty()));
        TableColumn<StudentDeadlineTask, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> studentDeadlineStatus(data.getValue()),
                data.getValue().deadline().dueAtEpochMillisProperty(),
                data.getValue().completedProperty()));

        table.getColumns().addAll(completedCol, courseCol, typeCol, titleCol, dueCol, reminderCol, statusCol);

        box.getChildren().addAll(title, note, metricRow, highlightRow, table);
        return box;
    }

    private VBox buildStudentCalendarPane(ObservableList<StudentDeadlineTask> plannerItems) {
        return buildCalendarPane(
                "Academic Calendar",
                "View your class routine alongside assignment, CT, quiz, and exam dates in a weekly or monthly calendar.",
                (periodStart, periodEnd) -> buildStudentCalendarEntries(plannerItems, periodStart, periodEnd));
    }

    private VBox buildStudentSubmissionsPane(ObservableList<StudentDeadlineTask> plannerItems) {
        VBox box = new VBox(14);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("Assignment Submission Center");
        title.getStyleClass().add("card-title");
        Label note = new Label(
                "Submit text or an attachment for your enrolled-course deadlines, then come back here to track grades and feedback.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        Label summary = new Label();
        summary.getStyleClass().add("muted");
        summary.setWrapText(true);
        observeStudentDeadlineTasks(plannerItems, () -> refreshStudentSubmissionSummary(plannerItems, summary));

        TableView<StudentDeadlineTask> table = new TableView<>(plannerItems);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(340);
        table.setPlaceholder(new Label("No deadlines are available for your enrolled courses."));

        TableColumn<StudentDeadlineTask, String> courseCol = new TableColumn<>("Course");
        courseCol.setCellValueFactory(data -> data.getValue().deadline().courseCodeProperty());

        TableColumn<StudentDeadlineTask, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> data.getValue().deadline().typeProperty());

        TableColumn<StudentDeadlineTask, String> itemCol = new TableColumn<>("Item");
        itemCol.setCellValueFactory(data -> data.getValue().deadline().titleProperty());

        TableColumn<StudentDeadlineTask, String> dueCol = new TableColumn<>("Due");
        dueCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> formatDeadlineDateTime(data.getValue().deadline().dueAtEpochMillis()),
                data.getValue().deadline().dueAtEpochMillisProperty()));

        TableColumn<StudentDeadlineTask, String> submissionCol = new TableColumn<>("Submission");
        submissionCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> studentSubmissionStatus(data.getValue()),
                data.getValue().deadline().dueAtEpochMillisProperty(),
                data.getValue().submissionProperty()));

        TableColumn<StudentDeadlineTask, String> gradeCol = new TableColumn<>("Grade");
        gradeCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> studentSubmissionGrade(data.getValue()),
                data.getValue().submissionProperty()));

        TableColumn<StudentDeadlineTask, Void> actionCol = new TableColumn<>("Action");
        actionCol.setCellFactory(column -> new TableCell<>() {
            private final Button actionButton = new Button();

            {
                actionButton.getStyleClass().add("outline-button");
                actionButton.setOnAction(event -> {
                    StudentDeadlineTask task = getTableView().getItems().get(getIndex());
                    openStudentSubmissionDialog(task);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                StudentDeadlineTask task = getTableView().getItems().get(getIndex());
                actionButton.setText(task.submission() == null ? "Submit" : "View / Edit");
                setGraphic(actionButton);
            }
        });

        table.getColumns().addAll(courseCol, typeCol, itemCol, dueCol, submissionCol, gradeCol, actionCol);
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                StudentDeadlineTask selected = table.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openStudentSubmissionDialog(selected);
                }
            }
        });

        Label tip = new Label("Grades and feedback appear automatically after the teacher reviews your submission.");
        tip.getStyleClass().add("muted");
        tip.setWrapText(true);

        box.getChildren().addAll(title, note, summary, table, tip);
        return box;
    }

    private VBox buildStudentAttendancePane() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("Attendance History");
        title.getStyleClass().add("card-title");
        Label note = new Label(
                "Track your attendance percentage by course, review every marked class, and monitor whether you are below the warning threshold.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        ComboBox<Integer> thresholdBox = new ComboBox<>(FXCollections.observableArrayList(60, 70, 75, 80, 85, 90));
        thresholdBox.setValue(75);
        thresholdBox.setMaxWidth(140);

        Label summary = new Label();
        summary.getStyleClass().add("muted");
        summary.setWrapText(true);

        ObservableList<AttendanceCourseSummary> courseItems = FXCollections.observableArrayList();
        TableView<AttendanceCourseSummary> courseTable = new TableView<>(courseItems);
        courseTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        courseTable.setPrefHeight(230);
        courseTable.setPlaceholder(new Label("Attendance will appear after teachers mark your classes."));

        TableColumn<AttendanceCourseSummary, String> courseCol = new TableColumn<>("Course");
        courseCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::courseCode));

        TableColumn<AttendanceCourseSummary, String> attendedCol = new TableColumn<>("Attended");
        attendedCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> Integer.toString(data.getValue().attendedCount())));

        TableColumn<AttendanceCourseSummary, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> Integer.toString(data.getValue().totalCount())));

        TableColumn<AttendanceCourseSummary, String> percentageCol = new TableColumn<>("Attendance %");
        percentageCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> formatAttendancePercentage(data.getValue().percentage())));

        TableColumn<AttendanceCourseSummary, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> attendanceWarningText(data.getValue().percentage(), thresholdBox.getValue()),
                thresholdBox.valueProperty()));

        courseTable.getColumns().addAll(courseCol, attendedCol, totalCol, percentageCol, statusCol);

        ObservableList<StudentAttendanceRecord> historyItems = FXCollections.observableArrayList();
        TableView<StudentAttendanceRecord> historyTable = new TableView<>(historyItems);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        historyTable.setPrefHeight(240);
        historyTable.setPlaceholder(new Label("Select a course above to inspect class-by-class attendance."));

        TableColumn<StudentAttendanceRecord, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::attendanceDate));

        TableColumn<StudentAttendanceRecord, String> sectionCol = new TableColumn<>("Section");
        sectionCol.setCellValueFactory(data -> Bindings.createStringBinding(() -> clean(data.getValue().section()).isBlank()
                ? "-"
                : data.getValue().section()));

        TableColumn<StudentAttendanceRecord, String> attendanceCol = new TableColumn<>("Status");
        attendanceCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::statusText));

        TableColumn<StudentAttendanceRecord, String> markedCol = new TableColumn<>("Marked");
        markedCol.setCellValueFactory(data -> Bindings.createStringBinding(() -> {
            String markedAt = data.getValue().markedAtText();
            return markedAt.isBlank() ? "-" : markedAt;
        }));

        historyTable.getColumns().addAll(dateCol, sectionCol, attendanceCol, markedCol);

        List<StudentAttendanceRecord> allRecords = state.loadMyAttendanceRecords();
        Runnable refreshCourseView = () -> {
            int threshold = thresholdBox.getValue() == null ? 75 : thresholdBox.getValue();
            List<AttendanceCourseSummary> summaries = buildAttendanceCourseSummaries(allRecords);
            courseItems.setAll(summaries);
            refreshStudentAttendanceOverview(summary, summaries, allRecords, threshold);
            AttendanceCourseSummary selected = courseTable.getSelectionModel().getSelectedItem();
            if (selected == null && !summaries.isEmpty()) {
                courseTable.getSelectionModel().selectFirst();
                selected = courseTable.getSelectionModel().getSelectedItem();
            } else if (selected != null) {
                String selectedCourse = selected.courseCode();
                summaries.stream()
                        .filter(item -> item.courseCode().equalsIgnoreCase(selectedCourse))
                        .findFirst()
                        .ifPresent(item -> courseTable.getSelectionModel().select(item));
                selected = courseTable.getSelectionModel().getSelectedItem();
            }
            refreshStudentAttendanceHistory(historyItems, allRecords, selected);
            courseTable.refresh();
        };

        courseTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) ->
                refreshStudentAttendanceHistory(historyItems, allRecords, newValue));
        thresholdBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshCourseView.run());

        HBox thresholdRow = new HBox(10, buildFieldLabel("Warning %"), thresholdBox);
        thresholdRow.setAlignment(Pos.CENTER_LEFT);

        refreshCourseView.run();
        box.getChildren().addAll(title, note, thresholdRow, summary, courseTable, historyTable);
        return box;
    }

    private VBox buildCalendarPane(String titleText,
            String noteText,
            java.util.function.BiFunction<LocalDate, LocalDate, List<CalendarEntry>> eventProvider) {
        VBox box = new VBox(14);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label(titleText);
        title.getStyleClass().add("card-title");
        Label note = new Label(noteText);
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        ComboBox<String> viewBox = buildComboBox(List.of("Week", "Month"), "Week");
        ComboBox<String> filterBox = buildComboBox(List.of("All Items", "Routine", "Deadlines", "Exams"),
                "All Items");
        DatePicker anchorDatePicker = new DatePicker(LocalDate.now());

        GridPane controls = new GridPane();
        controls.setHgap(10);
        controls.setVgap(10);
        controls.add(buildFieldLabel("View"), 0, 0);
        controls.add(viewBox, 1, 0);
        controls.add(buildFieldLabel("Filter"), 2, 0);
        controls.add(filterBox, 3, 0);
        controls.add(buildFieldLabel("Anchor Date"), 4, 0);
        controls.add(anchorDatePicker, 5, 0);
        GridPane.setHgrow(viewBox, Priority.ALWAYS);
        GridPane.setHgrow(filterBox, Priority.ALWAYS);
        GridPane.setHgrow(anchorDatePicker, Priority.ALWAYS);

        Button previousButton = new Button("Previous");
        previousButton.getStyleClass().add("outline-button");
        Button todayButton = new Button("Today");
        todayButton.getStyleClass().add("outline-button");
        Button nextButton = new Button("Next");
        nextButton.getStyleClass().add("outline-button");
        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("primary-button");

        HBox actions = new HBox(10, previousButton, todayButton, nextButton, refreshButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        Label periodLabel = new Label();
        periodLabel.getStyleClass().add("section-title");

        Label summary = new Label();
        summary.getStyleClass().add("muted");
        summary.setWrapText(true);

        VBox calendarHost = new VBox();
        calendarHost.getStyleClass().add("calendar-shell");

        Label agendaTitle = new Label("Visible Agenda");
        agendaTitle.getStyleClass().add("section-title");

        ListView<String> agendaList = new ListView<>(FXCollections.observableArrayList());
        agendaList.setPrefHeight(220);
        agendaList.setPlaceholder(new Label("No routine items or deadlines fall in this calendar range."));

        Runnable refreshView = () -> {
            LocalDate anchorDate = anchorDatePicker.getValue() == null ? LocalDate.now() : anchorDatePicker.getValue();
            boolean weekView = "Week".equalsIgnoreCase(viewBox.getValue());
            LocalDate periodStart = weekView ? startOfCalendarWeek(anchorDate) : startOfMonthGrid(anchorDate);
            LocalDate periodEnd = weekView ? periodStart.plusDays(6) : periodStart.plusDays(41);

            List<CalendarEntry> visibleEntries = eventProvider.apply(periodStart, periodEnd).stream()
                    .filter(entry -> matchesCalendarFilter(entry, filterBox.getValue()))
                    .sorted(Comparator.comparing(CalendarEntry::sortDateTime)
                            .thenComparing(CalendarEntry::title, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            periodLabel.setText(formatCalendarPeriodTitle(anchorDate, periodStart, periodEnd, weekView));
            summary.setText(buildCalendarSummary(visibleEntries, weekView));
            renderCalendarGrid(calendarHost, anchorDate, periodStart, visibleEntries, weekView);
            agendaList.getItems().setAll(buildCalendarAgendaLines(visibleEntries));
        };

        previousButton.setOnAction(event -> {
            LocalDate current = anchorDatePicker.getValue() == null ? LocalDate.now() : anchorDatePicker.getValue();
            anchorDatePicker.setValue(shiftCalendarAnchor(current, viewBox.getValue(), -1));
            refreshView.run();
        });
        todayButton.setOnAction(event -> {
            anchorDatePicker.setValue(LocalDate.now());
            refreshView.run();
        });
        nextButton.setOnAction(event -> {
            LocalDate current = anchorDatePicker.getValue() == null ? LocalDate.now() : anchorDatePicker.getValue();
            anchorDatePicker.setValue(shiftCalendarAnchor(current, viewBox.getValue(), 1));
            refreshView.run();
        });
        refreshButton.setOnAction(event -> refreshView.run());
        viewBox.setOnAction(event -> refreshView.run());
        filterBox.setOnAction(event -> refreshView.run());
        anchorDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> refreshView.run());

        refreshView.run();
        box.getChildren().addAll(title, note, controls, actions, periodLabel, summary, calendarHost, agendaTitle,
                agendaList);
        return box;
    }

    private ObservableList<RoutineRow> studentRoutineRows() {
        List<RoutineRow> rows = state.courseSelections().stream()
                .filter(CourseSelection::isSelected)
                .flatMap(selection -> buildStudentRoutineRowsForCourse(selection).stream())
                .sorted(Comparator.comparingInt((RoutineRow row) -> routineDaySortKey(row.dayProperty().get()))
                        .thenComparing(row -> parseRoutineStartTime(row.timeProperty().get()))
                        .thenComparing(row -> clean(row.courseProperty().get()), String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (!rows.isEmpty()) {
            return FXCollections.observableArrayList(rows);
        }

        return FXCollections.observableArrayList(
                new RoutineRow("Sun", "09:30-10:50", "CSE-110 Structured Programming [Sec A]", "Room 401"),
                new RoutineRow("Sun", "11:00-12:20", "EEE-103 Basic Electrical Circuits [Sec A]", "Room 403"),
                new RoutineRow("Mon", "08:00-09:20", "MAT-105 Engineering Mathematics [Sec A]", "Room 305"),
                new RoutineRow("Mon", "13:00-14:20", "CSE-110 Structured Programming [Sec B]", "Room 402"),
                new RoutineRow("Tue", "09:30-10:50", "PHY-109 Physics Fundamentals [Sec A]", "Room 203"),
                new RoutineRow("Tue", "14:00-16:00", "CSE-111 Structured Programming Lab [Sec A]", "Lab 3"),
                new RoutineRow("Wed", "08:00-09:20", "MAT-105 Engineering Mathematics [Sec B]", "Room 306"),
                new RoutineRow("Thu", "09:30-10:50", "PHY-109 Physics Fundamentals [Sec B]", "Room 204"));
    }

    private VBox buildResourcesPane() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("Course Resources");
        title.getStyleClass().add("card-title");
        Label note = new Label("Materials shared by faculty and academic support.");
        note.getStyleClass().add("muted");

        ListView<String> listView = new ListView<>();
        listView.getItems().addAll(
                "CSE-110 Structured Programming - Week 6 Slides (PDF)",
                "CSE-111 Structured Programming Lab Manual",
                "EEE-103 Basic Electrical Circuits - Tutorial Notes",
                "MAT-105 Engineering Mathematics - Problem Set 3",
                "PHY-109 Physics Fundamentals - Formula Sheet");
        listView.setPrefHeight(260);

        box.getChildren().addAll(title, note, listView);
        return box;
    }

    private VBox buildRoutinePane() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("Weekly Routine");
        title.getStyleClass().add("card-title");
        Label note = new Label("Updated by the department authority. Rows show days and columns show one-hour periods.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        List<RoutineTimetableEntry> routineEntries = studentRoutineRows().stream()
                .map(row -> new RoutineTimetableEntry(
                        parseRoutineDay(row.dayProperty().get()),
                        parseRoutineStartTime(row.timeProperty().get()),
                        parseRoutineEndTime(row.timeProperty().get()),
                        clean(row.courseProperty().get()),
                        clean(row.roomProperty().get()),
                        clean(row.timeProperty().get())))
                .filter(entry -> entry.dayOfWeek() != null)
                .toList();

        box.getChildren().addAll(title, note, buildRoutineTimetable(routineEntries, "No routine available."));
        return box;
    }

    private VBox buildResultsPane() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("Previous Term Results");
        title.getStyleClass().add("card-title");
        Label note = new Label("Course-wise grade points for completed terms. Use Statistic for overall CG distribution.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        VBox heading = new VBox(4, title, note);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String currentCgText = clean(state.profile().cgpa());
        Label currentCg = new Label("Current CG: " + (currentCgText.isBlank() ? "N/A" : currentCgText));
        currentCg.getStyleClass().addAll("tag", "cg-summary-tag");

        Button statisticButton = new Button("Statistic");
        statisticButton.getStyleClass().add("outline-button");
        statisticButton.setOnAction(event -> openCgStatisticsDialog());

        HBox headerRow = new HBox(12, heading, spacer, currentCg, statisticButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.getStyleClass().add("results-header");

        TableView<ResultRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ResultRow, String> termCol = new TableColumn<>("Term");
        termCol.setCellValueFactory(data -> data.getValue().termProperty());
        TableColumn<ResultRow, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(data -> data.getValue().codeProperty());
        TableColumn<ResultRow, String> titleCol = new TableColumn<>("Course Title");
        titleCol.setCellValueFactory(data -> data.getValue().titleProperty());
        TableColumn<ResultRow, String> gradeCol = new TableColumn<>("GP");
        gradeCol.setCellValueFactory(data -> data.getValue().gradeProperty());

        table.getColumns().addAll(termCol, codeCol, titleCol, gradeCol);
        table.getItems().addAll(
                new ResultRow("Level 1 Term 1", "CSE-102", "Discrete Mathematics", "3.75"),
                new ResultRow("Level 1 Term 1", "MAT-101", "Calculus", "3.50"),
                new ResultRow("Level 1 Term 1", "PHY-101", "Physics I", "3.25"),
                new ResultRow("Level 1 Term 1", "CHE-101", "Chemistry", "3.50"),
                new ResultRow("Level 1 Term 1", "ENG-101", "English Communication", "3.75"));
        table.setPlaceholder(new Label("No results available."));
        sizeTableToRows(table);

        box.getChildren().addAll(headerRow, table);
        return box;
    }

    private void openCgStatisticsDialog() {
        Stage dialog = createEditorDialog("Result Statistics");

        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(24));

        Label title = new Label("CG Statistics");
        title.getStyleClass().add("card-title");

        Label note = new Label(
                "This graph shows how many students fall into each CG range from 2.20 to 4.00. It does not show any individual student's result.");
        note.getStyleClass().addAll("muted", "statistics-note");
        note.setWrapText(true);

        Map<String, Integer> distribution = buildCgDistribution(state.allStudents());
        int includedStudents = distribution.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        Label summary = new Label(includedStudents == 0
                ? "No student CG data is available in the 2.20 to 4.00 range yet."
                : includedStudents + " students are included in this chart.");
        summary.getStyleClass().add("muted");

        Node chartContent;
        if (includedStudents == 0) {
            Label placeholder = new Label("Statistics will appear after student CG values are available.");
            placeholder.getStyleClass().addAll("muted", "statistics-placeholder");
            placeholder.setWrapText(true);
            chartContent = placeholder;
        } else {
            chartContent = buildCgStatisticsChart(distribution);
            VBox.setVgrow(chartContent, Priority.ALWAYS);
        }

        Button closeButton = new Button("Close");
        closeButton.getStyleClass().add("primary-button");
        closeButton.setDefaultButton(true);
        closeButton.setOnAction(event -> dialog.close());

        card.getChildren().addAll(title, note, summary, chartContent, closeButton);
        setDialogContent(dialog, card, 780, 620);
        dialog.showAndWait();
    }

    private Node buildCgStatisticsChart(Map<String, Integer> distribution) {
        VBox chart = new VBox(14);
        chart.getStyleClass().add("statistics-graph");

        int maxCount = distribution.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(1);
        double plotHeight = 300;

        Label caption = new Label("Students per CG range");
        caption.getStyleClass().addAll("field-label", "statistics-axis-caption");

        HBox bars = new HBox(10);
        bars.getStyleClass().add("statistics-bars");
        bars.setAlignment(Pos.BOTTOM_CENTER);
        bars.setPadding(new Insets(12, 14, 10, 14));

        distribution.forEach((range, count) -> bars.getChildren()
                .add(buildCgStatisticsBarColumn(range, count, maxCount, plotHeight)));

        Label footer = new Label("Each bar shows how many students belong to that CG range.");
        footer.getStyleClass().add("muted");
        footer.setWrapText(true);

        chart.getChildren().addAll(caption, bars, footer);
        return chart;
    }

    private VBox buildCgStatisticsBarColumn(String range, int count, int maxCount, double plotHeight) {
        VBox column = new VBox(8);
        column.getStyleClass().add("statistics-column");
        column.setAlignment(Pos.BOTTOM_CENTER);
        column.setPrefWidth(72);

        Label countLabel = new Label(Integer.toString(count));
        countLabel.getStyleClass().add("statistics-count-label");

        StackPane barSlot = new StackPane();
        barSlot.getStyleClass().add("statistics-bar-slot");
        barSlot.setAlignment(Pos.BOTTOM_CENTER);
        barSlot.setPrefSize(68, plotHeight);
        barSlot.setMinHeight(plotHeight);
        barSlot.setMaxHeight(plotHeight);

        Region bar = new Region();
        bar.getStyleClass().add("statistics-bar");
        if (count == 0) {
            bar.getStyleClass().add("statistics-bar-empty");
        }

        double barHeight = count == 0
                ? 8
                : Math.max(18, (count / (double) maxCount) * (plotHeight - 18));
        bar.setPrefSize(42, barHeight);
        bar.setMinSize(42, barHeight);
        bar.setMaxSize(42, barHeight);
        barSlot.getChildren().add(bar);

        Label rangeLabel = new Label(range);
        rangeLabel.getStyleClass().add("statistics-range-label");
        rangeLabel.setWrapText(true);
        rangeLabel.setMaxWidth(72);
        rangeLabel.setAlignment(Pos.TOP_CENTER);

        column.getChildren().addAll(countLabel, barSlot, rangeLabel);
        return column;
    }

    private Map<String, Integer> buildCgDistribution(List<StudentProfile> students) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (int bucketStart = CG_STAT_MIN_HUNDREDTHS;
                bucketStart < CG_STAT_MAX_HUNDREDTHS;
                bucketStart += CG_STAT_BUCKET_WIDTH_HUNDREDTHS) {
            distribution.put(buildCgRangeLabel(bucketStart), 0);
        }

        for (StudentProfile student : students) {
            Integer bucketStart = resolveCgBucketStart(student == null ? null : student.cgpa());
            if (bucketStart == null) {
                continue;
            }

            String label = buildCgRangeLabel(bucketStart);
            distribution.computeIfPresent(label, (key, count) -> count + 1);
        }

        return distribution;
    }

    private Integer resolveCgBucketStart(String cgpa) {
        String valueText = clean(cgpa);
        if (valueText.isBlank()) {
            return null;
        }

        try {
            int hundredths = (int) Math.round(Double.parseDouble(valueText) * 100);
            if (hundredths < CG_STAT_MIN_HUNDREDTHS || hundredths > CG_STAT_MAX_HUNDREDTHS) {
                return null;
            }
            if (hundredths == CG_STAT_MAX_HUNDREDTHS) {
                return CG_STAT_MAX_HUNDREDTHS - CG_STAT_BUCKET_WIDTH_HUNDREDTHS;
            }

            return CG_STAT_MIN_HUNDREDTHS
                    + ((hundredths - CG_STAT_MIN_HUNDREDTHS) / CG_STAT_BUCKET_WIDTH_HUNDREDTHS)
                            * CG_STAT_BUCKET_WIDTH_HUNDREDTHS;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String buildCgRangeLabel(int bucketStartHundredths) {
        int bucketEndHundredths = bucketStartHundredths + CG_STAT_BUCKET_WIDTH_HUNDREDTHS >= CG_STAT_MAX_HUNDREDTHS
                ? CG_STAT_MAX_HUNDREDTHS
                : bucketStartHundredths + CG_STAT_BUCKET_WIDTH_HUNDREDTHS - 1;
        return formatCg(bucketStartHundredths / 100.0) + " - " + formatCg(bucketEndHundredths / 100.0);
    }

    private String formatCg(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private VBox buildCoursesSection() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("My Courses");
        title.getStyleClass().add("card-title");
        Label note = new Label("Scroll down to see your enrolled courses. Click a course card for full details.");
        note.getStyleClass().add("muted");

        VBox courseList = new VBox(12);
        courseList.getStyleClass().add("course-list");
        courseList.setFillWidth(true);

        state.courseSelections().forEach(selection -> selection.selectedProperty()
                .addListener((obs, oldVal, newVal) -> updateCourseCards(courseList)));
        updateCourseCards(courseList);

        box.getChildren().addAll(title, note, courseList);
        return box;
    }

    private void updateCourseCards(VBox courseList) {
        courseList.getChildren().clear();
        List<CourseSelection> selected = state.courseSelections().stream()
                .filter(CourseSelection::isSelected)
                .collect(Collectors.toList());

        if (selected.isEmpty()) {
            Label empty = new Label("No enrolled courses yet. Select courses in Term Enrollment.");
            empty.getStyleClass().add("muted");
            courseList.getChildren().add(empty);
            return;
        }

        selected.forEach(selection -> courseList.getChildren().add(buildCourseCard(selection.detail())));
    }

    private Button buildCourseCard(CourseDetail detail) {
        Button button = new Button();
        button.getStyleClass().add("course-card");
        button.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(6);
        Label title = new Label(detail.code() + " - " + detail.title());
        title.getStyleClass().add("course-card-title");

        Label instructor = new Label("Instructor: " + detail.instructor());
        instructor.getStyleClass().add("muted");

        Label objective = new Label(detail.objective());
        objective.getStyleClass().add("course-card-note");
        objective.setWrapText(true);

        content.getChildren().addAll(title, instructor, objective);
        button.setGraphic(content);
        button.setOnAction(event -> openCourseWindow(detail));
        return button;
    }

    private VBox buildNoticesPane() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("Authority Notices");
        title.getStyleClass().add("card-title");
        Label note = new Label("Official updates from BUET administration.");
        note.getStyleClass().add("muted");

        ListView<String> listView = new ListView<>(state.authorityNotices());
        listView.setPrefHeight(260);
        listView.setTooltip(new Tooltip("Latest official announcements."));

        box.getChildren().addAll(title, note, listView);
        return box;
    }

    private VBox buildEnrollmentPane() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");

        Label title = new Label("Enroll for " + state.profile().term());
        title.getStyleClass().add("card-title");
        Label note = new Label(
                "Select your courses for the new term, confirm enrollment, or open Profile for public instructor information.");
        note.getStyleClass().add("muted");

        ObservableList<CourseSelection> options = state.courseSelections();

        TableView<CourseSelection> table = new TableView<>(options);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(42);
        table.prefHeightProperty().bind(table.fixedCellSizeProperty().multiply(options.size()).add(40));

        TableColumn<CourseSelection, Boolean> selectCol = new TableColumn<>("Enroll");
        selectCol.setCellValueFactory(data -> data.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setEditable(true);

        TableColumn<CourseSelection, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(data -> data.getValue().codeProperty());

        TableColumn<CourseSelection, String> titleCol = new TableColumn<>("Course Title");
        titleCol.setCellValueFactory(data -> data.getValue().titleProperty());

        TableColumn<CourseSelection, String> instructorCol = new TableColumn<>("Instructor");
        instructorCol.setCellValueFactory(data -> data.getValue().instructorProperty());

        TableColumn<CourseSelection, String> creditCol = new TableColumn<>("Credits");
        creditCol.setCellValueFactory(data -> data.getValue().creditProperty());

        TableColumn<CourseSelection, Void> profileCol = new TableColumn<>("Profile");
        profileCol.setCellFactory(column -> new TableCell<>() {
            private final Button profileButton = new Button("Profile");

            {
                profileButton.getStyleClass().add("outline-button");
                profileButton.setOnAction(event -> {
                    CourseSelection selection = getTableView().getItems().get(getIndex());
                    openFacultyProfile(selection.instructor());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : profileButton);
            }
        });

        selectCol.setPrefWidth(70);
        codeCol.setPrefWidth(90);
        titleCol.setPrefWidth(220);
        instructorCol.setPrefWidth(180);
        creditCol.setPrefWidth(70);
        profileCol.setPrefWidth(100);

        table.getColumns().addAll(selectCol, codeCol, titleCol, instructorCol, creditCol, profileCol);

        VBox selectionBox = new VBox(10);
        selectionBox.setPrefWidth(260);
        Label selectionTitle = new Label("Selected courses");
        selectionTitle.getStyleClass().add("card-title");
        ListView<String> selectedList = new ListView<>();
        selectedList.setPrefHeight(200);

        Label creditLabel = new Label("Total credits: 0");
        creditLabel.getStyleClass().add("field-value");

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Button enrollButton = new Button("Confirm enrollment");
        enrollButton.getStyleClass().add("primary-button");
        enrollButton.setOnAction(event -> {
            if (options.stream().noneMatch(CourseSelection::isSelected)) {
                message.setText("Select at least one course to enroll.");
                return;
            }
            message.setText("Enrollment submitted to the department office.");
        });

        selectionBox.getChildren().addAll(selectionTitle, selectedList, creditLabel, message, enrollButton);

        HBox content = new HBox(16, table, selectionBox);
        content.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(table, Priority.ALWAYS);

        options.forEach(option -> option.selectedProperty()
                .addListener((obs, oldVal, newVal) -> updateEnrollmentSummary(options, selectedList, creditLabel)));
        updateEnrollmentSummary(options, selectedList, creditLabel);

        box.getChildren().addAll(title, note, content);
        return box;
    }

    private void updateEnrollmentSummary(ObservableList<CourseSelection> options,
            ListView<String> selectedList,
            Label creditLabel) {
        selectedList.getItems().setAll(
                options.stream()
                        .filter(CourseSelection::isSelected)
                        .map(option -> option.code() + " - " + option.title())
                        .collect(Collectors.toList()));
        int credits = options.stream()
                .filter(CourseSelection::isSelected)
                .mapToInt(CourseSelection::credits)
                .sum();
        creditLabel.setText("Total credits: " + credits);
    }

    private void openCourseWindow(CourseDetail detail) {
        Stage existing = courseStages.get(detail.code());
        if (existing != null) {
            existing.toFront();
            existing.requestFocus();
            return;
        }

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");

        VBox header = new VBox(6);
        header.getStyleClass().addAll("card", "detail-header");
        header.setPadding(new Insets(18, 20, 18, 20));

        Label eyebrow = new Label("Course details");
        eyebrow.getStyleClass().add("detail-eyebrow");
        Label courseTitle = new Label(detail.code() + " - " + detail.title());
        courseTitle.getStyleClass().add("detail-title");
        Label instructor = new Label("Instructor: " + detail.instructor());
        instructor.getStyleClass().add("muted");
        Label meta = new Label("Credits: " + detail.credits() + " | Term: " + state.profile().term());
        meta.getStyleClass().add("muted");
        Button profileButton = new Button("Profile");
        profileButton.getStyleClass().add("outline-button");
        profileButton.setOnAction(event -> openFacultyProfile(detail.instructor()));

        header.getChildren().addAll(eyebrow, courseTitle, instructor, meta, profileButton);

        VBox content = new VBox(14);
        content.setPadding(new Insets(0, 20, 20, 20));
        content.getChildren().addAll(
                buildDetailTextSection("Course objective", detail.objective()),
                buildDetailTextSection("Instructor introduction", detail.teacherIntro()),
                buildDetailListSection(
                        "Deadlines",
                        "Teacher-published assignments, quizzes, and exams for this course.",
                        state.loadCourseDeadlines(detail.code()).stream()
                                .map(this::formatCourseDeadlineLine)
                                .toList(),
                        "No deadlines published yet."),
                buildDetailListSection(
                        "Resources",
                        "Materials shared by faculty and support staff.",
                        detail.resources(),
                        "No resources available yet."),
                buildDetailListSection(
                        "Authority PDFs",
                        "Approved files from the department authority.",
                        detail.pdfs(),
                        "No PDFs available yet."));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("detail-scroll");

        root.setTop(header);
        root.setCenter(scrollPane);

        Scene courseScene = new Scene(root, 820, 620);
        courseScene.getStylesheets().add(stylesheetUrl);

        Stage stage = new Stage();
        stage.setTitle(detail.code() + " - " + detail.title());
        stage.setScene(courseScene);
        stage.setOnCloseRequest(event -> courseStages.remove(detail.code()));
        stage.show();
        courseStages.put(detail.code(), stage);
    }

    private Parent buildAuthView(String portalSubtitle, VBox card) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");

        HBox header = new HBox(16);
        header.setPadding(new Insets(32, 48, 10, 48));
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("brand-bar");

        ImageView logo = buildLogoView(48);
        VBox brandText = new VBox(4);
        Label title = new Label("BUET Intelligent Study System");
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label(portalSubtitle);
        subtitle.getStyleClass().add("brand-subtitle");
        brandText.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button homeButton = new Button("Home");
        homeButton.getStyleClass().add("outline-button");
        homeButton.setOnAction(event -> goHome());

        header.getChildren().addAll(logo, brandText, spacer, homeButton);

        VBox center = new VBox(card);
        center.setAlignment(Pos.TOP_CENTER);
        center.setPadding(new Insets(20, 48, 48, 48));

        ScrollPane scrollPane = new ScrollPane(center);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("main-scroll");

        root.setTop(header);
        root.setCenter(scrollPane);
        return root;
    }

    private VBox buildCampusGallery() {
        VBox gallery = new VBox();
        gallery.getStyleClass().add("gallery-shell");
        gallery.setPrefWidth(604);
        gallery.setMaxWidth(Region.USE_PREF_SIZE);
        gallery.setFillWidth(false);
        gallery.setAlignment(Pos.TOP_CENTER);

        GridPane collage = new GridPane();
        collage.getStyleClass().add("photo-grid");
        collage.setHgap(12);
        collage.setVgap(12);

        ColumnConstraints colOne = new ColumnConstraints();
        colOne.setPrefWidth(136);
        ColumnConstraints colTwo = new ColumnConstraints();
        colTwo.setPrefWidth(136);
        ColumnConstraints colThree = new ColumnConstraints();
        colThree.setPrefWidth(136);
        ColumnConstraints colFour = new ColumnConstraints();
        colFour.setPrefWidth(136);
        collage.getColumnConstraints().addAll(colOne, colTwo, colThree, colFour);

        collage.add(buildPhotoCard("buet-campus-2.png", 284, 182), 0, 0, 2, 1);
        collage.add(buildPhotoCard("home-campus-3.png", 284, 182), 2, 0, 2, 1);

        collage.add(buildPhotoCard("buet-campus-1.png", 136, 136), 0, 1);
        collage.add(buildPhotoCard("home-campus-2.png", 136, 136), 1, 1);
        collage.add(buildPhotoCard("home-campus-4.png", 136, 136), 2, 1);
        collage.add(buildPhotoCard("home-campus-1.png", 136, 136), 3, 1);

        collage.add(buildPhotoCard("home-campus-5.png", 284, 170), 0, 2, 2, 1);
        collage.add(buildPhotoCard("buet-campus-3.png", 284, 170), 2, 2, 2, 1);

        gallery.getChildren().add(collage);
        return gallery;
    }

    private ComboBox<String> buildComboBox(List<String> values, String defaultValue) {
        ComboBox<String> comboBox = new ComboBox<>(FXCollections.observableArrayList(values));
        comboBox.setMaxWidth(Double.MAX_VALUE);
        comboBox.setValue(defaultValue);
        return comboBox;
    }

    private void configureLandingActionButton(Button button) {
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(54);
        button.setWrapText(true);
    }

    private void goHome() {
        closeAllTransientWindows();
        state.setLoggedInStudent(null);
        state.setLoggedInTeacher(null);
        scene.setRoot(buildLandingView());
    }

    private String clean(String text) {
        return text == null ? "" : text.trim();
    }

    private boolean isValidEmail(String email) {
        int at = email.indexOf('@');
        int dot = email.lastIndexOf('.');
        return at > 0 && dot > at + 1 && dot < email.length() - 1;
    }

    private boolean isValidCgpa(String cgpa) {
        try {
            double value = Double.parseDouble(cgpa);
            return value >= 0.0 && value <= 4.0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private void openFacultyProfile(String instructorName) {
        FacultyProfile profile = state.findFacultyByName(instructorName);
        TeacherProfile teacherProfile = state.findTeacherByName(instructorName);
        Stage dialog = createEditorDialog("Teacher Profile");

        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(24));

        Label title = new Label("Teacher Profile");
        title.getStyleClass().add("card-title");
        Label note = new Label(
                "Public teacher information for course-related contact. Chat opens a private 1-to-1 conversation.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        if (profile == null) {
            Label missing = new Label("No teacher profile is available for this instructor.");
            missing.getStyleClass().add("form-message");

            Button chatButton = new Button("Chat");
            chatButton.getStyleClass().add("primary-button");
            chatButton.setDisable(true);
            chatButton.setTooltip(new Tooltip("This instructor does not have a public teacher profile yet."));

            Button closeButton = new Button("Close");
            closeButton.getStyleClass().add("outline-button");
            closeButton.setOnAction(event -> dialog.close());

            HBox actions = new HBox(10, chatButton, closeButton);
            actions.setAlignment(Pos.CENTER_LEFT);

            card.getChildren().addAll(title, note, missing, actions);
            setDialogContent(dialog, card, 520, 260);
            dialog.showAndWait();
            return;
        }

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(12);
        infoGrid.setVgap(12);
        infoGrid.add(buildFieldLabel("Name"), 0, 0);
        infoGrid.add(buildFieldValue(profile.name()), 1, 0);
        infoGrid.add(buildFieldLabel("Degree"), 0, 1);
        infoGrid.add(buildFieldValue(profile.degree()), 1, 1);
        infoGrid.add(buildFieldLabel("Department"), 0, 2);
        infoGrid.add(buildFieldValue(profile.department()), 1, 2);
        infoGrid.add(buildFieldLabel("Email"), 0, 3);
        infoGrid.add(buildFieldValue(profile.email()), 1, 3);

        VBox coursesBox = new VBox(8);
        Label coursesTitle = new Label("Courses");
        coursesTitle.getStyleClass().add("field-label");
        coursesBox.getChildren().add(coursesTitle);
        state.coursesByInstructor(instructorName).forEach(course -> {
            Label courseLabel = new Label(course.code() + " - " + course.title());
            courseLabel.getStyleClass().add("field-value");
            courseLabel.setWrapText(true);
            coursesBox.getChildren().add(courseLabel);
        });

        Button chatButton = new Button("Chat");
        chatButton.getStyleClass().add("primary-button");
        chatButton.setText(privateConversationButtonTextForTeacher(teacherProfile));
        chatButton.setDisable(teacherProfile == null);
        if (teacherProfile == null) {
            chatButton.setTooltip(new Tooltip("This teacher has not registered a chat account yet."));
        } else {
            chatButton.setOnAction(event -> {
                dialog.close();
                openTeacherStudentChatWindow(teacherProfile, state.profile());
            });
        }

        Button closeButton = new Button("Close");
        closeButton.getStyleClass().add("outline-button");
        closeButton.setOnAction(event -> dialog.close());

        HBox actions = new HBox(10, chatButton, closeButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(title, note, infoGrid, coursesBox, actions);
        setDialogContent(dialog, card, 560, 450);
        dialog.showAndWait();
    }

    private void openStudentProfileEditor() {
        StudentProfile profile = state.profile();
        Stage dialog = createEditorDialog("Edit Student Profile");

        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(24));

        Label title = new Label("Edit Student Profile");
        title.getStyleClass().add("card-title");
        Label note = new Label("Update personal details here. The student roll stays locked for this account.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        GridPane formGrid = new GridPane();
        formGrid.setHgap(12);
        formGrid.setVgap(12);

        TextField nameField = new TextField(profile.name());
        TextField rollField = buildReadOnlyField(profile.roll());
        ComboBox<String> departmentBox = buildComboBox(
                List.of("CSE", "EEE", "CE", "ME", "ChE", "IPE", "Arch", "URP"),
                profile.department());
        ComboBox<String> termBox = buildComboBox(
                List.of("Level 1 Term 1", "Level 1 Term 2", "Level 2 Term 1", "Level 2 Term 2", "Level 3 Term 1",
                        "Level 3 Term 2", "Level 4 Term 1", "Level 4 Term 2"),
                profile.term());
        TextField cgpaField = new TextField(profile.cgpa());
        TextField emailField = new TextField(profile.email());
        TextField phoneField = new TextField(profile.phone());

        formGrid.add(buildFieldLabel("Full name"), 0, 0);
        formGrid.add(nameField, 1, 0);
        formGrid.add(buildFieldLabel("Roll"), 0, 1);
        formGrid.add(rollField, 1, 1);
        formGrid.add(buildFieldLabel("Department"), 0, 2);
        formGrid.add(departmentBox, 1, 2);
        formGrid.add(buildFieldLabel("Term"), 0, 3);
        formGrid.add(termBox, 1, 3);
        formGrid.add(buildFieldLabel("CGPA"), 0, 4);
        formGrid.add(cgpaField, 1, 4);
        formGrid.add(buildFieldLabel("Email"), 0, 5);
        formGrid.add(emailField, 1, 5);
        formGrid.add(buildFieldLabel("Phone"), 0, 6);
        formGrid.add(phoneField, 1, 6);

        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(rollField, Priority.ALWAYS);
        GridPane.setHgrow(departmentBox, Priority.ALWAYS);
        GridPane.setHgrow(termBox, Priority.ALWAYS);
        GridPane.setHgrow(cgpaField, Priority.ALWAYS);
        GridPane.setHgrow(emailField, Priority.ALWAYS);
        GridPane.setHgrow(phoneField, Priority.ALWAYS);

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Button saveButton = new Button("Save Changes");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> {
            String name = clean(nameField.getText());
            String cgpa = clean(cgpaField.getText());
            String email = clean(emailField.getText());
            String phone = clean(phoneField.getText());

            if (name.isBlank() || cgpa.isBlank() || email.isBlank() || phone.isBlank()) {
                message.setText("Fill in all student profile fields.");
                return;
            }

            if (!isValidEmail(email)) {
                message.setText("Enter a valid student email address.");
                return;
            }

            if (!isValidCgpa(cgpa)) {
                message.setText("CGPA must be between 0.00 and 4.00.");
                return;
            }

            try {
                state.updateStudentProfile(
                        new StudentProfile(
                                name,
                                profile.roll(),
                                departmentBox.getValue(),
                                termBox.getValue(),
                                cgpa,
                                email,
                                phone));
                dialog.close();
                scene.setRoot(buildDashboardView());
            } catch (IllegalArgumentException ex) {
                message.setText(ex.getMessage());
            } catch (IllegalStateException ex) {
                message.setText("Could not save the student profile right now.");
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("outline-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(event -> dialog.close());

        HBox actions = new HBox(10, saveButton, cancelButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(title, note, formGrid, message, actions);
        setDialogContent(dialog, card, 520, 530);
        dialog.showAndWait();
    }

    private void openTeacherProfileEditor(TeacherWorkspace workspace) {
        TeacherProfile profile = workspace.profile();
        Stage dialog = createEditorDialog("Edit Teacher Profile");

        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(24));

        Label title = new Label("Edit Teacher Profile");
        title.getStyleClass().add("card-title");
        Label note = new Label(
                "Update faculty details here. The teacher registration number stays locked for this account.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        GridPane formGrid = new GridPane();
        formGrid.setHgap(12);
        formGrid.setVgap(12);

        TextField nameField = new TextField(profile.name());
        TextField idField = buildReadOnlyField(profile.id());
        ComboBox<String> departmentBox = buildComboBox(
                List.of("CSE", "EEE", "CE", "ME", "ChE", "IPE", "Arch", "URP"),
                profile.department());
        ComboBox<String> designationBox = buildComboBox(
                List.of("Lecturer", "Assistant Professor", "Associate Professor", "Professor"),
                profile.designation());
        TextField emailField = new TextField(profile.email());
        TextField officeField = new TextField(profile.officeRoom());
        TextField phoneField = new TextField(profile.phone());

        formGrid.add(buildFieldLabel("Full name"), 0, 0);
        formGrid.add(nameField, 1, 0);
        formGrid.add(buildFieldLabel("Teacher ID"), 0, 1);
        formGrid.add(idField, 1, 1);
        formGrid.add(buildFieldLabel("Department"), 0, 2);
        formGrid.add(departmentBox, 1, 2);
        formGrid.add(buildFieldLabel("Designation"), 0, 3);
        formGrid.add(designationBox, 1, 3);
        formGrid.add(buildFieldLabel("Email"), 0, 4);
        formGrid.add(emailField, 1, 4);
        formGrid.add(buildFieldLabel("Office"), 0, 5);
        formGrid.add(officeField, 1, 5);
        formGrid.add(buildFieldLabel("Phone"), 0, 6);
        formGrid.add(phoneField, 1, 6);

        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(idField, Priority.ALWAYS);
        GridPane.setHgrow(departmentBox, Priority.ALWAYS);
        GridPane.setHgrow(designationBox, Priority.ALWAYS);
        GridPane.setHgrow(emailField, Priority.ALWAYS);
        GridPane.setHgrow(officeField, Priority.ALWAYS);
        GridPane.setHgrow(phoneField, Priority.ALWAYS);

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Button saveButton = new Button("Save Changes");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> {
            String name = clean(nameField.getText());
            String email = clean(emailField.getText());
            String office = clean(officeField.getText());
            String phone = clean(phoneField.getText());

            if (name.isBlank() || email.isBlank() || office.isBlank() || phone.isBlank()) {
                message.setText("Fill in all teacher profile fields.");
                return;
            }

            if (!isValidEmail(email)) {
                message.setText("Enter a valid teacher email address.");
                return;
            }

            try {
                state.updateTeacherProfile(
                        new TeacherProfile(
                                profile.id(),
                                name,
                                departmentBox.getValue(),
                                designationBox.getValue(),
                                email,
                                office,
                                phone));
                dialog.close();
                scene.setRoot(buildTeacherDashboardView());
            } catch (IllegalArgumentException ex) {
                message.setText(ex.getMessage());
            } catch (IllegalStateException ex) {
                message.setText("Could not save the teacher profile right now.");
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("outline-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(event -> dialog.close());

        HBox actions = new HBox(10, saveButton, cancelButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(title, note, formGrid, message, actions);
        setDialogContent(dialog, card, 540, 530);
        dialog.showAndWait();
    }

    private VBox buildDetailTextSection(String titleText, String bodyText) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(16));
        box.getStyleClass().addAll("card", "detail-section");

        Label title = new Label(titleText);
        title.getStyleClass().add("section-title");
        Label body = new Label(bodyText);
        body.getStyleClass().add("muted");
        body.setWrapText(true);

        box.getChildren().addAll(title, body);
        return box;
    }

    private VBox buildDetailListSection(String titleText, String noteText, List<String> items, String emptyText) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(16));
        box.getStyleClass().addAll("card", "detail-section");

        Label title = new Label(titleText);
        title.getStyleClass().add("section-title");
        Label note = new Label(noteText);
        note.getStyleClass().add("muted");

        ListView<String> listView = new ListView<>();
        listView.getItems().setAll(items);
        listView.setPrefHeight(180);
        listView.setPlaceholder(new Label(emptyText));

        box.getChildren().addAll(title, note, listView);
        return box;
    }

    private void openStudentSubmissionDialog(StudentDeadlineTask task) {
        if (task == null || task.deadline() == null) {
            return;
        }

        AcademicDeadline deadline = task.deadline();
        AssignmentSubmission existing = task.submission();
        Stage dialog = createEditorDialog(existing == null ? "Submit Assignment" : "Update Submission");

        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(24));

        Label title = new Label(deadline.title());
        title.getStyleClass().add("card-title");
        Label note = new Label(
                deadline.courseCode() + " • " + deadline.type() + " • Due " + formatDeadlineDateTime(deadline.dueAtEpochMillis()));
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        Label detailsLabel = null;
        if (!clean(deadline.details()).isBlank()) {
            detailsLabel = new Label("Instructions: " + deadline.details());
            detailsLabel.getStyleClass().add("muted");
            detailsLabel.setWrapText(true);
        }

        TextArea submissionArea = new TextArea(existing == null ? "" : existing.submissionText());
        submissionArea.setPromptText("Write your submission, notes, or short answer here...");
        submissionArea.setWrapText(true);
        submissionArea.setPrefRowCount(10);

        TextField attachmentField = new TextField(existing == null ? "" : clean(existing.attachmentName()));
        attachmentField.setPromptText("Optional attachment");
        attachmentField.setEditable(false);

        final File[] selectedAttachment = new File[1];
        final String[] attachmentName = { existing == null ? "" : clean(existing.attachmentName()) };
        final String[] attachmentPath = { existing == null ? "" : clean(existing.attachmentPath()) };

        Button openFileButton = new Button("Open File");
        openFileButton.getStyleClass().add("outline-button");
        openFileButton.setDisable(existing == null || !existing.hasAttachment());
        openFileButton.setOnAction(event -> {
            if (selectedAttachment[0] != null) {
                openLocalFile(selectedAttachment[0].getAbsolutePath());
                return;
            }
            openLocalFile(attachmentPath[0]);
        });

        Button chooseFileButton = new Button("Choose File");
        chooseFileButton.getStyleClass().add("outline-button");
        chooseFileButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Assignment Attachment");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("All files", "*.*"),
                    new FileChooser.ExtensionFilter("PDF", "*.pdf"),
                    new FileChooser.ExtensionFilter("Documents", "*.doc", "*.docx", "*.txt"),
                    new FileChooser.ExtensionFilter("Slides", "*.ppt", "*.pptx"),
                    new FileChooser.ExtensionFilter("Source files", "*.java", "*.c", "*.cpp", "*.py"));
            File selected = chooser.showOpenDialog(scene.getWindow());
            if (selected == null) {
                return;
            }
            selectedAttachment[0] = selected;
            attachmentField.setText(selected.getName());
            openFileButton.setDisable(false);
        });

        Button clearFileButton = new Button("Clear");
        clearFileButton.getStyleClass().add("outline-button");
        clearFileButton.setOnAction(event -> {
            selectedAttachment[0] = null;
            attachmentName[0] = "";
            attachmentPath[0] = "";
            attachmentField.clear();
            openFileButton.setDisable(true);
        });

        HBox fileRow = new HBox(10, attachmentField, chooseFileButton, openFileButton, clearFileButton);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(attachmentField, Priority.ALWAYS);

        VBox reviewBox = new VBox(8);
        reviewBox.getStyleClass().addAll("card", "detail-section");

        Label reviewTitle = new Label("Teacher Review");
        reviewTitle.getStyleClass().add("section-title");
        String reviewText;
        if (existing == null) {
            reviewText = "No submission has been sent yet.";
        } else if (existing.isGraded()) {
            reviewText = "Grade: " + existing.gradeDisplay()
                    + " • Reviewed " + existing.gradedAtText()
                    + (clean(existing.gradedByName()).isBlank() ? "" : " by " + existing.gradedByName());
        } else {
            reviewText = "Submitted " + existing.submittedAtText() + ". Awaiting teacher review.";
        }
        Label reviewMeta = new Label(reviewText);
        reviewMeta.getStyleClass().add("muted");
        reviewMeta.setWrapText(true);

        TextArea feedbackArea = new TextArea(existing == null ? "" : clean(existing.feedback()));
        feedbackArea.setEditable(false);
        feedbackArea.setWrapText(true);
        feedbackArea.setPrefRowCount(4);
        feedbackArea.setPromptText("Teacher feedback will appear here.");

        reviewBox.getChildren().addAll(reviewTitle, reviewMeta, feedbackArea);

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Button saveButton = new Button(existing == null ? "Submit" : "Save Submission");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> {
            try {
                String finalAttachmentName = attachmentName[0];
                String finalAttachmentPath = attachmentPath[0];
                if (selectedAttachment[0] != null) {
                    finalAttachmentPath = storeStudentSubmissionFile(selectedAttachment[0], deadline, state.profile().roll());
                    finalAttachmentName = selectedAttachment[0].getName();
                }

                AssignmentSubmission saved = state.saveMySubmission(
                        deadline,
                        submissionArea.getText(),
                        finalAttachmentName,
                        finalAttachmentPath);
                task.setSubmission(saved);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                message.setText(ex.getMessage());
            } catch (IllegalStateException ex) {
                message.setText("Could not save the submission right now.");
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("outline-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(event -> dialog.close());

        HBox actions = new HBox(10, saveButton, cancelButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(title, note);
        if (detailsLabel != null) {
            card.getChildren().add(detailsLabel);
        }
        card.getChildren().addAll(submissionArea, fileRow, reviewBox, message, actions);
        setDialogContent(dialog, card, 760, 680);
        dialog.showAndWait();
    }

    private void openTeacherSubmissionReviewDialog(TeacherWorkspace workspace,
            AssignmentSubmission submission,
            Runnable afterSave) {
        if (submission == null) {
            return;
        }

        Stage dialog = createEditorDialog("Review Submission");

        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(24));

        Label title = new Label(submission.deadlineTitle());
        title.getStyleClass().add("card-title");
        Label note = new Label(
                submission.courseCode() + " • " + submission.deadlineType() + " • "
                        + submission.studentName() + " (" + submission.studentRoll() + ")");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        Label timing = new Label("Due " + formatDeadlineDateTime(submission.dueAtEpochMillis())
                + " • Submitted " + submission.submittedAtText()
                + (submission.isLate() ? " • Late" : ""));
        timing.getStyleClass().add("muted");
        timing.setWrapText(true);

        Label instructionsLabel = null;
        AcademicDeadline deadline = workspace.deadlines().stream()
                .filter(item -> item.id() == submission.deadlineId())
                .findFirst()
                .orElse(null);
        if (deadline != null && !clean(deadline.details()).isBlank()) {
            instructionsLabel = new Label("Instructions: " + deadline.details());
            instructionsLabel.getStyleClass().add("muted");
            instructionsLabel.setWrapText(true);
        }

        Label submissionLabel = new Label("Submission Text");
        submissionLabel.getStyleClass().add("section-title");

        TextArea submissionArea = new TextArea(clean(submission.submissionText()));
        submissionArea.setEditable(false);
        submissionArea.setWrapText(true);
        submissionArea.setPrefRowCount(10);
        submissionArea.setPromptText("This submission contains only an attachment.");

        Label attachmentLabel = new Label("Attachment");
        attachmentLabel.getStyleClass().add("section-title");

        TextField attachmentField = new TextField(clean(submission.attachmentName()));
        attachmentField.setEditable(false);
        attachmentField.setPromptText("No attachment provided");

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Button openFileButton = new Button("Open Attachment");
        openFileButton.getStyleClass().add("outline-button");
        openFileButton.setDisable(!submission.hasAttachment());
        openFileButton.setOnAction(event -> {
            if (submission.hasAttachment()) {
                openLocalFile(submission.attachmentPath());
            }
        });

        HBox attachmentRow = new HBox(10, attachmentField, openFileButton);
        attachmentRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(attachmentField, Priority.ALWAYS);

        GridPane gradingGrid = new GridPane();
        gradingGrid.setHgap(10);
        gradingGrid.setVgap(10);

        TextField gradeField = new TextField(clean(submission.grade()));
        gradeField.setPromptText("e.g. 18/20, A-, 95");

        TextArea feedbackField = new TextArea(clean(submission.feedback()));
        feedbackField.setPromptText("Write grading feedback for the student...");
        feedbackField.setWrapText(true);
        feedbackField.setPrefRowCount(5);

        gradingGrid.add(buildFieldLabel("Grade"), 0, 0);
        gradingGrid.add(gradeField, 1, 0);
        gradingGrid.add(buildFieldLabel("Feedback"), 0, 1);
        gradingGrid.add(feedbackField, 1, 1);
        GridPane.setHgrow(gradeField, Priority.ALWAYS);
        GridPane.setHgrow(feedbackField, Priority.ALWAYS);

        Button saveButton = new Button(submission.isGraded() ? "Update Grade" : "Save Grade");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> {
            try {
                state.gradeSubmission(submission.id(), gradeField.getText(), feedbackField.getText());
                if (afterSave != null) {
                    afterSave.run();
                }
                dialog.close();
            } catch (IllegalArgumentException ex) {
                message.setText(ex.getMessage());
            } catch (IllegalStateException ex) {
                message.setText("Could not save the grade right now.");
            }
        });

        Button cancelButton = new Button("Close");
        cancelButton.getStyleClass().add("outline-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(event -> dialog.close());

        HBox actions = new HBox(10, saveButton, cancelButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(title, note, timing);
        if (instructionsLabel != null) {
            card.getChildren().add(instructionsLabel);
        }
        card.getChildren().addAll(
                submissionLabel,
                submissionArea,
                attachmentLabel,
                attachmentRow,
                gradingGrid,
                message,
                actions);
        setDialogContent(dialog, card, 780, 720);
        dialog.showAndWait();
    }

    private void refreshStudentDeadlineTasks(ObservableList<StudentDeadlineTask> plannerItems) {
        java.util.Set<Long> completedIds = state.loadCompletedDeadlineIds();
        Map<Long, AssignmentSubmission> submissionsByDeadline = state.loadMySubmissions().stream()
                .collect(Collectors.toMap(
                        AssignmentSubmission::deadlineId,
                        submission -> submission,
                        (left, right) -> right,
                        LinkedHashMap::new));
        List<StudentDeadlineTask> refreshed = state.loadSelectedCourseDeadlines().stream()
                .sorted(Comparator.comparingLong(AcademicDeadline::dueAtEpochMillis))
                .map(deadline -> createStudentDeadlineTask(
                        deadline,
                        completedIds.contains(deadline.id()),
                        submissionsByDeadline.get(deadline.id())))
                .toList();
        plannerItems.setAll(refreshed);
    }

    private StudentDeadlineTask createStudentDeadlineTask(AcademicDeadline deadline,
            boolean completed,
            AssignmentSubmission submission) {
        StudentDeadlineTask task = new StudentDeadlineTask(deadline, completed, submission);
        task.completedProperty().addListener((obs, oldVal, newVal) -> {
            if (Objects.equals(oldVal, newVal)) {
                return;
            }
            state.setDeadlineCompleted(deadline.id(), newVal);
        });
        return task;
    }

    private void observeStudentDeadlineTasks(ObservableList<StudentDeadlineTask> plannerItems, Runnable onChange) {
        Map<StudentDeadlineTask, javafx.beans.value.ChangeListener<Boolean>> completionListeners = new HashMap<>();
        Map<StudentDeadlineTask, javafx.beans.value.ChangeListener<AssignmentSubmission>> submissionListeners = new HashMap<>();
        java.util.function.Consumer<StudentDeadlineTask> attachListener = task -> {
            javafx.beans.value.ChangeListener<Boolean> completionListener = (obs, oldVal, newVal) -> onChange.run();
            javafx.beans.value.ChangeListener<AssignmentSubmission> submissionListener = (obs, oldVal, newVal) -> onChange.run();
            task.completedProperty().addListener(completionListener);
            task.submissionProperty().addListener(submissionListener);
            completionListeners.put(task, completionListener);
            submissionListeners.put(task, submissionListener);
        };

        plannerItems.forEach(attachListener);
        plannerItems.addListener((ListChangeListener<StudentDeadlineTask>) change -> {
            while (change.next()) {
                if (change.wasRemoved()) {
                    change.getRemoved().forEach(task -> {
                        javafx.beans.value.ChangeListener<Boolean> completionListener = completionListeners.remove(task);
                        if (completionListener != null) {
                            task.completedProperty().removeListener(completionListener);
                        }
                        javafx.beans.value.ChangeListener<AssignmentSubmission> submissionListener = submissionListeners.remove(task);
                        if (submissionListener != null) {
                            task.submissionProperty().removeListener(submissionListener);
                        }
                    });
                }
                if (change.wasAdded()) {
                    change.getAddedSubList().forEach(attachListener);
                }
            }
            onChange.run();
        });
        onChange.run();
    }

    private void populateStudentDeadlineHighlights(ObservableList<StudentDeadlineTask> plannerItems,
            HBox metricRow,
            VBox upcomingBox,
            VBox overdueBox,
            int highlightLimit) {
        List<StudentDeadlineTask> completed = plannerItems.stream()
                .filter(StudentDeadlineTask::isCompleted)
                .toList();
        List<StudentDeadlineTask> allUpcoming = plannerItems.stream()
                .filter(task -> !task.isCompleted())
                .filter(task -> isUpcoming(task.deadline()))
                .sorted(Comparator.comparingLong(task -> task.deadline().dueAtEpochMillis()))
                .toList();
        List<StudentDeadlineTask> upcoming = allUpcoming.stream()
                .limit(highlightLimit)
                .toList();
        List<StudentDeadlineTask> allOverdue = plannerItems.stream()
                .filter(task -> !task.isCompleted())
                .filter(task -> isOverdue(task.deadline()))
                .sorted(Comparator.comparingLong(task -> task.deadline().dueAtEpochMillis()))
                .toList();
        List<StudentDeadlineTask> overdue = allOverdue.stream()
                .limit(highlightLimit)
                .toList();

        metricRow.getChildren().setAll(
                buildDeadlineMetricCard("Upcoming", String.valueOf(allUpcoming.size()), "deadline-metric-upcoming"),
                buildDeadlineMetricCard("Overdue", String.valueOf(allOverdue.size()), "deadline-metric-overdue"),
                buildDeadlineMetricCard("Completed", String.valueOf(completed.size()), "deadline-metric-completed"));

        upcomingBox.getChildren().clear();
        overdueBox.getChildren().clear();

        Label upcomingTitle = new Label("Upcoming Cards");
        upcomingTitle.getStyleClass().add("section-title");
        upcomingBox.getChildren().add(upcomingTitle);
        if (upcoming.isEmpty()) {
            Label empty = new Label(plannerItems.isEmpty()
                    ? "Enroll in courses to populate your planner."
                    : "No unfinished deadlines are due in the next 7 days.");
            empty.getStyleClass().add("muted");
            empty.setWrapText(true);
            upcomingBox.getChildren().add(empty);
        } else {
            upcoming.forEach(task -> upcomingBox.getChildren().add(buildDeadlineAlertCard(task, false)));
        }

        Label overdueTitle = new Label("Overdue Alerts");
        overdueTitle.getStyleClass().add("section-title");
        overdueBox.getChildren().add(overdueTitle);
        if (overdue.isEmpty()) {
            Label empty = new Label("No overdue deadlines.");
            empty.getStyleClass().add("muted");
            overdueBox.getChildren().add(empty);
        } else {
            overdue.forEach(task -> overdueBox.getChildren().add(buildDeadlineAlertCard(task, true)));
        }
    }

    private VBox buildDeadlineMetricCard(String labelText, String valueText, String accentClass) {
        VBox card = new VBox(4);
        card.getStyleClass().addAll("deadline-metric-card", accentClass);
        Label valueLabel = new Label(valueText);
        valueLabel.getStyleClass().add("deadline-metric-value");
        Label textLabel = new Label(labelText);
        textLabel.getStyleClass().add("deadline-metric-label");
        card.getChildren().addAll(valueLabel, textLabel);
        return card;
    }

    private VBox buildDeadlineAlertCard(StudentDeadlineTask task, boolean overdue) {
        VBox card = new VBox(5);
        card.getStyleClass().addAll("deadline-alert-card",
                overdue ? "deadline-alert-overdue" : "deadline-alert-upcoming");

        Label title = new Label(task.deadline().title());
        title.getStyleClass().add("deadline-alert-title");
        title.setWrapText(true);

        Label meta = new Label(task.deadline().courseCode() + " • " + task.deadline().type()
                + " • " + formatDeadlineDateTime(task.deadline().dueAtEpochMillis()));
        meta.getStyleClass().add("deadline-alert-meta");
        meta.setWrapText(true);

        Label reminder = new Label(reminderText(task));
        reminder.getStyleClass().add(overdue ? "form-message" : "muted");
        reminder.setWrapText(true);

        card.getChildren().addAll(title, meta, reminder);
        return card;
    }

    private void refreshStudentSubmissionSummary(ObservableList<StudentDeadlineTask> plannerItems, Label summaryLabel) {
        long submitted = plannerItems.stream()
                .filter(task -> task.submission() != null)
                .count();
        long graded = plannerItems.stream()
                .map(StudentDeadlineTask::submission)
                .filter(Objects::nonNull)
                .filter(AssignmentSubmission::isGraded)
                .count();
        long pending = plannerItems.stream()
                .map(StudentDeadlineTask::submission)
                .filter(Objects::nonNull)
                .filter(submission -> !submission.isGraded())
                .count();
        long missing = plannerItems.stream()
                .filter(task -> task.submission() == null)
                .filter(task -> isOverdue(task.deadline()))
                .count();

        summaryLabel.setText(plannerItems.isEmpty()
                ? "Enroll in courses to unlock the submission center."
                : submitted + " submitted, " + pending + " awaiting review, "
                        + graded + " graded, " + missing + " overdue and missing.");
    }

    private String studentSubmissionStatus(StudentDeadlineTask task) {
        AssignmentSubmission submission = task.submission();
        if (submission == null) {
            return isOverdue(task.deadline()) ? "Missing" : "Not Submitted";
        }
        return submission.submissionStatus();
    }

    private String studentSubmissionGrade(StudentDeadlineTask task) {
        AssignmentSubmission submission = task.submission();
        return submission == null ? "--" : submission.gradeDisplay();
    }

    private List<CalendarEntry> buildTeacherCalendarEntries(TeacherWorkspace workspace,
            LocalDate periodStart,
            LocalDate periodEnd) {
        List<CalendarRoutineSlot> routineSlots = workspace.routineRows().stream()
                .map(row -> {
                    String section = clean(row.sectionProperty().get());
                    String room = clean(row.roomProperty().get());
                    return new CalendarRoutineSlot(
                            parseRoutineDay(row.dayProperty().get()),
                            clean(row.timeProperty().get()),
                            clean(row.courseProperty().get()),
                            joinCalendarMeta(section.isBlank() ? "" : "Section " + section, room));
                })
                .filter(slot -> slot.dayOfWeek() != null)
                .toList();

        List<CalendarEntry> entries = new java.util.ArrayList<>(buildRoutineCalendarEntries(routineSlots, periodStart,
                periodEnd));
        entries.addAll(buildDeadlineCalendarEntries(workspace.deadlines(), periodStart, periodEnd));
        return entries;
    }

    private List<RoutineRow> buildStudentRoutineRowsForCourse(CourseSelection selection) {
        TeacherWorkspace workspace = state.findTeacherWorkspaceByInstructorName(selection.instructor());
        if (workspace == null) {
            return List.of();
        }

        return workspace.routineRows().stream()
                .filter(row -> routineMatchesCourse(row.courseProperty().get(), selection.code()))
                .map(row -> new RoutineRow(
                        clean(row.dayProperty().get()),
                        clean(row.timeProperty().get()),
                        formatStudentRoutineCourseLabel(row.courseProperty().get(), row.sectionProperty().get()),
                        clean(row.roomProperty().get())))
                .toList();
    }

    private List<CalendarEntry> buildStudentCalendarEntries(ObservableList<StudentDeadlineTask> plannerItems,
            LocalDate periodStart,
            LocalDate periodEnd) {
        List<CalendarRoutineSlot> routineSlots = studentRoutineRows().stream()
                .map(row -> new CalendarRoutineSlot(
                        parseRoutineDay(row.dayProperty().get()),
                        clean(row.timeProperty().get()),
                        clean(row.courseProperty().get()),
                        clean(row.roomProperty().get())))
                .filter(slot -> slot.dayOfWeek() != null)
                .toList();

        List<CalendarEntry> entries = new java.util.ArrayList<>(buildRoutineCalendarEntries(routineSlots, periodStart,
                periodEnd));
        entries.addAll(buildDeadlineCalendarEntries(
                plannerItems.stream().map(StudentDeadlineTask::deadline).toList(),
                periodStart,
                periodEnd));
        return entries;
    }

    private List<CalendarEntry> buildRoutineCalendarEntries(List<CalendarRoutineSlot> routineSlots,
            LocalDate periodStart,
            LocalDate periodEnd) {
        if (routineSlots == null || routineSlots.isEmpty()) {
            return List.of();
        }

        List<CalendarEntry> result = new java.util.ArrayList<>();
        for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            for (CalendarRoutineSlot slot : routineSlots) {
                if (slot.dayOfWeek() != dayOfWeek) {
                    continue;
                }
                result.add(new CalendarEntry(
                        date,
                        LocalDateTime.of(date, parseRoutineStartTime(slot.timeText())),
                        slot.timeText(),
                        slot.title(),
                        slot.subtitle(),
                        "Routine",
                        true,
                        false));
            }
        }
        return result;
    }

    private List<CalendarEntry> buildDeadlineCalendarEntries(List<AcademicDeadline> deadlines,
            LocalDate periodStart,
            LocalDate periodEnd) {
        if (deadlines == null || deadlines.isEmpty()) {
            return List.of();
        }

        return deadlines.stream()
                .filter(Objects::nonNull)
                .map(deadline -> {
                    LocalDateTime dueDateTime = deadline.dueDateTime();
                    String type = clean(deadline.type()).isBlank() ? "Deadline" : deadline.type();
                    return new CalendarEntry(
                            dueDateTime.toLocalDate(),
                            dueDateTime,
                            dueDateTime.format(DEADLINE_TIME_FORMATTER),
                            deadline.title(),
                            joinCalendarMeta(deadline.courseCode(), deadline.type()),
                            type,
                            false,
                            "Exam".equalsIgnoreCase(deadline.type()));
                })
                .filter(entry -> !entry.date().isBefore(periodStart) && !entry.date().isAfter(periodEnd))
                .toList();
    }

    private void renderCalendarGrid(VBox calendarHost,
            LocalDate anchorDate,
            LocalDate periodStart,
            List<CalendarEntry> entries,
            boolean weekView) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        for (int column = 0; column < 7; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / 7.0);
            constraints.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraints);
        }

        Map<LocalDate, List<CalendarEntry>> entriesByDate = entries.stream()
                .collect(Collectors.groupingBy(
                        CalendarEntry::date,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<DayOfWeek> calendarDays = List.of(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY);

        for (int column = 0; column < calendarDays.size(); column++) {
            VBox header = new VBox(2);
            header.getStyleClass().add("calendar-header-cell");

            Label dayLabel = new Label(shortDayName(calendarDays.get(column)));
            dayLabel.getStyleClass().add("calendar-header-day");
            header.getChildren().add(dayLabel);

            if (weekView) {
                Label dateLabel = new Label(periodStart.plusDays(column).format(CALENDAR_HEADER_DATE_FORMATTER));
                dateLabel.getStyleClass().add("calendar-header-date");
                header.getChildren().add(dateLabel);
            }

            grid.add(header, column, 0);
        }

        if (weekView) {
            for (int column = 0; column < 7; column++) {
                LocalDate date = periodStart.plusDays(column);
                grid.add(buildCalendarDayCell(date, entriesByDate.getOrDefault(date, List.of()), false, true), column,
                        1);
            }
        } else {
            LocalDate cursor = periodStart;
            for (int row = 1; row <= 6; row++) {
                for (int column = 0; column < 7; column++) {
                    boolean outsideMonth = !cursor.getMonth().equals(anchorDate.getMonth());
                    grid.add(buildCalendarDayCell(
                            cursor,
                            entriesByDate.getOrDefault(cursor, List.of()),
                            outsideMonth,
                            false), column, row);
                    cursor = cursor.plusDays(1);
                }
            }
        }

        calendarHost.getChildren().setAll(grid);
    }

    private VBox buildCalendarDayCell(LocalDate date,
            List<CalendarEntry> entries,
            boolean outsideMonth,
            boolean weekView) {
        VBox cell = new VBox(6);
        cell.getStyleClass().add("calendar-day-cell");
        cell.setFillWidth(true);
        cell.setMinHeight(weekView ? 220 : 150);
        if (outsideMonth) {
            cell.getStyleClass().add("calendar-day-outside");
        }
        if (date.equals(LocalDate.now())) {
            cell.getStyleClass().add("calendar-day-today");
        }

        Label dateLabel = new Label(weekView ? date.format(CALENDAR_HEADER_DATE_FORMATTER) : Integer.toString(date.getDayOfMonth()));
        dateLabel.getStyleClass().add("calendar-date-label");
        if (outsideMonth) {
            dateLabel.getStyleClass().add("calendar-date-muted");
        }
        cell.getChildren().add(dateLabel);

        if (entries.isEmpty()) {
            if (weekView) {
                Label empty = new Label("No scheduled items.");
                empty.getStyleClass().add("muted");
                empty.setWrapText(true);
                cell.getChildren().add(empty);
            }
            return cell;
        }

        int visibleLimit = weekView ? entries.size() : 4;
        entries.stream()
                .limit(visibleLimit)
                .map(this::buildCalendarEventCard)
                .forEach(cell.getChildren()::add);

        if (!weekView && entries.size() > visibleLimit) {
            Label moreLabel = new Label("+" + (entries.size() - visibleLimit) + " more");
            moreLabel.getStyleClass().add("calendar-more-label");
            cell.getChildren().add(moreLabel);
        }

        return cell;
    }

    private VBox buildCalendarEventCard(CalendarEntry entry) {
        VBox card = new VBox(3);
        card.getStyleClass().addAll("calendar-item", entry.styleClass());

        Label meta = new Label(entry.timeText() + " • " + entry.badgeText());
        meta.getStyleClass().add("calendar-item-time");
        meta.setWrapText(true);

        Label title = new Label(entry.title());
        title.getStyleClass().add("calendar-item-title");
        title.setWrapText(true);

        card.getChildren().addAll(meta, title);
        if (!clean(entry.subtitle()).isBlank()) {
            Label subtitle = new Label(entry.subtitle());
            subtitle.getStyleClass().add("calendar-item-subtitle");
            subtitle.setWrapText(true);
            card.getChildren().add(subtitle);
        }
        return card;
    }

    private List<String> buildCalendarAgendaLines(List<CalendarEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        return entries.stream()
                .map(entry -> entry.date().format(CALENDAR_AGENDA_DATE_FORMATTER)
                        + " • " + entry.timeText()
                        + " • " + entry.badgeText()
                        + " • " + entry.title()
                        + (clean(entry.subtitle()).isBlank() ? "" : " • " + entry.subtitle()))
                .toList();
    }

    private String buildCalendarSummary(List<CalendarEntry> entries, boolean weekView) {
        String periodText = weekView ? "week" : "month";
        if (entries == null || entries.isEmpty()) {
            return "No routine items or deadlines fall in this " + periodText + ".";
        }

        long routineCount = entries.stream().filter(CalendarEntry::routine).count();
        long examCount = entries.stream().filter(CalendarEntry::exam).count();
        long courseworkDeadlineCount = entries.stream()
                .filter(entry -> !entry.routine() && !entry.exam())
                .count();

        return entries.size() + " scheduled items in this " + periodText + ": "
                + routineCount + " routine class" + (routineCount == 1 ? "" : "es") + ", "
                + courseworkDeadlineCount + " coursework deadline" + (courseworkDeadlineCount == 1 ? "" : "s") + ", "
                + examCount + " exam" + (examCount == 1 ? "" : "s") + ".";
    }

    private String formatCalendarPeriodTitle(LocalDate anchorDate,
            LocalDate periodStart,
            LocalDate periodEnd,
            boolean weekView) {
        if (!weekView) {
            return anchorDate.format(CALENDAR_MONTH_FORMATTER);
        }
        return "Week of " + periodStart.format(CALENDAR_PERIOD_FORMATTER)
                + " to " + periodEnd.format(CALENDAR_PERIOD_FORMATTER);
    }

    private LocalDate shiftCalendarAnchor(LocalDate current, String viewMode, int direction) {
        if ("Month".equalsIgnoreCase(viewMode)) {
            return current.plusMonths(direction);
        }
        return current.plusWeeks(direction);
    }

    private LocalDate startOfCalendarWeek(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() % 7L);
    }

    private LocalDate startOfMonthGrid(LocalDate anchorDate) {
        return YearMonth.from(anchorDate)
                .atDay(1)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    private boolean matchesCalendarFilter(CalendarEntry entry, String filterValue) {
        String filter = clean(filterValue);
        if ("Routine".equalsIgnoreCase(filter)) {
            return entry.routine();
        }
        if ("Deadlines".equalsIgnoreCase(filter)) {
            return !entry.routine() && !entry.exam();
        }
        if ("Exams".equalsIgnoreCase(filter)) {
            return entry.exam();
        }
        return true;
    }

    private boolean routineMatchesCourse(String routineCourseText, String courseCode) {
        String normalizedCourseText = clean(routineCourseText).toUpperCase(Locale.ROOT);
        String normalizedCourseCode = clean(courseCode).toUpperCase(Locale.ROOT);
        return !normalizedCourseCode.isBlank()
                && (normalizedCourseText.startsWith(normalizedCourseCode)
                || normalizedCourseText.contains(normalizedCourseCode + " "));
    }

    private String formatStudentRoutineCourseLabel(String courseText, String section) {
        String normalizedCourse = clean(courseText);
        String normalizedSection = clean(section);
        if (normalizedSection.isBlank()) {
            return normalizedCourse;
        }
        return normalizedCourse + " [Sec " + normalizedSection + "]";
    }

    private int routineDaySortKey(String dayText) {
        return routineDaySortKey(parseRoutineDay(dayText));
    }

    private int routineDaySortKey(DayOfWeek dayOfWeek) {
        if (dayOfWeek == null) {
            return Integer.MAX_VALUE;
        }
        return switch (dayOfWeek) {
            case SUNDAY -> 0;
            case MONDAY -> 1;
            case TUESDAY -> 2;
            case WEDNESDAY -> 3;
            case THURSDAY -> 4;
            case FRIDAY -> 5;
            case SATURDAY -> 6;
        };
    }

    private String shortDayName(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case SUNDAY -> "Sun";
            case MONDAY -> "Mon";
            case TUESDAY -> "Tue";
            case WEDNESDAY -> "Wed";
            case THURSDAY -> "Thu";
            case FRIDAY -> "Fri";
            case SATURDAY -> "Sat";
        };
    }

    private DayOfWeek parseRoutineDay(String value) {
        String normalized = clean(value).toUpperCase(Locale.ROOT);
        if (normalized.startsWith("SUN")) {
            return DayOfWeek.SUNDAY;
        }
        if (normalized.startsWith("MON")) {
            return DayOfWeek.MONDAY;
        }
        if (normalized.startsWith("TUE")) {
            return DayOfWeek.TUESDAY;
        }
        if (normalized.startsWith("WED")) {
            return DayOfWeek.WEDNESDAY;
        }
        if (normalized.startsWith("THU")) {
            return DayOfWeek.THURSDAY;
        }
        if (normalized.startsWith("FRI")) {
            return DayOfWeek.FRIDAY;
        }
        if (normalized.startsWith("SAT")) {
            return DayOfWeek.SATURDAY;
        }
        return null;
    }

    private LocalTime parseRoutineStartTime(String value) {
        String normalized = clean(value);
        int separatorIndex = normalized.indexOf('-');
        if (separatorIndex > 0) {
            normalized = normalized.substring(0, separatorIndex);
        }
        try {
            return LocalTime.parse(normalized.trim());
        } catch (Exception ex) {
            return LocalTime.of(23, 59);
        }
    }

    private LocalTime parseRoutineEndTime(String value) {
        String normalized = clean(value);
        int separatorIndex = normalized.indexOf('-');
        if (separatorIndex >= 0 && separatorIndex + 1 < normalized.length()) {
            normalized = normalized.substring(separatorIndex + 1);
            try {
                LocalTime parsed = LocalTime.parse(normalized.trim());
                LocalTime startTime = parseRoutineStartTime(value);
                return parsed.isAfter(startTime) ? parsed : startTime.plusHours(1);
            } catch (Exception ex) {
                // Fall back to a default one-hour slot below.
            }
        }
        return parseRoutineStartTime(value).plusHours(1);
    }

    private Node buildRoutineTimetable(List<RoutineTimetableEntry> entries, String emptyText) {
        List<RoutineTimetableEntry> validEntries = entries == null ? List.of() : entries.stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.dayOfWeek() != null && entry.startTime() != null && entry.endTime() != null)
                .sorted(Comparator.comparingInt((RoutineTimetableEntry entry) -> routineDaySortKey(entry.dayOfWeek()))
                        .thenComparing(RoutineTimetableEntry::startTime)
                        .thenComparing(entry -> clean(entry.title())))
                .toList();

        if (validEntries.isEmpty()) {
            Label empty = new Label(emptyText);
            empty.getStyleClass().add("muted");
            empty.setWrapText(true);
            return empty;
        }

        List<DayOfWeek> days = validEntries.stream()
                .map(RoutineTimetableEntry::dayOfWeek)
                .distinct()
                .sorted(Comparator.comparingInt(this::routineDaySortKey))
                .toList();

        LocalTime earliestStart = validEntries.stream()
                .map(RoutineTimetableEntry::startTime)
                .min(LocalTime::compareTo)
                .orElse(LocalTime.of(8, 0));
        LocalTime latestEnd = validEntries.stream()
                .map(RoutineTimetableEntry::endTime)
                .max(LocalTime::compareTo)
                .orElse(earliestStart.plusHours(1));

        LocalTime firstPeriodStart = floorRoutinePeriod(earliestStart);
        LocalTime lastPeriodBoundary = ceilRoutinePeriod(latestEnd);
        if (!lastPeriodBoundary.isAfter(firstPeriodStart)) {
            lastPeriodBoundary = firstPeriodStart.plusHours(1);
        }

        List<LocalTime> periodStarts = new java.util.ArrayList<>();
        for (LocalTime cursor = firstPeriodStart; cursor.isBefore(lastPeriodBoundary); cursor = cursor.plusHours(1)) {
            periodStarts.add(cursor);
        }

        GridPane grid = new GridPane();
        grid.getStyleClass().add("routine-timetable");
        grid.setMinWidth(Region.USE_PREF_SIZE);

        ColumnConstraints dayColumn = new ColumnConstraints();
        dayColumn.setMinWidth(92);
        dayColumn.setPrefWidth(92);
        grid.getColumnConstraints().add(dayColumn);

        for (int columnIndex = 0; columnIndex < periodStarts.size(); columnIndex++) {
            ColumnConstraints periodColumn = new ColumnConstraints();
            periodColumn.setMinWidth(138);
            periodColumn.setPrefWidth(138);
            grid.getColumnConstraints().add(periodColumn);
        }

        Label cornerLabel = new Label("Day");
        cornerLabel.getStyleClass().add("routine-corner-label");
        StackPane cornerCell = new StackPane(cornerLabel);
        cornerCell.getStyleClass().add("routine-corner-cell");
        cornerCell.setMinHeight(56);
        grid.add(cornerCell, 0, 0);

        for (int columnIndex = 0; columnIndex < periodStarts.size(); columnIndex++) {
            Label headerLabel = new Label(routinePeriodLabel(periodStarts.get(columnIndex)));
            headerLabel.getStyleClass().add("routine-header-label");

            StackPane headerCell = new StackPane(headerLabel);
            headerCell.getStyleClass().add("routine-header-cell");
            headerCell.setMinHeight(56);
            grid.add(headerCell, columnIndex + 1, 0);
        }

        Map<DayOfWeek, Integer> dayRows = new HashMap<>();
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            DayOfWeek day = days.get(dayIndex);
            dayRows.put(day, dayIndex);

            Label dayLabel = new Label(shortDayName(day));
            dayLabel.getStyleClass().add("routine-day-label");

            StackPane dayCell = new StackPane(dayLabel);
            dayCell.getStyleClass().add("routine-day-cell");
            dayCell.setMinHeight(96);
            grid.add(dayCell, 0, dayIndex + 1);

            for (int columnIndex = 0; columnIndex < periodStarts.size(); columnIndex++) {
                StackPane slotCell = new StackPane();
                slotCell.getStyleClass().add("routine-slot-cell");
                slotCell.setMinSize(138, 96);
                grid.add(slotCell, columnIndex + 1, dayIndex + 1);
            }
        }

        for (RoutineTimetableEntry entry : validEntries) {
            Integer rowIndex = dayRows.get(entry.dayOfWeek());
            if (rowIndex == null) {
                continue;
            }

            int startColumn = Math.max(0,
                    (int) Duration.between(firstPeriodStart, floorRoutinePeriod(entry.startTime())).toHours());
            int spanColumns = Math.max(1,
                    (int) Duration.between(
                            floorRoutinePeriod(entry.startTime()),
                            ceilRoutinePeriod(entry.endTime()))
                            .toHours());

            if (startColumn >= periodStarts.size()) {
                continue;
            }

            spanColumns = Math.min(spanColumns, periodStarts.size() - startColumn);

            VBox classCard = buildRoutineClassCard(entry);
            GridPane.setHgrow(classCard, Priority.ALWAYS);
            GridPane.setMargin(classCard, new Insets(10, 6, 10, 6));
            grid.add(classCard, startColumn + 1, rowIndex + 1, spanColumns, 1);
        }

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.getStyleClass().add("routine-timetable-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefViewportHeight(Math.max(180, 92 + (days.size() * 96)));
        return scrollPane;
    }

    private VBox buildRoutineClassCard(RoutineTimetableEntry entry) {
        VBox card = new VBox(4);
        card.getStyleClass().add("routine-class-card");
        card.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label(clean(entry.title()));
        title.getStyleClass().add("routine-class-title");
        title.setWrapText(true);
        card.getChildren().add(title);

        String subtitleText = clean(entry.subtitle());
        if (!subtitleText.isBlank()) {
            Label subtitle = new Label(subtitleText);
            subtitle.getStyleClass().add("routine-class-subtitle");
            subtitle.setWrapText(true);
            card.getChildren().add(subtitle);
        }

        String timeText = clean(entry.timeText());
        if (!timeText.isBlank()) {
            Label timeLabel = new Label(timeText);
            timeLabel.getStyleClass().add("routine-class-time");
            card.getChildren().add(timeLabel);
        }

        String tooltipText = buildRoutineTooltipText(entry);
        if (!tooltipText.isBlank()) {
            Tooltip tooltip = new Tooltip(tooltipText);
            tooltip.setWrapText(true);
            tooltip.setMaxWidth(280);
            Tooltip.install(card, tooltip);
        }
        return card;
    }

    private String buildRoutineTooltipText(RoutineTimetableEntry entry) {
        List<String> lines = new java.util.ArrayList<>();
        String title = clean(entry.title());
        String subtitle = clean(entry.subtitle());
        String time = clean(entry.timeText());
        String day = entry.dayOfWeek() == null ? "" : shortDayName(entry.dayOfWeek());

        if (!title.isBlank()) {
            lines.add(title);
        }
        if (!subtitle.isBlank()) {
            lines.add(subtitle);
        }
        if (!day.isBlank() || !time.isBlank()) {
            lines.add(joinNonBlank(" | ", day, time));
        }
        return String.join(System.lineSeparator(), lines);
    }

    private String formatRoutineSubtitle(String section, String room) {
        String normalizedSection = clean(section);
        String sectionLabel = normalizedSection.isBlank() ? "" : "Sec " + normalizedSection;
        return joinNonBlank(" | ", sectionLabel, clean(room));
    }

    private String joinNonBlank(String delimiter, String... values) {
        return java.util.Arrays.stream(values)
                .map(this::clean)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(delimiter));
    }

    private LocalTime floorRoutinePeriod(LocalTime time) {
        LocalTime normalized = time == null ? LocalTime.of(8, 0) : time;
        return normalized.withMinute(0).withSecond(0).withNano(0);
    }

    private LocalTime ceilRoutinePeriod(LocalTime time) {
        LocalTime normalized = time == null ? LocalTime.of(9, 0) : time.withSecond(0).withNano(0);
        if (normalized.getMinute() == 0) {
            return normalized;
        }
        return normalized.plusHours(1).withMinute(0).withSecond(0).withNano(0);
    }

    private String routinePeriodLabel(LocalTime startTime) {
        LocalTime endTime = startTime.plusHours(1);
        return startTime.getHour() + "-" + endTime.getHour();
    }

    private String joinCalendarMeta(String... values) {
        return java.util.Arrays.stream(values)
                .map(this::clean)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" • "));
    }

    private List<AttendanceMarkRow> buildAttendanceMarkRows(String courseCode,
            String section,
            LocalDate selectedDate,
            List<StudentAttendanceRecord> teacherRecords) {
        if (courseCode == null || courseCode.isBlank() || selectedDate == null) {
            return List.of();
        }

        String normalizedSection = clean(section).toUpperCase(Locale.ROOT);
        String attendanceDate = selectedDate.toString();

        List<StudentAttendanceRecord> filtered = teacherRecords.stream()
                .filter(record -> record.courseCode().equalsIgnoreCase(courseCode))
                .filter(record -> normalizedSection.isBlank()
                        || clean(record.section()).equalsIgnoreCase(normalizedSection))
                .toList();

        Map<String, StudentAttendanceRecord> currentSessionByRoll = filtered.stream()
                .filter(record -> attendanceDate.equals(record.attendanceDate()))
                .collect(Collectors.toMap(
                        StudentAttendanceRecord::studentRoll,
                        record -> record,
                        (left, right) -> right,
                        LinkedHashMap::new));

        Map<String, List<StudentAttendanceRecord>> historyByRoll = filtered.stream()
                .filter(record -> !attendanceDate.equals(record.attendanceDate()))
                .collect(Collectors.groupingBy(
                        StudentAttendanceRecord::studentRoll,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return state.allStudents().stream()
                .sorted(Comparator.comparing(StudentProfile::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(StudentProfile::roll, String.CASE_INSENSITIVE_ORDER))
                .map(student -> {
                    List<StudentAttendanceRecord> history = historyByRoll.getOrDefault(student.roll(), List.of());
                    int attendedBefore = (int) history.stream().filter(StudentAttendanceRecord::present).count();
                    int totalBefore = history.size();
                    StudentAttendanceRecord currentRecord = currentSessionByRoll.get(student.roll());
                    boolean presentToday = currentRecord != null && currentRecord.present();
                    return new AttendanceMarkRow(student, presentToday, attendedBefore, totalBefore);
                })
                .toList();
    }

    private void attachAttendanceSummaryListeners(ObservableList<AttendanceMarkRow> studentRows,
            Label summary,
            ComboBox<Integer> thresholdBox) {
        studentRows.forEach(row -> row.presentProperty().addListener((obs, oldValue, newValue) ->
                refreshTeacherAttendanceSummary(summary, studentRows, thresholdBox.getValue())));
    }

    private void refreshTeacherAttendanceSummary(Label summary,
            ObservableList<AttendanceMarkRow> studentRows,
            Integer thresholdValue) {
        int threshold = thresholdValue == null ? 75 : thresholdValue;
        if (studentRows.isEmpty()) {
            summary.setText("No student roster is available for the selected course yet.");
            return;
        }

        long presentCount = studentRows.stream().filter(AttendanceMarkRow::isPresent).count();
        long atRiskCount = studentRows.stream()
                .filter(row -> row.previewPercentage() < threshold)
                .count();
        summary.setText(presentCount + " students marked present out of " + studentRows.size()
                + ". " + atRiskCount + " students are below the " + threshold + "% warning threshold.");
    }

    private List<AttendanceSessionSummary> buildAttendanceSessionSummaries(String courseCode,
            String section,
            List<StudentAttendanceRecord> teacherRecords) {
        if (courseCode == null || courseCode.isBlank()) {
            return List.of();
        }

        String normalizedSection = clean(section).toUpperCase(Locale.ROOT);
        List<StudentAttendanceRecord> filtered = teacherRecords.stream()
                .filter(record -> record.courseCode().equalsIgnoreCase(courseCode))
                .filter(record -> normalizedSection.isBlank()
                        || clean(record.section()).equalsIgnoreCase(normalizedSection))
                .sorted(Comparator.comparing(StudentAttendanceRecord::attendanceDate).reversed()
                        .thenComparing(StudentAttendanceRecord::section, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Map<String, List<StudentAttendanceRecord>> grouped = new LinkedHashMap<>();
        filtered.forEach(record -> {
            String key = record.attendanceDate() + "|" + clean(record.section()).toUpperCase(Locale.ROOT);
            grouped.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(record);
        });

        return grouped.values().stream()
                .map(records -> {
                    StudentAttendanceRecord sample = records.getFirst();
                    int presentCount = (int) records.stream().filter(StudentAttendanceRecord::present).count();
                    int totalCount = records.size();
                    return new AttendanceSessionSummary(
                            sample.attendanceDate(),
                            clean(sample.section()).isBlank() ? "-" : sample.section(),
                            presentCount,
                            totalCount);
                })
                .toList();
    }

    private List<AttendanceCourseSummary> buildAttendanceCourseSummaries(List<StudentAttendanceRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        Map<String, List<StudentAttendanceRecord>> byCourse = records.stream()
                .collect(Collectors.groupingBy(
                        StudentAttendanceRecord::courseCode,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return byCourse.entrySet().stream()
                .map(entry -> {
                    int total = entry.getValue().size();
                    int attended = (int) entry.getValue().stream().filter(StudentAttendanceRecord::present).count();
                    return new AttendanceCourseSummary(entry.getKey(), attended, total);
                })
                .sorted(Comparator.comparing(AttendanceCourseSummary::courseCode, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void refreshStudentAttendanceOverview(Label summary,
            List<AttendanceCourseSummary> summaries,
            List<StudentAttendanceRecord> allRecords,
            int threshold) {
        if (allRecords == null || allRecords.isEmpty()) {
            summary.setText("Attendance history will appear after teachers mark your classes.");
            return;
        }

        int totalClasses = allRecords.size();
        int attendedClasses = (int) allRecords.stream().filter(StudentAttendanceRecord::present).count();
        double overallPercentage = totalClasses == 0 ? 0.0 : (attendedClasses * 100.0) / totalClasses;
        long coursesBelowThreshold = summaries.stream()
                .filter(item -> item.percentage() < threshold)
                .count();

        summary.setText("Overall attendance: " + formatAttendancePercentage(overallPercentage)
                + " across " + totalClasses + " classes. "
                + coursesBelowThreshold + " course"
                + (coursesBelowThreshold == 1 ? "" : "s")
                + " are below the " + threshold + "% warning threshold.");
    }

    private void refreshStudentAttendanceHistory(ObservableList<StudentAttendanceRecord> historyItems,
            List<StudentAttendanceRecord> allRecords,
            AttendanceCourseSummary selectedCourse) {
        if (selectedCourse == null) {
            historyItems.clear();
            return;
        }

        historyItems.setAll(allRecords.stream()
                .filter(record -> record.courseCode().equalsIgnoreCase(selectedCourse.courseCode()))
                .sorted(Comparator.comparing(StudentAttendanceRecord::attendanceDate).reversed()
                        .thenComparing(StudentAttendanceRecord::section, String.CASE_INSENSITIVE_ORDER))
                .toList());
    }

    private String formatAttendancePercentage(double percentage) {
        return String.format(Locale.US, "%.1f%%", percentage);
    }

    private String attendanceWarningText(double percentage, Integer thresholdValue) {
        int threshold = thresholdValue == null ? 75 : thresholdValue;
        return percentage < threshold ? "Below " + threshold + "%" : "On Track";
    }

    private Label buildFieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private Label buildFieldValue(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-value");
        return label;
    }

    private TextField buildReadOnlyField(String value) {
        TextField field = new TextField(value);
        field.setEditable(false);
        field.setFocusTraversable(false);
        field.getStyleClass().add("readonly-field");
        return field;
    }

    private String inferResourceType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) {
            return "PPT";
        }
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "DOC";
        }
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) {
            return "Sheet";
        }
        return "Other";
    }

    private String storeTeacherResource(File sourceFile) {
        if (sourceFile == null || !sourceFile.isFile()) {
            throw new IllegalStateException("Choose a valid file to upload.");
        }

        try {
            Path uploadDirectory = Path.of(System.getProperty("user.dir"), "data", "uploads");
            Files.createDirectories(uploadDirectory);
            String storedName = System.currentTimeMillis() + "-" + sanitizeFileName(sourceFile.getName());
            Path targetPath = uploadDirectory.resolve(storedName);
            Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath.toAbsolutePath().toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not store the selected resource file.");
        }
    }

    private String storeStudentSubmissionFile(File sourceFile, AcademicDeadline deadline, String studentRoll) {
        if (sourceFile == null || !sourceFile.isFile()) {
            throw new IllegalStateException("Choose a valid file to submit.");
        }
        if (deadline == null || deadline.id() <= 0L) {
            throw new IllegalStateException("This deadline cannot accept attachments right now.");
        }

        try {
            Path uploadDirectory = Path.of(
                    System.getProperty("user.dir"),
                    "data",
                    "submissions",
                    String.valueOf(deadline.id()),
                    sanitizeFileName(studentRoll));
            Files.createDirectories(uploadDirectory);
            String storedName = System.currentTimeMillis() + "-" + sanitizeFileName(sourceFile.getName());
            Path targetPath = uploadDirectory.resolve(storedName);
            Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath.toAbsolutePath().toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not store the selected submission file.");
        }
    }

    private void openLocalFile(String path) {
        if (path == null || path.isBlank()) {
            return;
        }

        try {
            Path targetPath = Path.of(path);
            if (!Files.exists(targetPath)) {
                throw new IllegalStateException("The file is not available on disk.");
            }
            if (!java.awt.Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Desktop file opening is not supported on this system.");
            }
            java.awt.Desktop.getDesktop().open(targetPath.toFile());
        } catch (Exception ex) {
            Stage dialog = createEditorDialog("File Open Error");
            VBox card = new VBox(16);
            card.getStyleClass().add("card");
            card.setPadding(new Insets(24));

            Label title = new Label("Could not open file");
            title.getStyleClass().add("card-title");
            Label note = new Label(ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "The selected file could not be opened."
                    : ex.getMessage());
            note.getStyleClass().add("muted");
            note.setWrapText(true);

            Button closeButton = new Button("Close");
            closeButton.getStyleClass().add("primary-button");
            closeButton.setOnAction(event -> dialog.close());

            card.getChildren().addAll(title, note, closeButton);
            setDialogContent(dialog, card, 420, 220);
            dialog.showAndWait();
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private List<String> teacherCourseOptions(TeacherWorkspace workspace) {
        LinkedHashSet<String> courseCodes = new LinkedHashSet<>();
        state.coursesByInstructor(workspace.profile().name()).forEach(course -> courseCodes.add(course.code()));
        workspace.routineRows().forEach(row -> {
            String extracted = extractCourseCode(row.courseProperty().get());
            if (!extracted.isBlank()) {
                courseCodes.add(extracted);
            }
        });
        workspace.deadlines().forEach(deadline -> courseCodes.add(deadline.courseCode()));
        return courseCodes.stream().toList();
    }

    private String extractCourseCode(String courseText) {
        String normalized = clean(courseText);
        if (normalized.isBlank()) {
            return "";
        }
        int firstSpace = normalized.indexOf(' ');
        return (firstSpace > 0 ? normalized.substring(0, firstSpace) : normalized).toUpperCase(Locale.ROOT);
    }

    private LocalTime parseDeadlineTime(String value) {
        String normalized = clean(value);
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(normalized, DateTimeFormatter.ofPattern("H:mm"));
        } catch (Exception ex) {
            return null;
        }
    }

    private String formatDeadlineDateTime(long epochMillis) {
        LocalDateTime dueDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        return dueDateTime.format(DEADLINE_DATE_TIME_FORMATTER);
    }

    private boolean isOverdue(AcademicDeadline deadline) {
        return deadline != null && deadline.dueAtEpochMillis() < System.currentTimeMillis();
    }

    private boolean isUpcoming(AcademicDeadline deadline) {
        long now = System.currentTimeMillis();
        long dueAt = deadline.dueAtEpochMillis();
        return dueAt >= now && dueAt <= now + Duration.ofDays(7).toMillis();
    }

    private String reminderText(StudentDeadlineTask task) {
        if (task.isCompleted()) {
            return "Completed";
        }

        long remainingMillis = task.deadline().dueAtEpochMillis() - System.currentTimeMillis();
        if (remainingMillis < 0L) {
            return "Overdue by " + humanizeDuration(-remainingMillis);
        }
        if (remainingMillis <= Duration.ofHours(6).toMillis()) {
            return "Due in " + humanizeDuration(remainingMillis);
        }

        LocalDateTime dueDateTime = task.deadline().dueDateTime();
        LocalDate today = LocalDate.now();
        if (dueDateTime.toLocalDate().equals(today)) {
            return "Due today at " + dueDateTime.toLocalTime().format(DEADLINE_TIME_FORMATTER);
        }
        if (dueDateTime.toLocalDate().equals(today.plusDays(1))) {
            return "Due tomorrow at " + dueDateTime.toLocalTime().format(DEADLINE_TIME_FORMATTER);
        }
        if (remainingMillis <= Duration.ofDays(7).toMillis()) {
            return "Due in " + humanizeDuration(remainingMillis);
        }
        return "Scheduled";
    }

    private String humanizeDuration(long millis) {
        long days = millis / Duration.ofDays(1).toMillis();
        long hours = (millis % Duration.ofDays(1).toMillis()) / Duration.ofHours(1).toMillis();
        long minutes = Math.max(1L, (millis % Duration.ofHours(1).toMillis()) / Duration.ofMinutes(1).toMillis());

        if (days > 0) {
            return days + " day" + (days == 1 ? "" : "s") + (hours > 0 ? " " + hours + " hr" : "");
        }
        if (hours > 0) {
            return hours + " hr" + (hours == 1 ? "" : "s");
        }
        return minutes + " min";
    }

    private String studentDeadlineStatus(StudentDeadlineTask task) {
        if (task.isCompleted()) {
            return "Completed";
        }
        if (isOverdue(task.deadline())) {
            return "Overdue";
        }
        if (isUpcoming(task.deadline())) {
            return "Due Soon";
        }
        return "Scheduled";
    }

    private String teacherDeadlineStatus(AcademicDeadline deadline) {
        return isOverdue(deadline) ? "Overdue" : "Open";
    }

    private String formatCourseDeadlineLine(AcademicDeadline deadline) {
        return deadline.courseCode() + " • " + deadline.type() + " • "
                + deadline.title() + " • Due " + formatDeadlineDateTime(deadline.dueAtEpochMillis());
    }

    private String teacherSubmissionStatus(AssignmentSubmission submission) {
        if (submission == null) {
            return "";
        }
        if (submission.isGraded()) {
            return "Graded";
        }
        return submission.isLate() ? "Late - Awaiting Review" : "Awaiting Review";
    }

    private Stage createEditorDialog(String title) {
        Stage dialog = new Stage();
        dialog.initOwner(scene.getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(title);
        dialog.setResizable(false);
        return dialog;
    }

    private void setDialogContent(Stage dialog, VBox card, double width, double height) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setPadding(new Insets(18));

        ScrollPane scrollPane = new ScrollPane(card);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("main-scroll");

        root.setCenter(scrollPane);

        Scene dialogScene = new Scene(root, width, height);
        dialogScene.getStylesheets().add(stylesheetUrl);
        dialog.setScene(dialogScene);
    }

    private ImageView buildLogoView(double size) {
        Image image = new Image(Objects.requireNonNull(getClass().getResourceAsStream("buet-logo.png")));
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.getStyleClass().add("logo-image");
        return view;
    }

    private StackPane buildPhotoCard(String resourceName, double width, double height) {
        Image image = new Image(Objects.requireNonNull(getClass().getResourceAsStream(resourceName)));
        ImageView view = new ImageView(image);
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setPreserveRatio(false);

        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        view.setClip(clip);

        StackPane pane = new StackPane(view);
        pane.setPrefSize(width, height);
        pane.getStyleClass().add("photo-card");
        return pane;
    }

    private void sizeTableToRows(TableView<?> table) {
        double rowHeight = 42;
        table.setFixedCellSize(rowHeight);
        table.prefHeightProperty().bind(Bindings.size(table.getItems()).multiply(rowHeight).add(36));
        table.setMinHeight(Region.USE_PREF_SIZE);
    }

    private void openChatWindow() {
        ensureStudentCourseChatEnvironment();
        markAllCourseChatsRead();
        if (chatStage != null && chatStage.isShowing()) {
            chatStage.toFront();
            chatStage.requestFocus();
            return;
        }
        chatStage = new Chatview(state, chatService)
                .buildStage(scene.getWindow());
        chatStage.show();
    }

    private void ensureStudentCourseChatEnvironment() {
        StudentProfile studentProfile = state.profile();
        if (studentProfile == null || studentProfile.roll() == null || studentProfile.roll().isBlank()) {
            return;
        }

        String actorKey = studentProfile.roll().trim().toUpperCase() + "|" + studentProfile.name().trim();
        if (chatService == null || !chatService.isRunning() || !actorKey.equals(courseChatActorKey)) {
            closeCourseChatResources();
            chatService = new Chatservice(studentProfile.roll(), studentProfile.name(), state::saveChatMessage);
            chatService.addListener(courseChatNotificationListener);
            courseChatActorKey = actorKey;
            courseChatActorId = studentProfile.roll();
        }

        java.util.Set<String> enrolledCourseCodes = state.courseSelections().stream()
                .filter(CourseSelection::isSelected)
                .map(CourseSelection::code)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        courseChatUnreadCounts.keySet().retainAll(enrolledCourseCodes);
        enrolledCourseCodes.forEach(code -> {
            courseChatUnreadCounts.putIfAbsent(code, 0);
            chatService.joinCourse(code);
        });
    }

    private void onCourseChatNotification(ChatMessage message) {
        if (message == null || message.courseCode() == null || message.courseCode().startsWith("PRIVATE_CHAT_")) {
            return;
        }
        if (courseChatActorId != null && message.isSelf(courseChatActorId)) {
            return;
        }

        Platform.runLater(() -> {
            if (!courseChatUnreadCounts.containsKey(message.courseCode())) {
                return;
            }
            if (chatStage != null && chatStage.isShowing()) {
                return;
            }
            courseChatUnreadCounts.merge(message.courseCode(), 1, Integer::sum);
            updateCourseChatButton();
        });
    }

    private void markAllCourseChatsRead() {
        if (courseChatUnreadCounts.isEmpty()) {
            updateCourseChatButton();
            return;
        }
        courseChatUnreadCounts.replaceAll((code, count) -> 0);
        updateCourseChatButton();
    }

    private void updateCourseChatButton() {
        if (studentCourseChatButton == null) {
            refreshStudentNotificationCenter();
            return;
        }
        int unreadTotal = courseChatUnreadCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        studentCourseChatButton.setText(unreadTotal > 0
                ? "Course Chat (" + unreadTotal + ")"
                : "Course Chat");
        refreshStudentNotificationCenter();
    }

    private void ensureStudentPrivateChatEnvironment() {
        StudentProfile studentProfile = state.profile();
        if (studentProfile == null || studentProfile.roll() == null || studentProfile.roll().isBlank()) {
            return;
        }

        ensurePrivateChatService(studentProfile.roll(), studentProfile.name());
        state.allTeachers().forEach(teacherProfile -> joinPrivateConversation(teacherProfile, studentProfile));
    }

    private void ensureTeacherPrivateChatEnvironment(TeacherWorkspace workspace) {
        if (workspace == null || workspace.profile() == null || workspace.profile().id() == null
                || workspace.profile().id().isBlank()) {
            return;
        }

        ensurePrivateChatService(workspace.profile().id(), workspace.profile().name());
        state.allStudents().forEach(studentProfile -> joinPrivateConversation(workspace.profile(), studentProfile));
    }

    private void ensurePrivateChatService(String actorId, String actorName) {
        String actorKey = actorId.trim().toUpperCase() + "|" + actorName.trim();
        if (teacherChatService != null && teacherChatService.isRunning() && actorKey.equals(teacherChatActorKey)) {
            return;
        }

        closeTeacherChatResources();
        teacherChatService = new Chatservice(actorId, actorName, state::saveChatMessage);
        teacherChatService.addListener(privateChatNotificationListener);
        teacherChatActorKey = actorKey;
        teacherChatActorId = actorId;
    }

    private PrivateChatConversation joinPrivateConversation(TeacherProfile teacherProfile, StudentProfile studentProfile) {
        PrivateChatConversation conversation = ensurePrivateConversation(teacherProfile, studentProfile);
        if (teacherChatService != null) {
            teacherChatService.joinCourse(conversation.channelId());
        }
        return conversation;
    }

    private PrivateChatConversation ensurePrivateConversation(TeacherProfile teacherProfile, StudentProfile studentProfile) {
        String channelId = teacherStudentChatChannel(teacherProfile.id(), studentProfile.roll());
        PrivateChatConversation conversation = privateChatConversations.get(channelId);
        if (conversation == null) {
            conversation = new PrivateChatConversation(channelId, teacherProfile, studentProfile);
            conversation.updateLastMessage(loadLastPrivateChatMessage(channelId));
            privateChatConversations.put(channelId, conversation);
        } else {
            conversation.updateProfiles(teacherProfile, studentProfile);
        }
        return conversation;
    }

    private ChatMessage loadLastPrivateChatMessage(String channelId) {
        List<ChatMessage> messages = state.loadChatMessages(channelId, 1);
        return messages.isEmpty() ? null : messages.getFirst();
    }

    private void onPrivateChatNotification(ChatMessage message) {
        if (message == null || message.courseCode() == null || !message.courseCode().startsWith("PRIVATE_CHAT_")) {
            return;
        }

        PrivateChatConversation conversation = privateChatConversations.get(message.courseCode());
        if (conversation == null) {
            conversation = restorePrivateConversation(message.courseCode());
            if (conversation == null) {
                return;
            }
        }

        PrivateChatConversation finalConversation = conversation;
        Platform.runLater(() -> {
            finalConversation.updateLastMessage(message);
            boolean messageFromSelf = teacherChatActorId != null && message.isSelf(teacherChatActorId);
            Stage openStage = teacherChatStages.get(message.courseCode());
            boolean conversationOpen = openStage != null && openStage.isShowing();

            if (messageFromSelf || conversationOpen) {
                finalConversation.markRead();
            } else {
                finalConversation.incrementUnread();
            }

            updatePrivateChatButtons();
        });
    }

    private PrivateChatConversation restorePrivateConversation(String channelId) {
        String[] parsed = parsePrivateChatChannel(channelId);
        if (parsed == null) {
            return null;
        }

        TeacherProfile teacherProfile = state.findTeacherById(parsed[0]);
        StudentProfile studentProfile = state.findStudentByRoll(parsed[1]);
        if (teacherProfile == null || studentProfile == null) {
            return null;
        }

        return ensurePrivateConversation(teacherProfile, studentProfile);
    }

    private String[] parsePrivateChatChannel(String channelId) {
        String prefix = "PRIVATE_CHAT_";
        if (channelId == null || !channelId.startsWith(prefix)) {
            return null;
        }

        String raw = channelId.substring(prefix.length());
        int separator = raw.indexOf("__");
        if (separator <= 0 || separator >= raw.length() - 2) {
            return null;
        }

        return new String[] {
                raw.substring(0, separator),
                raw.substring(separator + 2)
        };
    }

    private void updatePrivateChatButtons() {
        int unreadTotal = privateChatConversations.values().stream()
                .mapToInt(PrivateChatConversation::unreadCount)
                .sum();

        if (teacherDashboardChatButton != null) {
            teacherDashboardChatButton.setText(formatPrivateChatButtonText("Chat", unreadTotal));
        }
        if (studentTeacherChatButton != null) {
            studentTeacherChatButton.setText(formatPrivateChatButtonText("Teacher Chat", unreadTotal));
        }
        refreshStudentNotificationCenter();
    }

    private String formatPrivateChatButtonText(String baseLabel, int unreadCount) {
        return unreadCount > 0 ? baseLabel + " (" + unreadCount + ")" : baseLabel;
    }

    private void markPrivateConversationRead(String channelId) {
        PrivateChatConversation conversation = privateChatConversations.get(channelId);
        if (conversation == null) {
            return;
        }
        conversation.markRead();
        updatePrivateChatButtons();
    }

    private List<PrivateChatConversation> sortedPrivateConversations() {
        boolean teacherView = state.loggedInTeacher() != null;
        return privateChatConversations.values().stream()
                .sorted(Comparator
                        .comparingLong(PrivateChatConversation::lastActivity).reversed()
                        .thenComparing(conversation -> teacherView
                                ? conversation.studentProfile().name()
                                : conversation.teacherProfile().name(),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void openTeacherConversationPicker(TeacherWorkspace workspace) {
        ensureTeacherPrivateChatEnvironment(workspace);
        Stage dialog = createEditorDialog("Choose Student Chat");

        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(24));

        Label title = new Label("Private Student Chats");
        title.getStyleClass().add("card-title");

        Label note = new Label("Latest conversations stay on top. Unread messages appear in the Chat button and beside each student.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        ObservableList<PrivateChatConversation> conversations = FXCollections.observableArrayList(sortedPrivateConversations());
        ListView<PrivateChatConversation> conversationList = new ListView<>(conversations);
        conversationList.setPrefHeight(320);
        conversationList.setPlaceholder(new Label("No registered students found."));
        conversationList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(PrivateChatConversation item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setGraphic(buildPrivateConversationCell(item, true));
            }
        });

        conversationList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                PrivateChatConversation selected = conversationList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    dialog.close();
                    openTeacherStudentChatWindow(selected.teacherProfile(), selected.studentProfile());
                }
            }
        });

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Button openButton = new Button("Open Chat");
        openButton.getStyleClass().add("primary-button");
        openButton.setOnAction(event -> {
            PrivateChatConversation selected = conversationList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                message.setText("Select a student first.");
                return;
            }
            dialog.close();
            openTeacherStudentChatWindow(selected.teacherProfile(), selected.studentProfile());
        });

        Button cancelButton = new Button("Close");
        cancelButton.getStyleClass().add("outline-button");
        cancelButton.setOnAction(event -> dialog.close());

        HBox actions = new HBox(10, openButton, cancelButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(title, note, conversationList, message, actions);
        setDialogContent(dialog, card, 680, 520);
        dialog.showAndWait();
    }

    private void openStudentTeacherConversationPicker() {
        ensureStudentPrivateChatEnvironment();
        Stage dialog = createEditorDialog("Teacher Chats");

        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(24));

        Label title = new Label("Private Teacher Chats");
        title.getStyleClass().add("card-title");

        Label note = new Label("Latest teacher conversations stay on top. Unread replies appear in the Teacher Chat button.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        ObservableList<PrivateChatConversation> conversations = FXCollections.observableArrayList(sortedPrivateConversations());
        ListView<PrivateChatConversation> conversationList = new ListView<>(conversations);
        conversationList.setPrefHeight(320);
        conversationList.setPlaceholder(new Label("No teacher chat accounts are available."));
        conversationList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(PrivateChatConversation item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setGraphic(buildPrivateConversationCell(item, false));
            }
        });

        conversationList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                PrivateChatConversation selected = conversationList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    dialog.close();
                    openTeacherStudentChatWindow(selected.teacherProfile(), selected.studentProfile());
                }
            }
        });

        Label message = new Label(" ");
        message.getStyleClass().add("form-message");

        Button openButton = new Button("Open Chat");
        openButton.getStyleClass().add("primary-button");
        openButton.setOnAction(event -> {
            PrivateChatConversation selected = conversationList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                message.setText("Select a teacher first.");
                return;
            }
            dialog.close();
            openTeacherStudentChatWindow(selected.teacherProfile(), selected.studentProfile());
        });

        Button cancelButton = new Button("Close");
        cancelButton.getStyleClass().add("outline-button");
        cancelButton.setOnAction(event -> dialog.close());

        HBox actions = new HBox(10, openButton, cancelButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(title, note, conversationList, message, actions);
        setDialogContent(dialog, card, 680, 520);
        dialog.showAndWait();
    }

    private Node buildPrivateConversationCell(PrivateChatConversation conversation, boolean teacherView) {
        VBox textBox = new VBox(4);

        String nameText = teacherView
                ? conversation.studentProfile().name() + " (" + conversation.studentProfile().roll() + ")"
                : conversation.teacherProfile().name();
        Label nameLabel = new Label(nameText);
        nameLabel.getStyleClass().add("field-label");

        String subtitleText = teacherView
                ? conversation.studentProfile().department() + " • " + conversation.studentProfile().term()
                : conversation.teacherProfile().department() + " • " + conversation.teacherProfile().designation();
        Label subtitleLabel = new Label(subtitleText);
        subtitleLabel.getStyleClass().add("muted");

        String previewText = conversation.lastMessage() == null
                ? "No messages yet"
                : trimPreview(conversation.lastMessage().isSelf(teacherChatActorId) ? "You: " + conversation.lastMessage().content()
                        : conversation.lastMessage().senderName() + ": " + conversation.lastMessage().content(), 54);
        Label previewLabel = new Label(previewText);
        previewLabel.getStyleClass().add("muted");

        textBox.getChildren().addAll(nameLabel, subtitleLabel, previewLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox metaBox = new VBox(6);
        metaBox.setAlignment(Pos.CENTER_RIGHT);

        Label timeLabel = new Label(formatConversationTime(conversation.lastActivity()));
        timeLabel.getStyleClass().add("muted");
        metaBox.getChildren().add(timeLabel);

        if (conversation.unreadCount() > 0) {
            Label unreadLabel = new Label(conversation.unreadCount() + " new");
            unreadLabel.setStyle("-fx-background-color:#ef4444;-fx-text-fill:white;"
                    + "-fx-font-size:11px;-fx-font-weight:bold;"
                    + "-fx-background-radius:10;-fx-padding:2 8 2 8;");
            metaBox.getChildren().add(unreadLabel);
        }

        HBox row = new HBox(12, textBox, spacer, metaBox);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 4, 8, 4));
        return row;
    }

    private String formatConversationTime(long timestamp) {
        if (timestamp <= 0) {
            return "No activity";
        }

        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        LocalDate today = LocalDate.now();
        if (dateTime.toLocalDate().equals(today)) {
            return dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("dd MMM"));
    }

    private String trimPreview(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 1) + "…";
    }

    private String privateConversationButtonTextForTeacher(TeacherProfile teacherProfile) {
        if (teacherProfile == null || state.loggedInTeacher() != null) {
            return "Chat";
        }

        PrivateChatConversation conversation = privateChatConversations.get(
                teacherStudentChatChannel(teacherProfile.id(), state.profile().roll()));
        if (conversation == null || conversation.unreadCount() <= 0) {
            return "Chat";
        }
        return "Chat (" + conversation.unreadCount() + ")";
    }

    private void openTeacherStudentChatWindow(TeacherProfile teacherProfile, StudentProfile studentProfile) {
        if (teacherProfile == null || studentProfile == null) {
            return;
        }

        String actorId;
        String actorName;
        TeacherWorkspace loggedTeacher = state.loggedInTeacher();
        if (loggedTeacher != null) {
            actorId = loggedTeacher.profile().id();
            actorName = loggedTeacher.profile().name();
        } else {
            actorId = state.profile().roll();
            actorName = state.profile().name();
        }

        ensurePrivateChatService(actorId, actorName);
        PrivateChatConversation conversation = joinPrivateConversation(teacherProfile, studentProfile);

        Stage existing = teacherChatStages.get(conversation.channelId());
        if (existing != null && existing.isShowing()) {
            markPrivateConversationRead(conversation.channelId());
            existing.toFront();
            existing.requestFocus();
            return;
        }

        boolean teacherSignedIn = loggedTeacher != null;
        String title = teacherSignedIn
                ? studentProfile.name() + " (" + studentProfile.roll() + ")"
                : teacherProfile.name();
        String subtitle = teacherSignedIn
                ? "Private chat with " + studentProfile.department() + " student " + studentProfile.roll()
                : "Private chat with " + teacherProfile.department() + " - " + teacherProfile.designation();

        markPrivateConversationRead(conversation.channelId());
        Stage stage = new TeacherChatView(
                state,
                teacherChatService,
                conversation.channelId(),
                title,
                subtitle,
                actorId,
                actorName)
                        .buildStage(scene.getWindow());
        stage.setOnHidden(event -> teacherChatStages.remove(conversation.channelId()));
        teacherChatStages.put(conversation.channelId(), stage);
        stage.show();
    }

    private String teacherStudentChatChannel(String teacherId, String studentRoll) {
        return "PRIVATE_CHAT_" + teacherId.trim().toUpperCase() + "__" + studentRoll.trim().toUpperCase();
    }

    private void closeAllTransientWindows() {
        closeCourseChatResources();
        closeTeacherChatResources();
        closeStudySpaceWindow();
    }

    private void closeCourseChatResources() {
        if (chatService != null) {
            chatService.removeListener(courseChatNotificationListener);
            chatService.close();
            chatService = null;
        }
        if (chatStage != null && chatStage.isShowing()) {
            chatStage.close();
        }
        chatStage = null;
        courseChatActorKey = null;
        courseChatActorId = null;
        courseChatUnreadCounts.clear();
    }

    private void closeTeacherChatResources() {
        new java.util.ArrayList<>(teacherChatStages.values()).forEach(stage -> {
            if (stage != null && stage.isShowing()) {
                stage.close();
            }
        });
        teacherChatStages.clear();

        if (teacherChatService != null) {
            teacherChatService.removeListener(privateChatNotificationListener);
            teacherChatService.close();
            teacherChatService = null;
        }
        teacherChatActorKey = null;
        teacherChatActorId = null;
        privateChatConversations.clear();
    }

    private void closeStudySpaceWindow() {
        if (studyStage != null && studyStage.isShowing()) {
            studyStage.close();
        }
        studyStage = null;
    }

    private void openStudySpace() {
        if (studyStage != null && studyStage.isShowing()) {
            studyStage.toFront();
            studyStage.requestFocus();
            return;
        }
        ensureStudentCourseChatEnvironment();
        studyStage = new StudySpaceView(state, stylesheetUrl, this::sendStudySpaceMessageToCourseChat)
                .buildStage(scene.getWindow());
        studyStage.show();
    }

    private void sendStudySpaceMessageToCourseChat(String courseCode, String content) {
        if (courseCode == null || courseCode.isBlank() || content == null || content.isBlank()) {
            return;
        }
        ensureStudentCourseChatEnvironment();
        if (chatService == null) {
            return;
        }
        chatService.joinCourse(courseCode);
        chatService.send(courseCode, content);
    }

    private static final class PrivateChatConversation {
        private final String channelId;
        private TeacherProfile teacherProfile;
        private StudentProfile studentProfile;
        private ChatMessage lastMessage;
        private long lastActivity;
        private int unreadCount;

        private PrivateChatConversation(String channelId, TeacherProfile teacherProfile, StudentProfile studentProfile) {
            this.channelId = channelId;
            this.teacherProfile = teacherProfile;
            this.studentProfile = studentProfile;
        }

        private String channelId() {
            return channelId;
        }

        private TeacherProfile teacherProfile() {
            return teacherProfile;
        }

        private StudentProfile studentProfile() {
            return studentProfile;
        }

        private ChatMessage lastMessage() {
            return lastMessage;
        }

        private long lastActivity() {
            return lastActivity;
        }

        private int unreadCount() {
            return unreadCount;
        }

        private void updateProfiles(TeacherProfile teacherProfile, StudentProfile studentProfile) {
            this.teacherProfile = teacherProfile;
            this.studentProfile = studentProfile;
        }

        private void updateLastMessage(ChatMessage lastMessage) {
            this.lastMessage = lastMessage;
            this.lastActivity = lastMessage == null ? 0L : lastMessage.timestamp();
        }

        private void incrementUnread() {
            unreadCount++;
        }

        private void markRead() {
            unreadCount = 0;
        }

        private String counterpartName() {
            if (studentProfile != null) {
                return studentProfile.name();
            }
            return teacherProfile != null ? teacherProfile.name() : "";
        }
    }

    static final class AttendanceMarkRow {
        private final StudentProfile student;
        private final javafx.beans.property.SimpleBooleanProperty present = new javafx.beans.property.SimpleBooleanProperty();
        private final int attendedBefore;
        private final int totalBefore;

        AttendanceMarkRow(StudentProfile student, boolean present, int attendedBefore, int totalBefore) {
            this.student = student;
            this.present.set(present);
            this.attendedBefore = attendedBefore;
            this.totalBefore = totalBefore;
        }

        StudentProfile student() {
            return student;
        }

        javafx.beans.property.SimpleBooleanProperty presentProperty() {
            return present;
        }

        boolean isPresent() {
            return present.get();
        }

        int previewAttended() {
            return attendedBefore + (isPresent() ? 1 : 0);
        }

        int previewTotal() {
            return totalBefore + 1;
        }

        double previewPercentage() {
            return previewTotal() == 0 ? 0.0 : (previewAttended() * 100.0) / previewTotal();
        }
    }

    record AttendanceSessionSummary(String attendanceDate, String section, int presentCount, int totalCount) {
        double percentage() {
            return totalCount == 0 ? 0.0 : (presentCount * 100.0) / totalCount;
        }
    }

    record AttendanceCourseSummary(String courseCode, int attendedCount, int totalCount) {
        double percentage() {
            return totalCount == 0 ? 0.0 : (attendedCount * 100.0) / totalCount;
        }
    }

    record StudentDashboardNotification(String category,
            String title,
            String detail,
            String itemStyleClass,
            String badgeStyleClass,
            String actionLabel,
            Runnable action) {
    }

    record RoutineTimetableEntry(DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String title,
            String subtitle,
            String timeText) {
    }

    record CalendarRoutineSlot(DayOfWeek dayOfWeek, String timeText, String title, String subtitle) {
    }

    record CalendarEntry(LocalDate date,
            LocalDateTime sortDateTime,
            String timeText,
            String title,
            String subtitle,
            String badgeText,
            boolean routine,
            boolean exam) {
        String styleClass() {
            if (exam) {
                return "calendar-item-exam";
            }
            return routine ? "calendar-item-routine" : "calendar-item-deadline";
        }
    }

    static final class StudentDeadlineTask {
        private final AcademicDeadline deadline;
        private final javafx.beans.property.SimpleBooleanProperty completed = new javafx.beans.property.SimpleBooleanProperty();
        private final javafx.beans.property.SimpleObjectProperty<AssignmentSubmission> submission =
                new javafx.beans.property.SimpleObjectProperty<>();

        StudentDeadlineTask(AcademicDeadline deadline, boolean completed, AssignmentSubmission submission) {
            this.deadline = deadline;
            this.completed.set(completed);
            this.submission.set(submission);
        }

        AcademicDeadline deadline() {
            return deadline;
        }

        javafx.beans.property.SimpleBooleanProperty completedProperty() {
            return completed;
        }

        boolean isCompleted() {
            return completed.get();
        }

        javafx.beans.property.SimpleObjectProperty<AssignmentSubmission> submissionProperty() {
            return submission;
        }

        AssignmentSubmission submission() {
            return submission.get();
        }

        void setSubmission(AssignmentSubmission submission) {
            this.submission.set(submission);
        }
    }

}
