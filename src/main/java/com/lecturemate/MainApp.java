package com.lecturemate;

import java.io.IOException;

import com.lecturemate.dao.DatabaseManager;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * LectureMate JavaFX application entry point.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        initializeDatabase();

        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/main-view.fxml"));
        BorderPane root = loader.load();

        Scene scene = new Scene(root, 1200, 760);
        scene.getStylesheets().add(MainApp.class.getResource("/styles.css").toExternalForm());

        primaryStage.setTitle("LectureMate");
        primaryStage.setMinWidth(980);
        primaryStage.setMinHeight(640);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Creates the SQLite database schema at startup so the UI can use DAO classes immediately.
     */
    private void initializeDatabase() {
        try {
            DatabaseManager.initializeDatabase();
        } catch (IllegalStateException exception) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText("데이터베이스 초기화에 실패했습니다.");
            alert.setContentText(exception.getMessage());
            alert.showAndWait();
        }
    }
}
