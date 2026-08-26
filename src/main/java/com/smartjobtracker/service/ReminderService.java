package com.smartjobtracker.service;

import com.smartjobtracker.model.Reminder;
import com.smartjobtracker.model.ReminderStatus;
import com.smartjobtracker.repository.ReminderRepository;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Deprecated
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final JavaMailSender mailSender;

    public ReminderService(ReminderRepository reminderRepository, JavaMailSender mailSender) {
        this.reminderRepository = reminderRepository;
        this.mailSender = mailSender;
    }

    @Transactional
    public void sendDueReminders() {
        List<Reminder> due = reminderRepository.findByStatusAndRemindAtBefore(ReminderStatus.PENDING, OffsetDateTime.now());
        for (Reminder r : due) {
            try {
                sendEmailForReminder(r);
                r.setStatus(ReminderStatus.SENT);
                reminderRepository.save(r);
            } catch (Exception ex) {
                // log and leave as pending
            }
        }
    }

    private void sendEmailForReminder(Reminder r) {
        // In a full app we'd resolve the user's email; for now assume message contains recipient placeholder
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo("noreply@example.com");
        msg.setSubject("Job Tracker Reminder: " + r.getType());
        msg.setText(r.getMessage() == null ? "Reminder" : r.getMessage());
        mailSender.send(msg);
    }
}
