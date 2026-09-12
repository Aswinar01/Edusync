package com.example.edusync.model;

/**
 * Overall pipeline status for an individual sentence after document orchestration.
 */
public enum PipelineStatus {
    /**
     * Sentence is timeless / foundational and not outdated.
     */
    UNCHANGED,

    /**
     * Sentence was flagged as potentially outdated, but external source could not verify the claim.
     */
    POTENTIALLY_OUTDATED_UNVERIFIED,

    /**
     * Sentence was verified by authoritative source, and a grounded replacement proposal was generated.
     */
    VERIFIED_UPDATE_PROPOSED,

    /**
     * Sentence was verified by authoritative source, but no textual update was needed.
     */
    VERIFIED_NO_UPDATE,

    /**
     * Sentence was verified, but evidence was insufficient to formulate a safe proposal.
     */
    VERIFIED_INSUFFICIENT_EVIDENCE,

    /**
     * Verification source was unavailable (HTTP 4xx/5xx/timeout/network down).
     */
    SOURCE_UNAVAILABLE,

    /**
     * AI analysis service was unavailable.
     */
    AI_UNAVAILABLE,

    /**
     * AI service rate limited.
     */
    RATE_LIMITED,

    /**
     * General error during pipeline execution.
     */
    PIPELINE_ERROR
}
