package com.attendance.app;

public class Student {
    private final String id;
    private final String name;
    private final String course;

    public Student(String id, String name, String course) {
        this.id = id;
        this.name = name;
        this.course = course == null ? "" : course;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCourse() { return course; }

    @Override
    public String toString() { return id + " - " + name; }
}
