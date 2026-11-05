package com.attendance.app;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DashboardController {

    private final AttendanceDao dao = new AttendanceDao();
    private final StudentDao studentDao = new StudentDao();

    private final VBox root = new VBox(18);
    private final Label total = new Label("0");
    private final Label present = new Label("0");
    private final Label absent = new Label("0");
    private final Label average = new Label("N/A");
    private final Label low = new Label("0");
    private final ListView<AttendanceDao.RecentSession> recent = new ListView<>();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private final Spinner<Integer> threshold = new Spinner<>(0, 100, 75);

    public DashboardController() {
        root.getStyleClass().add("page");

        Label title = new Label("Dashboard");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Overview of students and attendance");
        subtitle.getStyleClass().add("page-subtitle");

        HBox cards = new HBox(16,
                Ui.statCard("Total Students", total, "stat-blue"),
                Ui.statCard("Present Today", present, "stat-green"),
                Ui.statCard("Absent Today", absent, "stat-red"),
                Ui.statCard("Average Attendance", average, "stat-purple"),
                Ui.statCard("Low Attendance Students", low, "stat-red"));
        for (Node n : cards.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
            ((VBox) n).setMaxWidth(Double.MAX_VALUE);
        }

        threshold.setEditable(true);
        threshold.setPrefWidth(90);
        Button save = new Button("Save Threshold");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> Ui.safe(() -> {
            // commit what was typed in the spinner box
            threshold.increment(0);
            dao.setThreshold(threshold.getValue());
            refresh();
            Ui.info("Warning threshold saved: " + threshold.getValue() + "%");
        }));
        HBox thresholdRow = new HBox(10, new Label("Attendance warning threshold (%):"), threshold, save);
        thresholdRow.setAlignment(Pos.CENTER_LEFT);
        thresholdRow.getStyleClass().add("card");
        thresholdRow.setPadding(new Insets(14, 18, 14, 18));

        Label recentTitle = new Label("Recent Attendance Activity");
        recentTitle.getStyleClass().add("section-title");
        recent.setPrefHeight(300);
        recent.setPlaceholder(new Label("No attendance has been saved yet."));
        recent.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AttendanceDao.RecentSession s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null
                        : LocalDate.parse(s.date()).format(FMT) + "   |   " + s.subject() + "   |   "
                        + s.semester() + "   |   " + s.present() + " present, " + s.absent() + " absent");
            }
        });
        Button delActivity = new Button("Delete Selected Activity");
        delActivity.getStyleClass().add("danger-button");
        delActivity.setOnAction(e -> deleteActivity());
        VBox recentCard = new VBox(10, recentTitle, recent, delActivity);
        recentCard.getStyleClass().add("card");
        recentCard.setPadding(new Insets(16));

        root.getChildren().addAll(title, subtitle, cards, thresholdRow, recentCard);
        refresh();
    }

    private void refresh() {
        Ui.safe(() -> {
            int th = dao.getThreshold();
            threshold.getValueFactory().setValue(th);
            String today = LocalDate.now().toString();
            total.setText(String.valueOf(studentDao.count()));
            present.setText(String.valueOf(dao.studentsWithStatusTodayAll(today, "P")));
            absent.setText(String.valueOf(dao.studentsWithStatusTodayAll(today, "A")));
            double avg = dao.averageAttendance();
            average.setText(avg < 0 ? "N/A" : String.format(Locale.ENGLISH, "%.1f%%", avg));
            low.setText(String.valueOf(dao.lowAttendanceCount(th)));

            recent.setItems(FXCollections.observableArrayList(dao.recentSessions(20)));
        });
    }

    private void deleteActivity() {
        AttendanceDao.RecentSession s = recent.getSelectionModel().getSelectedItem();
        if (s == null) {
            Ui.error("Click an item in the list first.");
            return;
        }
        if (!Ui.confirm("Delete ALL attendance saved on " + s.date() + " for " + s.subject() + " (" + s.semester()
                + ")?\n\nThis removes " + (s.present() + s.absent()) + " record(s) and cannot be undone.")) {
            return;
        }
        Ui.safe(() -> {
            dao.deleteSession(s.semesterId(), s.subjectId(), s.date());
            refresh();
        });
    }

    public Node getView() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("page-scroll");
        return sp;
    }
}
