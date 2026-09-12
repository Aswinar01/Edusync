package com.example.edusync.service.revision;

/**
 * Exception thrown when document revision preparation, location resolution, or generation fails.
 */
public class RevisionException extends RuntimeException {

    public RevisionException(String message) {
        super(message);
    }

    public RevisionException(String message, Throwable cause) {
        super(message, cause);
    }
}
