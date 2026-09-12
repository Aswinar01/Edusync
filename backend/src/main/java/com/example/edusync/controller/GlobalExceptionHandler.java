package com.example.edusync.controller;

import com.example.edusync.model.ErrorResponse;
import com.example.edusync.service.DocumentExtractionException;
import com.example.edusync.service.InvalidDocumentException;
import com.example.edusync.service.ReviewValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(com.example.edusync.service.revision.SentenceLocationException.class)
    public ResponseEntity<ErrorResponse> handleSentenceLocation(com.example.edusync.service.revision.SentenceLocationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("Unprocessable Entity", ex.getMessage()));
    }

    @ExceptionHandler(com.example.edusync.service.revision.RevisionException.class)
    public ResponseEntity<ErrorResponse> handleRevisionException(com.example.edusync.service.revision.RevisionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("Unprocessable Entity", ex.getMessage()));
    }

    @ExceptionHandler(ReviewValidationException.class)
    public ResponseEntity<ErrorResponse> handleReviewValidation(ReviewValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(InvalidDocumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDocument(InvalidDocumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(DocumentExtractionException.class)
    public ResponseEntity<ErrorResponse> handleDocumentExtraction(DocumentExtractionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("Unprocessable Entity", ex.getMessage()));
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleMissingPart(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Bad Request", "File is missing. Please provide a file with field name 'file'."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Bad Request", "File size exceeds maximum allowable limit."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Bad Request", "Request must be multipart/form-data."));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipartException(MultipartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Bad Request", "Invalid multipart request: " + ex.getMessage()));
    }
}
