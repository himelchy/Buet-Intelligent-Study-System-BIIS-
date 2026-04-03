package com.example;

import javafx.beans.property.SimpleStringProperty;

public final class AttendanceEntry {
    private final SimpleStringProperty date = new SimpleStringProperty();
    private final SimpleStringProperty course = new SimpleStringProperty();
    private final SimpleStringProperty section = new SimpleStringProperty();
    private final SimpleStringProperty status = new SimpleStringProperty();

    public AttendanceEntry(String date, String course, String section, String status) {
        this.date.set(date);
        this.course.set(course);
        this.section.set(section);
        this.status.set(status);
    }

    public SimpleStringProperty dateProperty() {
        return date;
    }

    public SimpleStringProperty courseProperty() {
        return course;
    }

    public SimpleStringProperty sectionProperty() {
        return section;
    }

    public SimpleStringProperty statusProperty() {
        return status;
    }

    public String getDate() {
        return date.get();
    }

    public String getCourse() {
        return course.get();
    }

    public String getSection() {
        return section.get();
    }

    public String getStatus() {
        return status.get();
    }
}
