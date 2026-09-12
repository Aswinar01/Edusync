package com.example.edusync.controller;

import com.example.edusync.model.DocumentRevisionResult;
import com.example.edusync.service.DocumentRevisionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for document revision preparation.
 * Operates strictly on server-owned review sessions identified by requestId.
 * Does not accept client-supplied analysis, proposal, or review payloads.
 */
@RestController
@RequestMapping("/api/revisions")
public class RevisionController {

    private final DocumentRevisionService documentRevisionService;

    public RevisionController(DocumentRevisionService documentRevisionService) {
        this.documentRevisionService = documentRevisionService;
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
}
