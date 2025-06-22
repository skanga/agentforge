package com.skanga.rag.dataloader.reader;

import com.skanga.rag.dataloader.DocumentLoaderException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * A {@link FileReader} implementation for extracting text content from PDF files.
 * This class uses the Apache PDFBox library.
 *
 * <p><b>Dependency Requirement:</b>
 * To use this reader, the Apache PDFBox library must be included in the project's dependencies.
 * Example Maven dependency:
 * <pre>{@code
 * <dependency>
 *     <groupId>org.apache.pdfbox</groupId>
 *     <artifactId>pdfbox</artifactId>
 *     <version>2.0.30</version> <!-- Or a recent compatible version for PDFBox 2.x -->
 * </dependency>
 * <!-- For PDFBox 3.x, the artifactId might be pdfbox-community or similar,
 *      and loading mechanism changes:
 *      e.g. try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(filePath.toFile()))
 * -->
 * }</pre>
 * This implementation is based on PDFBox 2.x's `PDDocument.load(File file)` static method.
 * If using PDFBox 3.x or later, adjust the document loading call accordingly
 * (e.g., `org.apache.pdfbox.Loader.loadPDF(filePath.toFile())`).
 * </p>
 *
 * <p>The `options` map in {@link #getText(Path, Map)} can be used to pass
 * parameters like "password" for encrypted PDFs, or "startPage"/"endPage"
 * to limit text extraction to a range of pages.</p>
 */
public class PdfTextFileReader implements FileReader {

    /**
     * {@inheritDoc}
     * <p>Extracts text from a PDF file using Apache PDFBox.
     * Supports options like "password", "startPage", "endPage".</p>
     *
     * @param filePath The path to the PDF file.
     * @param options  Optional map. Supported options:
     *                 <ul>
     *                   <li>"password" (String): Password for encrypted PDFs.</li>
     *                   <li>"startPage" (Integer): Start page number for extraction (1-based).</li>
     *                   <li>"endPage" (Integer): End page number for extraction (inclusive).</li>
     *                 </ul>
     * @throws DocumentLoaderException if the file does not exist, is not readable, or if PDF parsing fails.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public String getText(Path filePath, Map<String, Object> options) throws IOException, DocumentLoaderException {
        if (!Files.exists(filePath)) {
            throw new DocumentLoaderException("PDF file does not exist: " + filePath);
        }
        if (!Files.isReadable(filePath)) {
            throw new DocumentLoaderException("PDF file is not readable: " + filePath);
        }

        String password = (options != null && options.get("password") instanceof String) ? (String) options.get("password") : "";

        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(filePath.toFile(), password)) {
            PDFTextStripper pdfStripper = new PDFTextStripper();

            if (options != null) {
                if (options.get("startPage") instanceof Integer) {
                    pdfStripper.setStartPage((Integer) options.get("startPage"));
                }
                if (options.get("endPage") instanceof Integer) {
                    pdfStripper.setEndPage((Integer) options.get("endPage"));
                }
            }

            return pdfStripper.getText(document);
        } catch (IOException e) { // Includes InvalidPasswordException from PDDocument.load
            throw new IOException("Error reading PDF file: " + filePath + ". Ensure it's not encrypted or provide password in options.", e);
        } catch (Exception e) { // Catch other PDFBox-specific or unexpected exceptions
            throw new DocumentLoaderException("Failed to parse PDF file content: " + filePath, e);
        }
    }
}
