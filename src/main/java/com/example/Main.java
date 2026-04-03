package com.example;

import java.util.Objects;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage stage) {
        AppState state = SampleData.defaultState();
        Scene scene = new Scene(new StackPane(), 1180, 720);
        String stylesheetUrl = Objects.requireNonNull(getClass().getResource("app.css")).toExternalForm();
        scene.getStylesheets().add(stylesheetUrl);

        BissView view = new BissView(state, scene, stylesheetUrl);
        scene.setRoot(view.buildLandingView());

        stage.setTitle("BUET Intelligent Study System (BISS)");
        stage.setScene(scene);
        stage.setMinWidth(1180);
        stage.setMinHeight(720);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
