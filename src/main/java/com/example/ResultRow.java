package com.example;

import javafx.beans.property.SimpleStringProperty;

public final class ResultRow {
    private final SimpleStringProperty term = new SimpleStringProperty();
    private final SimpleStringProperty code = new SimpleStringProperty();
    private final SimpleStringProperty title = new SimpleStringProperty();
    private final SimpleStringProperty grade = new SimpleStringProperty();

    public ResultRow(String term, String code, String title, String grade) {
        this.term.set(term);
        this.code.set(code);
        this.title.set(title);
        this.grade.set(grade);
    }

    public SimpleStringProperty termProperty() {
        return term;
    }

    public SimpleStringProperty codeProperty() {
        return code;
    }

    public SimpleStringProperty titleProperty() {
        return title;
    }

    public SimpleStringProperty gradeProperty() {
        return grade;
    }
}
