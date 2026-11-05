package com.attendance.app;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** All database operations for the Student Directory. */
public class StudentDao {

    public List<Student> findAll(String search) throws SQLException {
        String like = "%" + (search == null ? "" : search.trim()) + "%";
        String sql = "SELECT student_id, name, course FROM students "
                + "WHERE student_id LIKE ? OR name LIKE ? ORDER BY student_id";
        List<Student> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Student(rs.getString(1), rs.getString(2), rs.getString(3)));
                }
            }
        }
        return list;
    }

    public int count() throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM students");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public boolean exists(String id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM students WHERE student_id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void insert(Student s) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO students(student_id, name, course) VALUES (?,?,?)")) {
            ps.setString(1, s.getId());
            ps.setString(2, s.getName());
            ps.setString(3, s.getCourse());
            ps.executeUpdate();
        }
    }

    public void update(Student s) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE students SET name = ?, course = ? WHERE student_id = ?")) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getCourse());
            ps.setString(3, s.getId());
            ps.executeUpdate();
        }
    }

    /** Deletes the student and that student's attendance records in one transaction. */
    public void delete(String id) throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement a = c.prepareStatement("DELETE FROM attendance WHERE student_id = ?");
                 PreparedStatement s = c.prepareStatement("DELETE FROM students WHERE student_id = ?")) {
                a.setString(1, id);
                a.executeUpdate();
                s.setString(1, id);
                s.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
