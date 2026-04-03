package com.example;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record AssignmentSubmission(
        long id,
        long deadlineId,
        String teacherId,
        String courseCode,
        String deadlineType,
        String deadlineTitle,
        long dueAtEpochMillis,
        String studentRoll,
        String studentName,
        String submissionText,
        String attachmentName,
        String attachmentPath,
        long submittedAtEpochMillis,
        String grade,
        String feedback,
        long gradedAtEpochMillis,
        String gradedById,
        String gradedByName
) {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    public boolean hasAttachment() {
        return attachmentPath != null && !attachmentPath.isBlank();
    }

    public boolean isGraded() {
        return gradedAtEpochMillis > 0L;
    }

    public boolean isLate() {
        return dueAtEpochMillis > 0L && submittedAtEpochMillis > dueAtEpochMillis;
    }

    public String submissionStatus() {
        if (isGraded()) {
            return "Graded";
        }
        return isLate() ? "Submitted Late" : "Submitted";
    }

    public String gradeDisplay() {
        return grade == null || grade.isBlank() ? "Ungraded" : grade.trim();
    }

    public String submittedAtText() {
        return formatEpochMillis(submittedAtEpochMillis);
    }

    public String gradedAtText() {
        return gradedAtEpochMillis <= 0L ? "Not graded yet" : formatEpochMillis(gradedAtEpochMillis);
    }

    private String formatEpochMillis(long epochMillis) {
        if (epochMillis <= 0L) {
            return "";
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        return dateTime.format(DATE_TIME_FORMATTER);
    }
}
