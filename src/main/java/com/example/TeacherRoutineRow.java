package com.example;

import javafx.beans.property.SimpleStringProperty;

public final class TeacherRoutineRow {
    private final SimpleStringProperty day = new SimpleStringProperty();
    private final SimpleStringProperty time = new SimpleStringProperty();
    private final SimpleStringProperty course = new SimpleStringProperty();
    private final SimpleStringProperty section = new SimpleStringProperty();
    private final SimpleStringProperty room = new SimpleStringProperty();

    public TeacherRoutineRow(String day, String time, String course, String section, String room) {
        this.day.set(day);
        this.time.set(time);
        this.course.set(course);
        this.section.set(section);
        this.room.set(room);
    }

    public SimpleStringProperty dayProperty() {
        return day;
    }

    public SimpleStringProperty timeProperty() {
        return time;
    }

    public SimpleStringProperty courseProperty() {
        return course;
    }

    public SimpleStringProperty sectionProperty() {
        return section;
    }

    public SimpleStringProperty roomProperty() {
        return room;
    }
}
