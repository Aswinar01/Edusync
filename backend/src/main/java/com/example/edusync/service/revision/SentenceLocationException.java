package com.example.edusync.service.revision;

/**
 * Exception thrown when a sentence cannot be deterministically and safely mapped
 * to coordinates or structural elements in the original document.
 */
public class SentenceLocationException extends RevisionException {

    public SentenceLocationException(String message) {
        super(message);
    }

    public SentenceLocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
