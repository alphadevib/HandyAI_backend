package com.handyai.build.service;

import com.handyai.build.domain.PriceBook.Currency;
import com.handyai.build.domain.ProfessionCatalog;
import com.handyai.build.dto.ChatRequest;
import com.handyai.build.dto.ChatRequest.Context;
import com.handyai.build.dto.ChatResponse;
import com.handyai.build.dto.RecommendationRequest;
import com.handyai.build.dto.RecommendationResponse;
import com.handyai.build.dto.RecommendationResponse.Recommendation;
import com.handyai.build.repository.UserRepository;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A conversational front end to the recommendation engine, with no external AI service.
 *
 * <p>Each message is read for a few kinds of intent: a new task to find tools for, a profession,
 * a budget ("only free", "under 1000 rupees"), a request for different results, or small talk.
 * Anything that only refines the last answer keeps the earlier task, so "only free ones" after
 * "edit podcasts" means free podcast tools rather than a new search for the word "free".
 */
@Service
public class ChatService {

    private static final int RESULTS_PER_TURN = 4;

    /** Groups rupee amounts the Indian way (1,00,000). Built from a tag so it runs on Java 17. */
    private static final Locale INDIA = Locale.forLanguageTag("en-IN");

    private static final Pattern GREETING = Pattern.compile(
            "^(hi|hii+|hello|hey|hey there|namaste|good (morning|afternoon|evening)|yo)\\W*$");
    private static final Pattern THANKS = Pattern.compile(
            "^(thanks|thank you|thx|ty|great|cool|ok|okay|perfect|awesome|bye|goodbye)\\W*.*$");
    /** Only a bare request for help; "help me summarise my meetings" is a task. */
    private static final Pattern HELP = Pattern.compile(
            "^(help|help me|\\?|what can you do|how does this work|who are you|what do you do)\\W*$");
    private static final Pattern BUDGET = Pattern.compile(
            "(under|below|less than|within|upto|up to|max(?:imum)?|budget(?: of| is)?|cheaper than)"
                    + "\\s*(₹|rs\\.?|inr|\\$|usd)?\\s*([\\d,]+(?:\\.\\d+)?)\\s*(k)?"
                    + "\\s*(₹|rs\\.?|rupees?|inr|\\$|usd|dollars?)?");
    private static final Pattern FREE = Pattern.compile("\\bfree\\b");
    private static final Pattern CHEAP = Pattern.compile("\\b(cheap|cheaper|affordable|low cost|budget)\\b");
    private static final Pattern ANY_PRICE = Pattern.compile(
            "\\b(any price|paid is fine|paid is ok|price doesn'?t matter|no budget|remove (the )?"
                    + "(budget|filter|price)|clear (the )?filters?)\\b");
    private static final Pattern MORE = Pattern.compile(
            "\\b(more|others?|another|different|else|alternatives?|something new)\\b");
    private static final Pattern PROFESSION_CUE = Pattern.compile(
            "\\b(i am|i'm|im|i work as|work as|as an?|we are|we're|my job|my profession|"
                    + "profession is|for an?)\\b");

    /** Words that carry no task on their own, used to tell a refinement from a new request. */
    private static final Set<String> FILLER = Set.of(
            "show", "me", "give", "find", "suggest", "recommend", "some", "any", "only", "just",
            "tools", "tool", "apps", "app", "options", "option", "ones", "one", "please", "pls",
            "can", "you", "the", "and", "with", "that", "are", "for", "what", "which", "about",
            "need", "want", "like", "would", "could", "also", "too", "now", "then", "but", "not",
            "plan", "plans", "price", "priced", "month", "monthly", "per", "rupees", "rupee",
            "dollars", "dollar", "inr", "usd", "cost", "costs", "work", "job", "profession",
            "ai", "i'm", "im", "am", "we", "our", "my", "is", "it", "its", "has", "have", "do",
            "there", "something", "instead", "really", "very", "good", "best", "top", "fine",
            "okay", "alright", "actually", "maybe", "still", "same", "again", "those", "these",
            "them", "they", "lot", "lots", "much", "less", "list", "see");

    private final RecommendationService recommendationService;
    private final UserRepository userRepository;

    public ChatService(RecommendationService recommendationService,
                       UserRepository userRepository) {
        this.recommendationService = recommendationService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ChatResponse reply(ChatRequest request, Long currentUserId) {
        Context previous = request.context() == null ? Context.empty() : request.context();
        String original = request.message().trim();
        String text = original.toLowerCase(Locale.ROOT);

        if (GREETING.matcher(text).matches()) {
            return conversational("Hi! Tell me what you are trying to get done, like \"edit my "
                    + "podcast\" or \"build a website\", and I will suggest the AI tools that fit. "
                    + "You can also tell me your profession or a monthly budget.", previous);
        }
        if (THANKS.matcher(text).matches() && text.split("\\s+").length <= 4) {
            return conversational("Happy to help. Ask me about another task whenever you like.",
                    previous);
        }
        if (HELP.matcher(text).matches()) {
            return conversational("I match your task to tools in the HandyAI marketplace. Describe "
                    + "the job in a sentence, then refine: say \"only free ones\", \"under 1000 "
                    + "rupees a month\", \"I'm a teacher\" or \"show me others\".", previous);
        }

        Currency currency = detectCurrency(text, previous.currency());
        Boolean freePlanOnly = previous.freePlanOnly();
        Double maxMonthly = previous.maxMonthlyPrice();
        String profession = previous.profession();
        String goal = previous.goal();
        String remainder = text;

        if (ANY_PRICE.matcher(text).find()) {
            freePlanOnly = false;
            maxMonthly = null;
            remainder = ANY_PRICE.matcher(remainder).replaceAll(" ");
        }

        Matcher budget = BUDGET.matcher(text);
        if (budget.find()) {
            double amount = Double.parseDouble(budget.group(3).replace(",", ""));
            if (budget.group(4) != null) {
                amount *= 1000;
            }
            String unit = (budget.group(2) == null ? "" : budget.group(2))
                    + (budget.group(5) == null ? "" : budget.group(5));
            if (unit.contains("$") || unit.contains("usd") || unit.contains("dollar")) {
                currency = Currency.USD;
            } else if (!unit.isBlank()) {
                currency = Currency.INR;
            }
            maxMonthly = amount;
            remainder = BUDGET.matcher(remainder).replaceAll(" ");
        } else if (CHEAP.matcher(text).find() && maxMonthly == null) {
            maxMonthly = currency == Currency.INR ? 1000d : 12d;
        }
        remainder = CHEAP.matcher(remainder).replaceAll(" ");

        if (FREE.matcher(text).find() && !text.contains("free trial")) {
            freePlanOnly = true;
            remainder = FREE.matcher(remainder).replaceAll(" ");
        }

        boolean wantsMore = MORE.matcher(text).find();
        remainder = MORE.matcher(remainder).replaceAll(" ");

        Optional<ProfessionCatalog.Entry> mentioned = ProfessionCatalog.mentionedIn(text);
        if (mentioned.isPresent() && (PROFESSION_CUE.matcher(text).find()
                || contentWords(remainder).size() <= 2)) {
            profession = mentioned.get().name();
            for (String alias : profession.toLowerCase(Locale.ROOT).split("\\s*/\\s*")) {
                remainder = remainder.replace(alias, " ");
            }
            remainder = PROFESSION_CUE.matcher(remainder).replaceAll(" ");
        }

        // Whatever is left after the refinements decides whether this is a new task.
        boolean newTask = !contentWords(remainder).isEmpty();
        List<String> shown = previous.shownSlugs() == null ? List.of() : previous.shownSlugs();
        if (newTask) {
            goal = original.length() > 400 ? original.substring(0, 400) : original;
            shown = List.of();
            wantsMore = false;
        }

        String effectiveProfession = profession != null ? profession : profileProfession(currentUserId);
        if ((goal == null || goal.isBlank()) && effectiveProfession == null) {
            Context context = new Context(goal, profession, freePlanOnly, maxMonthly,
                    currency.name(), shown);
            return new ChatResponse("Got it. Now tell me what you are trying to get done, or what "
                    + "you do for work, and I will pick tools for it.", List.of(), context,
                    List.of("Summarise my meetings", "Make a logo", "I'm a software developer"));
        }

        RecommendationResponse result = recommendationService.recommend(new RecommendationRequest(
                goal, profession, List.of(), null, RESULTS_PER_TURN, freePlanOnly, maxMonthly,
                currency.name(), wantsMore ? shown : List.of()), currentUserId);

        List<Recommendation> picks = result.recommendations();
        Set<String> nowShown = new LinkedHashSet<>(wantsMore ? shown : List.of());
        picks.forEach(pick -> nowShown.add(pick.tool().slug()));
        // A new task that found nothing is not worth remembering: the next "show me others" or
        // "only free ones" should still refine the last task that worked.
        boolean keepPrevious = newTask && picks.isEmpty();
        Context context = keepPrevious
                ? new Context(previous.goal(), profession, freePlanOnly, maxMonthly,
                        currency.name(), previous.shownSlugs())
                : new Context(goal, profession, freePlanOnly, maxMonthly, currency.name(),
                        List.copyOf(nowShown));

        return new ChatResponse(
                replyText(picks, result.summary(), goal, effectiveProfession, freePlanOnly,
                        maxMonthly, currency, wantsMore),
                picks, context, suggestions(context, effectiveProfession, picks.isEmpty()));
    }

    private ChatResponse conversational(String reply, Context context) {
        return new ChatResponse(reply, List.of(), context, List.of(
                "Edit videos for YouTube", "Write blog posts faster", "I'm a teacher",
                "Free tools for designers"));
    }

    private String replyText(List<Recommendation> picks, String noMatchSummary, String goal,
                             String profession, Boolean freePlanOnly, Double maxMonthly,
                             Currency currency, boolean wantsMore) {
        StringBuilder filters = new StringBuilder();
        if (Boolean.TRUE.equals(freePlanOnly)) {
            filters.append(" with a free plan");
        }
        if (maxMonthly != null) {
            filters.append(filters.isEmpty() ? "" : ",").append(" up to ")
                    .append(money(maxMonthly, currency)).append(" a month");
        }

        if (picks.isEmpty()) {
            if (wantsMore) {
                return "That is everything I have" + filters + " for this. Try relaxing the budget "
                        + "or describing the task another way.";
            }
            return noMatchSummary;
        }

        StringBuilder reply = new StringBuilder(picks.size() == 1 ? "Here is " : "Here are ");
        reply.append(picks.size()).append(wantsMore ? " more" : "")
                .append(picks.size() == 1 ? " tool" : " tools");
        if (goal != null && !goal.isBlank()) {
            reply.append(" for \"").append(goal.trim()).append('"');
        }
        if (profession != null) {
            reply.append(goal == null || goal.isBlank() ? " picked for a " : " as a ")
                    .append(profession.toLowerCase(Locale.ROOT));
        }
        reply.append(filters).append(". ");

        Recommendation top = picks.get(0);
        reply.append("My top pick is ").append(top.tool().name());
        if (!top.reasons().isEmpty()) {
            reply.append(": ").append(lowerFirst(top.reasons().get(0)));
        }
        reply.append('.');
        return reply.toString();
    }

    private List<String> suggestions(Context context, String profession, boolean empty) {
        List<String> chips = new ArrayList<>();
        if (empty) {
            if (Boolean.TRUE.equals(context.freePlanOnly()) || context.maxMonthlyPrice() != null) {
                chips.add("Any price is fine");
            }
            chips.add("Help");
            return chips;
        }
        chips.add("Show me others");
        if (!Boolean.TRUE.equals(context.freePlanOnly())) {
            chips.add("Only free ones");
        }
        if (context.maxMonthlyPrice() == null) {
            chips.add("INR".equals(context.currency()) ? "Under ₹1,000 a month" : "Under $15 a month");
        } else {
            chips.add("Any price is fine");
        }
        if (profession == null) {
            chips.add("I'm a student");
        }
        return chips;
    }

    private String profileProfession(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(user -> user.getProfession()).orElse(null);
    }

    private static Currency detectCurrency(String text, String previous) {
        if (text.contains("$") || text.matches(".*\\b(usd|dollars?)\\b.*")) {
            return Currency.USD;
        }
        if (text.contains("₹") || text.matches(".*\\b(rs\\.?|inr|rupees?)\\b.*")) {
            return Currency.INR;
        }
        try {
            return Currency.parse(previous);
        } catch (IllegalArgumentException ex) {
            return Currency.INR;
        }
    }

    private static List<String> contentWords(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9+#']+"))
                .filter(word -> word.length() > 2)
                .filter(word -> !FILLER.contains(word))
                .toList();
    }

    private static String money(double amount, Currency currency) {
        NumberFormat format = NumberFormat.getIntegerInstance(INDIA);
        return currency == Currency.INR ? "₹" + format.format(Math.round(amount))
                : "$" + (amount == Math.rint(amount) ? String.valueOf((long) amount)
                        : String.format(Locale.ROOT, "%.2f", amount));
    }

    private static String lowerFirst(String value) {
        return value.isEmpty() ? value : Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }
}
