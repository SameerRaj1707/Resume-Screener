package com.resumescreener.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Extracts plain text from resume file bytes.
 * Same logic as the local Spring Boot version, adapted to work from a raw
 * byte array instead of a MultipartFile, since Lambda receives files as
 * base64-decoded bytes rather than multipart form data.
 */
public class PdfTextExtractor {

    public String extractText(byte[] content, String filename) throws IOException {
        String lower = filename == null ? "" : filename.toLowerCase();

        if (lower.endsWith(".pdf")) {
            return extractFromPdf(content);
        } else if (lower.endsWith(".txt")) {
            return new String(content, StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + filename + " (only .pdf and .txt are supported)");
        }
    }

    private String extractFromPdf(byte[] content) throws IOException {
        try (ByteArrayInputStream is = new ByteArrayInputStream(content);
             PDDocument document = PDDocument.load(is)) {

            if (document.isEncrypted()) {
                throw new IOException("Cannot read an encrypted PDF");
            }
            return new PDFTextStripper().getText(document);
        }
    }
}
