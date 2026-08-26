package com.smartjobtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public class ReminderPreferencesDto {
    @NotBlank
    @Size(max = 100)
    private String timezone = "UTC";
    private boolean interviewsEnabled = true;
    private boolean assessmentsEnabled = true;
    private boolean deadlinesEnabled = true;
    private boolean followUpsEnabled = true;
    private List<Integer> interviewOffsetsHours = List.of(24, 2);
    private List<Integer> assessmentOffsetsHours = List.of(24, 6, 1);
    private List<Integer> deadlineOffsetsHours = List.of(24);
    private List<Integer> followUpOffsetsHours = List.of(0);

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public boolean isInterviewsEnabled() { return interviewsEnabled; }
    public void setInterviewsEnabled(boolean value) { interviewsEnabled = value; }
    public boolean isAssessmentsEnabled() { return assessmentsEnabled; }
    public void setAssessmentsEnabled(boolean value) { assessmentsEnabled = value; }
    public boolean isDeadlinesEnabled() { return deadlinesEnabled; }
    public void setDeadlinesEnabled(boolean value) { deadlinesEnabled = value; }
    public boolean isFollowUpsEnabled() { return followUpsEnabled; }
    public void setFollowUpsEnabled(boolean value) { followUpsEnabled = value; }
    public List<Integer> getInterviewOffsetsHours() { return interviewOffsetsHours; }
    public void setInterviewOffsetsHours(List<Integer> value) { interviewOffsetsHours = value; }
    public List<Integer> getAssessmentOffsetsHours() { return assessmentOffsetsHours; }
    public void setAssessmentOffsetsHours(List<Integer> value) { assessmentOffsetsHours = value; }
    public List<Integer> getDeadlineOffsetsHours() { return deadlineOffsetsHours; }
    public void setDeadlineOffsetsHours(List<Integer> value) { deadlineOffsetsHours = value; }
    public List<Integer> getFollowUpOffsetsHours() { return followUpOffsetsHours; }
    public void setFollowUpOffsetsHours(List<Integer> value) { followUpOffsetsHours = value; }
}