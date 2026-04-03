package com.example;

public enum AiStudyCoachAction {
    SUMMARIZE(
            "Summarize",
            """
            Create a concise study summary from the provided material.
            Keep the most important concepts, formulas, definitions, and takeaways.
            End with a short "Revision focus" section.
            """,
            900,
            0.2),
    EXPLAIN_SIMPLE(
            "Explain Simply",
            """
            Explain the material in simple language for a BUET undergraduate student.
            Break difficult ideas into steps, add intuition, and point out common confusion.
            End with a short "In one line" recap.
            """,
            1000,
            0.25),
    MCQ_QUIZ(
            "Generate MCQ Quiz",
            """
            Create 6 multiple-choice questions from the material.
            For each question, provide 4 options labeled A-D, then mark the correct answer and add a one-line explanation.
            Keep the questions useful for actual revision rather than trivia.
            """,
            1300,
            0.35),
    FLASHCARDS(
            "Create Flashcards",
            """
            Create 8 revision flashcards from the material.
            Format them as:
            Q1: ...
            A1: ...
            Keep each answer focused and exam-oriented.
            """,
            1100,
            0.25),
    CUSTOM(
            "Ask AI",
            "",
            1100,
            0.3);

    private final String label;
    private final String defaultInstruction;
    private final int maxTokens;
    private final double temperature;

    AiStudyCoachAction(String label, String defaultInstruction, int maxTokens, double temperature) {
        this.label = label;
        this.defaultInstruction = defaultInstruction;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    public String label() {
        return label;
    }

    public int maxTokens() {
        return maxTokens;
    }

    public double temperature() {
        return temperature;
    }

    public String instruction(String customPrompt) {
        if (this == CUSTOM) {
            return customPrompt == null ? "" : customPrompt.trim();
        }
        return defaultInstruction;
    }
}
