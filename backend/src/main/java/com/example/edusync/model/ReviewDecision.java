package com.example.edusync.model;

/**
 * Explicit human review decisions on proposed updates.
 */
public enum ReviewDecision {
    /**
     * The user explicitly approves the proposed update for the sentence.
     */
    APPROVE,

    /**
     * The user explicitly rejects the proposed update, retaining the original sentence.
     */
    REJECT
}
