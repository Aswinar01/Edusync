package com.example.edusync.service;

import com.example.edusync.model.DocumentRevisionItem;
import com.example.edusync.model.DocumentRevisionOutput;
import com.example.edusync.model.DocumentRevisionResult;
import com.example.edusync.model.DocumentReviewResult;
import com.example.edusync.model.DocumentType;
import com.example.edusync.model.ReviewStatus;
import com.example.edusync.model.RevisionStatus;
import com.example.edusync.model.SentenceReviewItem;
import com.example.edusync.service.revision.DocxRevisionProcessor;
import com.example.edusync.service.revision.DocumentRevisionProcessor;
import com.example.edusync.service.revision.PdfRevisionProcessor;
import com.example.edusync.service.revision.RevisionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for preparing document revision instructions and applying approved updates
 * to generate revised PDF and DOCX documents.
 * Strictly operates on server-owned review sessions and stored documents.
 * Never modifies or overwrites the original uploaded documents.
 */
@Service
public class DocumentRevisionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRevisionService.class);

    private final ReviewService reviewService;
    private final DocumentStorageService documentStorageService;
    private final PdfRevisionProcessor pdfRevisionProcessor;
    private final DocxRevisionProcessor docxRevisionProcessor;

    public DocumentRevisionService(ReviewService reviewService) {
        this(reviewService, null, null, null);
    }

    @Autowired
    public DocumentRevisionService(ReviewService reviewService,
                                   DocumentStorageService documentStorageService,
                                   PdfRevisionProcessor pdfRevisionProcessor,
                                   DocxRevisionProcessor docxRevisionProcessor) {
        this.reviewService = reviewService;
        this.documentStorageService = documentStorageService;
        this.pdfRevisionProcessor = pdfRevisionProcessor;
        this.docxRevisionProcessor = docxRevisionProcessor;
    }

    /**
     * Prepares revision instructions for the specified requestId by inspecting the server-owned review session.
     * Only sentences with ReviewStatus.APPROVED are converted into revision items.
     *
     * @param requestId unique identifier of the review session
     * @return DocumentRevisionResult containing revision items and status
     */
    public DocumentRevisionResult prepareRevision(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new ReviewValidationException("Request ID cannot be null or empty.");
        }

        // Retrieve server-owned review session (throws ReviewValidationException if not found)
        DocumentReviewResult reviewSession = reviewService.getReviewSession(requestId);

        int totalSentences = reviewSession.getTotalSentences();
        List<SentenceReviewItem> sessionItems = reviewSession.getItems();

        List<DocumentRevisionItem> approvedRevisionItems = new ArrayList<>();

        if (sessionItems != null) {
            for (SentenceReviewItem item : sessionItems) {
                if (item != null && item.getReviewStatus() == ReviewStatus.APPROVED) {
                    approvedRevisionItems.add(DocumentRevisionItem.fromApprovedSentence(item));
                }
            }
        }

        if (approvedRevisionItems.isEmpty()) {
            log.info("No approved updates found for review session '{}' (out of {} sentences).",
                    requestId, totalSentences);
            return DocumentRevisionResult.noApprovedUpdates(requestId, totalSentences);
        }

        log.info("Prepared {} revision instruction(s) for review session '{}' (out of {} sentences).",
                approvedRevisionItems.size(), requestId, totalSentences);
        return DocumentRevisionResult.ready(requestId, totalSentences, approvedRevisionItems);
    }

    /**
     * Applies approved revision items to generate a revised PDF or DOCX document.
     * Operates strictly on server-stored original documents and server-owned review sessions.
     *
     * @param requestId unique identifier of the review session and document
     * @return metadata output describing the generated revised document
     */
    public DocumentRevisionOutput applyRevision(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new ReviewValidationException("Request ID cannot be null or empty.");
        }

        if (documentStorageService == null) {
            throw new RevisionException("Document storage service is not configured.");
        }

        DocumentReviewResult reviewSession = reviewService.getReviewSession(requestId);
        DocumentRevisionResult prepared = prepareRevision(requestId);

        DocumentStorageService.StoredDocument originalDoc = documentStorageService.getOriginalDocument(requestId);
        DocumentType docType = originalDoc.getDocumentType();
        String originalFilename = originalDoc.getFilename();
        String revisedFilename = "revised-" + originalFilename;

        byte[] revisedBytes;
        if (prepared.getRevisionStatus() == RevisionStatus.NO_APPROVED_UPDATES
                || prepared.getRevisionItems().isEmpty()) {
            log.info("No approved updates to apply for requestId '{}'. Generating unchanged copy.", requestId);
            revisedBytes = originalDoc.getData();
        } else {
            DocumentRevisionProcessor processor = switch (docType) {
                case PDF -> pdfRevisionProcessor;
                case DOCX -> docxRevisionProcessor;
            };

            if (processor == null) {
                throw new RevisionException("No revision processor available for document type: " + docType);
            }

            revisedBytes = processor.revise(
                    originalDoc.getData(),
                    prepared.getRevisionItems(),
                    reviewSession.getItems()
            );
        }

        documentStorageService.storeRevisedDocument(requestId, revisedFilename, docType, revisedBytes);

        String downloadUrl = "/api/revisions/" + requestId + "/download";
        return new DocumentRevisionOutput(
                requestId,
                originalFilename,
                revisedFilename,
                docType,
                prepared.getRevisionStatus(),
                prepared.getApprovedUpdateCount(),
                revisedBytes.length,
                Instant.now().toString(),
                downloadUrl
        );
    }
}
