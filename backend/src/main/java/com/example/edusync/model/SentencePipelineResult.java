package com.example.edusync.model;

/**
 * Result of the complete analysis pipeline for an individual sentence.
 * Preserves the exact original sentence and holds step-by-step results.
 */
public class SentencePipelineResult {

    private int sentenceId;
    private String originalSentence;
    private PipelineStatus status;
    private SentenceAnalysisResult analysis;
    private SourceVerificationResult verification;
    private ProposedUpdate proposedUpdate;

    public SentencePipelineResult() {
    }

    public SentencePipelineResult(int sentenceId,
                                  String originalSentence,
                                  PipelineStatus status,
                                  SentenceAnalysisResult analysis,
                                  SourceVerificationResult verification,
                                  ProposedUpdate proposedUpdate) {
        this.sentenceId = sentenceId;
        this.originalSentence = originalSentence;
        this.status = status;
        this.analysis = analysis;
        this.verification = verification;
        this.proposedUpdate = proposedUpdate;
    }

    public int getSentenceId() {
        return sentenceId;
    }

    public void setSentenceId(int sentenceId) {
        this.sentenceId = sentenceId;
    }

    public String getOriginalSentence() {
        return originalSentence;
    }

    public void setOriginalSentence(String originalSentence) {
        this.originalSentence = originalSentence;
    }

    public PipelineStatus getStatus() {
        return status;
    }

    public void setStatus(PipelineStatus status) {
        this.status = status;
    }

    public SentenceAnalysisResult getAnalysis() {
        return analysis;
    }

    public void setAnalysis(SentenceAnalysisResult analysis) {
        this.analysis = analysis;
    }

    public SourceVerificationResult getVerification() {
        return verification;
    }

    public void setVerification(SourceVerificationResult verification) {
        this.verification = verification;
    }

    public ProposedUpdate getProposedUpdate() {
        return proposedUpdate;
    }

    public void setProposedUpdate(ProposedUpdate proposedUpdate) {
        this.proposedUpdate = proposedUpdate;
    }

    @Override
    public String toString() {
        return "SentencePipelineResult{" +
                "sentenceId=" + sentenceId +
                ", status=" + status +
                ", originalSentence='" + originalSentence + '\'' +
                '}';
    }
}
