package com.smartjobtracker.service;

import java.util.List;

public interface ResumeTailoringProvider {
    List<Proposal> suggest(String resumeText, String jobDescription, List<String> atsKeywords);

    record Proposal(String category, String beforeText, String afterText, String rationale, String evidenceText) {}
}