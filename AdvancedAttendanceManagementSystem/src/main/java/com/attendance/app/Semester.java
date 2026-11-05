package com.attendance.app;

/** One semester, for example "Spring 2026" in academic year "2025-2026". */
public record Semester(int id, String name, String academicYear) {
    @Override
    public String toString() {
        return name;
    }
}
