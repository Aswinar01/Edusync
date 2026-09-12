package com.example.edusync.service;

import com.example.edusync.model.DocumentUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;

@Service
public class DocumentService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx");

    private static final Set<String> PDF_MIME_TYPES = Set.of(
            "application/pdf",
            "application/x-pdf",
            "application/acrobat",
            "applications/vnd.pdf",
            "text/pdf"
    );

    private static final Set<String> DOCX_MIME_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/x-vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream"
    );

    public DocumentUploadResponse validateAndProcess(MultipartFile file) {
        if (file == null) {
            throw new InvalidDocumentException("File is missing. Please provide a file with field name 'file'.");
        }

        if (file.isEmpty() || file.getSize() == 0) {
            throw new InvalidDocumentException("File is empty.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new InvalidDocumentException("Filename is missing.");
        }

        // Sanitize filename to prevent directory traversal and null-byte attacks
        String cleanFilename = Paths.get(originalFilename).getFileName().toString().trim();
        if (cleanFilename.isEmpty() || cleanFilename.contains("\0")) {
            throw new InvalidDocumentException("Filename is invalid.");
        }

        int lastDotIndex = cleanFilename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == cleanFilename.length() - 1) {
            throw new InvalidDocumentException("File has no extension. Only .pdf and .docx files are supported.");
        }

        String extension = cleanFilename.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new InvalidDocumentException("Unsupported file extension: ." + extension + ". Only .pdf and .docx files are supported.");
        }

        String rawContentType = file.getContentType();
        if (rawContentType == null || rawContentType.trim().isEmpty()) {
            throw new InvalidDocumentException("Content-Type header is missing.");
        }

        // Extract base media type (e.g. application/pdf from application/pdf; charset=UTF-8)
        String mediaType = rawContentType.split(";")[0].trim().toLowerCase(Locale.ROOT);

        if ("pdf".equals(extension)) {
            if (!PDF_MIME_TYPES.contains(mediaType)) {
                throw new InvalidDocumentException("Invalid Content-Type '" + rawContentType + "' for PDF document. Expected 'application/pdf'.");
            }
        } else if ("docx".equals(extension)) {
            if (!DOCX_MIME_TYPES.contains(mediaType)) {
                throw new InvalidDocumentException("Invalid Content-Type '" + rawContentType + "' for DOCX document. Expected 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'.");
            }
        }

        return new DocumentUploadResponse(
                cleanFilename,
                file.getSize(),
                rawContentType,
                "File uploaded and validated successfully."
        );
    }
}
