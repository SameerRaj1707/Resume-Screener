package com.resumescreener.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumescreener.model.*;
import com.resumescreener.service.PdfTextExtractor;
import com.resumescreener.service.TfIdfScorer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.*;

/**
 * Entry point AWS invokes for every API Gateway request.
 *
 * Flow:
 *   1. Parse the JSON body (job description + resumes as base64).
 *   2. Store each original resume file in S3 (audit trail / re-download later).
 *   3. Extract text (PDFBox) and score every resume against the JD
 *      using the exact same TF-IDF + cosine similarity engine as the local version.
 *   4. Persist each result to DynamoDB.
 *   5. Return the ranked list as the API response.
 */
public class ScreeningLambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TfIdfScorer tfIdfScorer = new TfIdfScorer();
    private final PdfTextExtractor pdfTextExtractor = new PdfTextExtractor();
    private final S3Client s3 = S3Client.builder().build();
    private final DynamoDbClient dynamoDb = DynamoDbClient.builder().build();

    private final String bucketName = System.getenv("BUCKET_NAME");
    private final String tableName = System.getenv("TABLE_NAME");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");

        try {
            ScreenRequest screenRequest = mapper.readValue(request.getBody(), ScreenRequest.class);

            if (screenRequest.getJobDescription() == null || screenRequest.getJobDescription().isBlank()) {
                return errorResponse(headers, 400, "jobDescription must not be empty");
            }
            if (screenRequest.getResumes() == null || screenRequest.getResumes().isEmpty()) {
                return errorResponse(headers, 400, "At least one resume must be provided");
            }

            String jobId = UUID.randomUUID().toString();

            // 1. Extract text from every resume + upload original file to S3
            Map<String, String> resumeTexts = new LinkedHashMap<>();
            for (ResumeInput resume : screenRequest.getResumes()) {
                byte[] fileBytes = Base64.getDecoder().decode(resume.getContentBase64());

                // Store the original resume in S3 under this job's folder
                String s3Key = "resumes/" + jobId + "/" + resume.getFilename();
                s3.putObject(
                        PutObjectRequest.builder().bucket(bucketName).key(s3Key).build(),
                        RequestBody.fromBytes(fileBytes)
                );

                String text = pdfTextExtractor.extractText(fileBytes, resume.getFilename());
                resumeTexts.put(resume.getFilename(), text);
            }

            // 2. Tokenize JD + resumes, build shared corpus for IDF (same logic as local version)
            List<String> jdTokens = tfIdfScorer.tokenize(screenRequest.getJobDescription());
            Map<String, List<String>> resumeTokensMap = new LinkedHashMap<>();
            for (var entry : resumeTexts.entrySet()) {
                resumeTokensMap.put(entry.getKey(), tfIdfScorer.tokenize(entry.getValue()));
            }

            List<List<String>> corpus = new ArrayList<>();
            corpus.add(jdTokens);
            corpus.addAll(resumeTokensMap.values());
            Map<String, Double> idf = tfIdfScorer.computeIdf(corpus);

            Map<String, Double> jdVector = tfIdfScorer.computeTfIdfVector(tfIdfScorer.computeTf(jdTokens), idf);

            // 3. Score each resume + persist result to DynamoDB
            List<ResumeResult> results = new ArrayList<>();
            for (var entry : resumeTokensMap.entrySet()) {
                String filename = entry.getKey();
                List<String> tokens = entry.getValue();

                Map<String, Double> resumeVector = tfIdfScorer.computeTfIdfVector(tfIdfScorer.computeTf(tokens), idf);
                double similarity = tfIdfScorer.cosineSimilarity(jdVector, resumeVector);
                double matchPercentage = Math.round(similarity * 10000.0) / 100.0;

                List<String> missingKeywords = tfIdfScorer.findMissingKeywords(jdVector, new HashSet<>(tokens), 8);

                results.add(new ResumeResult(filename, matchPercentage, missingKeywords));
                saveResultToDynamoDb(jobId, filename, matchPercentage, missingKeywords);
            }

            results.sort((a, b) -> Double.compare(b.getMatchPercentage(), a.getMatchPercentage()));

            ScreeningResponse response = new ScreeningResponse(results.size(), results);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(mapper.writeValueAsString(response));

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return errorResponse(headers, 500, "Internal error: " + e.getMessage());
        }
    }

    private void saveResultToDynamoDb(String jobId, String filename, double matchPercentage, List<String> missingKeywords) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(jobId + "#" + filename).build());
        item.put("jobId", AttributeValue.builder().s(jobId).build());
        item.put("filename", AttributeValue.builder().s(filename).build());
        item.put("matchPercentage", AttributeValue.builder().n(String.valueOf(matchPercentage)).build());
        item.put("missingKeywords", AttributeValue.builder().ss(
                missingKeywords.isEmpty() ? List.of("none") : missingKeywords
        ).build());
        item.put("timestamp", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build());

        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
    }

    private APIGatewayProxyResponseEvent errorResponse(Map<String, String> headers, int statusCode, String message) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(headers)
                    .withBody(mapper.writeValueAsString(Map.of("error", message)));
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("{\"error\":\"unknown error\"}");
        }
    }
}
