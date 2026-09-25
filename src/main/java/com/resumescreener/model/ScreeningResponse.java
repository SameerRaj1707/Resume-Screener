package com.resumescreener.model;

import java.util.List;

public class ScreeningResponse {
    private int totalResumesScreened;
    private List<ResumeResult> rankedResults;

    public ScreeningResponse(int totalResumesScreened, List<ResumeResult> rankedResults) {
        this.totalResumesScreened = totalResumesScreened;
        this.rankedResults = rankedResults;
    }

    public int getTotalResumesScreened() {
        return totalResumesScreened;
    }

    public List<ResumeResult> getRankedResults() {
        return rankedResults;
    }
}
