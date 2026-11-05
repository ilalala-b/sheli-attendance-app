package com.attendance.app;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Saves attendance reports to a CSV file (opens in Excel). */
public final class ReportController {

    private ReportController() { }

    /** Asks where to save, then writes the lines as a CSV file. Each String[] is one row. */
    public static void saveCsv(Window owner, List<String[]> lines) throws Exception {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save attendance report");
        fc.setInitialFileName("attendance_report.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        File file = fc.showSaveDialog(owner);
        if (file == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String[] row : lines) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(row[i] == null ? "" : row[i].replace("\"", "\"\"")).append('"');
            }
            sb.append('\n');
        }
        Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
        Ui.info("Report saved to:\n" + file.getAbsolutePath());
    }
}
