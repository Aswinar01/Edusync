package com.example.edusync.model;

/**
 * Status of the proposed update generation for a verified sentence.
 */
public enum ProposalStatus {
    /**
     * A concrete replacement sentence was generated and is grounded in the verified evidence.
     */
    PROPOSED,

    /**
     * The verified evidence indicates that the original sentence does not require a textual update.
     */
    NO_UPDATE,

    /**
     * The source was VERIFIED, but the available evidence is insufficient to safely formulate a replacement.
     */
    INSUFFICIENT_EVIDENCE,

    /**
     * The proposal-generation operation failed due to Gemini/API/parsing/internal failure.
     */
    GENERATION_FAILED
}
