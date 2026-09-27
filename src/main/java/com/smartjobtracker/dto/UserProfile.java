package com.smartjobtracker.dto;

public class UserProfile {
    private Long id;
    private String name;
    private String email;
    /** USER or ADMIN (additive field). */
    private String role;
    /** False for Google sign-ups that never chose a password (additive field). */
    private boolean passwordSet = true;
    /** Whether a profile photo exists at GET /api/users/me/photo (additive field). */
    private boolean hasPhoto;

    public UserProfile() {}
    public UserProfile(Long id, String name, String email){ this.id = id; this.name = name; this.email = email; }
    public UserProfile(Long id, String name, String email, String role, boolean passwordSet, boolean hasPhoto) {
        this(id, name, email);
        this.role = role; this.passwordSet = passwordSet; this.hasPhoto = hasPhoto;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isPasswordSet() { return passwordSet; }
    public void setPasswordSet(boolean passwordSet) { this.passwordSet = passwordSet; }
    public boolean isHasPhoto() { return hasPhoto; }
    public void setHasPhoto(boolean hasPhoto) { this.hasPhoto = hasPhoto; }
}
