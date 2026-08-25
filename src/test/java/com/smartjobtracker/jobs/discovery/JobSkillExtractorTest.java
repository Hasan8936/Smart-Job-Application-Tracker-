package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.model.JobSkill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobSkillExtractorTest {
    @Test
    void extractsOnlyDictionarySkillsAndClassifiesRequiredContext() {
        var skills = new JobSkillExtractor().extract(7L,
                "Required qualifications: Java and SQL. Experience with React is a plus.");
        assertTrue(skills.stream().anyMatch(skill -> skill.getName().equals("Java") && skill.getRequirement().equals("REQUIRED")));
        assertTrue(skills.stream().anyMatch(skill -> skill.getName().equals("SQL") && skill.getRequirement().equals("REQUIRED")));
        assertTrue(skills.stream().anyMatch(skill -> skill.getName().equals("React") && skill.getRequirement().equals("PREFERRED")));
        assertEquals(3, skills.size());
    }
}