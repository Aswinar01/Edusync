package com.example.edusync.service;

import com.example.edusync.model.DocumentType;
import com.example.edusync.model.TextExtractionResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TextExtractionService {

    public TextExtractionResult extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("Cannot extract text from empty or missing file.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidDocumentException("Filename is missing.");
        }

        String cleanFilename = Paths.get(originalFilename).getFileName().toString().trim();
        DocumentType documentType = determineDocumentType(cleanFilename);

        try (InputStream inputStream = file.getInputStream()) {
            return extractText(inputStream, cleanFilename, documentType);
        } catch (IOException ex) {
            throw new DocumentExtractionException("Failed to read document stream for " + cleanFilename, ex);
        }
    }

    public TextExtractionResult extractText(InputStream inputStream, String filename, DocumentType documentType) {
        if (inputStream == null) {
            throw new InvalidDocumentException("Input stream cannot be null.");
        }
        if (documentType == null) {
            throw new InvalidDocumentException("Document type must be specified.");
        }

        return switch (documentType) {
            case PDF -> extractPdf(inputStream, filename);
            case DOCX -> extractDocx(inputStream, filename);
        };
    }

    public TextExtractionResult extractPdf(InputStream inputStream, String filename) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(inputStream))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            int pageCount = document.getNumberOfPages();
            return new TextExtractionResult(
                    filename,
                    DocumentType.PDF,
                    text != null ? text.trim() : "",
                    pageCount
            );
        } catch (Exception ex) {
            throw new DocumentExtractionException("Failed to extract text from PDF document: " + filename, ex);
        }
    }

    public TextExtractionResult extractDocx(InputStream inputStream, String filename) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<String> paragraphs = extractParagraphs(document);
            String text = String.join("\n\n", paragraphs).trim();
            return new TextExtractionResult(
                    filename,
                    DocumentType.DOCX,
                    text,
                    paragraphs.size()
            );
        } catch (Exception ex) {
            throw new DocumentExtractionException("Failed to extract text from DOCX document: " + filename, ex);
        }
    }

    private List<String> extractParagraphs(XWPFDocument document) {
        List<String> extractedParagraphs = new ArrayList<>();
        for (IBodyElement element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    extractedParagraphs.add(text.trim());
                }
            } else if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cellValues = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.isBlank()) {
                            cellValues.add(cellText.trim());
                        }
                    }
                    if (!cellValues.isEmpty()) {
                        extractedParagraphs.add(String.join(" | ", cellValues));
                    }
                }
            }
        }
        return extractedParagraphs;
    }

    private DocumentType determineDocumentType(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            throw new InvalidDocumentException("File has no extension. Only .pdf and .docx files are supported.");
        }
        String extension = filename.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "pdf" -> DocumentType.PDF;
            case "docx" -> DocumentType.DOCX;
            default -> throw new InvalidDocumentException("Unsupported document extension: ." + extension);
        };
    }
}
