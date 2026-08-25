package com.smartjobtracker.jobs.match;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FallbackSemanticSimilarityProviderTest {
    @Test
    void returnsBoundedTokenSimilarityWhenNoAiProviderIsAvailable() {
        double score = new FallbackSemanticSimilarityProvider()
                .similarity("Java Spring API", "Java Spring Cloud").orElseThrow();
        assertEquals(0.5, score, 0.0001);
    }
}