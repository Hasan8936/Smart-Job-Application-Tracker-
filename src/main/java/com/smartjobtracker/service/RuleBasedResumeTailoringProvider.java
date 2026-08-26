package com.smartjobtracker.service;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class RuleBasedResumeTailoringProvider implements ResumeTailoringProvider {
    @Override
    public List<Proposal> suggest(String resumeText, String jobDescription, List<String> atsKeywords) {
        List<Proposal> proposals = new ArrayList<>();
        for (String keyword : atsKeywords) {
            String evidence = findEvidence(resumeText, keyword);
            if (evidence == null) continue;
            proposals.add(new Proposal("ATS_KEYWORD", evidence, evidence,
                    "Highlight this existing resume evidence because it matches a job keyword.", evidence));
        }
        return proposals;
    }

    private String findEvidence(String text, String keyword) {
        for (String line : text.split("\\r?\\n")) {
            if (line.toLowerCase().contains(keyword.toLowerCase())) return line.trim();
        }
        return null;
    }
}