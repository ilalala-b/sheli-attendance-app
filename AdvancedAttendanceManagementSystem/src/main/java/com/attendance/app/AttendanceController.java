package com.attendance.app;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class AttendanceController {

    private record MonthItem(String key, String label) {
        @Override public String toString() { return label; }
    }

    private static final class Row {
        final AttendanceDao.StudentStat stat;
        final Map<String, String> status = new HashMap<>();

        Row(AttendanceDao.StudentStat stat) { this.stat = stat; }

        int absent() { return stat.total() - stat.present(); }

        double pct() { return stat.total() == 0 ? 0 : stat.present() * 100.0 / stat.total(); }
    }

    private final AttendanceDao dao = new AttendanceDao();
    private final StudentDao studentDao = new StudentDao();

    private final VBox root = new VBox(16);
    private final ComboBox<Semester> semBox = new ComboBox<>();
    private final ComboBox<Subject> subBox = new ComboBox<>();
    private final ComboBox<MonthItem> monthBox = new ComboBox<>();
    private final TextField searchField = new TextField();

    private final Label totalStudents = new Label("0");
    private final Label presentToday = new Label("0");
    private final Label absentToday = new Label("0");
    private final Label totalClasses = new Label("0");

    private final TableView<Row> table = new TableView<>();
    private final TableView<AttendanceRecord> detailTable = new TableView<>();
    private final Label detailHeader = new Label("Click any student row (or \"View Details\") to see their full history here.");
    private final Label showing = new Label();

    private boolean loading = false;
    private int threshold = 75;
    private List<String> currentDates = new ArrayList<>();
    private AttendanceDao.StudentStat selectedStudent = null;

    public AttendanceController() {
        root.getStyleClass().add("page");

        // ---------- header
        Label title = new Label("Attendance History");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("View and manage complete semester attendance records");
        subtitle.getStyleClass().add("page-subtitle");
        VBox titleBox = new VBox(2, title, subtitle);

        semBox.setPrefWidth(190);
        HBox semHolder = new HBox(10, new Label("Semester:"), semBox);
        semHolder.setAlignment(Pos.CENTER_LEFT);
        semHolder.getStyleClass().add("card");
        semHolder.setPadding(new Insets(8, 14, 8, 14));

        Button mark = new Button("Mark Attendance");
        mark.getStyleClass().add("success-button");
        mark.setOnAction(e -> {
            MarkAttendanceDialog.show(root.getScene().getWindow());
            loadChoices();
        });
        Button export = new Button("Export Report");
        export.getStyleClass().add("primary-button");
        export.setOnAction(e -> exportCsv());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, titleBox, spacer, semHolder, mark, export);
        header.setAlignment(Pos.CENTER_LEFT);

        // ---------- summary cards
        HBox cards = new HBox(16,
                Ui.statCard("Total Students", totalStudents, "stat-blue"),
                Ui.statCard("Present Today", presentToday, "stat-green"),
                Ui.statCard("Absent Today", absentToday, "stat-red"),
                Ui.statCard("Total Classes (This Semester)", totalClasses, "stat-purple"));
        for (Node n : cards.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
            ((VBox) n).setMaxWidth(Double.MAX_VALUE);
        }

        // ---------- filters
        subBox.setPrefWidth(220);
        monthBox.setPrefWidth(200);
        VBox subHolder = new VBox(4, new Label("Select Subject"), subBox);
        VBox monthHolder = new VBox(4, new Label("Select Month"), monthBox);
        searchField.setPromptText("Search by Student ID or Name...");
        searchField.setPrefWidth(300);
        searchField.setOnAction(e -> refresh());
        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("primary-button");
        searchBtn.setOnAction(e -> refresh());
        Button clear = new Button("Clear");
        clear.getStyleClass().add("secondary-button");
        clear.setOnAction(e -> {
            searchField.clear();
            refresh();
        });
        HBox searchRow = new HBox(10, searchField, searchBtn, clear);
        searchRow.setAlignment(Pos.BOTTOM_LEFT);
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        Button addSemBtn = new Button("+ Semester");
        addSemBtn.getStyleClass().add("secondary-button");
        addSemBtn.setOnAction(e -> addSemesterQuick());
        Button addSubBtn = new Button("+ Subject");
        addSubBtn.getStyleClass().add("secondary-button");
        addSubBtn.setOnAction(e -> addSubjectQuick());
        HBox filters = new HBox(16, subHolder, monthHolder, addSemBtn, addSubBtn, spacer2, searchRow);
        filters.setAlignment(Pos.BOTTOM_LEFT);
        filters.getStyleClass().add("card");
        filters.setPadding(new Insets(14, 18, 14, 18));

        semBox.setOnAction(e -> {
            if (!loading) {
                Ui.safe(() -> {
                    reloadMonths();
                    doRefresh();
                });
            }
        });
        subBox.setOnAction(e -> {
            if (!loading) {
                Ui.safe(() -> {
                    reloadMonths();
                    doRefresh();
                });
            }
        });
        monthBox.setOnAction(e -> {
            if (!loading) {
                refresh();
            }
        });

        // ---------- main table
        table.setPrefHeight(340);
        table.setMinHeight(200);
        table.setPlaceholder(new Label("No attendance yet. Use \"+ Semester\" and \"+ Subject\" above, then scroll up and click \"Mark Attendance\"."));
        table.getSelectionModel().selectedItemProperty().addListener((o, oldRow, row) -> {
            if (row != null) {
                selectedStudent = row.stat;
                Ui.safe(() -> showDetails(row.stat));
            }
        });
        VBox tableCard = new VBox(10, table, showing);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(16));

        // ---------- details panel
        Label detailTitle = new Label("Student Attendance Details");
        detailTitle.getStyleClass().add("section-title");
        buildDetailColumns();
        detailTable.setRowFactory(tv -> {
            TableRow<AttendanceRecord> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    editRecord();
                }
            });
            return row;
        });
        detailTable.setPrefHeight(230);
        detailTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        detailTable.setPlaceholder(new Label("No details to show."));
        Button editRec = new Button("Edit Selected Record");
        editRec.getStyleClass().add("secondary-button");
        editRec.setOnAction(e -> editRecord());
        Button delRec = new Button("Delete Selected Record");
        delRec.getStyleClass().add("danger-button");
        delRec.setOnAction(e -> deleteRecord());
        HBox recButtons = new HBox(10, editRec, delRec);
        VBox detailCard = new VBox(10, detailTitle, detailHeader, detailTable, recButtons);
        detailCard.getStyleClass().add("card");
        detailCard.setPadding(new Insets(16));

        root.getChildren().addAll(header, cards, filters, tableCard, detailCard);
        loadChoices();
    }

    public Node getView() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("page-scroll");
        return sp;
    }

    // ------------------------------------------------------------ loading

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
            reloadMonths();
            doRefresh();
        });
    }

    private void reloadMonths() throws Exception {
        loading = true;
        try {
            MonthItem prev = monthBox.getValue();
            List<MonthItem> items = new ArrayList<>();
            items.add(new MonthItem(null, "All months"));
            Semester sem = semBox.getValue();
            Subject sub = subBox.getValue();
            if (sem != null && sub != null) {
                for (String key : dao.months(sem.id(), sub.id())) {
                    items.add(new MonthItem(key,
                            YearMonth.parse(key).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))));
                }
            }
            monthBox.setItems(FXCollections.observableArrayList(items));
            MonthItem pick = null;
            if (prev != null) {
                for (MonthItem m : items) {
                    if (Objects.equals(m.key(), prev.key())) {
                        pick = m;
                    }
                }
            }
            if (pick == null) {
                pick = items.get(items.size() - 1);
            }
            monthBox.setValue(pick);
        } finally {
            loading = false;
        }
    }

    private void refresh() {
        Ui.safe(this::doRefresh);
    }

    private void doRefresh() throws Exception {
        threshold = dao.getThreshold();
        totalStudents.setText(String.valueOf(studentDao.count()));
        Semester sem = semBox.getValue();
        Subject sub = subBox.getValue();
        if (sem == null || sub == null) {
            presentToday.setText("0");
            absentToday.setText("0");
            totalClasses.setText("0");
            currentDates = new ArrayList<>();
            buildColumns();
            table.setItems(FXCollections.observableArrayList());
            showing.setText("Add a semester and a subject using \"Mark Attendance\" to get started.");
            return;
        }
        String today = LocalDate.now().toString();
        presentToday.setText(String.valueOf(dao.studentsWithStatusToday(sem.id(), today, "P")));
        absentToday.setText(String.valueOf(dao.studentsWithStatusToday(sem.id(), today, "A")));
        totalClasses.setText(String.valueOf(dao.totalClasses(sem.id())));

        String month = monthBox.getValue() == null ? null : monthBox.getValue().key();
        currentDates = dao.dates(sem.id(), sub.id(), month);
        Map<String, String> statusMap = dao.statusMap(sem.id(), sub.id(), month);
        List<Row> rows = new ArrayList<>();
        for (AttendanceDao.StudentStat st : dao.stats(sem.id(), sub.id(), searchField.getText())) {
            Row r = new Row(st);
            for (String d : currentDates) {
                String s = statusMap.get(st.id() + "|" + d);
                if (s != null) {
                    r.status.put(d, s);
                }
            }
            rows.add(r);
        }
        buildColumns();
        table.setItems(FXCollections.observableArrayList(rows));
        showing.setText("Showing " + rows.size() + " of " + studentDao.count() + " students   |   Warning below "
                + threshold + "%");
        if (selectedStudent != null) {
            showDetails(selectedStudent);
        }
    }

    // ------------------------------------------------------------ table columns

    private TableColumn<Row, String> textCol(String title, double width, java.util.function.Function<Row, String> f) {
        TableColumn<Row, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(d -> new ReadOnlyStringWrapper(f.apply(d.getValue())));
        return c;
    }

    private void buildColumns() {
        table.getColumns().clear();
        table.getColumns().add(textCol("Student ID", 90, r -> r.stat.id()));
        table.getColumns().add(textCol("Name", 210, r -> r.stat.name()));
        table.getColumns().add(textCol("Class", 170, r -> r.stat.course()));
        table.getColumns().add(textCol("Total Classes", 100, r -> String.valueOf(r.stat.total())));
        table.getColumns().add(textCol("Present", 70, r -> String.valueOf(r.stat.present())));
        table.getColumns().add(textCol("Absent", 70, r -> String.valueOf(r.absent())));

        TableColumn<Row, Row> pct = new TableColumn<>("Attendance %");
        pct.setPrefWidth(150);
        pct.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        pct.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Row r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) {
                    setGraphic(null);
                    return;
                }
                if (r.stat.total() == 0) {
                    Label none = new Label("No records");
                    none.getStyleClass().add("muted");
                    setGraphic(none);
                    return;
                }
                double p = r.pct();
                Label value = new Label(String.format(Locale.ENGLISH, "%.1f%%", p));
                boolean isLow = p < threshold;
                Label tag = new Label(isLow ? "Low" : "Safe");
                tag.getStyleClass().add(isLow ? "tag-low" : "tag-safe");
                HBox box = new HBox(8, value, tag);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });
        table.getColumns().add(pct);

        TableColumn<Row, Row> action = new TableColumn<>("Action");
        action.setSortable(false);
        action.setPrefWidth(120);
        action.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        action.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Row r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) {
                    setGraphic(null);
                    return;
                }
                Button b = new Button("View Details");
                b.getStyleClass().add("small-button");
                b.setOnAction(e -> {
                    selectedStudent = r.stat;
                    Ui.safe(() -> showDetails(r.stat));
                });
                setGraphic(b);
            }
        });
        table.getColumns().add(action);

        for (String d : currentDates) {
            LocalDate ld = LocalDate.parse(d);
            String header = String.format("%02d\n%s", ld.getDayOfMonth(),
                    ld.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            TableColumn<Row, String> c = new TableColumn<>(header);
            c.setSortable(false);
            c.setPrefWidth(52);
            c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().status.getOrDefault(d, "")));
            c.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String s, boolean empty) {
                    super.updateItem(s, empty);
                    setText(null);
                    if (empty || s == null || s.isEmpty()) {
                        setGraphic(null);
                        return;
                    }
                    Label l = new Label(s);
                    l.getStyleClass().addAll("badge", "P".equals(s) ? "badge-p" : "badge-a");
                    setGraphic(l);
                    setAlignment(Pos.CENTER);
                }
            });
            table.getColumns().add(c);
        }

    }

    // ------------------------------------------------------------ details

    private void buildDetailColumns() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

        TableColumn<AttendanceRecord, String> cDate = new TableColumn<>("Date");
        cDate.setCellValueFactory(d -> new ReadOnlyStringWrapper(LocalDate.parse(d.getValue().date()).format(f)));
        TableColumn<AttendanceRecord, String> cDay = new TableColumn<>("Day");
        cDay.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                LocalDate.parse(d.getValue().date()).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH)));
        TableColumn<AttendanceRecord, String> cSub = new TableColumn<>("Subject");
        cSub.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().subject()));
        TableColumn<AttendanceRecord, String> cSem = new TableColumn<>("Semester");
        cSem.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().semester()));

        TableColumn<AttendanceRecord, String> cStatus = new TableColumn<>("Status");
        cStatus.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().status()));
        cStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(null);
                if (empty || s == null) {
                    setGraphic(null);
                    return;
                }
                boolean present = "P".equals(s);
                Label l = new Label(present ? "Present" : "Absent");
                l.getStyleClass().addAll("badge", present ? "badge-p" : "badge-a");
                setGraphic(l);
            }
        });

        TableColumn<AttendanceRecord, String> cRem = new TableColumn<>("Remarks");
        cRem.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                d.getValue().remarks() == null || d.getValue().remarks().isBlank() ? "-" : d.getValue().remarks()));

        detailTable.getColumns().addAll(cDate, cDay, cSub, cSem, cStatus, cRem);
    }

    private void showDetails(AttendanceDao.StudentStat st) throws Exception {
        Semester sem = semBox.getValue();
        if (sem == null) {
            return;
        }
        detailHeader.setText("Student ID: " + st.id() + "   |   Name: " + st.name()
                + "   |   Class: " + st.course() + "   |   Semester: " + sem.name());
        detailTable.setItems(FXCollections.observableArrayList(dao.details(st.id(), sem.id())));
    }

    private void addSemesterQuick() {
        Optional<String[]> r = Ui.promptTwo("Add Semester", "Semester name (e.g. Spring 2026)",
                "Academic year (e.g. 2025-2026)");
        r.ifPresent(v -> Ui.safe(() -> {
            dao.addSemester(v[0], v[1]);
            semBox.setValue(null);
            loadChoices();
        }));
    }

    private void addSubjectQuick() {
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
                subBox.setValue(null);
                loadChoices();
            });
        });
    }

    private void editRecord() {
        AttendanceRecord rec = detailTable.getSelectionModel().getSelectedItem();
        if (rec == null) {
            Ui.error("First click a student in the table above, then click a record in this table.\n\nTip: double-click a record to edit it.");
            return;
        }
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Edit Attendance Record");
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList("Present", "Absent"));
        status.setValue("P".equals(rec.status()) ? "Present" : "Absent");
        TextField remarks = new TextField(rec.remarks() == null ? "" : rec.remarks());
        remarks.setPrefWidth(260);
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        g.setPadding(new Insets(16));
        g.addRow(0, new Label("Class"), new Label(rec.date() + "  -  " + rec.subject()));
        g.addRow(1, new Label("Status"), status);
        g.addRow(2, new Label("Remarks"), remarks);
        dlg.getDialogPane().setContent(g);
        Optional<ButtonType> r = dlg.showAndWait();
        if (r.isPresent() && r.get() == saveType) {
            Ui.safe(() -> {
                dao.updateRecord(rec.id(), "Present".equals(status.getValue()) ? "P" : "A", remarks.getText());
                doRefresh();
            });
        }
    }

    private void deleteRecord() {
        AttendanceRecord rec = detailTable.getSelectionModel().getSelectedItem();
        if (rec == null) {
            Ui.error("First click a student in the table above, then click a record in this table.\n\nTip: double-click a record to edit it.");
            return;
        }
        if (!Ui.confirm("Delete the attendance record for " + rec.date() + " (" + rec.subject() + ")?")) {
            return;
        }
        Ui.safe(() -> {
            dao.deleteRecord(rec.id());
            doRefresh();
        });
    }

    // ------------------------------------------------------------ export

    private void exportCsv() {
        Ui.safe(() -> {
            if (table.getItems().isEmpty()) {
                Ui.error("There is nothing to export yet.");
                return;
            }
            List<String[]> lines = new ArrayList<>();
            List<String> head = new ArrayList<>(List.of("Student ID", "Name", "Class", "Total Classes",
                    "Present", "Absent", "Attendance %"));
            head.addAll(currentDates);
            lines.add(head.toArray(new String[0]));
            for (Row r : table.getItems()) {
                List<String> cells = new ArrayList<>();
                cells.add(r.stat.id());
                cells.add(r.stat.name());
                cells.add(r.stat.course());
                cells.add(String.valueOf(r.stat.total()));
                cells.add(String.valueOf(r.stat.present()));
                cells.add(String.valueOf(r.absent()));
                cells.add(r.stat.total() == 0 ? "" : String.format(Locale.ENGLISH, "%.1f", r.pct()));
                for (String d : currentDates) {
                    cells.add(r.status.getOrDefault(d, ""));
                }
                lines.add(cells.toArray(new String[0]));
            }
            ReportController.saveCsv(root.getScene().getWindow(), lines);
        });
    }
}
