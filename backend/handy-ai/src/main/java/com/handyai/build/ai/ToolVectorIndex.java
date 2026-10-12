package com.handyai.build.ai;

import com.handyai.build.domain.AiTool;
import com.handyai.build.repository.AiToolRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * An in-memory vector index over the tool catalogue: one {@link TextEmbedder} vector per tool,
 * searched by cosine similarity (an exact nearest-neighbour scan, which is plenty for a catalogue
 * of hundreds of tools).
 *
 * <p>Built on first use and rebuilt after the catalogue is seeded ({@link #invalidate}). Powers
 * semantic search, "similar tools", and the retrieval step of the chat's RAG prompt.
 */
@Component
public class ToolVectorIndex {

    /** Below this similarity a hit shares little more than a common word with the query. */
    static final double MIN_SCORE = 0.08;

    public record Hit(Long toolId, String slug, double score) {
    }

    private record Entry(Long toolId, String slug, float[] vector) {
    }

    private record Index(TextEmbedder embedder, Map<String, Entry> bySlug) {
    }

    private final AiToolRepository toolRepository;
    private volatile Index index;
    /** Bumped by every invalidation, so a build that raced one is used once but not kept. */
    private final AtomicInteger generation = new AtomicInteger();

    public ToolVectorIndex(AiToolRepository toolRepository) {
        this.toolRepository = toolRepository;
    }

    /** The tools closest in meaning to free text, best first. */
    public List<Hit> search(String text, int limit, Collection<String> excludeSlugs) {
        Index current = index();
        List<String> features = TextEmbedder.features(text);
        if (features.isEmpty()) {
            return List.of();
        }
        return nearest(current, current.embedder().embed(features), limit, excludeSlugs);
    }

    /** The tools closest to the given one, excluding itself; empty for an unknown slug. */
    public List<Hit> similarTo(String slug, int limit) {
        Index current = index();
        Entry entry = current.bySlug().get(slug);
        return entry == null ? List.of() : nearest(current, entry.vector(), limit, List.of(slug));
    }

    /**
     * Drops the index so the next search rebuilds it. Called inside a transaction (the seeder),
     * it drops it again after the commit: the web server takes requests while the seeder runs, and
     * a search in between would otherwise cache an index built from rows not yet committed.
     */
    public void invalidate() {
        drop();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    drop();
                }
            });
        }
    }

    private void drop() {
        generation.incrementAndGet();
        index = null;
    }

    private static List<Hit> nearest(Index index, float[] query, int limit,
                                     Collection<String> excludeSlugs) {
        return index.bySlug().values().stream()
                .filter(entry -> !excludeSlugs.contains(entry.slug()))
                .map(entry -> new Hit(entry.toolId(), entry.slug(),
                        TextEmbedder.cosine(query, entry.vector())))
                .filter(hit -> hit.score() >= MIN_SCORE)
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(Math.max(limit, 0))
                .toList();
    }

    private Index index() {
        Index current = index;
        if (current == null) {
            synchronized (this) {
                current = index;
                if (current == null) {
                    int startedAt = generation.get();
                    current = build();
                    if (generation.get() == startedAt) {
                        index = current;
                    }
                }
            }
        }
        return current;
    }

    private Index build() {
        List<AiTool> tools = toolRepository.findAllByOrderByPopularityDesc(Pageable.unpaged());
        Map<String, List<String>> documents = new LinkedHashMap<>();
        tools.forEach(tool -> documents.put(tool.getSlug(), document(tool)));
        TextEmbedder embedder = TextEmbedder.fit(documents.values());

        Map<String, Entry> bySlug = new LinkedHashMap<>();
        for (AiTool tool : tools) {
            bySlug.put(tool.getSlug(), new Entry(tool.getId(), tool.getSlug(),
                    embedder.embed(documents.get(tool.getSlug()))));
        }
        return new Index(embedder, bySlug);
    }

    /** The text a tool is known by, with the most telling fields repeated to weigh more. */
    private static List<String> document(AiTool tool) {
        List<String> features = new ArrayList<>();
        addTimes(features, TextEmbedder.features(tool.getName()), 3);
        addTimes(features, TextEmbedder.features(String.join(" ", tool.tagList())), 2);
        addTimes(features, TextEmbedder.features(tool.getTagline()), 2);
        addTimes(features, TextEmbedder.features(tool.getCategory().getName()), 2);
        features.addAll(TextEmbedder.features(tool.getDescription()));
        return features;
    }

    private static void addTimes(List<String> target, List<String> features, int times) {
        for (int i = 0; i < times; i++) {
            target.addAll(features);
        }
    }
}
