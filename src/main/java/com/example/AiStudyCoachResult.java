package com.example;

public record AiStudyCoachResult(
        AiStudyCoachAction action,
        String title,
        String content,
        String courseCode,
        String contextLabel
) {
    public String chatMessage() {
        String body = content == null ? "" : content.trim();
        if (body.length() > 1200) {
            body = body.substring(0, 1197) + "...";
        }
        return title + System.lineSeparator() + body;
    }
}
