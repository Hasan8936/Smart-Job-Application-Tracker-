package com.smartjobtracker.service;

import com.smartjobtracker.dto.MatchResponse;
import com.smartjobtracker.model.Resume;
import com.smartjobtracker.repository.ResumeRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KeywordMatchService {

    private final ResumeRepository resumeRepository;
    private Set<String> skillSet = new HashSet<>();
    private final Set<String> stopwords = Set.of("and","or","the","a","an","to","for","with","in","on","of","by","is","are","as","at","from");

    public KeywordMatchService(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    @PostConstruct
    public void loadSkills() throws Exception {
        ClassPathResource res = new ClassPathResource("skills.txt");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(res.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.strip().isEmpty()) continue;
                skillSet.add(line.strip().toLowerCase());
            }
        }
    }

    public MatchResponse score(Long resumeId, String jobDescriptionText) {
        Optional<Resume> resumeOpt = resumeRepository.findById(resumeId);
        String resumeText = resumeOpt.map(Resume::getExtractedText).orElse("");

        String resumeNormalized = (resumeText == null ? "" : resumeText).toLowerCase();
        String jobNormalized = (jobDescriptionText == null ? "" : jobDescriptionText).toLowerCase();

        Set<String> resumeSkills = skillSet.stream().filter(s -> resumeNormalized.contains(s)).collect(Collectors.toSet());
        Set<String> jobSkills = skillSet.stream().filter(s -> jobNormalized.contains(s)).collect(Collectors.toSet());

        Set<String> matched = new TreeSet<>();
        for (String s : jobSkills) if (resumeSkills.contains(s)) matched.add(s);

        Set<String> missing = new TreeSet<>();
        for (String s : jobSkills) if (!resumeSkills.contains(s)) missing.add(s);

        double score = 0.0;
        if (!jobSkills.isEmpty()) {
            score = (matched.size() * 100.0) / jobSkills.size();
        }

        return new MatchResponse(score, new ArrayList<>(matched), new ArrayList<>(missing));
    }

    private Set<String> tokenizeText(String text) {
        if (text == null) return Collections.emptySet();
        String cleaned = text.replaceAll("[^A-Za-z0-9+#\\.\\- ]", " ").toLowerCase();
        String[] parts = cleaned.split("\\s+");
        Set<String> toks = new HashSet<>();
        for (String p : parts) {
            if (p.isBlank()) continue;
            if (stopwords.contains(p)) continue;
            toks.add(p.trim());
        }
        return toks;
    }
}
