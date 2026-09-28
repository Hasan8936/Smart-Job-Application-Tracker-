package com.smartjobtracker.service;

import com.smartjobtracker.model.InterviewQuestionCategory;
import java.util.List;

public interface InterviewPrepProvider {
    List<QuestionAnswer> generate(String jobDescription, FactProfile facts, int count);

    /**
     * Resume facts only -- not JD text -- so answers can never be grounded in the job posting.
     * resumeText is the candidate's own extracted resume (bounded by the caller); the structured fields are
     * the extractor's summary of it. Answers may only restate what appears here.
     */
    record FactProfile(String name, String education, String experience, String skills, String projects, String resumeText) {
        public FactProfile(String name, String education, String experience, String skills, String projects) {
            this(name, education, experience, skills, projects, null);
        }
    }

    record QuestionAnswer(InterviewQuestionCategory category, String question, String suggestedAnswer, String sourceEvidence) {}
}
