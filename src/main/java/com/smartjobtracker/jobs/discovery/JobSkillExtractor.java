package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.model.JobSkill;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class JobSkillExtractor {
    private final List<String> dictionary = new ArrayList<>();
    /** One compiled word-boundary pattern per dictionary skill, built once instead of on every extraction. */
    private final List<Pattern> patterns = new ArrayList<>();

    public JobSkillExtractor() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("skills.txt").getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().map(String::trim).filter(value -> !value.isBlank()).forEach(dictionary::add);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load job skill dictionary", exception);
        }
        for (String skill : dictionary) {
            patterns.add(Pattern.compile("(?<![a-z0-9])" + Pattern.quote(skill.toLowerCase(Locale.ROOT)) + "(?![a-z0-9])"));
        }
    }

    public List<JobSkill> extract(Long jobPostingId, String description) {
        if (description == null || description.isBlank()) return List.of();
        String lower = description.toLowerCase(Locale.ROOT);
        List<JobSkill> skills = new ArrayList<>();
        for (int i = 0; i < dictionary.size(); i++) {
            String skill = dictionary.get(i);
            String normalized = skill.toLowerCase(Locale.ROOT);
            if (!patterns.get(i).matcher(lower).find()) continue;
            JobSkill result = new JobSkill();
            result.setJobPostingId(jobPostingId); result.setName(skill); result.setNormalizedName(normalized);
            result.setRequirement(isRequired(lower, normalized) ? "REQUIRED" : "PREFERRED");
            skills.add(result);
        }
        return skills;
    }

    /** Lower-cased dictionary skills that literally appear in {@code text} (e.g. a resume); nothing is inferred. */
    public Set<String> extractNames(String text) {
        Set<String> names = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return names;
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < dictionary.size(); i++) {
            if (patterns.get(i).matcher(lower).find()) names.add(dictionary.get(i).toLowerCase(Locale.ROOT));
        }
        return names;
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