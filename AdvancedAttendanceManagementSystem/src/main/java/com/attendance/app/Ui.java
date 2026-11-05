package com.attendance.app;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.event.ActionEvent;

import java.net.URL;
import java.util.Optional;

/** Small helpers shared by all pages. */
public final class Ui {

    public interface Action {
        void run() throws Exception;
    }

    private Ui() { }

    /** Runs code and shows a friendly error dialog if anything fails. */
    public static void safe(Action action) {
        try {
            action.run();
        } catch (Exception e) {
            e.printStackTrace();
            error("Something went wrong:\n" + e.getMessage());
        }
    }

    public static void error(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.setHeaderText("Error");
        a.showAndWait();
    }

    public static void info(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.setHeaderText("Success");
        a.showAndWait();
    }

    public static boolean confirm(String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        a.setHeaderText("Please confirm");
        return a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    public static String css() {
        URL url = Ui.class.getResource("/styles/style.css");
        if (url == null) {
            throw new IllegalStateException("style.css not found. Put it in src/main/resources/styles/style.css");
        }
        return url.toExternalForm();
    }

    public static VBox statCard(String title, Label value, String colorClass) {
        Label t = new Label(title);
        t.getStyleClass().add("stat-title");
        value.getStyleClass().add("stat-value");
        VBox box = new VBox(4, t, value);
        box.getStyleClass().addAll("stat-card", colorClass);
        box.setPadding(new Insets(16, 20, 16, 20));
        return box;
    }

    /** Dialog asking for two required text values. Returns empty if cancelled. */
    public static Optional<String[]> promptTwo(String title, String label1, String label2) {
        return promptTwo(title, label1, label2, "", "");
    }

    public static Optional<String[]> promptTwo(String title, String label1, String label2,
                                               String value1, String value2) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle(title);
        ButtonType ok = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        TextField f1 = new TextField(value1);
        TextField f2 = new TextField(value2);
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        g.setPadding(new Insets(16));
        g.addRow(0, new Label(label1), f1);
        g.addRow(1, new Label(label2), f2);
        dlg.getDialogPane().setContent(g);
        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ok);
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            if (f1.getText().isBlank() || f2.getText().isBlank()) {
                error("Both fields are required.");
                ev.consume();
            }
        });
        Optional<ButtonType> r = dlg.showAndWait();
        if (r.isPresent() && r.get() == ok) {
            return Optional.of(new String[]{f1.getText().trim(), f2.getText().trim()});
        }
        return Optional.empty();
    }
}
