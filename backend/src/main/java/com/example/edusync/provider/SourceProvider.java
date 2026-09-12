package com.example.edusync.provider;

import com.example.edusync.model.SourceEvidence;
import com.example.edusync.model.SourceVerificationRequest;

/**
 * Pluggable provider abstraction for retrieving factual evidence from external sources.
 */
public interface SourceProvider {

    /**
     * Unique identifier for this provider.
     */
    String getProviderName();

    /**
     * Determines whether this provider can handle the factual verification request.
     */
    boolean supports(SourceVerificationRequest request);

    /**
     * Retrieves candidate evidence for the given verification request.
     */
    SourceEvidence fetchEvidence(SourceVerificationRequest request);
}
