package com.example;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public final class PdfStudyContextExtractor {
    private PdfStudyContextExtractor() {
    }

    public static String extractPageRange(Path pdfPath, int startPageZeroBased, int endPageZeroBased, int maxChars)
            throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            if (document.getNumberOfPages() == 0) {
                return "";
            }

            int start = Math.max(0, startPageZeroBased);
            int end = Math.max(start, endPageZeroBased);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(Math.min(document.getNumberOfPages(), start + 1));
            stripper.setEndPage(Math.min(document.getNumberOfPages(), end + 1));

            return truncate(normalize(stripper.getText(document)), maxChars);
        }
    }

    private static String normalize(String rawText) {
        if (rawText == null) {
            return "";
        }

        return rawText
                .replace("\r", "")
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }
}
