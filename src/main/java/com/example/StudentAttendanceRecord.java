package com.example;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record StudentAttendanceRecord(
        long id,
        String teacherId,
        String courseCode,
        String section,
        String attendanceDate,
        String studentRoll,
        String studentName,
        boolean present,
        long markedAtEpochMillis
) {
    private static final DateTimeFormatter MARKED_AT_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    public String statusText() {
        return present ? "Present" : "Absent";
    }

    public String markedAtText() {
        if (markedAtEpochMillis <= 0L) {
            return "";
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(markedAtEpochMillis), ZoneId.systemDefault());
        return dateTime.format(MARKED_AT_FORMATTER);
    }
}
