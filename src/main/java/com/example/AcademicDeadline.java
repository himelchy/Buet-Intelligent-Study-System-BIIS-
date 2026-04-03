package com.example;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

public final class AcademicDeadline {
    private final SimpleLongProperty id = new SimpleLongProperty();
    private final SimpleStringProperty teacherId = new SimpleStringProperty();
    private final SimpleStringProperty teacherName = new SimpleStringProperty();
    private final SimpleStringProperty courseCode = new SimpleStringProperty();
    private final SimpleStringProperty type = new SimpleStringProperty();
    private final SimpleStringProperty title = new SimpleStringProperty();
    private final SimpleStringProperty details = new SimpleStringProperty();
    private final SimpleLongProperty dueAtEpochMillis = new SimpleLongProperty();
    private final SimpleLongProperty createdAtEpochMillis = new SimpleLongProperty();

    public AcademicDeadline(String teacherId,
            String teacherName,
            String courseCode,
            String type,
            String title,
            String details,
            long dueAtEpochMillis) {
        this(0L, teacherId, teacherName, courseCode, type, title, details, dueAtEpochMillis, 0L);
    }

    public AcademicDeadline(long id,
            String teacherId,
            String teacherName,
            String courseCode,
            String type,
            String title,
            String details,
            long dueAtEpochMillis,
            long createdAtEpochMillis) {
        this.id.set(id);
        this.teacherId.set(teacherId);
        this.teacherName.set(teacherName);
        this.courseCode.set(courseCode);
        this.type.set(type);
        this.title.set(title);
        this.details.set(details == null ? "" : details);
        this.dueAtEpochMillis.set(dueAtEpochMillis);
        this.createdAtEpochMillis.set(createdAtEpochMillis);
    }

    public SimpleLongProperty idProperty() {
        return id;
    }

    public SimpleStringProperty teacherIdProperty() {
        return teacherId;
    }

    public SimpleStringProperty teacherNameProperty() {
        return teacherName;
    }

    public SimpleStringProperty courseCodeProperty() {
        return courseCode;
    }

    public SimpleStringProperty typeProperty() {
        return type;
    }

    public SimpleStringProperty titleProperty() {
        return title;
    }

    public SimpleStringProperty detailsProperty() {
        return details;
    }

    public SimpleLongProperty dueAtEpochMillisProperty() {
        return dueAtEpochMillis;
    }

    public SimpleLongProperty createdAtEpochMillisProperty() {
        return createdAtEpochMillis;
    }

    public long id() {
        return id.get();
    }

    public String teacherId() {
        return teacherId.get();
    }

    public String teacherName() {
        return teacherName.get();
    }

    public String courseCode() {
        return courseCode.get();
    }

    public String type() {
        return type.get();
    }

    public String title() {
        return title.get();
    }

    public String details() {
        return details.get();
    }

    public long dueAtEpochMillis() {
        return dueAtEpochMillis.get();
    }

    public long createdAtEpochMillis() {
        return createdAtEpochMillis.get();
    }

    public LocalDateTime dueDateTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(dueAtEpochMillis()), ZoneId.systemDefault());
    }

    public LocalDateTime createdAtDateTime() {
        if (createdAtEpochMillis() <= 0L) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAtEpochMillis()), ZoneId.systemDefault());
    }
}
