package com.smartjobtracker.service;

import com.smartjobtracker.model.ApplicationFieldType;
import java.util.List;

public interface ApplicationPreparationProvider {
    List<Proposal> suggest(String jobDescription, FactProfile facts, List<FieldRequest> fields);
    record FieldRequest(String externalField, ApplicationFieldType fieldType) {}
    record FactProfile(String name, String email, String phone, String education, String experience, String skills, String github, String linkedin, String portfolio, String resumeFileName) {}
    record Proposal(String externalField, ApplicationFieldType fieldType, String value, String evidence, String rationale) {}
}