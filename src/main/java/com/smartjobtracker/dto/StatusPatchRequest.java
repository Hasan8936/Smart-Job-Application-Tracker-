package com.smartjobtracker.dto;

import com.smartjobtracker.model.ApplicationStatus;

public class StatusPatchRequest {
    private ApplicationStatus status;
    private String remark;

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
