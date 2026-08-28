package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.DeepMatchDtos.*;
import com.smartjobtracker.model.DeepMatchAnalysis;
import com.smartjobtracker.repository.DeepMatchAnalysisRepository;
import com.smartjobtracker.repository.ResumeRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ResumeDeepMatchService {

    private final AnthropicClient anthropicClient;
    private final ObjectMapper mapper;
    private final ResumeRepository resumes;
    private final DeepMatchAnalysisRepository analyses;

    public ResumeDeepMatchService(AnthropicClient anthropicClient, ObjectMapper mapper, ResumeRepository resumes, DeepMatchAnalysisRepository analyses) {
        this.anthropicClient = anthropicClient;
        this.mapper = mapper;
        this.resumes = resumes;
        this.analyses = analyses;
    }

    public DeepMatchResult analyze(Long userId, DeepMatchRequest request) {
        var resume = resumes.findById(request.resumeId()).filter(item -> Objects.equals(item.getUserId(), userId))
                .orElseThrow(() -> new IllegalArgumentException("Resume not found"));
        RecruiterTestResult recruiterTest = runRecruiterTest(resume.getExtractedText(), request.jobDescription());
        DeepMatchAnalysis saved = new DeepMatchAnalysis();
        saved.setUserId(userId); saved.setResumeId(resume.getId()); saved.setJobDescription(request.jobDescription());
        saved.setCompatibilityScore(recruiterTest.compatibilityScore());
        saved.setMissingKeywords(toJson(recruiterTest.missingKeywords())); saved.setRedFlags(toJson(recruiterTest.redFlags()));
        saved = analyses.save(saved);
        return new DeepMatchResult(saved.getId(), resume.getId(), request.jobDescription(), recruiterTest, null, null);
    }

    private RecruiterTestResult runRecruiterTest(String resumeText, String jd) {
        String system = "Take on the role of a lead recruiter for this specific company. Review the attached resume against the "
                + "provided job description. Respond with JSON only — no markdown code fences, no commentary before or after — "
                + "matching exactly this shape: {\"compatibilityScore\": 0, \"missingKeywords\": [\"string\"], \"redFlags\": [\"string\"]}. "
                + "compatibilityScore is an integer from 0 to 100. missingKeywords lists at most the 5 most critical missing keywords. "
                + "redFlags lists at most 3 major red flags a hiring manager would notice within the first 10 seconds.";
        String response = anthropicClient.complete(system, "RESUME:\n" + resumeText + "\n\nJOB DESCRIPTION:\n" + jd, 1024);
        RecruiterTestResult parsed;
        try { parsed = parse(response, RecruiterTestResult.class); } catch (IllegalStateException ignored) { parsed = parseRecruiterText(response); }
        return bound(parsed);
    }

    RecruiterTestResult bound(RecruiterTestResult result) {
        int score = Math.max(0, Math.min(100, result.compatibilityScore()));
        List<String> missing = limit(result.missingKeywords() == null ? List.of() : result.missingKeywords(), 5);
        List<String> flags = limit(result.redFlags() == null ? List.of() : result.redFlags(), 3);
        return new RecruiterTestResult(score, missing, flags);
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse model output as " + type.getSimpleName(), e);
        }
    }

    private RecruiterTestResult parseRecruiterText(String response) {
        Matcher score = Pattern.compile("(?i)(?:score|compatibility)[^0-9]{0,30}(\\d{1,3})").matcher(response);
        if (!score.find()) throw new IllegalStateException("Claude response did not include a compatibility score");
        List<String> missing = sectionItems(response, "missing keywords", "red flags");
        List<String> flags = sectionItems(response, "red flags", null);
        return new RecruiterTestResult(Math.min(100, Integer.parseInt(score.group(1))), limit(missing, 5), limit(flags, 3));
    }

    private List<String> sectionItems(String response, String heading, String nextHeading) {
        String lower = response.toLowerCase();
        int start = lower.indexOf(heading);
        if (start < 0) return List.of();
        int end = nextHeading == null ? response.length() : lower.indexOf(nextHeading, start + heading.length());
        if (end < 0) end = response.length();
        List<String> result = new ArrayList<>();
        for (String line : response.substring(start + heading.length(), end).split("\\r?\\n")) {
            String item = line.replaceFirst("^\\s*(?:[-*]|\\d+[.)])\\s*", "").trim();
            if (!item.isBlank() && !item.matches("(?i)[:#]*")) result.add(item);
        }
        return result;
    }

    private List<String> limit(List<String> values, int count) { return values.size() <= count ? values : values.subList(0, count); }

    private String toJson(List<String> values) { try { return mapper.writeValueAsString(values == null ? List.of() : values); } catch (Exception ex) { throw new IllegalStateException(ex); } }
}