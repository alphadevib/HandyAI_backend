package com.handyai.build.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns text into a fixed-size vector (an embedding) so texts can be compared by cosine similarity.
 *
 * <p>This is a small, local, deterministic embedding rather than a learned one: words and word pairs
 * are hashed into {@link #DIMENSIONS} buckets (the "hashing trick") and weighted by TF-IDF, so words
 * that are rare in the catalogue count for more than common ones. It needs no API call, costs
 * nothing per query, and works offline. A hosted embedding model could replace it behind the same
 * {@link #embed} call without changing the vector search.
 */
final class TextEmbedder {

    static final int DIMENSIONS = 4096;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "best", "by", "can", "do", "for", "from",
            "get", "help", "how", "i", "in", "into", "is", "it", "its", "me", "my", "need", "of",
            "on", "or", "our", "so", "some", "that", "the", "their", "them", "this", "to", "tool",
            "tools", "use", "want", "we", "what", "with", "you", "your");

    private final Map<String, Double> idf;
    private TextEmbedder(Map<String, Double> idf) {
        this.idf = idf;
    }

    /** Learns how rare each feature is across the documents (each a list of features). */
    static TextEmbedder fit(Collection<List<String>> documents) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (List<String> features : documents) {
            for (String feature : new HashSet<>(features)) {
                documentFrequency.merge(feature, 1, Integer::sum);
            }
        }
        int n = Math.max(documents.size(), 1);
        Map<String, Double> idf = new HashMap<>();
        documentFrequency.forEach((feature, df) ->
                idf.put(feature, Math.log((n + 1d) / (df + 1d)) + 1d));
        return new TextEmbedder(idf);
    }

    /**
     * A unit-length vector; repeat a feature in the input to give it more weight. Features the
     * catalogue never uses are left out: they cannot match any tool and would only shrink the
     * similarity of the words that do.
     */
    float[] embed(List<String> features) {
        Map<String, Integer> termFrequency = new HashMap<>();
        features.forEach(feature -> termFrequency.merge(feature, 1, Integer::sum));

        float[] vector = new float[DIMENSIONS];
        termFrequency.forEach((feature, tf) -> {
            Double rarity = idf.get(feature);
            if (rarity == null) {
                return;
            }
            int hash = mix(feature.hashCode());
            int bucket = Math.floorMod(hash, DIMENSIONS);
            // The top bit picks the sign, so the collisions that remain tend to cancel out.
            float sign = (hash >>> 31) == 0 ? 1f : -1f;
            double weight = (1 + Math.log(tf)) * rarity;
            vector[bucket] += (float) (sign * weight);
        });
        normalise(vector);
        return vector;
    }

    /** Lower-cased word stems plus adjacent pairs ("video editing" adds "video_edit"). */
    static List<String> features(String text) {
        List<String> words = new ArrayList<>();
        if (text != null) {
            for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9+#]+")) {
                if (raw.length() > 1 && !STOP_WORDS.contains(raw)) {
                    words.add(stem(raw));
                }
            }
        }
        List<String> features = new ArrayList<>(words);
        for (int i = 1; i < words.size(); i++) {
            features.add(words.get(i - 1) + "_" + words.get(i));
        }
        return features;
    }

    /** Both inputs are unit length, so the dot product is the cosine. */
    static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    /**
     * MurmurHash3's finaliser: spreads String.hashCode, whose low bits barely differ between
     * similar words, over all 32 bits before they pick a bucket.
     */
    private static int mix(int hash) {
        hash ^= hash >>> 16;
        hash *= 0x85ebca6b;
        hash ^= hash >>> 13;
        hash *= 0xc2b2ae35;
        hash ^= hash >>> 16;
        return hash;
    }

    /**
     * A crude suffix stripper: enough to match "videos" with "video", "editing" with "edit", and,
     * by also dropping a final "e", "coding" with "code" (both "cod").
     */
    private static String stem(String word) {
        String stem = word;
        if (stem.length() > 4 && stem.endsWith("ies")) {
            stem = stem.substring(0, stem.length() - 3) + "y";
        } else if (stem.length() > 5 && stem.endsWith("ing")) {
            stem = stem.substring(0, stem.length() - 3);
        } else if (stem.length() > 4 && stem.endsWith("ed")) {
            stem = stem.substring(0, stem.length() - 2);
        } else if (stem.length() > 3 && stem.endsWith("s") && !stem.endsWith("ss")) {
            stem = stem.substring(0, stem.length() - 1);
        }
        if (stem.length() > 3 && stem.endsWith("e")) {
            stem = stem.substring(0, stem.length() - 1);
        }
        return stem;
    }

    private static void normalise(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum == 0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }
}
