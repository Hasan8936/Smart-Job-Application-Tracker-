package com.smartjobtracker.repository;

import com.smartjobtracker.model.Reminder;
import com.smartjobtracker.model.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    List<Reminder> findByStatusAndRemindAtBefore(ReminderStatus status, OffsetDateTime before);

    List<Reminder> findByUserIdAndStatusOrderByRemindAtAsc(Long userId, ReminderStatus status);
}
