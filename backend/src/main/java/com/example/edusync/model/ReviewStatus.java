package com.example.edusync.model;

/**
 * Lifecycle state of a sentence's review process.
 */
public enum ReviewStatus {
    /**
     * Sentence has an eligible grounded proposal awaiting user review.
     */
    PENDING,

    /**
     * User explicitly approved the proposed update.
     */
    APPROVED,

    /**
     * User explicitly rejected the proposed update.
     */
    REJECTED,

    /**
     * Sentence has no valid proposal to review (unchanged, unverified, insufficient evidence, etc.).
     */
    NOT_APPLICABLE
}
