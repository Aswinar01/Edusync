package com.example.edusync.controller;

import com.example.edusync.model.DocumentUploadResponse;
import com.example.edusync.service.DocumentService;
import com.example.edusync.service.InvalidDocumentException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam(value = "file", required = false) MultipartFile file) {
        if (file == null) {
            throw new InvalidDocumentException("File is missing. Please provide a file with field name 'file'.");
        }
        DocumentUploadResponse response = documentService.validateAndProcess(file);
        return ResponseEntity.ok(response);
    }
}
