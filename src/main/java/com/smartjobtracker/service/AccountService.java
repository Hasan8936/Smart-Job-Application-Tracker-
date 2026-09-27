package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.AccountDtos;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/** Self-service account operations: profile details, profile photo, password, and "download my data". */
@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository users;
    private final CandidateProfileRepository profiles;
    private final UserPhotoRepository photos;
    private final ProfilePhotoProcessor photoProcessor;
    private final PasswordEncoder passwordEncoder;
    private final JobApplicationRepository applications;
    private final ApplicationStatusHistoryRepository statusHistory;
    private final ResumeRepository resumes;
    private final UniversalResumeRepository universalResumes;
    private final SupportTicketRepository tickets;
    private final SupportTicketMessageRepository ticketMessages;
    private final EntityManager em;
    private final ObjectMapper mapper;

    public AccountService(UserRepository users, CandidateProfileRepository profiles, UserPhotoRepository photos,
                          ProfilePhotoProcessor photoProcessor, PasswordEncoder passwordEncoder,
                          JobApplicationRepository applications, ApplicationStatusHistoryRepository statusHistory,
                          ResumeRepository resumes, UniversalResumeRepository universalResumes,
                          SupportTicketRepository tickets, SupportTicketMessageRepository ticketMessages,
                          EntityManager em, ObjectMapper mapper) {
        this.users = users; this.profiles = profiles; this.photos = photos; this.photoProcessor = photoProcessor;
        this.passwordEncoder = passwordEncoder; this.applications = applications; this.statusHistory = statusHistory;
        this.resumes = resumes; this.universalResumes = universalResumes; this.tickets = tickets;
        this.ticketMessages = ticketMessages; this.em = em; this.mapper = mapper;
    }

    // ─── Details ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AccountDtos.Details details(Long userId) {
        User u = user(userId);
        CandidateProfile p = profiles.findByUserId(userId).orElse(null);
        return new AccountDtos.Details(u.getName(), u.getEmail(),
                p == null ? null : p.getHeadline(), p == null ? null : p.getLocation(), p == null ? null : p.getPhone(),
                p == null ? null : p.getLinkedinUrl(), p == null ? null : p.getGithubUrl(), p == null ? null : p.getWebsiteUrl());
    }

    @Transactional
    public AccountDtos.Details updateDetails(Long userId, AccountDtos.Details d) {
        User u = user(userId);
        u.setName(clean(d.name()));
        users.save(u);
        CandidateProfile p = profiles.findByUserId(userId).orElseGet(() -> {
            CandidateProfile created = new CandidateProfile();
            created.setUserId(userId);
            return created;
        });
        p.setHeadline(clean(d.headline()));
        p.setLocation(clean(d.location()));
        p.setPhone(clean(d.phone()));
        p.setLinkedinUrl(url(d.linkedinUrl()));
        p.setGithubUrl(url(d.githubUrl()));
        p.setWebsiteUrl(url(d.websiteUrl()));
        p.setUpdatedAt(OffsetDateTime.now());
        profiles.save(p);
        log.info("Account details updated: userId={}", userId);
        return details(userId);
    }

    // ─── Photo ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<UserPhoto> photo(Long userId) { return photos.findById(userId); }

    @Transactional
    public AccountDtos.PhotoInfo savePhoto(Long userId, byte[] upload) {
        user(userId);
        ProfilePhotoProcessor.Processed processed = photoProcessor.process(upload);
        UserPhoto photo = photos.findById(userId).orElseGet(() -> { UserPhoto p = new UserPhoto(); p.setUserId(userId); return p; });
        photo.setData(processed.data());
        photo.setContentType(processed.contentType());
        photo.setUpdatedAt(OffsetDateTime.now());
        photos.save(photo);
        log.info("Profile photo saved: userId={} bytes={}", userId, processed.data().length);
        return new AccountDtos.PhotoInfo(true, photo.getUpdatedAt());
    }

    @Transactional
    public void deletePhoto(Long userId) {
        if (photos.existsById(userId)) {
            photos.deleteById(userId);
            log.info("Profile photo removed: userId={}", userId);
        }
    }

    // ─── Password ───────────────────────────────────────────────────────────────

    /** Requires the current password, except for accounts that never set one (Google sign-up) setting a first password. */
    @Transactional
    public void changePassword(Long userId, AccountDtos.ChangePassword req) {
        User u = user(userId);
        if (u.isPasswordSet()
                && (req.currentPassword() == null || !passwordEncoder.matches(req.currentPassword(), u.getPasswordHash()))) {
            throw new AccountException(AccountException.Kind.BAD_REQUEST, "Your current password is incorrect.");
        }
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        u.setPasswordSet(true);
        users.save(u);
        log.info("Password changed: userId={}", userId);
    }

    // ─── Export ─────────────────────────────────────────────────────────────────

    /** The user's own data only. Never includes password hashes, OAuth/Gmail/Calendar tokens, or other users' data. */
    @Transactional(readOnly = true)
    public Map<String, Object> export(Long userId) {
        User u = user(userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", OffsetDateTime.now());

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", u.getId()); account.put("name", u.getName()); account.put("email", u.getEmail());
        account.put("createdAt", u.getCreatedAt()); account.put("timezone", u.getTimezone()); account.put("lastLoginAt", u.getLastLoginAt());
        out.put("account", account);
        out.put("profileDetails", details(userId));

        profiles.findByUserId(userId).ifPresent(p -> {
            Map<String, Object> extracted = new LinkedHashMap<>();
            extracted.put("skills", json(p.getSkills())); extracted.put("programmingLanguages", json(p.getProgrammingLanguages()));
            extracted.put("frameworks", json(p.getFrameworks())); extracted.put("projects", json(p.getProjects()));
            extracted.put("education", json(p.getEducation())); extracted.put("experience", json(p.getExperience()));
            extracted.put("preferredRoles", json(p.getPreferredRoles()));
            out.put("candidateProfile", extracted);
        });

        List<Map<String, Object>> apps = new ArrayList<>();
        for (JobApplication a : applications.findByUserId(userId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId()); m.put("companyName", a.getCompanyName()); m.put("roleTitle", a.getRoleTitle());
            m.put("status", a.getStatus()); m.put("appliedDate", a.getAppliedDate()); m.put("jobDescription", a.getJobDescription());
            m.put("statusHistory", statusHistory.findByApplicationId(a.getId()).stream().map(h -> {
                Map<String, Object> hm = new LinkedHashMap<>();
                hm.put("status", h.getStatus()); hm.put("changedAt", h.getChangedAt()); hm.put("remark", h.getRemark());
                return hm;
            }).toList());
            apps.add(m);
        }
        out.put("applications", apps);

        out.put("resumes", resumes.findByUserId(userId).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId()); m.put("fileName", r.getFileName()); m.put("uploadedAt", r.getUploadedAt());
            m.put("extractedText", r.getExtractedText());
            return m;
        }).toList());
        universalResumes.findByUserId(userId).ifPresent(ur -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("template", ur.getTemplate()); m.put("updatedAt", ur.getUpdatedAt()); m.put("data", json(ur.getDataJson()));
            out.put("universalResume", m);
        });

        out.put("reminders", em.createQuery("select r from Reminder r where r.userId = :u order by r.remindAt", Reminder.class)
                .setParameter("u", userId).getResultList().stream().map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getId()); m.put("type", r.getType()); m.put("status", r.getStatus());
                    m.put("message", r.getMessage()); m.put("remindAt", r.getRemindAt()); m.put("applicationId", r.getApplicationId());
                    return m;
                }).toList());

        List<Map<String, Object>> saved = new ArrayList<>();
        for (Object[] row : em.createQuery("select s, j from SavedJob s, JobPosting j where s.userId = :u and j.id = s.jobPostingId", Object[].class)
                .setParameter("u", userId).getResultList()) {
            SavedJob s = (SavedJob) row[0]; JobPosting j = (JobPosting) row[1];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("state", s.getState()); m.put("savedAt", s.getCreatedAt());
            m.put("company", j.getCompany()); m.put("title", j.getTitle()); m.put("location", j.getLocation()); m.put("applyUrl", j.getApplyUrl());
            saved.add(m);
        }
        out.put("savedJobs", saved);

        out.put("supportTickets", tickets.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId()); m.put("subject", t.getSubject()); m.put("category", t.getCategory());
            m.put("status", t.getStatus()); m.put("createdAt", t.getCreatedAt());
            m.put("messages", ticketMessages.findByTicketIdOrderByCreatedAtAscIdAsc(t.getId()).stream().map(msg -> {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("from", msg.isFromAdmin() ? "support" : "you"); mm.put("body", msg.getBody()); mm.put("createdAt", msg.getCreatedAt());
                return mm;
            }).toList());
            return m;
        }).toList());

        out.put("hasProfilePhoto", photos.existsById(userId));
        log.info("Account data exported: userId={}", userId);
        return out;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private User user(Long userId) {
        return users.findById(userId).orElseThrow(() -> new AccountException(AccountException.Kind.NOT_FOUND, "Account not found."));
    }

    private Object json(String value) {
        if (value == null || value.isBlank()) return null;
        try { return mapper.readTree(value); } catch (Exception e) { return value; }
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    /** Validation already allows an optional scheme; store a clickable https:// URL. */
    private static String url(String value) {
        String v = clean(value);
        if (v == null) return null;
        return v.matches("(?i)^https?://.*") ? v : "https://" + v;
    }
}
