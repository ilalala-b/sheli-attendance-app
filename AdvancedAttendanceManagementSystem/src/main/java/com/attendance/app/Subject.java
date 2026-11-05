package com.attendance.app;

/** One subject, for example "Computer Science". */
public record Subject(int id, String name) {
    @Override
    public String toString() {
        return name;
    }
}
