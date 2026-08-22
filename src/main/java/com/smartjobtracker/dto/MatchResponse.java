package com.smartjobtracker.dto;

import java.util.List;

public class MatchResponse {
    private double matchScore;
    private List<String> matchedKeywords;
    private List<String> missingKeywords;

    public MatchResponse() {}
    public MatchResponse(double matchScore, List<String> matchedKeywords, List<String> missingKeywords) {
        this.matchScore = matchScore;
        this.matchedKeywords = matchedKeywords;
        this.missingKeywords = missingKeywords;
    }

    public double getMatchScore() { return matchScore; }
    public void setMatchScore(double matchScore) { this.matchScore = matchScore; }
    public List<String> getMatchedKeywords() { return matchedKeywords; }
    public void setMatchedKeywords(List<String> matchedKeywords) { this.matchedKeywords = matchedKeywords; }
    public List<String> getMissingKeywords() { return missingKeywords; }
    public void setMissingKeywords(List<String> missingKeywords) { this.missingKeywords = missingKeywords; }
}
