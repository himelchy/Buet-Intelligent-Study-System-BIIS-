package com.example;

import java.util.HashMap;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public final class AppState {
    private final DatabaseService databaseService;
    private final StudentProfile defaultStudentProfile;
    private final ObservableList<CourseSelection> courseSelections;
    private final ObservableList<String> authorityNotices;
    private final Map<String, TeacherWorkspace> teacherWorkspacesById = new HashMap<>();
    private StudentProfile loggedInStudent;
    private TeacherWorkspace loggedInTeacher;

    public AppState(DatabaseService databaseService,
            StudentProfile defaultStudentProfile,
            ObservableList<CourseSelection> courseSelections,
            ObservableList<TeacherWorkspace> teacherWorkspaceTemplates,
            ObservableList<String> authorityNotices) {
        this.databaseService = databaseService;
        this.defaultStudentProfile = defaultStudentProfile;
        this.courseSelections = courseSelections;
        this.authorityNotices = authorityNotices;

        teacherWorkspaceTemplates.forEach(workspace -> {
            ObservableList<AcademicDeadline> deadlines = FXCollections.observableArrayList(
                    databaseService.loadDeadlinesByTeacher(workspace.profile().id()));
            teacherWorkspacesById.put(workspace.profile().id().toUpperCase(),
                    new TeacherWorkspace(
                            workspace.profile(),
                            workspace.routineRows(),
                            workspace.attendanceEntries(),
                            workspace.uploadedResources(),
                            deadlines));
        });

        this.loggedInStudent = defaultStudentProfile;
        this.loggedInTeacher = null;
    }

    public StudentProfile profile() {
        if (loggedInStudent != null) {
            return loggedInStudent;
        }

        if (defaultStudentProfile != null) {
            return defaultStudentProfile;
        }

        return new StudentProfile("No Student", "N/A", "N/A", "N/A", "N/A", "N/A", "N/A");
    }

    public ObservableList<CourseSelection> courseSelections() {
        return courseSelections;
    }

    public ObservableList<String> authorityNotices() {
        return authorityNotices;
    }

    public String loggedInRoll() {
        return profile().roll();
    }

    public StudentProfile loggedInStudent() {
        return loggedInStudent;
    }

    public void setLoggedInStudent(StudentProfile loggedInStudent) {
        this.loggedInStudent = loggedInStudent;
    }

    public TeacherWorkspace loggedInTeacher() {
        return loggedInTeacher;
    }

    public void setLoggedInTeacher(TeacherWorkspace loggedInTeacher) {
        this.loggedInTeacher = loggedInTeacher;
    }

    public StudentProfile findStudentByRoll(String roll) {
        return databaseService.findStudentByRoll(roll);
    }

    public java.util.List<StudentProfile> allStudents() {
        return databaseService.loadAllStudents();
    }

    public TeacherProfile findTeacherById(String teacherId) {
        return databaseService.findTeacherById(teacherId);
    }

    public java.util.List<TeacherProfile> allTeachers() {
        return databaseService.loadAllTeachers();
    }

    public TeacherProfile findTeacherByName(String teacherName) {
        return databaseService.findTeacherByName(teacherName);
    }

    public TeacherWorkspace findTeacherWorkspaceByInstructorName(String instructorName) {
        TeacherProfile teacher = databaseService.findTeacherByName(instructorName);
        if (teacher == null) {
            return null;
        }
        return resolveTeacherWorkspace(teacher);
    }

    public FacultyProfile findFacultyByName(String name) {
        return databaseService.findFacultyByName(name);
    }

    public StudentProfile authenticateStudent(String roll, String password) {
        StudentProfile profile = databaseService.authenticateStudent(roll, password);
        if (profile != null) {
            loggedInStudent = profile;
        }
        return profile;
    }

    public TeacherWorkspace authenticateTeacher(String teacherId, String password) {
        TeacherProfile teacherProfile = databaseService.authenticateTeacher(teacherId, password);
        if (teacherProfile == null) {
            return null;
        }

        TeacherWorkspace workspace = resolveTeacherWorkspace(teacherProfile);
        loggedInTeacher = workspace;
        return workspace;
    }

    public StudentProfile registerStudent(StudentProfile profile, String password) {
        StudentProfile created = databaseService.registerStudent(profile, password);
        loggedInStudent = created;
        return created;
    }

    public StudentProfile updateStudentProfile(StudentProfile profile) {
        StudentProfile updated = databaseService.updateStudentProfile(profile);
        loggedInStudent = updated;
        return updated;
    }

    public TeacherWorkspace registerTeacher(TeacherProfile profile, String password) {
        TeacherProfile created = databaseService.registerTeacher(profile, password);
        TeacherWorkspace workspace = resolveTeacherWorkspace(created);
        loggedInTeacher = workspace;
        return workspace;
    }

    public TeacherWorkspace updateTeacherProfile(TeacherProfile profile) {
        TeacherProfile updated = databaseService.updateTeacherProfile(profile);
        TeacherWorkspace workspace = resolveTeacherWorkspace(updated);
        loggedInTeacher = workspace;
        return workspace;
    }

    public java.util.List<CourseDetail> coursesByInstructor(String instructorName) {
        return courseSelections.stream()
                .map(CourseSelection::detail)
                .filter(detail -> detail.instructor().equalsIgnoreCase(instructorName))
                .toList();
    }

    // ── Study Notes ──────────────────────────────────────────────────────

    public StudyNote saveNote(StudyNote note) {
        StudyNote saved = databaseService.saveNote(note);
        databaseService.setNotePinned(loggedInStudent().roll(), saved.id(), note.isPinned());
        return new StudyNote(
                saved.id(),
                saved.ownerRoll(),
                saved.courseCode(),
                saved.title(),
                saved.content(),
                saved.isPublic(),
                note.isPinned(),
                saved.createdAt(),
                saved.updatedAt());
    }

    public void deleteNote(long noteId) {
        databaseService.deleteNote(noteId, loggedInStudent().roll());
    }

    public java.util.List<StudyNote> loadMyNotes(String courseCode) {
        return databaseService.loadMyNotes(loggedInStudent().roll(), courseCode);
    }

    public java.util.List<StudyNote> loadSharedNotes(String courseCode) {
        return databaseService.loadSharedNotes(loggedInStudent().roll(), courseCode);
    }

    public void setNotePinned(long noteId, boolean pinned) {
        databaseService.setNotePinned(loggedInStudent().roll(), noteId, pinned);
    }

    // ── Study Books ──────────────────────────────────────────────────────

    public long addBook(String title, String sourceType, String sourcePath, String courseCode) {
        return databaseService.addBook(loggedInStudent().roll(), title, sourceType, sourcePath, courseCode);
    }

    public void updateBookProgress(long bookId, int lastPage, String theme) {
        databaseService.updateBookProgress(bookId, lastPage, theme);
    }

    public void deleteBook(long bookId) {
        databaseService.deleteBook(bookId, loggedInStudent().roll());
    }

    public java.util.List<DatabaseService.BookEntry> loadBookshelf() {
        return databaseService.loadBookshelfFull(loggedInStudent().roll());
    }

    public java.util.List<DatabaseService.BookBookmark> loadBookBookmarks(long bookId) {
        return databaseService.loadBookBookmarks(loggedInStudent().roll(), bookId);
    }

    public DatabaseService.BookBookmark saveBookBookmark(long bookId, int pageIndex, String label) {
        return databaseService.saveBookBookmark(loggedInStudent().roll(), bookId, pageIndex, label);
    }

    public void deleteBookBookmark(long bookmarkId) {
        databaseService.deleteBookBookmark(loggedInStudent().roll(), bookmarkId);
    }

    /**
     * Returns teacher-uploaded resources visible to students (visibility =
     * "Student").
     */
    public java.util.List<UploadedResource> teacherResources() {
        return teacherWorkspacesById.values().stream()
                .flatMap(ws -> ws.uploadedResources().stream())
                .filter(r -> "Student".equalsIgnoreCase(r.visibilityProperty().get()))
                .toList();
    }

    // ── Chat Messages ────────────────────────────────────────────────────

    public void saveChatMessage(ChatMessage message) {
        databaseService.saveChatMessage(message);
    }

    public java.util.List<ChatMessage> loadChatMessages(String courseCode, int limit) {
        return databaseService.loadChatMessages(courseCode, limit);
    }

    public AcademicDeadline saveDeadline(AcademicDeadline deadline) {
        return databaseService.saveDeadline(deadline);
    }

    public void deleteDeadline(long deadlineId, String teacherId) {
        databaseService.deleteDeadline(deadlineId, teacherId);
    }

    public java.util.List<AcademicDeadline> loadDeadlinesForTeacher(String teacherId) {
        return databaseService.loadDeadlinesByTeacher(teacherId);
    }

    public java.util.List<AcademicDeadline> loadCourseDeadlines(String courseCode) {
        return databaseService.loadDeadlinesForCourse(courseCode);
    }

    public java.util.List<AcademicDeadline> loadSelectedCourseDeadlines() {
        return databaseService.loadDeadlinesForCourses(
                courseSelections.stream()
                        .filter(CourseSelection::isSelected)
                        .map(CourseSelection::code)
                        .toList());
    }

    public java.util.Set<Long> loadCompletedDeadlineIds() {
        StudentProfile student = loggedInStudent();
        if (student == null) {
            return java.util.Set.of();
        }
        return databaseService.loadCompletedDeadlineIds(student.roll());
    }

    public void setDeadlineCompleted(long deadlineId, boolean completed) {
        StudentProfile student = loggedInStudent();
        if (student == null) {
            return;
        }
        databaseService.setDeadlineCompleted(student.roll(), deadlineId, completed);
    }

    public void saveAttendanceRecords(java.util.List<StudentAttendanceRecord> records) {
        databaseService.saveAttendanceRecords(records);
    }

    public java.util.List<StudentAttendanceRecord> loadTeacherAttendanceRecords(String teacherId) {
        return databaseService.loadAttendanceRecordsForTeacher(teacherId);
    }

    public java.util.List<StudentAttendanceRecord> loadCurrentTeacherAttendanceRecords() {
        TeacherWorkspace teacher = loggedInTeacher();
        if (teacher == null) {
            return java.util.List.of();
        }
        return databaseService.loadAttendanceRecordsForTeacher(teacher.profile().id());
    }

    public java.util.List<StudentAttendanceRecord> loadStudentAttendanceRecords(String studentRoll) {
        return databaseService.loadAttendanceRecordsForStudent(studentRoll);
    }

    public java.util.List<StudentAttendanceRecord> loadMyAttendanceRecords() {
        StudentProfile student = loggedInStudent();
        if (student == null) {
            return java.util.List.of();
        }
        return databaseService.loadAttendanceRecordsForStudent(student.roll());
    }

    public AssignmentSubmission loadMySubmission(long deadlineId) {
        StudentProfile student = loggedInStudent();
        if (student == null) {
            return null;
        }
        return databaseService.loadSubmission(deadlineId, student.roll());
    }

    public java.util.List<AssignmentSubmission> loadMySubmissions() {
        StudentProfile student = loggedInStudent();
        if (student == null) {
            return java.util.List.of();
        }
        return databaseService.loadSubmissionsForStudent(student.roll());
    }

    public AssignmentSubmission saveMySubmission(AcademicDeadline deadline,
            String submissionText,
            String attachmentName,
            String attachmentPath) {
        StudentProfile student = loggedInStudent();
        if (student == null) {
            throw new IllegalStateException("No student is signed in.");
        }
        if (deadline == null) {
            throw new IllegalArgumentException("A deadline is required.");
        }

        return databaseService.saveSubmission(new AssignmentSubmission(
                0L,
                deadline.id(),
                deadline.teacherId(),
                deadline.courseCode(),
                deadline.type(),
                deadline.title(),
                deadline.dueAtEpochMillis(),
                student.roll(),
                student.name(),
                submissionText,
                attachmentName,
                attachmentPath,
                0L,
                "",
                "",
                0L,
                "",
                ""));
    }

    public java.util.List<AssignmentSubmission> loadTeacherSubmissions(String teacherId) {
        return databaseService.loadSubmissionsForTeacher(teacherId);
    }

    public java.util.List<AssignmentSubmission> loadCurrentTeacherSubmissions() {
        TeacherWorkspace teacher = loggedInTeacher();
        if (teacher == null) {
            return java.util.List.of();
        }
        return databaseService.loadSubmissionsForTeacher(teacher.profile().id());
    }

    public AssignmentSubmission gradeSubmission(long submissionId, String grade, String feedback) {
        TeacherWorkspace teacher = loggedInTeacher();
        if (teacher == null) {
            throw new IllegalStateException("No teacher is signed in.");
        }
        return databaseService.gradeSubmission(
                submissionId,
                teacher.profile().id(),
                teacher.profile().name(),
                grade,
                feedback);
    }

    private TeacherWorkspace resolveTeacherWorkspace(TeacherProfile profile) {
        String key = profile.id().toUpperCase();
        TeacherWorkspace existing = teacherWorkspacesById.get(key);
        if (existing != null) {
            if (existing.profile().equals(profile)) {
                return existing;
            }

            TeacherWorkspace updated = new TeacherWorkspace(
                    profile,
                    existing.routineRows(),
                    existing.attendanceEntries(),
                    existing.uploadedResources(),
                    existing.deadlines());
            teacherWorkspacesById.put(key, updated);
            return updated;
        }

        TeacherWorkspace workspace = new TeacherWorkspace(
                profile,
                FXCollections.observableArrayList(),
                FXCollections.observableArrayList(),
                FXCollections.observableArrayList(),
                FXCollections.observableArrayList(databaseService.loadDeadlinesByTeacher(profile.id())));
        teacherWorkspacesById.put(key, workspace);
        return workspace;
    }
}
