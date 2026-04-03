package com.example;

import javafx.collections.ObservableList;

public final class TeacherWorkspace {
    private final TeacherProfile profile;
    private final ObservableList<TeacherRoutineRow> routineRows;
    private final ObservableList<AttendanceEntry> attendanceEntries;
    private final ObservableList<UploadedResource> uploadedResources;
    private final ObservableList<AcademicDeadline> deadlines;

    public TeacherWorkspace(TeacherProfile profile,
                            ObservableList<TeacherRoutineRow> routineRows,
                            ObservableList<AttendanceEntry> attendanceEntries,
                            ObservableList<UploadedResource> uploadedResources,
                            ObservableList<AcademicDeadline> deadlines) {
        this.profile = profile;
        this.routineRows = routineRows;
        this.attendanceEntries = attendanceEntries;
        this.uploadedResources = uploadedResources;
        this.deadlines = deadlines;
    }

    public TeacherProfile profile() {
        return profile;
    }

    public ObservableList<TeacherRoutineRow> routineRows() {
        return routineRows;
    }

    public ObservableList<AttendanceEntry> attendanceEntries() {
        return attendanceEntries;
    }

    public ObservableList<UploadedResource> uploadedResources() {
        return uploadedResources;
    }

    public ObservableList<AcademicDeadline> deadlines() {
        return deadlines;
    }
}
