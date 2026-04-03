package com.example;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

public final class AiStudyCoachService implements AutoCloseable {
    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:11434";
    private static final String DEFAULT_MODEL = "qwen2.5:3b";
    private static final int MAX_CONTEXT_CHARS = 12_000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;

    public AiStudyCoachService() {
        String requestedBaseUrl = clean(System.getenv("OLLAMA_BASE_URL"));
        String requestedModel = clean(System.getenv("OLLAMA_MODEL"));

        this.baseUrl = normalizeBaseUrl(requestedBaseUrl.isBlank() ? DEFAULT_BASE_URL : requestedBaseUrl);
        this.model = requestedModel.isBlank() ? DEFAULT_MODEL : requestedModel;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isConfigured() {
        return true;
    }

    public String statusMessage() {
        return "AI Coach uses local Ollama model " + model
                + ". Start Ollama and pull the model if requests fail.";
    }

    public AiStudyCoachResult generate(AiStudyCoachRequest request) {
        String instruction = request.effectiveInstruction();
        if (instruction.isBlank()) {
            throw new IllegalArgumentException("Enter a question for the AI Coach.");
        }

        String contextText = truncate(request.contextText(), MAX_CONTEXT_CHARS);
        String prompt = buildPrompt(request, instruction, contextText);

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(buildGenerateRequestJson(prompt, request.action())))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException ex) {
            throw new IllegalStateException(buildUnavailableMessage(), ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to contact Ollama: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The AI request was interrupted.", ex);
        }

        if (response.statusCode() >= 400) {
            String error = extractJsonStringField(response.body(), "error");
            String detail = error.isBlank() ? abbreviate(response.body(), 180) : error;
            throw new IllegalStateException(
                    "Ollama request failed (HTTP " + response.statusCode() + "): " + detail);
        }

        String output = extractJsonStringField(response.body(), "response").strip();
        if (output.isBlank()) {
            throw new IllegalStateException("Ollama returned an empty response.");
        }

        return new AiStudyCoachResult(
                request.action(),
                buildTitle(request),
                output,
                clean(request.courseCode()),
                request.contextLabel());
    }

    private String buildPrompt(AiStudyCoachRequest request, String instruction, String contextText) {
        String course = clean(request.courseCode());
        return """
                You are BISS AI Coach, an academic assistant for BUET students.
                Stay faithful to the provided study material.
                Do not invent formulas, marks, or facts not supported by the context.
                If the context is incomplete, say what is missing.
                Keep answers structured, study-oriented, and easy to scan.

                Task:
                %s

                Context source:
                %s

                Course:
                %s

                Study material:
                %s
                """.formatted(
                instruction.strip(),
                request.contextLabel().strip(),
                course.isBlank() ? "Not specified" : course,
                contextText.strip());
    }

    private String buildGenerateRequestJson(String prompt, AiStudyCoachAction action) {
        return """
                {
                  "model": "%s",
                  "prompt": "%s",
                  "stream": false,
                  "options": {
                    "temperature": %s
                  }
                }
                """.formatted(
                jsonEscape(model),
                jsonEscape(prompt),
                formatDouble(action.temperature()));
    }

    private String buildTitle(AiStudyCoachRequest request) {
        String source = request.contextLabel()
                .replaceAll("\\s+", " ")
                .replaceAll("[^A-Za-z0-9\\- ]", "")
                .trim();
        if (source.length() > 36) {
            source = source.substring(0, 36).trim();
        }

        if (source.isBlank()) {
            source = "Study Context";
        }

        return request.action().label() + " - " + source;
    }

    private String buildUnavailableMessage() {
        return "Cannot reach Ollama at " + baseUrl
                + ". Install/start Ollama, then run: ollama pull " + model;
    }

    private static String normalizeBaseUrl(String url) {
        String value = clean(url);
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String abbreviate(String text, int maxLength) {
        String normalized = clean(text).replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String jsonEscape(String text) {
        if (text == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder(text.length() + 32);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format(Locale.US, "\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static String extractJsonStringField(String json, String fieldName) {
        if (json == null || json.isBlank() || fieldName == null || fieldName.isBlank()) {
            return "";
        }

        String needle = "\"" + fieldName + "\"";
        int keyIndex = json.indexOf(needle);
        if (keyIndex < 0) {
            return "";
        }

        int colonIndex = json.indexOf(':', keyIndex + needle.length());
        if (colonIndex < 0) {
            return "";
        }

        int index = colonIndex + 1;
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }

        if (index >= json.length() || json.charAt(index) != '"') {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        index++;
        while (index < json.length()) {
            char current = json.charAt(index++);
            if (current == '"') {
                return builder.toString();
            }
            if (current != '\\') {
                builder.append(current);
                continue;
            }
            if (index >= json.length()) {
                break;
            }

            char escape = json.charAt(index++);
            switch (escape) {
                case '"', '\\', '/' -> builder.append(escape);
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    if (index + 4 <= json.length()) {
                        String hex = json.substring(index, index + 4);
                        try {
                            builder.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException ignored) {
                            builder.append("\\u").append(hex);
                        }
                        index += 4;
                    }
                }
                default -> builder.append(escape);
            }
        }

        return builder.toString();
    }

    @Override
    public void close() {
        // No persistent resources to close for the local HTTP client.
    }
}
