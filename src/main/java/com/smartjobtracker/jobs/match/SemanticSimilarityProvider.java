package com.smartjobtracker.jobs.match;

import java.util.OptionalDouble;

public interface SemanticSimilarityProvider {
    OptionalDouble similarity(String resumeText, String jobText);
}