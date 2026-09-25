package com.resumescreener.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implements text similarity scoring from scratch using TF-IDF vectorization
 * and Cosine Similarity — no external AI/ML API calls, so it costs nothing to run.
 */

public class TfIdfScorer {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9+#.]{1,}");

    // A small stopword list — common English words that carry no distinguishing signal
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "then", "so", "of", "in", "on", "at", "to", "for",
            "with", "as", "by", "is", "are", "was", "were", "be", "been", "being", "this", "that", "these",
            "those", "it", "its", "from", "into", "about", "we", "you", "your", "our", "their", "they",
            "he", "she", "his", "her", "will", "would", "can", "could", "should", "have", "has", "had",
            "do", "does", "did", "not", "no", "yes", "also", "such", "than", "over", "under", "up", "down",
            "out", "all", "any", "each", "more", "most", "other", "some", "using", "used", "use", "etc"
    );

    /**
     * Tokenizes text into lowercase words, stripping punctuation and stopwords.
     */
    public List<String> tokenize(String text) {
        if (text == null) return List.of();
        List<String> tokens = new ArrayList<>();
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase());
        while (matcher.find()) {
            String token = matcher.group();
            if (!STOPWORDS.contains(token) && token.length() > 1) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** Term Frequency: raw counts normalized by document length. */
    public Map<String, Double> computeTf(List<String> tokens) {
        Map<String, Double> tf = new HashMap<>();
        if (tokens.isEmpty()) return tf;
        for (String token : tokens) {
            tf.merge(token, 1.0, Double::sum);
        }
        int total = tokens.size();
        tf.replaceAll((k, v) -> v / total);
        return tf;
    }

    /** Inverse Document Frequency across the whole corpus (JD + all resumes). */
    public Map<String, Double> computeIdf(List<List<String>> allDocsTokens) {
        Map<String, Double> idf = new HashMap<>();
        int totalDocs = allDocsTokens.size();

        Map<String, Integer> docFrequency = new HashMap<>();
        for (List<String> docTokens : allDocsTokens) {
            for (String term : new HashSet<>(docTokens)) {
                docFrequency.merge(term, 1, Integer::sum);
            }
        }

        for (Map.Entry<String, Integer> entry : docFrequency.entrySet()) {
            // smoothed idf so terms appearing in every doc still get a small positive weight
            double value = Math.log((double) (totalDocs + 1) / (entry.getValue() + 1)) + 1.0;
            idf.put(entry.getKey(), value);
        }
        return idf;
    }

    /** Combines TF and IDF into a single weighted vector. */
    public Map<String, Double> computeTfIdfVector(Map<String, Double> tf, Map<String, Double> idf) {
        Map<String, Double> vector = new HashMap<>();
        for (Map.Entry<String, Double> entry : tf.entrySet()) {
            double idfValue = idf.getOrDefault(entry.getKey(), 0.0);
            vector.put(entry.getKey(), entry.getValue() * idfValue);
        }
        return vector;
    }

    /** Cosine similarity between two sparse TF-IDF vectors, returned as a value between 0 and 1. */
    public double cosineSimilarity(Map<String, Double> v1, Map<String, Double> v2) {
        Set<String> terms = new HashSet<>(v1.keySet());
        terms.retainAll(v2.keySet());

        double dotProduct = 0.0;
        for (String term : terms) {
            dotProduct += v1.get(term) * v2.get(term);
        }

        double norm1 = Math.sqrt(v1.values().stream().mapToDouble(x -> x * x).sum());
        double norm2 = Math.sqrt(v2.values().stream().mapToDouble(x -> x * x).sum());

        if (norm1 == 0 || norm2 == 0) return 0.0;
        return dotProduct / (norm1 * norm2);
    }

    /**
     * Finds the top JD keywords (by TF-IDF weight) that are missing from a given resume.
     * Used to surface "skill gaps" for the recruiter.
     */
    public List<String> findMissingKeywords(Map<String, Double> jdVector, Set<String> resumeTokens, int topN) {
        return jdVector.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .filter(term -> !resumeTokens.contains(term))
                .limit(topN)
                .collect(Collectors.toList());
    }
}
