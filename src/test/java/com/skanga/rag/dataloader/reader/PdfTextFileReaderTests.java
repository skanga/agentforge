package com.skanga.rag.dataloader.reader;

import com.skanga.rag.dataloader.DocumentLoaderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

// Import PDFBox classes for creating a dummy PDF for testing
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts; // For PDType1Font.HELVETICA

import static org.junit.jupiter.api.Assertions.*;

class PdfTextFileReaderTests {

    @TempDir
    Path tempDir;

    private final PdfTextFileReader reader = new PdfTextFileReader();

    @Test
    void readerIsInstanceOfFileReader() {
        assertTrue(reader instanceof FileReader);
    }

    @Test
    void getText_validPdfFile_returnsTextContent() throws IOException, DocumentLoaderException {
        Path pdfFile = tempDir.resolve("test.pdf");
        String expectedText = "Hello PDF World! This is a test.";

        // Create a simple PDF for testing purposes
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                // Use a standard font that is available
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText(expectedText);
                contentStream.endText();
            }
            document.save(pdfFile.toFile());
        }

        String extractedText = reader.getText(pdfFile, Collections.emptyMap());
        // PDFTextStripper might add newlines or format slightly differently.
        // We check for contains rather than exact match for robustness.
        assertTrue(extractedText.contains("Hello PDF World!"), "Extracted text should contain 'Hello PDF World!'");
        assertTrue(extractedText.contains("This is a test."), "Extracted text should contain 'This is a test.'");
    }

    @Test
    void getText_nonExistentFile_throwsDocumentLoaderException() {
        Path nonExistentPdf = tempDir.resolve("nonexistent.pdf");
        DocumentLoaderException ex = assertThrows(DocumentLoaderException.class, () -> {
            reader.getText(nonExistentPdf, Collections.emptyMap());
        });
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    void getText_notAPdfFile_throwsDocumentLoaderExceptionOrIOException() throws IOException {
        Path notAPdf = tempDir.resolve("not_a_pdf.txt");
        Files.writeString(notAPdf, "This is just a plain text file.");

        // PDFBox's PDDocument.load will likely throw an IOException if it's not a valid PDF.
        // The reader wraps this in DocumentLoaderException or re-throws IOException.
        Exception ex = assertThrows(Exception.class, () -> { // Catching general Exception as it could be IOException or DLE
            reader.getText(notAPdf, Collections.emptyMap());
        });
        assertTrue(ex instanceof IOException || ex instanceof DocumentLoaderException, "Exception should be IOException or DocumentLoaderException");
        if(ex.getMessage() != null) {
            // The actual message from PdfTextFileReader's IOException catch block
            assertTrue(ex.getMessage().startsWith("Error reading PDF file:") && ex.getMessage().contains("Ensure it's not encrypted or provide password in options."),
             "Exception message should indicate issues reading/parsing the PDF. Actual: " + ex.getMessage());
        } else {
            fail("Exception message was null, but expected a message indicating PDF read/parse error.");
        }
    }

    @Test
    void getText_encryptedPdfWithoutPassword_throwsIOException() throws IOException {
        Path encryptedPdf = tempDir.resolve("encrypted.pdf");
        // Create a simple password-protected PDF
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            // Basic text
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText("Encrypted content");
                contentStream.endText();
            }
            // Encrypt with a non-empty user password and owner password "ownerpass"
            org.apache.pdfbox.pdmodel.encryption.AccessPermission ap = new org.apache.pdfbox.pdmodel.encryption.AccessPermission();
            org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy spp =
                new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("ownerpass", "userpass", ap); // Set user password
            spp.setEncryptionKeyLength(128);
            document.protect(spp);
            document.save(encryptedPdf.toFile());
        }

        // Attempt to read without password
        Exception ex = assertThrows(IOException.class, () -> { // PDFBox throws IOException for this
            reader.getText(encryptedPdf, Collections.emptyMap());
        });
        assertTrue(ex.getMessage().toLowerCase().contains("password") || ex.getMessage().contains("Error decrypting document"),
            "Exception message should indicate password requirement. Actual: " + ex.getMessage());
    }

    @Test
    void getText_encryptedPdfWithCorrectPassword_returnsText() throws IOException, DocumentLoaderException {
        Path encryptedPdf = tempDir.resolve("encrypted_with_pass.pdf");
        String expectedText = "Secret Content";
        String password = "userpass";

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText(expectedText);
                contentStream.endText();
            }
            org.apache.pdfbox.pdmodel.encryption.AccessPermission ap = new org.apache.pdfbox.pdmodel.encryption.AccessPermission();
            org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy spp =
                new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("ownerpass", password, ap);
            spp.setEncryptionKeyLength(128);
            document.protect(spp);
            document.save(encryptedPdf.toFile());
        }

        Map<String, Object> options = Map.of("password", password);
        String extractedText = reader.getText(encryptedPdf, options);
        assertTrue(extractedText.contains(expectedText));
    }
}
