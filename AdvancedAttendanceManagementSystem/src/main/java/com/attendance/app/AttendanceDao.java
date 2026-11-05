package com.attendance.app;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All database operations for semesters, subjects, attendance and statistics. */
public class AttendanceDao {

    public record Saved(String status, String remarks) { }

    public record StudentStat(String id, String name, String course, int total, int present) { }

    public record RecentSession(String date, String semester, String subject, int present, int absent,
                                int semesterId, int subjectId) { }

    // ---------------------------------------------------------------- helpers

    private int scalarInt(String sql, Object... params) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ------------------------------------------------------ semesters/subjects

    public List<Semester> semesters() throws SQLException {
        List<Semester> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, name, academic_year FROM semesters ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Semester(rs.getInt(1), rs.getString(2), rs.getString(3)));
            }
        }
        return list;
    }

    public void addSemester(String name, String academicYear) throws SQLException {
        if (scalarInt("SELECT COUNT(*) FROM semesters WHERE name = ?", name) > 0) {
            throw new SQLException("A semester named \"" + name + "\" already exists.");
        }
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO semesters(name, academic_year) VALUES (?,?)")) {
            ps.setString(1, name);
            ps.setString(2, academicYear);
            ps.executeUpdate();
        }
    }

    public List<Subject> subjects() throws SQLException {
        List<Subject> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, name FROM subjects ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Subject(rs.getInt(1), rs.getString(2)));
            }
        }
        return list;
    }

    public void addSubject(String name) throws SQLException {
        if (scalarInt("SELECT COUNT(*) FROM subjects WHERE name = ?", name) > 0) {
            throw new SQLException("A subject named \"" + name + "\" already exists.");
        }
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO subjects(name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------ marking attendance

    /** Saved records for one class session, keyed by student ID. */
    public Map<String, Saved> load(int semesterId, int subjectId, String date) throws SQLException {
        Map<String, Saved> map = new HashMap<>();
        String sql = "SELECT student_id, status, remarks FROM attendance "
                + "WHERE semester_id = ? AND subject_id = ? AND att_date = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, semesterId);
            ps.setInt(2, subjectId);
            ps.setString(3, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString(1), new Saved(rs.getString(2), rs.getString(3)));
                }
            }
        }
        return map;
    }

    public int countExisting(int semesterId, int subjectId, String date) throws SQLException {
        return scalarInt("SELECT COUNT(*) FROM attendance WHERE semester_id = ? AND subject_id = ? AND att_date = ?",
                semesterId, subjectId, date);
    }

    /**
     * Saves a whole class in ONE transaction. Rows are {studentId, "P" or "A", remarks}.
     * The UNIQUE rule means a second save for the same student/date/subject/semester
     * updates the old record instead of creating a duplicate.
     */
    public void saveSession(int semesterId, int subjectId, String date, List<String[]> rows) throws SQLException {
        String sql = "INSERT INTO attendance (student_id, semester_id, subject_id, att_date, status, remarks) "
                + "VALUES (?,?,?,?,?,?) "
                + "ON CONFLICT(student_id, semester_id, subject_id, att_date) "
                + "DO UPDATE SET status = excluded.status, remarks = excluded.remarks";
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (String[] r : rows) {
                    ps.setString(1, r[0]);
                    ps.setInt(2, semesterId);
                    ps.setInt(3, subjectId);
                    ps.setString(4, date);
                    ps.setString(5, r[1]);
                    String remarks = r[2] == null || r[2].isBlank() ? null : r[2].trim();
                    ps.setString(6, remarks);
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // --------------------------------------------------------------- history

    /** Months (yyyy-MM) that have records for this semester + subject. */
    public List<String> months(int semesterId, int subjectId) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT substr(att_date, 1, 7) FROM attendance "
                + "WHERE semester_id = ? AND subject_id = ? ORDER BY 1";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, semesterId);
            ps.setInt(2, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString(1));
                }
            }
        }
        return list;
    }

    /** Class dates (yyyy-MM-dd) for this semester + subject, optionally limited to one month. */
    public List<String> dates(int semesterId, int subjectId, String monthKey) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT att_date FROM attendance WHERE semester_id = ? AND subject_id = ? "
                + (monthKey == null ? "" : "AND substr(att_date, 1, 7) = ? ")
                + "ORDER BY att_date";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, semesterId);
            ps.setInt(2, subjectId);
            if (monthKey != null) {
                ps.setString(3, monthKey);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString(1));
                }
            }
        }
        return list;
    }

    /** Map of "studentId|date" to "P"/"A" for the date columns. */
    public Map<String, String> statusMap(int semesterId, int subjectId, String monthKey) throws SQLException {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT student_id, att_date, status FROM attendance WHERE semester_id = ? AND subject_id = ? "
                + (monthKey == null ? "" : "AND substr(att_date, 1, 7) = ?");
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, semesterId);
            ps.setInt(2, subjectId);
            if (monthKey != null) {
                ps.setString(3, monthKey);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString(1) + "|" + rs.getString(2), rs.getString(3));
                }
            }
        }
        return map;
    }

    /** Per-student totals for a semester + subject. Students with no records get 0 / 0 (no division by zero). */
    public List<StudentStat> stats(int semesterId, int subjectId, String search) throws SQLException {
        String like = "%" + (search == null ? "" : search.trim()) + "%";
        String sql = "SELECT s.student_id, s.name, s.course, COUNT(a.id), "
                + "COALESCE(SUM(CASE WHEN a.status = 'P' THEN 1 ELSE 0 END), 0) "
                + "FROM students s LEFT JOIN attendance a ON a.student_id = s.student_id "
                + "AND a.semester_id = ? AND a.subject_id = ? "
                + "WHERE s.student_id LIKE ? OR s.name LIKE ? "
                + "GROUP BY s.student_id, s.name, s.course ORDER BY s.student_id";
        List<StudentStat> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, semesterId);
            ps.setInt(2, subjectId);
            ps.setString(3, like);
            ps.setString(4, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String course = rs.getString(3);
                    list.add(new StudentStat(rs.getString(1), rs.getString(2),
                            course == null ? "" : course, rs.getInt(4), rs.getInt(5)));
                }
            }
        }
        return list;
    }

    /** Complete history of one student in one semester (all subjects). */
    public List<AttendanceRecord> details(String studentId, int semesterId) throws SQLException {
        String sql = "SELECT a.id, a.att_date, sub.name, sem.name, a.status, a.remarks FROM attendance a "
                + "JOIN subjects sub ON sub.id = a.subject_id "
                + "JOIN semesters sem ON sem.id = a.semester_id "
                + "WHERE a.student_id = ? AND a.semester_id = ? ORDER BY a.att_date, sub.name";
        List<AttendanceRecord> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, studentId);
            ps.setInt(2, semesterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new AttendanceRecord(rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6)));
                }
            }
        }
        return list;
    }

    /** Distinct (subject, date) class sessions recorded in the semester. */
    public int totalClasses(int semesterId) throws SQLException {
        return scalarInt("SELECT COUNT(*) FROM (SELECT DISTINCT subject_id, att_date FROM attendance "
                + "WHERE semester_id = ?)", semesterId);
    }

    public int studentsWithStatusToday(int semesterId, String date, String status) throws SQLException {
        return scalarInt("SELECT COUNT(DISTINCT student_id) FROM attendance "
                + "WHERE semester_id = ? AND att_date = ? AND status = ?", semesterId, date, status);
    }

    // ------------------------------------------------------------- dashboard

    public int studentsWithStatusTodayAll(String date, String status) throws SQLException {
        return scalarInt("SELECT COUNT(DISTINCT student_id) FROM attendance WHERE att_date = ? AND status = ?",
                date, status);
    }

    /** Overall attendance % across every saved record, or -1 if there are no records. */
    public double averageAttendance() throws SQLException {
        int total = scalarInt("SELECT COUNT(*) FROM attendance");
        if (total == 0) {
            return -1;
        }
        int present = scalarInt("SELECT COUNT(*) FROM attendance WHERE status = 'P'");
        return present * 100.0 / total;
    }

    /** Students whose overall attendance is below the threshold (students with no records are not counted). */
    public int lowAttendanceCount(int threshold) throws SQLException {
        return scalarInt("SELECT COUNT(*) FROM (SELECT student_id FROM attendance GROUP BY student_id "
                + "HAVING 100.0 * SUM(CASE WHEN status = 'P' THEN 1 ELSE 0 END) / COUNT(*) < ?)", threshold);
    }

    public List<RecentSession> recentSessions(int limit) throws SQLException {
        String sql = "SELECT a.att_date, sem.name, sub.name, "
                + "SUM(CASE WHEN a.status = 'P' THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN a.status = 'A' THEN 1 ELSE 0 END), a.semester_id, a.subject_id "
                + "FROM attendance a JOIN semesters sem ON sem.id = a.semester_id "
                + "JOIN subjects sub ON sub.id = a.subject_id "
                + "GROUP BY a.att_date, a.semester_id, a.subject_id "
                + "ORDER BY a.att_date DESC, MAX(a.id) DESC LIMIT ?";
        List<RecentSession> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RecentSession(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7)));
                }
            }
        }
        return list;
    }


    // ------------------------------------------- edit / delete everything

    public void updateSemester(int id, String name, String academicYear) throws SQLException {
        if (scalarInt("SELECT COUNT(*) FROM semesters WHERE name = ? AND id <> ?", name, id) > 0) {
            throw new SQLException("A semester named \"" + name + "\" already exists.");
        }
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE semesters SET name = ?, academic_year = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setString(2, academicYear);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public int recordsInSemester(int id) throws SQLException {
        return scalarInt("SELECT COUNT(*) FROM attendance WHERE semester_id = ?", id);
    }

    /** Deletes the semester and all attendance saved in it (one transaction). */
    public void deleteSemester(int id) throws SQLException {
        deleteWithRecords("semester_id", "semesters", id);
    }

    public void updateSubject(int id, String name) throws SQLException {
        if (scalarInt("SELECT COUNT(*) FROM subjects WHERE name = ? AND id <> ?", name, id) > 0) {
            throw new SQLException("A subject named \"" + name + "\" already exists.");
        }
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE subjects SET name = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public int recordsInSubject(int id) throws SQLException {
        return scalarInt("SELECT COUNT(*) FROM attendance WHERE subject_id = ?", id);
    }

    /** Deletes the subject and all attendance saved for it (one transaction). */
    public void deleteSubject(int id) throws SQLException {
        deleteWithRecords("subject_id", "subjects", id);
    }

    private void deleteWithRecords(String attendanceColumn, String table, int id) throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement a = c.prepareStatement("DELETE FROM attendance WHERE " + attendanceColumn + " = ?");
                 PreparedStatement t = c.prepareStatement("DELETE FROM " + table + " WHERE id = ?")) {
                a.setInt(1, id);
                a.executeUpdate();
                t.setInt(1, id);
                t.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Changes the status and remarks of one saved attendance record. */
    public void updateRecord(int recordId, String status, String remarks) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE attendance SET status = ?, remarks = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setString(2, remarks == null || remarks.isBlank() ? null : remarks.trim());
            ps.setInt(3, recordId);
            ps.executeUpdate();
        }
    }

    /** Deletes every attendance record of one class session (semester + subject + date). */
    public void deleteSession(int semesterId, int subjectId, String date) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM attendance WHERE semester_id = ? AND subject_id = ? AND att_date = ?")) {
            ps.setInt(1, semesterId);
            ps.setInt(2, subjectId);
            ps.setString(3, date);
            ps.executeUpdate();
        }
    }

    public void deleteRecord(int recordId) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM attendance WHERE id = ?")) {
            ps.setInt(1, recordId);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------- settings

    public int getThreshold() throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT value FROM settings WHERE key = 'threshold'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                try {
                    return Integer.parseInt(rs.getString(1));
                } catch (NumberFormatException ignored) {
                    return 75;
                }
            }
        }
        return 75;
    }

    public void setThreshold(int value) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO settings(key, value) VALUES ('threshold', ?)")) {
            ps.setString(1, String.valueOf(value));
            ps.executeUpdate();
        }
    }
}
