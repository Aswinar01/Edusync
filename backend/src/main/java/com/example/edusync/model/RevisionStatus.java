package com.example.edusync.model;

/**
 * Lifecycle status of document revision preparation.
 */
public enum RevisionStatus {

    /**
     * One or more approved updates exist and revision instructions are ready.
     */
    READY,

    /**
     * The review session contains zero approved updates. This is a valid state, not an error.
     */
    NO_APPROVED_UPDATES,

    /**
     * An unexpected failure occurred during revision instruction preparation.
     */
    REVISION_FAILED
}
