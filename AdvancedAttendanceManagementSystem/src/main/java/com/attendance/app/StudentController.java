package com.attendance.app;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Optional;

public class StudentController {

    private final StudentDao dao = new StudentDao();
    private final VBox root = new VBox(16);
    private final TableView<Student> table = new TableView<>();
    private final TextField search = new TextField();
    private final Label count = new Label();

    public StudentController() {
        root.getStyleClass().add("page");

        Label title = new Label("Student Directory");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Register students once. Their ID and name are reused for attendance all semester.");
        subtitle.getStyleClass().add("page-subtitle");

        search.setPromptText("Search by Student ID or Name...");
        search.setPrefWidth(320);
        search.textProperty().addListener((o, a, b) -> load());

        Button add = new Button("Add Student");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> addStudent());
        Button edit = new Button("Edit Selected");
        edit.getStyleClass().add("secondary-button");
        edit.setOnAction(e -> editStudent());
        Button delete = new Button("Delete Selected");
        delete.getStyleClass().add("danger-button");
        delete.setOnAction(e -> deleteStudent());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(10, search, spacer, add, edit, delete);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("card");
        bar.setPadding(new Insets(14, 18, 14, 18));

        TableColumn<Student, String> cId = new TableColumn<>("Student ID");
        cId.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getId()));
        cId.setPrefWidth(160);
        TableColumn<Student, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getName()));
        cName.setPrefWidth(320);
        TableColumn<Student, String> cCourse = new TableColumn<>("Class / Course");
        cCourse.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getCourse()));
        cCourse.setPrefWidth(200);
        table.getColumns().addAll(cId, cName, cCourse);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No students found."));
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox tableCard = new VBox(10, table, count);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(16));
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        root.getChildren().addAll(title, subtitle, bar, tableCard);
        load();
    }

    private void load() {
        Ui.safe(() -> {
            var list = dao.findAll(search.getText());
            table.setItems(FXCollections.observableArrayList(list));
            count.setText("Showing " + list.size() + " student(s)");
        });
    }

    private void addStudent() {
        Student s = showDialog(null);
        if (s == null) {
            return;
        }
        Ui.safe(() -> {
            if (dao.exists(s.getId())) {
                Ui.error("A student with ID \"" + s.getId() + "\" already exists.");
                return;
            }
            dao.insert(s);
            load();
        });
    }

    private void editStudent() {
        Student selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Ui.error("Please select a student in the table first.");
            return;
        }
        Student s = showDialog(selected);
        if (s == null) {
            return;
        }
        Ui.safe(() -> {
            dao.update(s);
            load();
        });
    }

    private void deleteStudent() {
        Student selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Ui.error("Please select a student in the table first.");
            return;
        }
        if (!Ui.confirm("Delete " + selected.getName() + " (" + selected.getId() + ")?\n\n"
                + "All of this student's attendance records will also be deleted. This cannot be undone.")) {
            return;
        }
        Ui.safe(() -> {
            dao.delete(selected.getId());
            load();
        });
    }

    /** Returns the entered student, or null if cancelled. When editing, the ID cannot be changed. */
    private Student showDialog(Student existing) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle(existing == null ? "Add Student" : "Edit Student");
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField id = new TextField();
        TextField name = new TextField();
        TextField course = new TextField();
        if (existing != null) {
            id.setText(existing.getId());
            id.setDisable(true);
            name.setText(existing.getName());
            course.setText(existing.getCourse());
        }
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        g.setPadding(new Insets(16));
        g.addRow(0, new Label("Student ID"), id);
        g.addRow(1, new Label("Name"), name);
        g.addRow(2, new Label("Class / Course"), course);
        dlg.getDialogPane().setContent(g);

        Button ok = (Button) dlg.getDialogPane().lookupButton(saveType);
        ok.addEventFilter(ActionEvent.ACTION, ev -> {
            if (id.getText().isBlank() || name.getText().isBlank()) {
                Ui.error("Student ID and Name are required.");
                ev.consume();
            }
        });

        Optional<ButtonType> r = dlg.showAndWait();
        if (r.isPresent() && r.get() == saveType) {
            return new Student(id.getText().trim(), name.getText().trim(), course.getText().trim());
        }
        return null;
    }

    public Node getView() {
        return root;
    }
}
