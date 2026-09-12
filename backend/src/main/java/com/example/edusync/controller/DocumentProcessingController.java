package com.example.edusync.controller;

import com.example.edusync.model.DocumentProcessingResult;
import com.example.edusync.service.DocumentProcessingService;
import com.example.edusync.service.InvalidDocumentException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST endpoint for end-to-end document processing.
 * Accepts a PDF or DOCX file and runs the complete validation, extraction, segmentation,
 * analysis, and proposed-update generation pipeline.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentProcessingController {

    private final DocumentProcessingService documentProcessingService;

    public DocumentProcessingController(DocumentProcessingService documentProcessingService) {
        this.documentProcessingService = documentProcessingService;
    }

    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentProcessingResult> processDocument(
            @RequestParam(value = "file", required = false) MultipartFile file) {
        if (file == null) {
            throw new InvalidDocumentException("File is missing. Please provide a file with field name 'file'.");
        }

        DocumentProcessingResult result = documentProcessingService.processDocument(file);
        return ResponseEntity.ok(result);
    }
}
