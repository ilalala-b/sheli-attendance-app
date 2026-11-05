package com.attendance.app;

/** One saved attendance entry: database id, date (yyyy-MM-dd), subject, semester, status "P" or "A", optional remarks. */
public record AttendanceRecord(int id, String date, String subject, String semester, String status, String remarks) { }
