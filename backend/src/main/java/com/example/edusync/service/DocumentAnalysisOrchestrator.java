package com.example.edusync.service;

import com.example.edusync.model.DocumentAnalysisRequest;
import com.example.edusync.model.DocumentAnalysisResult;
import com.example.edusync.model.DocumentSentence;
import com.example.edusync.model.PipelineStatus;
import com.example.edusync.model.ProposalStatus;
import com.example.edusync.model.ProposedUpdate;
import com.example.edusync.model.SentenceAnalysisRequest;
import com.example.edusync.model.SentenceAnalysisResult;
import com.example.edusync.model.SentencePipelineResult;
import com.example.edusync.model.SourceVerificationRequest;
import com.example.edusync.model.SourceVerificationResult;
import com.example.edusync.model.VerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline orchestrator coordinating sentence analysis, source verification, and update proposal generation.
 * Enforces strict verification gates, error isolation, and evidence grounding without mutating original documents.
 */
@Service
public class DocumentAnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DocumentAnalysisOrchestrator.class);

    private final GeminiAnalysisService geminiAnalysisService;
    private final SourceVerificationService sourceVerificationService;
    private final ProposedUpdateService proposedUpdateService;

    public DocumentAnalysisOrchestrator(GeminiAnalysisService geminiAnalysisService,
                                        SourceVerificationService sourceVerificationService,
                                        ProposedUpdateService proposedUpdateService) {
        this.geminiAnalysisService = geminiAnalysisService;
        this.sourceVerificationService = sourceVerificationService;
        this.proposedUpdateService = proposedUpdateService;
    }

    /**
     * Executes the full document analysis pipeline over a set of document sentences.
     */
    public DocumentAnalysisResult analyzeDocument(DocumentAnalysisRequest request) {
        if (request == null || request.getSentences() == null || request.getSentences().isEmpty()) {
            return new DocumentAnalysisResult(
                    request != null ? request.getRequestId() : null,
                    0, 0, 0, 0, 0,
                    new ArrayList<>()
            );
        }

        List<DocumentSentence> sentences = request.getSentences();
        int totalSentences = sentences.size();
        int analyzedCount = 0;
        int potentiallyOutdatedCount = 0;
        int verifiedCount = 0;
        int proposalCount = 0;

        List<SentencePipelineResult> results = new ArrayList<>(totalSentences);

        for (DocumentSentence sentence : sentences) {
            SentencePipelineResult sentenceResult = processSentence(sentence);
            results.add(sentenceResult);

            analyzedCount++;
            if (sentenceResult.getAnalysis() != null && sentenceResult.getAnalysis().isPotentiallyOutdated()) {
                potentiallyOutdatedCount++;
            }
            if (sentenceResult.getVerification() != null && sentenceResult.getVerification().isVerified()) {
                verifiedCount++;
            }
            if (sentenceResult.getProposedUpdate() != null && sentenceResult.getProposedUpdate().getStatus() == ProposalStatus.PROPOSED) {
                proposalCount++;
            }
        }

        return new DocumentAnalysisResult(
                request.getRequestId(),
                totalSentences,
                analyzedCount,
                potentiallyOutdatedCount,
                verifiedCount,
                proposalCount,
                results
        );
    }

    private SentencePipelineResult processSentence(DocumentSentence sentence) {
        int sentenceId = sentence.getSentenceId();
        String originalSentence = sentence.getSentenceText() != null ? sentence.getSentenceText().trim() : "";

        try {
            // Step 1: Gemini AI Sentence Analysis
            SentenceAnalysisRequest analysisRequest = new SentenceAnalysisRequest(sentenceId, originalSentence);
            SentenceAnalysisResult analysisResult = geminiAnalysisService.analyzeSentence(analysisRequest);

            if (analysisResult == null) {
                return new SentencePipelineResult(
                        sentenceId, originalSentence, PipelineStatus.PIPELINE_ERROR, null, null, null
                );
            }

            // Check AI service status
            String aiStatus = analysisResult.getStatus();
            if ("AI_UNAVAILABLE".equals(aiStatus)) {
                return new SentencePipelineResult(
                        sentenceId, originalSentence, PipelineStatus.AI_UNAVAILABLE, analysisResult, null, null
                );
            }
            if ("RATE_LIMITED".equals(aiStatus)) {
                return new SentencePipelineResult(
                        sentenceId, originalSentence, PipelineStatus.RATE_LIMITED, analysisResult, null, null
                );
            }
            if ("ANALYSIS_FAILED".equals(aiStatus)) {
                return new SentencePipelineResult(
                        sentenceId, originalSentence, PipelineStatus.PIPELINE_ERROR, analysisResult, null, null
                );
            }

            // Verification Gate 1: Check if potentially outdated
            if (!analysisResult.isPotentiallyOutdated()) {
                // Timeless/definition concept: do NOT invoke source verification
                return new SentencePipelineResult(
                        sentenceId, originalSentence, PipelineStatus.UNCHANGED, analysisResult, null, null
                );
            }

            // Step 2: Source Verification for potentially outdated sentence
            SourceVerificationRequest verificationRequest = new SourceVerificationRequest(
                    sentenceId, originalSentence, analysisResult.getReason()
            );
            SourceVerificationResult verificationResult = sourceVerificationService.verifySource(verificationRequest);

            if (verificationResult == null) {
                return new SentencePipelineResult(
                        sentenceId, originalSentence, PipelineStatus.PIPELINE_ERROR, analysisResult, null, null
                );
            }

            // Verification Gate 2: Check if verified by external authoritative source
            if (verificationResult.getVerificationStatus() == VerificationStatus.SOURCE_UNAVAILABLE) {
                return new SentencePipelineResult(
                        sentenceId, originalSentence, PipelineStatus.SOURCE_UNAVAILABLE, analysisResult, verificationResult, null
                );
            }

            if (verificationResult.getVerificationStatus() != VerificationStatus.VERIFIED) {
                // Unverified or failed verification: do NOT invoke proposed update generator
                return new SentencePipelineResult(
                        sentenceId, originalSentence, PipelineStatus.POTENTIALLY_OUTDATED_UNVERIFIED, analysisResult, verificationResult, null
                );
            }

            // Step 3: Grounded Proposed Update Generation (only reached when verificationStatus == VERIFIED)
            ProposedUpdate proposedUpdate = proposedUpdateService.generateProposal(
                    sentence, analysisResult, verificationResult
            );

            PipelineStatus finalStatus;
            if (proposedUpdate != null && proposedUpdate.getStatus() == ProposalStatus.PROPOSED) {
                finalStatus = PipelineStatus.VERIFIED_UPDATE_PROPOSED;
            } else if (proposedUpdate != null && proposedUpdate.getStatus() == ProposalStatus.NO_UPDATE) {
                finalStatus = PipelineStatus.VERIFIED_NO_UPDATE;
            } else if (proposedUpdate != null && proposedUpdate.getStatus() == ProposalStatus.INSUFFICIENT_EVIDENCE) {
                finalStatus = PipelineStatus.VERIFIED_INSUFFICIENT_EVIDENCE;
            } else {
                finalStatus = PipelineStatus.PIPELINE_ERROR;
            }

            return new SentencePipelineResult(
                    sentenceId, originalSentence, finalStatus, analysisResult, verificationResult, proposedUpdate
            );

        } catch (Exception ex) {
            log.error("Unexpected error processing sentence {}: {}", sentenceId, ex.getMessage(), ex);
            return new SentencePipelineResult(
                    sentenceId, originalSentence, PipelineStatus.PIPELINE_ERROR, null, null, null
            );
        }
    }
}
