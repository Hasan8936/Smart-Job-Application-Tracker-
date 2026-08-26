package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.ReminderPreferencesDto;
import com.smartjobtracker.dto.ReminderScheduleRequest;
import com.smartjobtracker.model.Reminder;
import com.smartjobtracker.model.ReminderStatus;
import com.smartjobtracker.model.ReminderType;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.ReminderRepository;
import com.smartjobtracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class IntelligentReminderService {
    private static final Logger log = LoggerFactory.getLogger(IntelligentReminderService.class);
    private static final int MAX_ATTEMPTS = 5;
    private final ReminderRepository reminderRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final NotificationService notificationService;

    public IntelligentReminderService(ReminderRepository reminderRepository, UserRepository userRepository,
                                      EmailService emailService, ObjectMapper objectMapper) {
        this(reminderRepository, userRepository, emailService, objectMapper, Clock.systemUTC(), null);
    }

    @Autowired
    public IntelligentReminderService(ReminderRepository reminderRepository, UserRepository userRepository,
                                      EmailService emailService, ObjectMapper objectMapper,
                                      NotificationService notificationService) {
        this(reminderRepository, userRepository, emailService, objectMapper, Clock.systemUTC(), notificationService);
    }

    IntelligentReminderService(ReminderRepository reminderRepository, UserRepository userRepository,
                               EmailService emailService, ObjectMapper objectMapper, Clock clock) {
        this(reminderRepository, userRepository, emailService, objectMapper, clock, null);
    }

    IntelligentReminderService(ReminderRepository reminderRepository, UserRepository userRepository,
                               EmailService emailService, ObjectMapper objectMapper, Clock clock,
                               NotificationService notificationService) {
        this.reminderRepository = reminderRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.notificationService = notificationService;
    }

    @Transactional
    public List<Reminder> schedule(Long userId, ReminderScheduleRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        ZoneId zone = zone(request.getTimezone() == null || request.getTimezone().isBlank() ? user.getTimezone() : request.getTimezone());
        ReminderPreferencesDto preferences = preferences(user);
        if (!enabled(request.getType(), preferences)) return List.of();
        String eventKey = request.getEventKey() == null || request.getEventKey().isBlank()
                ? "application-" + (request.getApplicationId() == null ? "event" : request.getApplicationId()) : request.getEventKey();
        OffsetDateTime eventAt = request.getEventAt().atZone(zone).toOffsetDateTime();
        List<Integer> offsets = offsets(request.getType(), preferences);
        List<Reminder> result = new ArrayList<>();
        for (Integer offset : offsets) {
            if (offset == null || offset < 0) continue;
            String key = userId + ":" + eventKey + ":" + request.getType() + ":" + offset;
            if (reminderRepository.findByDedupeKey(key).isPresent()) continue;
            Reminder reminder = new Reminder();
            reminder.setUserId(userId);
            reminder.setApplicationId(request.getApplicationId());
            reminder.setType(request.getType());
            reminder.setEventAt(eventAt);
            reminder.setTriggerOffsetMinutes(offset * 60);
            reminder.setTimezone(zone.getId());
            reminder.setRemindAt(eventAt.minusHours(offset));
            reminder.setNextAttemptAt(reminder.getRemindAt());
            reminder.setDedupeKey(key);
            reminder.setMessage(request.getMessage() == null || request.getMessage().isBlank()
                    ? defaultMessage(request.getType(), offset) : request.getMessage());
            result.add(reminderRepository.save(reminder));
        }
        return result;
    }

    @Transactional
    public ReminderPreferencesDto getPreferences(Long userId) {
        return preferences(userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found")));
    }

    @Transactional
    public ReminderPreferencesDto savePreferences(Long userId, ReminderPreferencesDto dto) {
        ZoneId zone = zone(dto.getTimezone());
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        dto.setTimezone(zone.getId());
        try { user.setTimezone(zone.getId()); user.setReminderPreferences(objectMapper.writeValueAsString(dto)); }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid reminder preferences"); }
        userRepository.save(user);
        return dto;
    }

    @Scheduled(fixedDelayString = "${app.reminders.poll-interval-ms:60000}")
    @Transactional
    public void deliverDueReminders() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Reminder> due = reminderRepository.findByStatusInAndRemindAtBefore(
                List.of(ReminderStatus.PENDING, ReminderStatus.RETRYING), now);
        for (Reminder reminder : due) deliver(reminder, now);
    }

    private void deliver(Reminder reminder, OffsetDateTime now) {
        if (reminder.getNextAttemptAt() != null && reminder.getNextAttemptAt().isAfter(now)) return;
        Optional<User> user = userRepository.findById(reminder.getUserId());
        if (user.isEmpty() || user.get().getEmail() == null || user.get().getEmail().isBlank()) {
            fail(reminder, now, "Reminder recipient is unavailable");
            return;
        }
        try {
            emailService.sendReminderEmail(user.get().getEmail(), "Job Tracker Reminder: " + reminder.getType(), reminder.getMessage());
            reminder.setStatus(ReminderStatus.SENT);
            reminder.setSentAt(now);
            reminder.setLastError(null);
            reminderRepository.save(reminder);
                if (notificationService != null) notificationService.enqueueWhatsApp(reminder.getUserId(),
                    "reminder:" + reminder.getDedupeKey(), reminder.getMessage());
        } catch (Exception ex) {
            fail(reminder, now, ex.getMessage() == null ? "Delivery failed" : ex.getMessage());
        }
    }

    private void fail(Reminder reminder, OffsetDateTime now, String error) {
        int attempts = reminder.getAttempts() + 1;
        reminder.setAttempts(attempts);
        reminder.setLastError(error.substring(0, Math.min(error.length(), 1000)));
        if (attempts >= MAX_ATTEMPTS) reminder.setStatus(ReminderStatus.FAILED);
        else { reminder.setStatus(ReminderStatus.RETRYING); reminder.setNextAttemptAt(now.plusMinutes(1L << Math.min(attempts, 6))); }
        reminderRepository.save(reminder);
        log.warn("Reminder {} delivery attempt {} failed", reminder.getId(), attempts);
    }

    private ReminderPreferencesDto preferences(User user) {
        try {
            ReminderPreferencesDto dto = user.getReminderPreferences() == null || user.getReminderPreferences().isBlank()
                    ? new ReminderPreferencesDto() : objectMapper.readValue(user.getReminderPreferences(), ReminderPreferencesDto.class);
            if (dto.getTimezone() == null || dto.getTimezone().isBlank()) dto.setTimezone(user.getTimezone() == null ? "UTC" : user.getTimezone());
            return dto;
        } catch (Exception ex) { return new ReminderPreferencesDto(); }
    }

    private ZoneId zone(String value) {
        try { return ZoneId.of(value == null || value.isBlank() ? "UTC" : value); }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid timezone"); }
    }

    private boolean enabled(ReminderType type, ReminderPreferencesDto p) {
        return switch (type) {
            case INTERVIEW -> p.isInterviewsEnabled();
            case ASSESSMENT -> p.isAssessmentsEnabled();
            case DEADLINE -> p.isDeadlinesEnabled();
            case FOLLOW_UP -> p.isFollowUpsEnabled();
            case CUSTOM -> true;
        };
    }

    private List<Integer> offsets(ReminderType type, ReminderPreferencesDto p) {
        return switch (type) {
            case INTERVIEW -> p.getInterviewOffsetsHours();
            case ASSESSMENT -> p.getAssessmentOffsetsHours();
            case DEADLINE -> p.getDeadlineOffsetsHours();
            case FOLLOW_UP -> p.getFollowUpOffsetsHours();
            case CUSTOM -> List.of(0);
        };
    }

    private String defaultMessage(ReminderType type, int offset) {
        return offset == 0 ? type.name().toLowerCase(Locale.ROOT) + " reminder" :
                type.name().toLowerCase(Locale.ROOT) + " in " + offset + " hours";
    }
}