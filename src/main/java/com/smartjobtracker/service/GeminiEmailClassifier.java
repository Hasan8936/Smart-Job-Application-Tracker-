package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.config.GmailConfig;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Locale;

@Component
public class GeminiEmailClassifier implements EmailClassifier {
    private final GmailConfig config; private final RestClient client; private final ObjectMapper mapper;
    public GeminiEmailClassifier(GmailConfig config, RestClient.Builder builder, ObjectMapper mapper) { this.config=config; client=builder.build(); this.mapper=mapper; }
    @Override public Classification classify(EmailInput input) {
        if (!"gemini".equalsIgnoreCase(config.getClassificationProvider()) || config.getClassificationApiKey()==null || config.getClassificationApiKey().isBlank()) throw new IllegalStateException("Gemini email classification is not configured");
        String prompt = "Return JSON only with keys category,company,jobTitle,status,interviewDate,interviewTime,deadline,actionRequired,applicationReference,confidence. "
                + "category must be one of APPLICATION_RECEIVED, APPLICATION_STATUS_UPDATE, INTERVIEW_INVITATION, ONLINE_ASSESSMENT, REJECTION, OFFER, RECRUITER_MESSAGE, FOLLOW_UP_REQUIRED, OTHER. "
                + "Use null for unknown values. Never invent skills, experience, dates, company, or job titles. Confidence must be 0 to 1.\nEMAIL:\nfrom=" + safe(input.from()) + "\nsubject=" + safe(input.subject()) + "\nsnippet=" + safe(input.snippet());
        JsonNode payload=mapper.createObjectNode().put("contents", "");
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).set("contents", mapper.createArrayNode().add(mapper.createObjectNode().set("parts", mapper.createArrayNode().add(mapper.createObjectNode().put("text", prompt)))));
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).set("generationConfig", mapper.createObjectNode().put("responseMimeType", "application/json"));
        JsonNode root=client.post().uri(config.getClassificationEndpoint()+"/"+config.getClassificationModel()+":generateContent?key="+config.getClassificationApiKey()).contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(JsonNode.class);
        String raw=root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null); if(raw==null)throw new IllegalStateException("Gemini returned no classification");
        try { return parse(mapper.readTree(raw.replace("```json","").replace("```",""))); } catch(Exception ex) { throw new IllegalStateException("Invalid Gemini classification JSON",ex); }
    }
    private Classification parse(JsonNode n) { String category=text(n,"category"); double confidence=n.path("confidence").asDouble(-1); String status=text(n,"status"); if(!Classification.CATEGORIES.contains(category)||!Double.isFinite(confidence)||confidence<0||confidence>1||status!=null&&!java.util.Set.of("APPLIED","OA","INTERVIEW","OFFER","REJECTED","WITHDRAWN").contains(status)||!validDate(text(n,"interviewDate"))||!validDate(text(n,"deadline")))throw new IllegalArgumentException("Invalid email classification output"); return new Classification(category,text(n,"company"),text(n,"jobTitle"),status,text(n,"interviewDate"),text(n,"interviewTime"),text(n,"deadline"),text(n,"actionRequired"),text(n,"applicationReference"),confidence,"gemini"); }
    private String text(JsonNode n,String name){if(!n.hasNonNull(name))return null;String value=n.get(name).asText();return value.isBlank()?null:value.length()>1000?value.substring(0,1000):value;}
    private String safe(String value){return value==null?"":value.replaceAll("(?i)(token|password|authorization)\\s*[:=]\\s*\\S+","$1=[REDACTED]").substring(0,Math.min(value.length(),500));}
    private boolean validDate(String value){return value==null||value.matches("\\d{4}-\\d{2}-\\d{2}");}
}