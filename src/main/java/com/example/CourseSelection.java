package com.example;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

public final class CourseSelection {
    private final CourseDetail detail;
    private final SimpleStringProperty code = new SimpleStringProperty();
    private final SimpleStringProperty title = new SimpleStringProperty();
    private final SimpleStringProperty instructor = new SimpleStringProperty();
    private final SimpleStringProperty credit = new SimpleStringProperty();
    private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);

    public CourseSelection(CourseDetail detail, boolean selected) {
        this.detail = detail;
        this.code.set(detail.code());
        this.title.set(detail.title());
        this.instructor.set(detail.instructor());
        this.credit.set(String.valueOf(detail.credits()));
        this.selected.set(selected);
    }

    public CourseDetail detail() {
        return detail;
    }

    public SimpleStringProperty codeProperty() {
        return code;
    }

    public SimpleStringProperty titleProperty() {
        return title;
    }

    public SimpleStringProperty instructorProperty() {
        return instructor;
    }

    public SimpleStringProperty creditProperty() {
        return credit;
    }

    public SimpleBooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public int credits() {
        return detail.credits();
    }

    public String code() {
        return detail.code();
    }

    public String title() {
        return detail.title();
    }

    public String instructor() {
        return detail.instructor();
    }
}
