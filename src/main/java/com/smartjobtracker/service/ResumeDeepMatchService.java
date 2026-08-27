package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.DeepMatchDtos.*;
import org.springframework.stereotype.Service;

@Service
public class ResumeDeepMatchService {

    private final AnthropicClient anthropicClient;
    private final ObjectMapper mapper;

    public ResumeDeepMatchService(AnthropicClient anthropicClient, ObjectMapper mapper) {
        this.anthropicClient = anthropicClient;
        this.mapper = mapper;
    }

    public DeepMatchResult analyze(String resumeText, String jobDescription) {
        RecruiterTestResult stage1 = runRecruiterTest(resumeText, jobDescription);
        XyzRewriteResult stage2 = runXyzRewrite(resumeText, stage1);
        AtsFilterResult stage3 = runAtsFilter(stage2, jobDescription);
        return new DeepMatchResult(stage1, stage2, stage3);
    }

    private RecruiterTestResult runRecruiterTest(String resumeText, String jd) {
        String system = """
                You are the lead technical recruiter hiring for this exact role.
                Review the candidate's resume against the job description.

                Return ONLY valid JSON, no markdown fences, matching exactly:
                {"compatibilityScore": <0-100 integer>,"missingKeywords": [<exactly 5 strings>],"redFlags": [<exactly 3 strings>]}
                """;
        String json = anthropicClient.complete(system, "RESUME:\n" + resumeText + "\n\nJOB DESCRIPTION:\n" + jd, 1024);
        return parse(json, RecruiterTestResult.class);
    }

    private XyzRewriteResult runXyzRewrite(String resumeText, RecruiterTestResult stage1) {
        String system = """
                You are a resume writer using the Google XYZ framework: "Accomplished [X] as measured by [Y], by doing [Z]."
                Rewrite the candidate's Experience, Projects, and Skills sections. Do not invent employers, dates,
                technologies, or metrics not present in the source resume. Return ONLY valid JSON matching exactly:
                {"rewrittenExperience":"...","rewrittenProjects":"...","rewrittenSkills":"..."}
                """;
        String user = """
                ORIGINAL RESUME:
                %s

                MISSING KEYWORDS TO INTEGRATE: %s
                RED FLAGS TO ELIMINATE: %s
                """.formatted(resumeText, stage1.missingKeywords(), stage1.redFlags());
        return parse(anthropicClient.complete(system, user, 2048), XyzRewriteResult.class);
    }

    private AtsFilterResult runAtsFilter(XyzRewriteResult stage2, String jd) {
        String system = """
                Act as a strict ATS parser and a hiring manager skimming resumes. Identify weak rewritten sections
                against the job description and provide replacements in the same order. Return ONLY valid JSON:
                {"flaggedSections":["..."],"fixedSections":["..."]}
                """;
        String user = """
                REWRITTEN RESUME:
                Experience: %s
                Projects: %s
                Skills: %s

                JOB DESCRIPTION:
                %s
                """.formatted(stage2.rewrittenExperience(), stage2.rewrittenProjects(), stage2.rewrittenSkills(), jd);
        return parse(anthropicClient.complete(system, user, 1536), AtsFilterResult.class);
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse model output as " + type.getSimpleName(), e);
        }
    }
}