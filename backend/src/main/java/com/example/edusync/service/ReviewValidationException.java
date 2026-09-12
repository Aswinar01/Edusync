package com.example.edusync.service;

/**
 * Thrown when a review decision or review request fails validation
 * (e.g. invalid sentence ID, missing decision, approving an unverified or no-update sentence).
 */
public class ReviewValidationException extends RuntimeException {

    public ReviewValidationException(String message) {
        super(message);
    }

    public ReviewValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
