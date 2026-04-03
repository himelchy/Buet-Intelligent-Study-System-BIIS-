package com.example;

public record AiStudyCoachRequest(
        AiStudyCoachAction action,
        String contextLabel,
        String contextText,
        String courseCode,
        String customPrompt
) {
    public AiStudyCoachRequest {
        if (action == null) {
            throw new IllegalArgumentException("Action is required.");
        }
        if (contextLabel == null || contextLabel.isBlank()) {
            throw new IllegalArgumentException("Context label is required.");
        }
        if (contextText == null || contextText.isBlank()) {
            throw new IllegalArgumentException("Context text is required.");
        }
    }

    public String effectiveInstruction() {
        return action.instruction(customPrompt);
    }
}
