package com.attendance.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class Main extends Application {

    private final StackPane content = new StackPane();
    private final List<Button> navButtons = new ArrayList<>();
    private final List<Supplier<Node>> pages = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        try {
            Database.initialize();
        } catch (Exception e) {
            e.printStackTrace();
            Ui.error("Could not open the database:\n" + e.getMessage());
            Platform.exit();
            return;
        }

        Label brand = new Label("Advanced\nAttendance MS");
        brand.getStyleClass().add("brand");
        Region line = new Region();
        line.getStyleClass().add("brand-line");
        line.setPrefHeight(2);
        line.setMinHeight(2);

        VBox sidebar = new VBox(8);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(240);
        sidebar.getChildren().addAll(brand, line);

        // Every sidebar button has its own setOnAction that swaps the page.
        addNav(sidebar, "Dashboard", () -> new DashboardController().getView());
        addNav(sidebar, "Student Directory", () -> new StudentController().getView());
        addNav(sidebar, "Attendance History", () -> new AttendanceController().getView());
        addNav(sidebar, "Semesters & Subjects", () -> new ManageController().getView());

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        Label dbInfo = new Label("Data file:\n"
                + new java.io.File(Database.DB_URL.replace("jdbc:sqlite:", "")).getAbsolutePath());
        dbInfo.setWrapText(true);
        dbInfo.getStyleClass().add("db-path");
        sidebar.getChildren().addAll(grow, dbInfo);

        content.getStyleClass().add("content-area");

        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(content);

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(Ui.css());
        stage.setTitle("Advanced Attendance Management System");
        stage.setMinWidth(1000);
        stage.setMinHeight(650);
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();

        show(0);
    }

    private void addNav(VBox sidebar, String text, Supplier<Node> page) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("nav-button");
        int index = navButtons.size();
        navButtons.add(b);
        pages.add(page);
        b.setOnAction(e -> show(index));
        sidebar.getChildren().add(b);
    }

    /** Builds a fresh page each time so the numbers are always read from the database. */
    private void show(int index) {
        try {
            content.getChildren().setAll(pages.get(index).get());
        } catch (Exception e) {
            e.printStackTrace();
            Ui.error("Could not open this page:\n" + e.getMessage());
            return;
        }
        for (int i = 0; i < navButtons.size(); i++) {
            Button b = navButtons.get(i);
            b.getStyleClass().remove("nav-active");
            if (i == index) {
                b.getStyleClass().add("nav-active");
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
