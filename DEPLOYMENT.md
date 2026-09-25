# Deploying Smart Resume Screener to AWS (Free Tier, $0 cost)

This deploys the exact same TF-IDF scoring logic you already tested locally,
now running as: **API Gateway → Lambda (Java) → S3 (resume storage) → DynamoDB (results)**,
with the frontend hosted as a static website on S3.

Total cost: **$0**, as long as you stay within Free Tier limits (very hard to
exceed for a personal/demo project — see the cost notes at the bottom).

---

## Step 0: Create a free AWS account (if you don't have one)
https://aws.amazon.com/free — you'll need a card for verification, but nothing
in this guide will charge it if you follow the steps as written.

**Set a billing alarm first** (2 minutes, do this before anything else):
1. Search "Billing" in the AWS Console top search bar → **Billing and Cost Management**
2. Left sidebar → **Budgets** → **Create budget**
3. Choose **Zero spend budget** (a ready-made template) → Create
4. This emails you the moment any charge appears

---

## Step 1: Build the deployable Lambda jar

In VS Code terminal, inside the `resume-screener-aws` folder:
```bash
mvn clean package
```
This produces `target/resume-screener-lambda.jar` — a single file containing
your code + all dependencies (PDFBox, AWS SDK, Jackson). This is what you'll
upload to Lambda in Step 4.

---

## Step 2: Create the S3 bucket (resume storage)

1. AWS Console → search **S3** → **Create bucket**
2. Bucket name: `resume-screener-storage-<yourname>` (must be globally unique — add your name/initials)
3. Region: pick one close to you, e.g. `ap-south-1` (Mumbai) — **remember this region, use it for every step below**
4. Leave "Block all public access" **checked** (this bucket is private — resumes shouldn't be public)
5. Create bucket

---

## Step 3: Create the DynamoDB table (results storage)

1. AWS Console → search **DynamoDB** → **Create table**
2. Table name: `ResumeScreeningResults`
3. Partition key: `id` — type **String**
4. Leave everything else as default (on-demand capacity = always-free tier eligible)
5. Create table

---

## Step 4: Create the IAM role (permissions for Lambda)

1. AWS Console → search **IAM** → **Roles** → **Create role**
2. Trusted entity type: **AWS service** → Use case: **Lambda** → Next
3. Attach these policies (search and check each):
   - `AWSLambdaBasicExecutionRole` (lets Lambda write logs to CloudWatch)
   - `AmazonS3FullAccess` (fine for a personal project; for production you'd scope this to just your bucket)
   - `AmazonDynamoDBFullAccess` (same note)
4. Role name: `resume-screener-lambda-role`
5. Create role

---

## Step 5: Create the Lambda function

1. AWS Console → search **Lambda** → **Create function**
2. Choose **Author from scratch**
3. Function name: `resume-screener-function`
4. Runtime: **Java 21**
5. Architecture: `x86_64`
6. Under **Change default execution role** → **Use an existing role** → select `resume-screener-lambda-role`
7. Create function
8. Once created, go to the **Code** tab → **Upload from** → **.zip or .jar file** → upload `target/resume-screener-lambda.jar` from Step 1
9. Go to **Runtime settings** → **Edit** → set **Handler** to:
   ```
   com.resumescreener.lambda.ScreeningLambdaHandler::handleRequest
   ```
10. Go to **Configuration** tab → **General configuration** → **Edit**:
    - Memory: `512 MB`
    - Timeout: `30 sec` (PDF parsing + cold start needs more than the 3-second default)
11. Go to **Configuration** tab → **Environment variables** → **Edit** → **Add**:
    - `BUCKET_NAME` = the exact S3 bucket name from Step 2
    - `TABLE_NAME` = `ResumeScreeningResults`

---

## Step 6: Create the API Gateway endpoint

1. AWS Console → search **API Gateway** → **Create API**
2. Choose **HTTP API** (cheaper/simpler than REST API, also free-tier eligible) → **Build**
3. **Add integration** → Lambda → select `resume-screener-function`
4. API name: `resume-screener-api` → Next
5. Configure routes: Method `POST`, path `/screen` → Next
6. Stage: keep default `$default` (auto-deploy) → Next → Create
7. **Enable CORS** (needed so your browser frontend can call this):
   - Left sidebar → **CORS** → **Configure**
   - Access-Control-Allow-Origin: `*`
   - Access-Control-Allow-Methods: `POST, OPTIONS`
   - Access-Control-Allow-Headers: `content-type`
   - Save
8. Copy the **Invoke URL** shown at the top (looks like `https://abc123xyz.execute-api.ap-south-1.amazonaws.com`) — your full endpoint is this + `/screen`

---

## Step 7: Point the frontend at your API and host it on S3

1. Open `src/main/resources/static/index.html` in VS Code
2. Find this line near the top of the `<script>`:
   ```js
   const API_URL = "PASTE_YOUR_API_GATEWAY_URL_HERE";
   ```
   Replace it with your real URL + `/screen`, e.g.:
   ```js
   const API_URL = "https://abc123xyz.execute-api.ap-south-1.amazonaws.com/screen";
   ```
3. Create a **second** S3 bucket for the frontend (separate from your storage bucket):
   - Bucket name: `resume-screener-frontend-<yourname>`
   - This time, **uncheck** "Block all public access" (confirm the warning) — a static website must be publicly readable
4. Upload the edited `index.html` into this bucket
5. Go to bucket → **Properties** tab → scroll to **Static website hosting** → **Edit** → Enable → Index document: `index.html` → Save
6. Go to bucket → **Permissions** tab → **Bucket policy** → **Edit** → paste (replace `YOUR-BUCKET-NAME`):
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "PublicReadGetObject",
         "Effect": "Allow",
         "Principal": "*",
         "Action": "s3:GetObject",
         "Resource": "arn:aws:s3:::YOUR-BUCKET-NAME/*"
       }
     ]
   }
   ```
7. Go back to **Properties → Static website hosting** — copy the **Bucket website endpoint URL**. That's your live project link.

---

## Step 8: Test it

Open the bucket website endpoint URL from Step 7 in your browser, paste a job
description, upload resumes, click **Rank Candidates**. First request may take
2-3 seconds (Lambda cold start) — that's normal and free-tier expected behavior.

If something fails, check **CloudWatch Logs** (search "CloudWatch" → **Log groups**
→ `/aws/lambda/resume-screener-function`) — every error is logged there.

---

## Free Tier cost notes (why this stays at $0)
- **Lambda**: 1,000,000 requests + 400,000 GB-seconds compute/month, **always free** (not just 12 months)
- **DynamoDB**: 25 GB storage + 25 read/write capacity units, **always free**
- **S3**: 5 GB storage, 20,000 GET + 2,000 PUT requests/month, free for 12 months
- **API Gateway (HTTP API)**: 1,000,000 requests/month free for 12 months

A fresher project doing occasional demo runs will use a tiny fraction of any of
these. The billing alarm from Step 0 is your safety net regardless.

## What to say on your resume
> Built and deployed an AI-powered resume screening system (Java, AWS Lambda,
> S3, DynamoDB, API Gateway) that ranks candidates against a job description
> using TF-IDF vectorization and cosine similarity, implemented from scratch
> without paid AI APIs.
