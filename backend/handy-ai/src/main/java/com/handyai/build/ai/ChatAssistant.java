package com.handyai.build.ai;

import com.handyai.build.domain.PriceBook.Currency;
import com.handyai.build.domain.PriceBook.Money;
import com.handyai.build.dto.ToolResponse;
import com.handyai.build.repository.AiToolRepository;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Writes the chat reply with a local LLM (Ollama) using retrieval-augmented generation (RAG):
 * <ol>
 *   <li><b>Retrieve</b>: the tools the recommender picked (shown to the user as cards), plus the
 *   nearest tools from the {@link ToolVectorIndex} that also pass the user's price filters.</li>
 *   <li><b>Fit the token budget</b>: tools are added in order until the estimated prompt size
 *   reaches {@link AiProperties#contextTokenBudget()}, and long descriptions are clipped.</li>
 *   <li><b>Inject and generate</b>: the tools go into the prompt as the only facts the model may
 *   use, and it writes a short Markdown answer.</li>
 * </ol>
 * Returns empty whenever the model is off, over budget or fails; the chat then keeps its rule-based
 * reply, so the cards and the conversation never depend on the API.
 */
@Service
public class ChatAssistant {

    /** Static, so it stays byte-identical across requests (a prerequisite for prompt caching). */
    static final String SYSTEM_PROMPT = """
            You are the assistant inside HandyAI, a marketplace that helps people pick AI tools \
            for their work.

            Each message gives you <tools>, the catalogue entries most relevant to the request, \
            and <request>, what the person asked plus what you know about them. Recommend only \
            tools from <tools>, and use only the facts given there: never invent tools, features \
            or prices. Tools marked "shown as a card" are already on screen, so lead with those. \
            If nothing listed fits, say so plainly and suggest describing the task differently.

            Reply in at most 120 words of Markdown: one short opening sentence, then a bullet \
            list with each recommended tool's name in **bold** and one line on why it fits this \
            person. Quote prices only as written in <tools>.""";

    static final int MAX_RELATED = 4;
    private static final int DESCRIPTION_CHARS = 240;
    private static final Locale INDIA = Locale.forLanguageTag("en-IN");

    /** One chat turn after the rule-based parser has read it. */
    public record Turn(String message, String goal, String profession, Boolean freePlanOnly,
                       Double maxMonthly, Currency currency, List<ToolResponse> picks) {
    }

    private final OllamaClient llm;
    private final ToolVectorIndex vectorIndex;
    private final AiToolRepository toolRepository;
    private final AiProperties properties;

    public ChatAssistant(OllamaClient llm, ToolVectorIndex vectorIndex,
                         AiToolRepository toolRepository, AiProperties properties) {
        this.llm = llm;
        this.vectorIndex = vectorIndex;
        this.toolRepository = toolRepository;
        this.properties = properties;
    }

    public Optional<OllamaClient.Reply> compose(Turn turn) {
        if (!llm.available()) {
            return Optional.empty();
        }
        return llm.complete(SYSTEM_PROMPT, userMessage(turn));
    }

    String userMessage(Turn turn) {
        Set<String> cardSlugs = turn.picks().stream().map(ToolResponse::slug)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ToolResponse> candidates = new ArrayList<>(turn.picks());
        candidates.addAll(related(turn, cardSlugs));

        StringBuilder tools = new StringBuilder();
        int index = 1;
        for (ToolResponse tool : candidates) {
            String entry = describe(index, tool, cardSlugs.contains(tool.slug()), turn.currency());
            // Picks always go in (they are on screen); related tools only while the budget allows.
            if (index > turn.picks().size()
                    && estimateTokens(tools) + estimateTokens(entry) > properties.contextTokenBudget()) {
                break;
            }
            tools.append(entry);
            index++;
        }

        StringBuilder request = new StringBuilder();
        if (turn.goal() != null && !turn.goal().isBlank()) {
            request.append("Task: ").append(turn.goal().trim()).append('\n');
        }
        if (turn.profession() != null) {
            request.append("Profession: ").append(turn.profession()).append('\n');
        }
        if (Boolean.TRUE.equals(turn.freePlanOnly())) {
            request.append("Only tools with a free plan\n");
        }
        if (turn.maxMonthly() != null) {
            request.append("Budget: up to ").append(money(turn.maxMonthly(), turn.currency()))
                    .append(" a month\n");
        }
        request.append("Latest message: ").append(turn.message().trim());

        return "<tools>\n" + (tools.isEmpty() ? "(no matching tools)\n" : tools) + "</tools>\n\n"
                + "<request>\n" + request + "\n</request>";
    }

    /** Vector-search neighbours of the task that respect the same filters as the cards. */
    private List<ToolResponse> related(Turn turn, Set<String> cardSlugs) {
        String query = turn.goal() != null && !turn.goal().isBlank() ? turn.goal() : turn.message();
        List<ToolVectorIndex.Hit> hits = vectorIndex.search(query, MAX_RELATED * 3, cardSlugs);
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<Long, ToolResponse> byId = toolRepository
                .findByIdIn(hits.stream().map(ToolVectorIndex.Hit::toolId).toList()).stream()
                .map(tool -> ToolResponse.from(tool, false))
                .collect(Collectors.toMap(ToolResponse::id, Function.identity()));
        return hits.stream()
                .map(hit -> byId.get(hit.toolId()))
                .filter(tool -> tool != null && fitsFilters(tool, turn))
                .limit(MAX_RELATED)
                .toList();
    }

    private static boolean fitsFilters(ToolResponse tool, Turn turn) {
        if (Boolean.TRUE.equals(turn.freePlanOnly()) && !tool.hasFreePlan()) {
            return false;
        }
        if (turn.maxMonthly() == null || tool.hasFreePlan()) {
            return true;
        }
        Money monthly = tool.plans() == null ? null : tool.plans().monthly();
        return monthly != null && amount(monthly, turn.currency()) <= turn.maxMonthly();
    }

    private static String describe(int index, ToolResponse tool, boolean card, Currency currency) {
        String description = tool.description() == null ? "" : tool.description();
        if (description.length() > DESCRIPTION_CHARS) {
            description = description.substring(0, DESCRIPTION_CHARS).trim() + "...";
        }
        return index + ". " + tool.name() + " (" + tool.categoryName() + ")"
                + (card ? " - shown as a card" : "") + "\n"
                + "   Price: " + price(tool, currency) + "\n"
                + "   " + tool.tagline() + " " + description + "\n";
    }

    private static String price(ToolResponse tool, Currency currency) {
        Money monthly = tool.plans() == null ? null : tool.plans().monthly();
        String free = tool.hasFreePlan() ? "free plan" : "no free plan";
        if (monthly == null || !tool.plans().paidPlanAvailable()) {
            return free;
        }
        return free + "; paid from " + money(amount(monthly, currency), currency) + " a month";
    }

    private static double amount(Money money, Currency currency) {
        return currency == Currency.INR ? money.inr() : money.usd();
    }

    private static String money(double amount, Currency currency) {
        return currency == Currency.INR
                ? "₹" + NumberFormat.getIntegerInstance(INDIA).format(Math.round(amount))
                : "$" + String.format(Locale.ROOT, "%.2f", amount);
    }

    /** About four characters per token for English: close enough to keep prompts in budget. */
    static int estimateTokens(CharSequence text) {
        return (text.length() + 3) / 4;
    }
}
