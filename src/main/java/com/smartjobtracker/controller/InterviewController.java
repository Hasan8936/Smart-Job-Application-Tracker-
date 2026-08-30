package com.smartjobtracker.controller;

import com.smartjobtracker.model.ApplicationStatus;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.GmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Merges interview-shaped candidates from two sources behind one review surface:
 *  - GMAIL: emails already classified as INTERVIEW_INVITATION by the existing Gmail pipeline
 *    (GmailService.reviewQueue), filtered to interview-shaped ones. Confirm/dismiss delegate
 *    straight to the existing GmailService.review / dismissReviewEmail methods — no second
 *    Gmail confirmation path is introduced here.
 *  - CALENDAR: events flagged by InterviewHeuristic via GmailService.syncCalendar, stored in
 *    interview_candidates. Confirm/dismiss delegate to GmailService's calendar candidate methods.
 *
 * Neither source ever changes an application's status without the user calling /confirm.
 */
@RestController
@RequestMapping("/api/interviews")
public class InterviewController {
    private final GmailService gmailService;
    private final UserRepository users;

    public InterviewController(GmailService gmailService, UserRepository users) {
        this.gmailService = gmailService;
        this.users = users;
    }

    @PostMapping("/sync-calendar")
    public Map<String, Integer> syncCalendar() {
        return Map.of("added", gmailService.syncCalendar(userId()));
    }

    @GetMapping("/candidates")
    public List<CandidateSummary> candidates() {
        Long userId = userId();
        Stream<CandidateSummary> fromGmail = gmailService.reviewQueue(userId).stream()
                .filter(email -> "INTERVIEW_INVITATION".equals(email.getCategory()) || "INTERVIEW".equals(email.getExtractedStatus()))
                .map(email -> new CandidateSummary("GMAIL", email.getId(), email.getSubject(), email.getCompany(),
                        email.getSnippet(), email.getInterviewDate(), email.getInterviewTime(), null, null, null, null));
        Stream<CandidateSummary> fromCalendar = gmailService.calendarCandidates(userId).stream()
                .map(candidate -> new CandidateSummary("CALENDAR", candidate.getId(), candidate.getTitle(), null,
                        candidate.getDescription(), null, null, candidate.getEventStart(), candidate.getEventEnd(),
                        candidate.getSuggestedApplicationId(), candidate.getMatchMethod()));
        return Stream.concat(fromGmail, fromCalendar).toList();
    }

    @PostMapping("/candidates/{source}/{id}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable String source, @PathVariable Long id, @RequestBody ConfirmRequest request) {
        Long userId = userId();
        switch (source.toUpperCase()) {
            case "CALENDAR" -> gmailService.confirmCalendarCandidate(userId, id, request.applicationId(), request.status());
            case "GMAIL" -> gmailService.review(userId, id, request.applicationId(), request.status());
            default -> throw new IllegalArgumentException("Unknown candidate source: " + source);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/candidates/{source}/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable String source, @PathVariable Long id) {
        Long userId = userId();
        switch (source.toUpperCase()) {
            case "CALENDAR" -> gmailService.dismissCalendarCandidate(userId, id);
            case "GMAIL" -> gmailService.dismissReviewEmail(userId, id);
            default -> throw new IllegalArgumentException("Unknown candidate source: " + source);
        }
        return ResponseEntity.noContent().build();
    }

    private Long userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return users.findByEmail(authentication.getName()).map(User::getId).orElseThrow(() -> new IllegalStateException("User not found"));
    }

    public record ConfirmRequest(Long applicationId, ApplicationStatus status) {}

    /** Uniform shape for both sources; fields that don't apply to a given source are left null rather than fabricated. */
    public record CandidateSummary(String source, Long id, String title, String company, String snippet,
                                    String interviewDate, String interviewTime,
                                    OffsetDateTime eventStart, OffsetDateTime eventEnd,
                                    Long suggestedApplicationId, String matchMethod) {}
}
