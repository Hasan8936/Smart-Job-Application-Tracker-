package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.ReminderScheduleRequest;
import com.smartjobtracker.model.Reminder;
import com.smartjobtracker.model.ReminderType;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.ReminderRepository;
import com.smartjobtracker.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IntelligentReminderServiceTest {
    @Test
    void schedulesInterviewOffsetsInUserTimezoneAndIsIdempotent() {
        User user = user();
        ReminderRepository reminders = mock(ReminderRepository.class);
        when(reminders.findByDedupeKey(any())).thenReturn(Optional.empty());
        when(reminders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        IntelligentReminderService service = service(reminders, user);
        ReminderScheduleRequest request = request(ReminderType.INTERVIEW, "America/New_York");

        List<Reminder> first = service.schedule(7L, request);
        assertEquals(2, first.size());
        assertEquals(Instant.parse("2026-09-01T12:00:00Z"), first.get(0).getRemindAt().toInstant());
        assertEquals(Instant.parse("2026-09-02T12:00:00Z"), first.get(0).getEventAt().toInstant());
        verify(reminders, times(2)).save(any(Reminder.class));

        when(reminders.findByDedupeKey(any())).thenReturn(Optional.of(first.get(0)));
        assertTrue(service.schedule(7L, request).isEmpty());
    }

    @Test
    void assessmentUsesThreeDefaultOffsets() {
        ReminderRepository reminders = mock(ReminderRepository.class);
        when(reminders.findByDedupeKey(any())).thenReturn(Optional.empty());
        when(reminders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        IntelligentReminderService service = service(reminders, user());
        List<Reminder> scheduled = service.schedule(7L, request(ReminderType.ASSESSMENT, "UTC"));
        assertEquals(List.of(24, 6, 1), scheduled.stream().map(r -> r.getTriggerOffsetMinutes() / 60).toList());
    }

    @Test
    void disabledPreferenceProducesNoReminders() throws Exception {
        User user = user();
        com.smartjobtracker.dto.ReminderPreferencesDto preferences = new com.smartjobtracker.dto.ReminderPreferencesDto();
        preferences.setAssessmentsEnabled(false);
        user.setReminderPreferences(new ObjectMapper().writeValueAsString(preferences));
        ReminderRepository reminders = mock(ReminderRepository.class);
        IntelligentReminderService service = service(reminders, user);
        assertTrue(service.schedule(7L, request(ReminderType.ASSESSMENT, "UTC")).isEmpty());
        verifyNoInteractions(reminders);
    }

    @Test
    void failedDeliveryIsRetriedAndTracked() {
        Reminder reminder = new Reminder();
        reminder.setId(11L); reminder.setUserId(7L); reminder.setType(ReminderType.INTERVIEW);
        reminder.setRemindAt(Instant.parse("2026-08-25T00:00:00Z").atOffset(ZoneOffset.UTC));
        reminder.setNextAttemptAt(reminder.getRemindAt());
        ReminderRepository reminders = mock(ReminderRepository.class);
        when(reminders.findByStatusInAndRemindAtBefore(any(), any())).thenReturn(List.of(reminder));
        UserRepository users = mock(UserRepository.class); when(users.findById(7L)).thenReturn(Optional.of(user()));
        EmailService email = mock(EmailService.class); doThrow(new RuntimeException("SMTP down")).when(email).sendReminderEmail(any(), any(), any());
        IntelligentReminderService service = new IntelligentReminderService(reminders, users, email, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC));

        service.deliverDueReminders();

        assertEquals(1, reminder.getAttempts());
        assertEquals(com.smartjobtracker.model.ReminderStatus.RETRYING, reminder.getStatus());
        assertEquals("SMTP down", reminder.getLastError());
        verify(reminders).save(reminder);
    }

    private IntelligentReminderService service(ReminderRepository reminders, User user) {
        UserRepository users = mock(UserRepository.class);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        return new IntelligentReminderService(reminders, users, mock(EmailService.class), new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC));
    }

    private User user() {
        User user = new User(); user.setId(7L); user.setEmail("user@example.com"); user.setTimezone("UTC"); return user;
    }

    private ReminderScheduleRequest request(ReminderType type, String timezone) {
        ReminderScheduleRequest request = new ReminderScheduleRequest();
        request.setType(type); request.setTimezone(timezone); request.setEventAt(LocalDateTime.of(2026, 9, 2, 8, 0));
        request.setEventKey("event-1"); return request;
    }
}