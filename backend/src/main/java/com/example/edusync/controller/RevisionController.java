package com.example.edusync.controller;

import com.example.edusync.model.DocumentRevisionOutput;
import com.example.edusync.model.DocumentRevisionResult;
import com.example.edusync.model.DocumentType;
import com.example.edusync.service.DocumentRevisionService;
import com.example.edusync.service.DocumentStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for document revision workflows.
 * Operates strictly on server-owned review sessions identified by requestId.
 * Does not accept client-supplied analysis, proposal, or review payloads.
 */
@RestController
@RequestMapping("/api/revisions")
public class RevisionController {

    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final DocumentRevisionService documentRevisionService;
    private final DocumentStorageService documentStorageService;

    public RevisionController(DocumentRevisionService documentRevisionService) {
        this(documentRevisionService, null);
    }

    @Autowired
    public RevisionController(DocumentRevisionService documentRevisionService,
                              @Autowired(required = false) DocumentStorageService documentStorageService) {
        this.documentRevisionService = documentRevisionService;
        this.documentStorageService = documentStorageService;
    }

    /**
     * Prepares document revision instructions from the server-owned review session.
     *
     * @param requestId the review session identifier
     * @return 200 OK with DocumentRevisionResult
     */
    @PostMapping(value = "/{requestId}/prepare", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentRevisionResult> prepareRevision(@PathVariable("requestId") String requestId) {
        DocumentRevisionResult result = documentRevisionService.prepareRevision(requestId);
        return ResponseEntity.ok(result);
    }

    /**
     * Applies approved revision instructions and generates a revised PDF or DOCX document.
     *
     * @param requestId the review session and document identifier
     * @return 200 OK with DocumentRevisionOutput metadata
     */
    @PostMapping(value = "/{requestId}/apply", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentRevisionOutput> applyRevision(@PathVariable("requestId") String requestId) {
        DocumentRevisionOutput output = documentRevisionService.applyRevision(requestId);
        return ResponseEntity.ok(output);
    }

    /**
     * Streams the generated revised document for download.
     *
     * @param requestId the review session and document identifier
     * @return 200 OK with document byte stream and download attachment headers
     */
    @GetMapping(value = "/{requestId}/download")
    public ResponseEntity<byte[]> downloadRevisedDocument(@PathVariable("requestId") String requestId) {
        if (documentStorageService == null) {
            return ResponseEntity.notFound().build();
        }

        DocumentStorageService.StoredDocument doc = documentStorageService.getRevisedDocument(requestId);
        MediaType mediaType = (doc.getDocumentType() == DocumentType.PDF)
                ? MediaType.APPLICATION_PDF
                : DOCX_MEDIA_TYPE;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(doc.getData().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFilename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(doc.getData());
    }
}
