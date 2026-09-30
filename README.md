# Smart Resume Screener & Ranker — AWS Version
 
                                              

This is the cloud-deployed version of the resume screener you already tested
locally. Same TF-IDF + cosine similarity scoring engine, same logic — now
running as a serverless pipeline on AWS instead of a local Spring Boot server.

## Architecture
```
Browser (S3 static website)
      │  POST job description + resumes (base64 JSON)
      ▼
API Gateway (HTTP API)
      │
      ▼
AWS Lambda (Java 21)
      │  extracts text (PDFBox) → TF-IDF + cosine similarity scoring
      ├──► S3            (stores original resume files)
      └──► DynamoDB      (stores ranked results)
```

## Project Structure
```
resume-screener-aws/
├── pom.xml                                    # builds a fat jar via maven-shade-plugin
├── DEPLOYMENT.md                              # step-by-step AWS Console deployment guide
└── src/main/java/com/resumescreener/
    ├── lambda/ScreeningLambdaHandler.java      # Lambda entry point
    ├── service/TfIdfScorer.java                # scoring engine (identical to local version)
    ├── service/PdfTextExtractor.java           # byte[]-based text extraction
    └── model/                                  # request/response POJOs
```

## Quick Start
1. Read **DEPLOYMENT.md** — it walks through every AWS Console screen step by step.
2. Build the jar: `mvn clean package`
3. Follow Steps 2–8 in DEPLOYMENT.md to create the S3 buckets, DynamoDB table,
   IAM role, Lambda function, and API Gateway endpoint.
4. Update `API_URL` in `src/main/resources/static/index.html`, upload it to
   your frontend S3 bucket, and you have a live URL to put on your resume.

No AWS CLI, SAM, or Docker required — everything is done through the AWS
Console UI.
