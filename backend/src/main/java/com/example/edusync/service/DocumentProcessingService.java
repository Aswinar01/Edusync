package com.example.edusync.service;

import com.example.edusync.model.DocumentAnalysisRequest;
import com.example.edusync.model.DocumentAnalysisResult;
import com.example.edusync.model.DocumentProcessingResult;
import com.example.edusync.model.DocumentSentence;
import com.example.edusync.model.TextExtractionResult;
import com.example.edusync.model.DocumentReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * End-to-end document processing service.
 * Coordinates document validation, text extraction, sentence segmentation, analysis orchestration,
 * and server-side review session creation.
 * Does not mutate original documents or call AI/verification services directly.
 */
@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentService documentService;
    private final TextExtractionService textExtractionService;
    private final SentenceSegmentationService sentenceSegmentationService;
    private final DocumentAnalysisOrchestrator documentAnalysisOrchestrator;
    private final ReviewService reviewService;
    private final DocumentStorageService documentStorageService;

    public DocumentProcessingService(DocumentService documentService,
                                     TextExtractionService textExtractionService,
                                     SentenceSegmentationService sentenceSegmentationService,
                                     DocumentAnalysisOrchestrator documentAnalysisOrchestrator) {
        this(documentService, textExtractionService, sentenceSegmentationService, documentAnalysisOrchestrator, null, null);
    }

    public DocumentProcessingService(DocumentService documentService,
                                     TextExtractionService textExtractionService,
                                     SentenceSegmentationService sentenceSegmentationService,
                                     DocumentAnalysisOrchestrator documentAnalysisOrchestrator,
                                     ReviewService reviewService) {
        this(documentService, textExtractionService, sentenceSegmentationService, documentAnalysisOrchestrator, reviewService, null);
    }

    @Autowired
    public DocumentProcessingService(DocumentService documentService,
                                     TextExtractionService textExtractionService,
                                     SentenceSegmentationService sentenceSegmentationService,
                                     DocumentAnalysisOrchestrator documentAnalysisOrchestrator,
                                     ReviewService reviewService,
                                     DocumentStorageService documentStorageService) {
        this.documentService = documentService;
        this.textExtractionService = textExtractionService;
        this.sentenceSegmentationService = sentenceSegmentationService;
        this.documentAnalysisOrchestrator = documentAnalysisOrchestrator;
        this.reviewService = reviewService;
        this.documentStorageService = documentStorageService;
    }

    /**
     * Executes the complete document processing pipeline for an uploaded PDF or DOCX file.
     *
     * @param file the uploaded multipart file
     * @return review-ready structured processing result
     */
    public DocumentProcessingResult processDocument(MultipartFile file) {
        // Step 1: Validate document (size, filename, extension, MIME type)
        documentService.validateAndProcess(file);

        // Step 2: Extract text from PDF/DOCX
        TextExtractionResult extractionResult = textExtractionService.extractText(file);

        String extractedText = extractionResult.getExtractedText();
        if (extractedText == null || extractedText.isBlank()) {
            log.warn("Document '{}' extracted successfully but contains no readable text.", extractionResult.getFilename());
            throw new DocumentExtractionException("Document contains no readable text: " + extractionResult.getFilename());
        }

        // Step 3: Segment extracted text into sentences
        List<DocumentSentence> sentences = sentenceSegmentationService.segment(extractionResult);
        if (sentences == null || sentences.isEmpty()) {
            log.warn("Document '{}' contains text but could not be segmented into sentences.", extractionResult.getFilename());
            throw new DocumentExtractionException("Document contains no readable sentences: " + extractionResult.getFilename());
        }

        // Step 4: Generate a unique requestId for tracing
        String requestId = UUID.randomUUID().toString();
        log.info("Processing document '{}' ({}) with requestId '{}' - {} sentences extracted.",
                extractionResult.getFilename(), extractionResult.getDocumentType(), requestId, sentences.size());

        // Step 4b: Store original document bytes immutably for revision workflows
        if (documentStorageService != null) {
            try {
                byte[] fileBytes = file.getBytes();
                documentStorageService.storeOriginalDocument(
                        requestId,
                        extractionResult.getFilename(),
                        extractionResult.getDocumentType(),
                        fileBytes
                );
            } catch (Exception ex) {
                log.warn("Failed to store original document for requestId '{}': {}", requestId, ex.getMessage());
                throw new DocumentExtractionException("Failed to read document bytes for storage: " + extractionResult.getFilename(), ex);
            }
        }

        // Step 5: Execute orchestrated sentence analysis through the pipeline
        DocumentAnalysisRequest analysisRequest = new DocumentAnalysisRequest(requestId, sentences);
        DocumentAnalysisResult analysisResult = documentAnalysisOrchestrator.analyzeDocument(analysisRequest);

        // Step 6: Create server-side in-memory review session for human review & approval
        DocumentReviewResult reviewResult = null;
        if (reviewService != null) {
            reviewResult = reviewService.createReviewSession(analysisResult);
            if (reviewResult != null) {
                log.info("Initialized server-side review session for requestId '{}' ({} reviewable).",
                        requestId, reviewResult.getReviewableCount());
            }
        }

        // Step 7: Assemble review-ready document processing result
        return new DocumentProcessingResult(
                requestId,
                extractionResult.getFilename(),
                extractionResult.getDocumentType(),
                extractedText.length(),
                sentences.size(),
                analysisResult,
                reviewResult
        );
    }
}
