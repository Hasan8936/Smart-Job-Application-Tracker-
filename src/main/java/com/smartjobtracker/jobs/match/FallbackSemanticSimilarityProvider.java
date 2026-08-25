package com.smartjobtracker.jobs.match;

import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;

@Component
public class FallbackSemanticSimilarityProvider implements SemanticSimilarityProvider {
    @Override public OptionalDouble similarity(String resumeText, String jobText) {
        Set<String> resume = tokens(resumeText); Set<String> job = tokens(jobText);
        if (resume.isEmpty() || job.isEmpty()) return OptionalDouble.of(0.0);
        Set<String> intersection = new HashSet<>(resume); intersection.retainAll(job);
        Set<String> union = new HashSet<>(resume); union.addAll(job);
        return OptionalDouble.of(intersection.size() / (double) union.size());
    }
    private Set<String> tokens(String value) {
        if (value == null) return Set.of();
        return new HashSet<>(Arrays.asList(value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#]+", " ").trim().split("\\s+")));
    }
}