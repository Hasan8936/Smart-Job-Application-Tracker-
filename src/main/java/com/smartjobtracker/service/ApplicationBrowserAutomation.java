package com.smartjobtracker.service;

import com.smartjobtracker.model.ApplicationFieldType;
import java.util.Map;

/** Future browser integration seam. It deliberately exposes preparation only; submission is a user action. */
public interface ApplicationBrowserAutomation {
    Map<String, String> mapFields(Map<String, ApplicationFieldType> externalFields);
}