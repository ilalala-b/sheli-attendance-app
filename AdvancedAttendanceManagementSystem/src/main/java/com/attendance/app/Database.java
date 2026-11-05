package com.attendance.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Opens the SQLite database and safely creates/upgrades the tables.
 * Existing tables and rows are NEVER dropped or deleted.
 *
 * IMPORTANT: change DB_URL below if your existing database file has a different name.
 */
public final class Database {

    public static final String DB_URL = "jdbc:sqlite:attendance.db";

    private static boolean foreignKeysOn = true;

    private Database() { }

    public static Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL);
        if (foreignKeysOn) {
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
        }
        return c;
    }

    public static void initialize() throws SQLException {
        try (Connection c = DriverManager.getConnection(DB_URL);
             Statement st = c.createStatement()) {

            // Students: created only if missing. Existing table and data are kept.
            st.execute("CREATE TABLE IF NOT EXISTS students ("
                    + "student_id TEXT PRIMARY KEY, "
                    + "name TEXT NOT NULL, "
                    + "course TEXT)");
            if (!hasColumn(c, "students", "course")) {
                st.execute("ALTER TABLE students ADD COLUMN course TEXT");
            }
            try {
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_students_student_id ON students(student_id)");
            } catch (SQLException e) {
                // Duplicate IDs already exist in an old table: skip foreign-key enforcement
                foreignKeysOn = false;
            }

            st.execute("CREATE TABLE IF NOT EXISTS semesters ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL UNIQUE, "
                    + "academic_year TEXT NOT NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS subjects ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL UNIQUE)");

            st.execute("CREATE TABLE IF NOT EXISTS attendance ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "student_id TEXT NOT NULL, "
                    + "semester_id INTEGER NOT NULL, "
                    + "subject_id INTEGER NOT NULL, "
                    + "att_date TEXT NOT NULL, "
                    + "status TEXT NOT NULL CHECK (status IN ('P','A')), "
                    + "remarks TEXT, "
                    + "UNIQUE (student_id, semester_id, subject_id, att_date), "
                    + "FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE, "
                    + "FOREIGN KEY (semester_id) REFERENCES semesters(id), "
                    + "FOREIGN KEY (subject_id) REFERENCES subjects(id))");

            st.execute("CREATE INDEX IF NOT EXISTS idx_att_lookup ON attendance(semester_id, subject_id, att_date)");

            st.execute("CREATE TABLE IF NOT EXISTS settings ("
                    + "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            st.execute("INSERT OR IGNORE INTO settings(key, value) VALUES ('threshold', '75')");
        }
    }

    private static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
