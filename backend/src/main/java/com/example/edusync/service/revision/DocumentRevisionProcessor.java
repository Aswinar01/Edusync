package com.example.edusync.service.revision;

import com.example.edusync.model.DocumentRevisionItem;
import com.example.edusync.model.SentenceReviewItem;

import java.util.List;

/**
 * Strategy interface for document revision processors.
 * Generates revised document bytes without modifying the original document bytes.
 */
public interface DocumentRevisionProcessor {

    /**
     * Applies approved revision items to the original document bytes.
     *
     * @param originalBytes   immutable original document bytes
     * @param approvedItems   list of server-approved revision items to apply
     * @param allSessionItems all sentences in sequential order from the review session for provenance alignment
     * @return revised document bytes
     * @throws RevisionException if location resolution or revision application fails
     */
    byte[] revise(byte[] originalBytes,
                  List<DocumentRevisionItem> approvedItems,
                  List<SentenceReviewItem> allSessionItems) throws RevisionException;
}
