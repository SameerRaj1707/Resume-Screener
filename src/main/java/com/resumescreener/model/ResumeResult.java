package com.resumescreener.model;

import java.util.List;

public class ResumeResult {
    private String filename;
    private double matchPercentage;
    private List<String> missingKeywords;

    public ResumeResult(String filename, double matchPercentage, List<String> missingKeywords) {
        this.filename = filename;
        this.matchPercentage = matchPercentage;
        this.missingKeywords = missingKeywords;
    }

    public String getFilename() {
        return filename;
    }

    public double getMatchPercentage() {
        return matchPercentage;
    }

    public List<String> getMissingKeywords() {
        return missingKeywords;
    }
}
