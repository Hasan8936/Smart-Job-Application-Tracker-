package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.model.JobSkill;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class JobSkillExtractor {
    private final List<String> dictionary = new ArrayList<>();

    public JobSkillExtractor() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("skills.txt").getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().map(String::trim).filter(value -> !value.isBlank()).forEach(dictionary::add);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load job skill dictionary", exception);
        }
    }

    public List<JobSkill> extract(Long jobPostingId, String description) {
        if (description == null || description.isBlank()) return List.of();
        String lower = description.toLowerCase(Locale.ROOT);
        List<JobSkill> skills = new ArrayList<>();
        for (String skill : dictionary) {
            String normalized = skill.toLowerCase(Locale.ROOT);
            if (!Pattern.compile("(?<![a-z0-9])" + Pattern.quote(normalized) + "(?![a-z0-9])").matcher(lower).find()) continue;
            JobSkill result = new JobSkill();
            result.setJobPostingId(jobPostingId); result.setName(skill); result.setNormalizedName(normalized);
            result.setRequirement(isRequired(lower, normalized) ? "REQUIRED" : "PREFERRED");
            skills.add(result);
        }
        return skills;
    }

    private boolean isRequired(String description, String skill) {
        for (String sentence : description.split("[.!?\\n]+")) {
            if (sentence.contains(skill)) {
                return sentence.matches("(?s).*(required|must have|minimum qualifications|you have).*" );
            }
        }
        return false;
    }
}