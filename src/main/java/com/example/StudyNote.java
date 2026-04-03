package com.example;

/**
 * Represents a student study note stored in the database.
 * isPublic = true means other students enrolled in the same course can read it.
 */
public record StudyNote(
        long   id,          // 0 for unsaved notes
        String ownerRoll,
        String courseCode,
        String title,
        String content,
        boolean isPublic,
        boolean isPinned,
        long    createdAt,
        long    updatedAt
) {
    /** Convenience — returns a display-friendly time string. */
    public String formattedDate() {
        java.time.LocalDateTime dt = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(updatedAt),
                java.time.ZoneId.systemDefault());
        return dt.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm"));
    }

    /** Returns a trimmed preview of content for the sidebar list. */
    public String preview() {
        String c = content == null ? "" : content.strip();
        return c.length() > 60 ? c.substring(0, 57) + "…" : c;
    }
}
