package com.smartjobtracker.service;

import com.smartjobtracker.model.InterviewQuestionCategory;
import java.util.List;

public interface InterviewPrepProvider {
    List<QuestionAnswer> generate(String jobDescription, FactProfile facts, int count);

    /** Resume facts only -- not JD text -- so answers can never be grounded in the job posting. */
    record FactProfile(String name, String education, String experience, String skills, String projects) {}

    record QuestionAnswer(InterviewQuestionCategory category, String question, String suggestedAnswer, String sourceEvidence) {}
}
