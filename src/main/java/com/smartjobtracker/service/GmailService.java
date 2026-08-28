package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.config.GmailConfig;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class GmailService {
    private final GmailConfig config;
    private final GmailConnectionRepository connections;
    private final IngestedEmailRepository emails;
    private final JobApplicationRepository applications;
    private final JobApplicationService applicationService;
    private final GmailTokenCipher cipher;
    private final RestClient client;
    private final RuleBasedEmailClassifier rules;
    private final GeminiEmailClassifier gemini;
    private final EmailApplicationMatcher applicationMatcher;

    public GmailService(GmailConfig config, GmailConnectionRepository connections, IngestedEmailRepository emails,
                        JobApplicationRepository applications, JobApplicationService applicationService,
                        GmailTokenCipher cipher, RestClient.Builder builder, ObjectMapper mapper,
                        RuleBasedEmailClassifier rules, GeminiEmailClassifier gemini,
                        EmailApplicationMatcher applicationMatcher) {
        this.config=config; this.connections=connections; this.emails=emails; this.applications=applications;
        this.applicationService=applicationService; this.cipher=cipher; this.client=builder.build();
        this.rules=rules; this.gemini=gemini; this.applicationMatcher=applicationMatcher;
    }

    @Transactional
    public String begin(Long userId) {
        requireEnabled();
        GmailConnection connection=connections.findByUserId(userId).orElseGet(GmailConnection::new);
        connection.setUserId(userId); connection.setStatus("PENDING"); connection.setOauthState(UUID.randomUUID().toString());
        connection.setStateExpiresAt(OffsetDateTime.now().plusMinutes(10)); connections.save(connection);
        return "https://accounts.google.com/o/oauth2/v2/auth?client_id="+enc(config.getClientId())
                +"&redirect_uri="+enc(config.getRedirectUri())+"&response_type=code&scope="
                +enc("https://www.googleapis.com/auth/gmail.readonly")+"&access_type=offline&prompt=consent&state="+enc(connection.getOauthState());
    }

    @Transactional
    public void callback(String state, String code) {
        requireEnabled();
        if (code==null || code.isBlank()) throw new IllegalArgumentException("Gmail authorization was not granted");
        GmailConnection connection=connections.findByOauthState(state).orElseThrow(() -> new IllegalArgumentException("Invalid Gmail authorization state"));
        if (connection.getStateExpiresAt()==null || connection.getStateExpiresAt().isBefore(OffsetDateTime.now())) throw new IllegalArgumentException("Expired Gmail authorization state");
        JsonNode token=client.post().uri("https://oauth2.googleapis.com/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("code="+enc(code)+"&client_id="+enc(config.getClientId())+"&client_secret="+enc(config.getClientSecret())+"&redirect_uri="+enc(config.getRedirectUri())+"&grant_type=authorization_code").retrieve().body(JsonNode.class);
        String refresh=token.path("refresh_token").asText(null); String access=token.path("access_token").asText(null);
        if (refresh==null && connection.getEncryptedRefreshToken()!=null) refresh=cipher.decrypt(connection.getEncryptedRefreshToken());
        if (refresh==null || access==null) throw new IllegalArgumentException("Google did not return Gmail tokens");
        connection.setEncryptedRefreshToken(cipher.encrypt(refresh)); connection.setEncryptedAccessToken(cipher.encrypt(access));
        connection.setAccessTokenExpiresAt(OffsetDateTime.now().plusSeconds(token.path("expires_in").asLong(3600)-60));
        connection.setOauthState(null); connection.setStateExpiresAt(null); connection.setStatus("CONNECTED");
        connection.setConnectedAt(OffsetDateTime.now()); connection.setUpdatedAt(OffsetDateTime.now()); connections.save(connection);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> status(Long userId) {
        String configurationError = config.configurationError();
        Map<String,Object> connectionState = connections.findByUserId(userId).map(c -> Map.<String,Object>of(
            "connected", "CONNECTED".equals(c.getStatus()), "status", c.getStatus(), "email", c.getGoogleEmail()==null?"":c.getGoogleEmail(), "connectedAt", c.getConnectedAt()==null?"":c.getConnectedAt())).orElse(Map.of("connected",false,"status","DISCONNECTED"));
        if (configurationError == null) return connectionState;
        Map<String,Object> withError = new HashMap<>(connectionState);
        withError.put("configurationError", configurationError);
        return withError;
    }

    @Transactional public void disconnect(Long userId) { connections.findByUserId(userId).ifPresent(c -> { c.setEncryptedAccessToken(null); c.setEncryptedRefreshToken(null); c.setStatus("DISCONNECTED"); c.setOauthState(null); c.setUpdatedAt(OffsetDateTime.now()); connections.save(c); }); }

    @Transactional
    public int sync(Long userId) {
        GmailConnection connection=connections.findByUserId(userId).filter(c -> "CONNECTED".equals(c.getStatus())).orElseThrow(() -> new IllegalArgumentException("Gmail is not connected"));
        String access=accessToken(connection); JsonNode list=client.get().uri("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=50&q="+enc("newer_than:30d")).header("Authorization","Bearer "+access).retrieve().body(JsonNode.class); int processed=0;
        for (JsonNode reference:list.path("messages")) {
            String id=reference.path("id").asText(null); if (id==null || emails.findByUserIdAndGmailMessageId(userId,id).isPresent()) continue;
            JsonNode message=client.get().uri("https://gmail.googleapis.com/gmail/v1/users/me/messages/"+id+"?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date").header("Authorization","Bearer "+access).retrieve().body(JsonNode.class);
            IngestedEmail email=new IngestedEmail(); email.setUserId(userId); email.setGmailMessageId(id); email.setThreadId(message.path("threadId").asText(null)); String snippet=message.path("snippet").asText(null); email.setSnippet(snippet==null?null:snippet.substring(0,Math.min(snippet.length(),500))); email.setReceivedAt(OffsetDateTime.now());
            for (JsonNode header:message.path("payload").path("headers")) { if("From".equalsIgnoreCase(header.path("name").asText())) email.setFromAddress(header.path("value").asText(null)); if("Subject".equalsIgnoreCase(header.path("name").asText())) email.setSubject(header.path("value").asText(null)); }
            if (isJobEmail(email)) { EmailClassifier.Classification result=classify(email); applyClassification(email,result); email=emails.save(email); updateMatchingApplication(userId,email,result); email.setProcessedAt(OffsetDateTime.now()); } else { email.setCategory("OTHER"); email.setReviewStatus("NOT_RELEVANT"); emails.save(email); }
            processed++;
        }
        return processed;
    }

    private String accessToken(GmailConnection connection) { if(connection.getAccessTokenExpiresAt()!=null && connection.getAccessTokenExpiresAt().isAfter(OffsetDateTime.now().plusMinutes(1))) return cipher.decrypt(connection.getEncryptedAccessToken()); JsonNode token=client.post().uri("https://oauth2.googleapis.com/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).body("refresh_token="+enc(cipher.decrypt(connection.getEncryptedRefreshToken()))+"&client_id="+enc(config.getClientId())+"&client_secret="+enc(config.getClientSecret())+"&grant_type=refresh_token").retrieve().body(JsonNode.class); String access=token.path("access_token").asText(null); if(access==null)throw new IllegalArgumentException("Gmail token refresh failed"); connection.setEncryptedAccessToken(cipher.encrypt(access)); connection.setAccessTokenExpiresAt(OffsetDateTime.now().plusSeconds(token.path("expires_in").asLong(3600)-60)); connection.setUpdatedAt(OffsetDateTime.now()); connections.save(connection); return access; }
    private boolean isJobEmail(IngestedEmail email) { String text=((email.getSubject()==null?"":email.getSubject())+" "+(email.getFromAddress()==null?"":email.getFromAddress())+" "+(email.getSnippet()==null?"":email.getSnippet())).toLowerCase(Locale.ROOT); return List.of("application","interview","assessment","recruiter","job opportunity","offer","rejection","careers").stream().anyMatch(text::contains); }
    private EmailClassifier.Classification classify(IngestedEmail email) { EmailClassifier.EmailInput input=new EmailClassifier.EmailInput(email.getFromAddress(),email.getSubject(),email.getSnippet()); if(!"gemini".equalsIgnoreCase(config.getClassificationProvider()))return rules.classify(input); for(int attempt=0;;attempt++){try{return gemini.classify(input);}catch(RuntimeException ex){if(attempt>=config.getClassificationMaxRetries())return rules.classify(input);try{Thread.sleep(200L*(attempt+1));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return rules.classify(input);}}} }
    private void applyClassification(IngestedEmail email,EmailClassifier.Classification result) { email.setCategory(result.category()); email.setCompany(result.company()); email.setJobTitle(result.jobTitle()); email.setApplicationReference(result.applicationReference()); email.setExtractedStatus(result.status()); email.setInterviewDate(result.interviewDate()); email.setInterviewTime(result.interviewTime()); email.setDeadline(result.deadline()); email.setActionRequired(result.actionRequired()); email.setConfidence(result.confidence()); }
    private void updateMatchingApplication(Long userId,IngestedEmail email,EmailClassifier.Classification result) { if(result.confidence()<config.getClassificationMinConfidence()||result.status()==null){email.setReviewStatus("REVIEW_REQUIRED");return;} EmailApplicationMatcher.MatchResult match=applicationMatcher.match(applications.findByUserId(userId),result,config.getClassificationMinConfidence()); if(match==null){email.setReviewStatus("REVIEW_REQUIRED");return;} JobApplication application=match.application(); email.setMatchedApplicationId(application.getId()); email.setUpdateMethod(match.method()); email.setPreviousApplicationStatus(application.getStatus()==null?null:application.getStatus().name()); email.setReviewStatus("AUTO_APPLIED"); ApplicationStatus next=ApplicationStatus.valueOf(result.status()); if(next!=application.getStatus()) applicationService.changeStatus(application.getId(),userId,next,"Updated from classified Gmail job email","GMAIL",email.getId(),result.confidence()); }
    @Transactional(readOnly=true) public List<IngestedEmail> reviewQueue(Long userId){return emails.findByUserIdAndReviewStatusOrderByReceivedAtDesc(userId,"REVIEW_REQUIRED");}
    @Transactional public void review(Long userId,Long emailId,Long applicationId,ApplicationStatus status){IngestedEmail email=emails.findByIdAndUserId(emailId,userId).orElseThrow(()->new IllegalArgumentException("Email not found"));if(!"REVIEW_REQUIRED".equals(email.getReviewStatus()))throw new IllegalArgumentException("Email is not awaiting review");JobApplication app=applications.findById(applicationId).filter(a->userId.equals(a.getUserId())).orElseThrow(()->new IllegalArgumentException("Application not found"));if(status==null)throw new IllegalArgumentException("Status is required");if(status!=app.getStatus())applicationService.changeStatus(app.getId(),userId,status,"Confirmed from Gmail email review","GMAIL",email.getId(),email.getConfidence());email.setMatchedApplicationId(app.getId());email.setReviewStatus("REVIEWED");emails.save(email);}
    private void requireEnabled(){String error=config.configurationError();if(error!=null)throw new IllegalStateException(error);} private String enc(String value){return URLEncoder.encode(value==null?"":value, StandardCharsets.UTF_8);}
}
