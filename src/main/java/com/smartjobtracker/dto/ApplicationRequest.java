package com.smartjobtracker.dto;

import com.smartjobtracker.model.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class ApplicationRequest {
    @NotBlank(message = "companyName is required")
    @Size(max = 255)
    private String companyName;

    @NotBlank(message = "roleTitle is required")
    @Size(max = 255)
    private String roleTitle;

    @Size(max = 100_000)
    private String jobDescription;

    @NotNull(message = "status is required")
    private ApplicationStatus status;

    private LocalDate appliedDate;

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getRoleTitle() { return roleTitle; }
    public void setRoleTitle(String roleTitle) { this.roleTitle = roleTitle; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
    public LocalDate getAppliedDate() { return appliedDate; }
    public void setAppliedDate(LocalDate appliedDate) { this.appliedDate = appliedDate; }
}
