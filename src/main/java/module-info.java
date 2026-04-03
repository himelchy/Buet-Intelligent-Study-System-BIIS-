module com.example.biss {
    requires transitive javafx.base;
    requires transitive javafx.controls;
    requires transitive javafx.graphics;
    requires transitive javafx.web;
    requires transitive javafx.swing;
    requires transitive javafx.fxml;
    requires transitive javafx.media;
    requires java.net.http;
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires org.apache.pdfbox;
    requires anthropic.java.core;
    requires anthropic.java.client.okhttp;

    exports com.example;
}
