package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.repository.JobPostingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ScriptFilterTest {
    private final ScriptFilter arabic = new ScriptFilter(List.of("ARABIC"));

    @Test
    void arabicTitleIsRejected() {
        assertTrue(arabic.isBlocked("مهندس برمجيات أول", null));
    }

    @Test
    void persianAndUrduTitlesAreRejected() {
        assertTrue(arabic.isBlocked("سرآشپز حرفه‌ای، آشپز حرفه‌ای و ساندویچ زن", "Full-time role"), "Persian");
        assertTrue(arabic.isBlocked("سافٹ ویئر انجینئر", null), "Urdu");
        assertTrue(arabic.isBlocked("Senior Chef — سرآشپز", null), "any Arabic-script letter in the title");
    }

    @Test
    void englishPostingWithOneArabicWordInTheDescriptionIsKept() {
        assertFalse(arabic.isBlocked("Software Engineer",
                "Join our team in Dubai (دبي) building payment APIs with Java and Spring Boot for merchants."));
        assertFalse(arabic.isBlocked("Backend Engineer", null));
        assertFalse(arabic.isBlocked(null, ""));
    }

    @Test
    void descriptionIsRejectedOnlyAboveThirtyPercentArabicLetters() {
        // 7 Arabic letters vs 10 Latin letters → ~41%
        String above = "ابحث عنه abcdefghij";
        assertTrue(arabic.blockedLetterPercent(above) > 30);
        assertTrue(arabic.isBlocked("Software Engineer", above));

        // 7 Arabic letters vs 30 Latin letters → ~19%
        String below = "ابحث عنه abcdefghij abcdefghij abcdefghij";
        assertTrue(arabic.blockedLetterPercent(below) < 30);
        assertFalse(arabic.isBlocked("Software Engineer", below));
    }

    @Test
    void digitsAndPunctuationDoNotCountAsLetters() {
        assertEquals(0, arabic.blockedLetterPercent("123 — $120,000 / ٣٤٥"));
    }

    @Test
    void scriptsAreConfigurable() {
        ScriptFilter none = new ScriptFilter(List.of(""));
        assertFalse(none.isBlocked("مهندس برمجيات", null));

        ScriptFilter hebrew = new ScriptFilter(List.of("arabic", " HEBREW "));
        assertTrue(hebrew.isBlocked("מהנדס תוכנה", null));
        assertTrue(hebrew.isBlocked("مهندس", null));
        assertFalse(hebrew.isBlocked("Инженер", null));

        assertThrows(IllegalStateException.class, () -> new ScriptFilter(List.of("KLINGON")));
    }

    @Test
    void backfillFlipsOnlyRowsWhoseStateChanges() {
        JobPosting arabicJob = posting("مهندس برمجيات", false);
        JobPosting englishJob = posting("Backend Engineer", false);
        JobPosting wronglyBlocked = posting("Data Scientist", true);
        JobPostingRepository repo = mock(JobPostingRepository.class);
        when(repo.findAll()).thenReturn(List.of(arabicJob, englishJob, wronglyBlocked));

        assertEquals(2, new ScriptBlockBackfill(repo, arabic).refresh());
        assertTrue(arabicJob.isScriptBlocked());
        assertFalse(wronglyBlocked.isScriptBlocked());
        verify(repo).saveAll(argThat((List<JobPosting> saved) -> saved.size() == 2 && !saved.contains(englishJob)));
        verify(repo, never()).delete(any());
        verify(repo, never()).deleteAll(anyList());
    }

    private static JobPosting posting(String title, boolean blocked) {
        JobPosting p = new JobPosting();
        p.setTitle(title);
        p.setScriptBlocked(blocked);
        return p;
    }
}
