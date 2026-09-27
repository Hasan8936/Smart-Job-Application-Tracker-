package com.smartjobtracker.controller;

import com.smartjobtracker.dto.AccountDtos;
import com.smartjobtracker.model.User;
import com.smartjobtracker.model.UserPhoto;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.AccountDeletionService;
import com.smartjobtracker.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Self-service account area for the signed-in user. Existing GET /api/users/me stays in UserController. */
@RestController
@RequestMapping("/api/users/me")
public class AccountController {
    private final AccountService accounts;
    private final AccountDeletionService deletion;
    private final UserRepository users;

    public AccountController(AccountService accounts, AccountDeletionService deletion, UserRepository users) {
        this.accounts = accounts;
        this.deletion = deletion;
        this.users = users;
    }

    @GetMapping("/details")
    public ResponseEntity<AccountDtos.Details> details() {
        Long uid = currentUserId();
        return uid == null ? ResponseEntity.status(401).build() : ResponseEntity.ok(accounts.details(uid));
    }

    @PutMapping("/details")
    public ResponseEntity<AccountDtos.Details> updateDetails(@Valid @RequestBody AccountDtos.Details body) {
        Long uid = currentUserId();
        return uid == null ? ResponseEntity.status(401).build() : ResponseEntity.ok(accounts.updateDetails(uid, body));
    }

    @GetMapping("/photo")
    public ResponseEntity<byte[]> photo() {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return accounts.photo(uid).map(this::photoResponse).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AccountDtos.PhotoInfo> uploadPhoto(@RequestParam("file") MultipartFile file) throws IOException {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(accounts.savePhoto(uid, file.getBytes()));
    }

    @DeleteMapping("/photo")
    public ResponseEntity<Void> deletePhoto() {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        accounts.deletePhoto(uid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody AccountDtos.ChangePassword body) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        accounts.changePassword(uid, body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export() {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"smart-job-tracker-data-" + LocalDate.now() + ".json\"")
                .cacheControl(CacheControl.noStore())
                .body(accounts.export(uid));
    }

    /** DELETE /api/users/me — permanently deletes the account after confirmation. */
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@Valid @RequestBody(required = false) AccountDtos.DeleteAccount body) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        deletion.deleteAccount(uid, body);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<byte[]> photoResponse(UserPhoto p) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.getContentType()))
                // Private: the photo belongs to a signed-in user; short cache, revalidated via Last-Modified.
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .lastModified(p.getUpdatedAt().toInstant())
                .header("X-Content-Type-Options", "nosniff")
                .body(p.getData());
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return users.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }
}
