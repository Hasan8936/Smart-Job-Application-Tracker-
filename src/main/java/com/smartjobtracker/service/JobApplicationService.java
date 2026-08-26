package com.smartjobtracker.service;

import com.smartjobtracker.model.ApplicationStatus;
import com.smartjobtracker.model.ApplicationStatusHistory;
import com.smartjobtracker.model.JobApplication;
import com.smartjobtracker.repository.ApplicationStatusHistoryRepository;
import com.smartjobtracker.repository.JobApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class JobApplicationService {

    private final JobApplicationRepository appRepo;
    private final ApplicationStatusHistoryRepository historyRepo;

    public JobApplicationService(JobApplicationRepository appRepo, ApplicationStatusHistoryRepository historyRepo) {
        this.appRepo = appRepo;
        this.historyRepo = historyRepo;
    }

    public List<JobApplication> listByUser(Long userId) {
        return appRepo.findByUserId(userId);
    }

    /**
     * Returns the application only if it belongs to userId — otherwise empty,
     * so a caller can't view another user's application by guessing an id.
     */
    public Optional<JobApplication> get(Long id, Long userId) {
        return appRepo.findById(id).filter(a -> a.getUserId().equals(userId));
    }

    @Transactional
    public JobApplication create(JobApplication app) {
        JobApplication saved = appRepo.save(app);
        if (saved.getStatus() != null) {
            ApplicationStatusHistory h = new ApplicationStatusHistory();
            h.setApplicationId(saved.getId());
            h.setStatus(saved.getStatus());
            historyRepo.save(h);
        }
        return saved;
    }

    @Transactional
    public JobApplication update(Long id, Long userId, JobApplication update) {
        JobApplication existing = findOwnedOrThrow(id, userId);
        ApplicationStatus old = existing.getStatus();
        existing.setCompanyName(update.getCompanyName());
        existing.setRoleTitle(update.getRoleTitle());
        existing.setJobDescription(update.getJobDescription());
        existing.setAppliedDate(update.getAppliedDate());
        existing.setStatus(update.getStatus());
        JobApplication saved = appRepo.save(existing);
        if (update.getStatus() != null && update.getStatus() != old) {
            ApplicationStatusHistory h = new ApplicationStatusHistory();
            h.setApplicationId(saved.getId());
            h.setStatus(saved.getStatus());
            historyRepo.save(h);
        }
        return saved;
    }

    public void delete(Long id, Long userId) {
        findOwnedOrThrow(id, userId);
        appRepo.deleteById(id);
    }

    @Transactional
    public ApplicationStatusHistory changeStatus(Long applicationId, Long userId, ApplicationStatus status, String remark) {
        JobApplication app = findOwnedOrThrow(applicationId, userId);
        app.setStatus(status);
        appRepo.save(app);
        ApplicationStatusHistory h = new ApplicationStatusHistory();
        h.setApplicationId(applicationId);
        h.setStatus(status);
        h.setRemark(remark);
        return historyRepo.save(h);
    }

    @Transactional
    public ApplicationStatusHistory changeStatus(Long applicationId, Long userId, ApplicationStatus status,
                                                  String remark, String source, Long sourceEmailId,
                                                  Double confidence) {
        JobApplication app = findOwnedOrThrow(applicationId, userId);
        app.setStatus(status);
        appRepo.save(app);
        ApplicationStatusHistory h = new ApplicationStatusHistory();
        h.setApplicationId(applicationId); h.setStatus(status); h.setRemark(remark);
        h.setSource(source); h.setSourceEmailId(sourceEmailId); h.setConfidence(confidence);
        return historyRepo.save(h);
    }

    public List<ApplicationStatusHistory> getHistory(Long applicationId, Long userId) {
        findOwnedOrThrow(applicationId, userId);
        return historyRepo.findByApplicationId(applicationId);
    }

    private JobApplication findOwnedOrThrow(Long id, Long userId) {
        JobApplication app = appRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        if (!app.getUserId().equals(userId)) {
            // 404 rather than 403 so we don't confirm to a caller that the id exists at all
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        return app;
    }
}
