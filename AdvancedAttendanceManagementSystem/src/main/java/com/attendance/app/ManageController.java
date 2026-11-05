package com.attendance.app;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Optional;

/** Page where you add, edit and delete semesters and subjects. */
public class ManageController {

    private final AttendanceDao dao = new AttendanceDao();
    private final VBox root = new VBox(16);
    private final TableView<Semester> semTable = new TableView<>();
    private final TableView<Subject> subTable = new TableView<>();

    public ManageController() {
        root.getStyleClass().add("page");

        Label title = new Label("Semesters & Subjects");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Create the semesters and subjects you use for attendance. "
                + "You can rename or delete them at any time.");
        subtitle.getStyleClass().add("page-subtitle");

        // ---------- semesters
        TableColumn<Semester, String> sName = new TableColumn<>("Semester");
        sName.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().name()));
        TableColumn<Semester, String> sYear = new TableColumn<>("Academic Year");
        sYear.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().academicYear()));
        semTable.getColumns().addAll(sName, sYear);
        semTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        semTable.setPlaceholder(new Label("No semesters yet. Click \"Add Semester\"."));
        VBox.setVgrow(semTable, Priority.ALWAYS);

        Button addSem = button("Add Semester", "primary-button", this::addSemester);
        Button editSem = button("Edit Selected", "secondary-button", this::editSemester);
        Button delSem = button("Delete Selected", "danger-button", this::deleteSemester);
        VBox semCard = card("Semesters", semTable, new HBox(10, addSem, editSem, delSem));

        // ---------- subjects
        TableColumn<Subject, String> bName = new TableColumn<>("Subject");
        bName.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().name()));
        subTable.getColumns().add(bName);
        subTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        subTable.setPlaceholder(new Label("No subjects yet. Click \"Add Subject\"."));
        VBox.setVgrow(subTable, Priority.ALWAYS);

        Button addSub = button("Add Subject", "primary-button", this::addSubject);
        Button editSub = button("Edit Selected", "secondary-button", this::editSubject);
        Button delSub = button("Delete Selected", "danger-button", this::deleteSubject);
        VBox subCard = card("Subjects", subTable, new HBox(10, addSub, editSub, delSub));

        HBox both = new HBox(16, semCard, subCard);
        HBox.setHgrow(semCard, Priority.ALWAYS);
        HBox.setHgrow(subCard, Priority.ALWAYS);
        semCard.setPrefWidth(500);
        subCard.setPrefWidth(500);
        both.setPrefHeight(420);

        root.getChildren().addAll(title, subtitle, both);
        load();
    }

    private static Button button(String text, String style, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setOnAction(e -> action.run());
        return b;
    }

    private static VBox card(String heading, Node table, HBox buttons) {
        Label h = new Label(heading);
        h.getStyleClass().add("section-title");
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(10, h, table, buttons);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(16));
        return box;
    }

    private void load() {
        Ui.safe(() -> {
            semTable.setItems(FXCollections.observableArrayList(dao.semesters()));
            subTable.setItems(FXCollections.observableArrayList(dao.subjects()));
        });
    }

    // ---------------------------------------------------------- semesters

    private void addSemester() {
        Optional<String[]> r = Ui.promptTwo("Add Semester", "Semester name (e.g. Spring 2026)",
                "Academic year (e.g. 2025-2026)");
        r.ifPresent(v -> Ui.safe(() -> {
            dao.addSemester(v[0], v[1]);
            load();
        }));
    }

    private void editSemester() {
        Semester s = semTable.getSelectionModel().getSelectedItem();
        if (s == null) {
            Ui.error("Click a semester in the table first.");
            return;
        }
        Optional<String[]> r = Ui.promptTwo("Edit Semester", "Semester name", "Academic year",
                s.name(), s.academicYear());
        r.ifPresent(v -> Ui.safe(() -> {
            dao.updateSemester(s.id(), v[0], v[1]);
            load();
        }));
    }

    private void deleteSemester() {
        Semester s = semTable.getSelectionModel().getSelectedItem();
        if (s == null) {
            Ui.error("Click a semester in the table first.");
            return;
        }
        Ui.safe(() -> {
            int n = dao.recordsInSemester(s.id());
            String msg = "Delete the semester \"" + s.name() + "\"?"
                    + (n > 0 ? "\n\nThis will also delete " + n + " saved attendance record(s) in it." : "")
                    + "\n\nThis cannot be undone.";
            if (Ui.confirm(msg)) {
                dao.deleteSemester(s.id());
                load();
            }
        });
    }

    // ----------------------------------------------------------- subjects

    private void addSubject() {
        Optional<String> r = askSubject("Add Subject", "");
        r.ifPresent(name -> Ui.safe(() -> {
            dao.addSubject(name);
            load();
        }));
    }

    private void editSubject() {
        Subject s = subTable.getSelectionModel().getSelectedItem();
        if (s == null) {
            Ui.error("Click a subject in the table first.");
            return;
        }
        Optional<String> r = askSubject("Edit Subject", s.name());
        r.ifPresent(name -> Ui.safe(() -> {
            dao.updateSubject(s.id(), name);
            load();
        }));
    }

    private void deleteSubject() {
        Subject s = subTable.getSelectionModel().getSelectedItem();
        if (s == null) {
            Ui.error("Click a subject in the table first.");
            return;
        }
        Ui.safe(() -> {
            int n = dao.recordsInSubject(s.id());
            String msg = "Delete the subject \"" + s.name() + "\"?"
                    + (n > 0 ? "\n\nThis will also delete " + n + " saved attendance record(s) for it." : "")
                    + "\n\nThis cannot be undone.";
            if (Ui.confirm(msg)) {
                dao.deleteSubject(s.id());
                load();
            }
        });
    }

    private Optional<String> askSubject(String title, String initial) {
        TextInputDialog d = new TextInputDialog(initial);
        d.setTitle(title);
        d.setHeaderText("Subject name");
        d.setContentText("Subject:");
        Optional<String> r = d.showAndWait();
        if (r.isPresent() && r.get().isBlank()) {
            Ui.error("Subject name cannot be empty.");
            return Optional.empty();
        }
        return r.map(String::trim);
    }

    public Node getView() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("page-scroll");
        return sp;
    }
}
