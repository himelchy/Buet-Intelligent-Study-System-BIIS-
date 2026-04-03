package com.example;

import javafx.beans.property.SimpleStringProperty;

public final class UploadedResource {
    private final SimpleStringProperty course = new SimpleStringProperty();
    private final SimpleStringProperty title = new SimpleStringProperty();
    private final SimpleStringProperty type = new SimpleStringProperty();
    private final SimpleStringProperty fileName = new SimpleStringProperty();
    private final SimpleStringProperty visibility = new SimpleStringProperty();
    private final SimpleStringProperty uploadedAt = new SimpleStringProperty();
    private final SimpleStringProperty sourcePath = new SimpleStringProperty();

    public UploadedResource(String course,
                            String title,
                            String type,
                            String fileName,
                            String visibility,
                            String uploadedAt) {
        this(course, title, type, fileName, visibility, uploadedAt, "");
    }

    public UploadedResource(String course,
                            String title,
                            String type,
                            String fileName,
                            String visibility,
                            String uploadedAt,
                            String sourcePath) {
        this.course.set(course);
        this.title.set(title);
        this.type.set(type);
        this.fileName.set(fileName);
        this.visibility.set(visibility);
        this.uploadedAt.set(uploadedAt);
        this.sourcePath.set(sourcePath);
    }

    public SimpleStringProperty courseProperty() {
        return course;
    }

    public SimpleStringProperty titleProperty() {
        return title;
    }

    public SimpleStringProperty typeProperty() {
        return type;
    }

    public SimpleStringProperty fileNameProperty() {
        return fileName;
    }

    public SimpleStringProperty visibilityProperty() {
        return visibility;
    }

    public SimpleStringProperty uploadedAtProperty() {
        return uploadedAt;
    }

    public SimpleStringProperty sourcePathProperty() {
        return sourcePath;
    }

    public String sourcePath() {
        return sourcePath.get();
    }
}
