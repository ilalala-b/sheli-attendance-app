package com.attendance.app;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Window for marking a whole class at once. Students are loaded from the Student Directory. */
public final class MarkAttendanceDialog {

    private static final class MarkRow {
        final String id;
        final String name;
        final String course;
        final SimpleStringProperty status = new SimpleStringProperty("P");
        final SimpleStringProperty remarks = new SimpleStringProperty("");

        MarkRow(Student s) {
            this.id = s.getId();
            this.name = s.getName();
            this.course = s.getCourse();
        }
    }

    private final AttendanceDao dao = new AttendanceDao();
    private final StudentDao studentDao = new StudentDao();

    private final ComboBox<Semester> semBox = new ComboBox<>();
    private final ComboBox<Subject> subBox = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final TableView<MarkRow> table = new TableView<>();
    private final Label info = new Label("Choose a semester, subject and date.");
    private boolean loading = false;

    public static void show(Window owner) {
        new MarkAttendanceDialog().open(owner);
    }

    private void open(Window owner) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Mark Attendance");

        Label title = new Label("Mark Attendance");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Students come from the Student Directory - no typing of names or IDs.");
        subtitle.getStyleClass().add("page-subtitle");

        semBox.setPrefWidth(190);
        subBox.setPrefWidth(190);
        Button addSem = new Button("+ Semester");
        addSem.getStyleClass().add("secondary-button");
        addSem.setOnAction(e -> addSemester());
        Button addSub = new Button("+ Subject");
        addSub.getStyleClass().add("secondary-button");
        addSub.setOnAction(e -> addSubject());

        HBox selectors = new HBox(10,
                new Label("Semester"), semBox, addSem,
                new Label("Subject"), subBox, addSub,
                new Label("Date"), datePicker);
        selectors.setAlignment(Pos.CENTER_LEFT);
        selectors.getStyleClass().add("card");
        selectors.setPadding(new Insets(14, 18, 14, 18));

        semBox.setOnAction(e -> reloadStudents());
        subBox.setOnAction(e -> reloadStudents());
        datePicker.setOnAction(e -> reloadStudents());

        buildColumns();
        table.setPlaceholder(new Label("No students are registered yet. Add students in the Student Directory first."));
        VBox.setVgrow(table, Priority.ALWAYS);

        Button allP = new Button("Mark All Present");
        allP.getStyleClass().add("success-button");
        allP.setOnAction(e -> setAll("P"));
        Button allA = new Button("Mark All Absent");
        allA.getStyleClass().add("danger-button");
        allA.setOnAction(e -> setAll("A"));
        Button saveBtn = new Button("Save Attendance");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> save());
        Button close = new Button("Close");
        close.getStyleClass().add("secondary-button");
        close.setOnAction(e -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, allP, allA, spacer, saveBtn, close);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox tableCard = new VBox(10, info, table, buttons);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(16));
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        VBox root = new VBox(14, title, subtitle, selectors, tableCard);
        root.getStyleClass().add("page");

        Scene scene = new Scene(root, 1050, 700);
        scene.getStylesheets().add(Ui.css());
        stage.setScene(scene);

        loadChoices();
        stage.showAndWait();
    }

    private void buildColumns() {
        TableColumn<MarkRow, String> cId = new TableColumn<>("Student ID");
        cId.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().id));
        cId.setPrefWidth(110);
        TableColumn<MarkRow, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().name));
        cName.setPrefWidth(240);
        TableColumn<MarkRow, String> cCourse = new TableColumn<>("Class");
        cCourse.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().course));
        cCourse.setPrefWidth(100);

        TableColumn<MarkRow, MarkRow> cStatus = new TableColumn<>("Status");
        cStatus.setSortable(false);
        cStatus.setPrefWidth(130);
        cStatus.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(MarkRow r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) {
                    setGraphic(null);
                    return;
                }
                Button p = new Button("P");
                Button a = new Button("A");
                p.getStyleClass().add("mark-btn");
                a.getStyleClass().add("mark-btn");
                styleButtons(p, a, r);
                p.setOnAction(e -> {
                    r.status.set("P");
                    styleButtons(p, a, r);
                });
                a.setOnAction(e -> {
                    r.status.set("A");
                    styleButtons(p, a, r);
                });
                HBox box = new HBox(6, p, a);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });

        TableColumn<MarkRow, MarkRow> cRemarks = new TableColumn<>("Remarks (optional)");
        cRemarks.setSortable(false);
        cRemarks.setPrefWidth(300);
        cRemarks.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cRemarks.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(MarkRow r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) {
                    setGraphic(null);
                    return;
                }
                TextField tf = new TextField(r.remarks.get());
                tf.setPromptText("e.g. Sick Leave");
                tf.textProperty().addListener((o, oldV, newV) -> r.remarks.set(newV));
                setGraphic(tf);
            }
        });

        table.getColumns().addAll(cId, cName, cCourse, cStatus, cRemarks);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private static void styleButtons(Button p, Button a, MarkRow r) {
        p.getStyleClass().remove("mark-p-on");
        a.getStyleClass().remove("mark-a-on");
        if ("P".equals(r.status.get())) {
            p.getStyleClass().add("mark-p-on");
        } else {
            a.getStyleClass().add("mark-a-on");
        }
    }

    private void loadChoices() {
        Ui.safe(() -> {
            loading = true;
            try {
                Semester ps = semBox.getValue();
                Subject pb = subBox.getValue();
                List<Semester> sems = dao.semesters();
                List<Subject> subs = dao.subjects();
                semBox.setItems(FXCollections.observableArrayList(sems));
                subBox.setItems(FXCollections.observableArrayList(subs));
                Semester chosenS = null;
                for (Semester s : sems) {
                    if (ps != null && s.id() == ps.id()) {
                        chosenS = s;
                    }
                }
                if (chosenS == null && !sems.isEmpty()) {
                    chosenS = sems.get(sems.size() - 1);
                }
                Subject chosenB = null;
                for (Subject s : subs) {
                    if (pb != null && s.id() == pb.id()) {
                        chosenB = s;
                    }
                }
                if (chosenB == null && !subs.isEmpty()) {
                    chosenB = subs.get(0);
                }
                semBox.setValue(chosenS);
                subBox.setValue(chosenB);
            } finally {
                loading = false;
            }
            loadStudents();
        });
    }

    private void reloadStudents() {
        if (!loading) {
            Ui.safe(this::loadStudents);
        }
    }

    private void loadStudents() throws Exception {
        Semester sem = semBox.getValue();
        Subject sub = subBox.getValue();
        LocalDate date = datePicker.getValue();
        if (sem == null || sub == null || date == null) {
            table.setItems(FXCollections.observableArrayList());
            info.setText(sem == null || sub == null
                    ? "Add a semester and a subject with the + buttons, then pick a date."
                    : "Pick a date.");
            return;
        }
        List<Student> students = studentDao.findAll("");
        Map<String, AttendanceDao.Saved> saved = dao.load(sem.id(), sub.id(), date.toString());
        List<MarkRow> rows = new ArrayList<>();
        for (Student s : students) {
            MarkRow r = new MarkRow(s);
            AttendanceDao.Saved sv = saved.get(s.getId());
            if (sv != null) {
                r.status.set(sv.status());
                r.remarks.set(sv.remarks() == null ? "" : sv.remarks());
            }
            rows.add(r);
        }
        table.setItems(FXCollections.observableArrayList(rows));
        info.setText(students.size() + " student(s) loaded. "
                + (saved.isEmpty()
                ? "No attendance saved yet for this date - everyone starts as Present."
                : saved.size() + " already saved - you can correct them and press Save again."));
    }

    private void setAll(String status) {
        for (MarkRow r : table.getItems()) {
            r.status.set(status);
        }
        table.refresh();
    }

    private void save() {
        Ui.safe(() -> {
            Semester sem = semBox.getValue();
            Subject sub = subBox.getValue();
            LocalDate date = datePicker.getValue();
            if (sem == null || sub == null || date == null) {
                Ui.error("Please choose a semester, a subject and a date first.");
                return;
            }
            if (table.getItems().isEmpty()) {
                Ui.error("There are no registered students. Add students in the Student Directory first.");
                return;
            }
            if (date.isAfter(LocalDate.now())
                    && !Ui.confirm("The date " + date + " is in the future. Save attendance for it anyway?")) {
                return;
            }
            int existing = dao.countExisting(sem.id(), sub.id(), date.toString());
            if (existing > 0 && !Ui.confirm("Attendance for " + date + " (" + sub.name() + ", " + sem.name()
                    + ") is already saved for " + existing + " student(s).\n\n"
                    + "Saving will correct (update) those records. Continue?")) {
                return;
            }
            List<String[]> out = new ArrayList<>();
            for (MarkRow r : table.getItems()) {
                out.add(new String[]{r.id, r.status.get(), r.remarks.get()});
            }
            dao.saveSession(sem.id(), sub.id(), date.toString(), out);
            Ui.info("Attendance saved for " + out.size() + " student(s) on " + date + ".");
            loadStudents();
        });
    }

    private void addSemester() {
        Optional<String[]> r = Ui.promptTwo("Add Semester", "Semester name (e.g. Spring 2026)",
                "Academic year (e.g. 2025-2026)");
        r.ifPresent(v -> Ui.safe(() -> {
            dao.addSemester(v[0], v[1]);
            semBox.setValue(null);
            loadChoices();
        }));
    }

    private void addSubject() {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Add Subject");
        d.setHeaderText("New subject name");
        d.setContentText("Subject:");
        Optional<String> r = d.showAndWait();
        r.ifPresent(name -> {
            if (name.isBlank()) {
                Ui.error("Subject name cannot be empty.");
                return;
            }
            Ui.safe(() -> {
                dao.addSubject(name.trim());
                loadChoices();
            });
        });
    }
}
