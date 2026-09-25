package com.resumescreener.model;

import java.util.List;

public class ScreenRequest {
    private String jobDescription;
    private List<ResumeInput> resumes;

    public ScreenRequest() {
    }

    public String getJobDescription() {
        return jobDescription;
    }

    public void setJobDescription(String jobDescription) {
        this.jobDescription = jobDescription;
    }

    public List<ResumeInput> getResumes() {
        return resumes;
    }

    public void setResumes(List<ResumeInput> resumes) {
        this.resumes = resumes;
    }
}
