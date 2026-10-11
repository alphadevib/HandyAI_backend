package com.handyai.build.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The professions an individual can pick and the industries an organisation can pick, each mapped
 * onto the catalogue categories that matter most for that kind of work.
 *
 * <p>This is the single source for both the sign-up selection box and the recommendation engine,
 * so a profession on someone's profile always means the same thing when tools are ranked for them.
 */
public final class ProfessionCatalog {

    public record Entry(String name, String group, List<String> categories) {
    }

    private static final Map<String, Entry> PROFESSIONS = new LinkedHashMap<>();
    private static final Map<String, Entry> INDUSTRIES = new LinkedHashMap<>();

    static {
        // --- Technology ---------------------------------------------------------------------
        profession("Software developer", "Technology", "coding", "productivity", "automation");
        profession("Frontend developer", "Technology", "coding", "design");
        profession("Backend developer", "Technology", "coding", "data", "automation");
        profession("Mobile app developer", "Technology", "coding", "design");
        profession("DevOps engineer", "Technology", "coding", "automation");
        profession("QA / test engineer", "Technology", "coding", "automation");
        profession("Data scientist", "Technology", "data", "coding", "research");
        profession("Data analyst", "Technology", "data", "productivity");
        profession("Machine learning engineer", "Technology", "coding", "data", "research");
        profession("Cybersecurity analyst", "Technology", "coding", "research", "automation");
        profession("IT support specialist", "Technology", "chatbots", "automation", "productivity");
        profession("Product manager", "Technology", "productivity", "meetings", "data");
        // --- Design & creative --------------------------------------------------------------
        profession("Product designer", "Design & creative", "design", "image", "productivity");
        profession("UI/UX designer", "Design & creative", "design", "image");
        profession("Graphic designer", "Design & creative", "image", "design");
        profession("Illustrator", "Design & creative", "image", "design");
        profession("Photographer", "Design & creative", "image", "design");
        profession("Video editor", "Design & creative", "video", "audio");
        profession("Animator", "Design & creative", "video", "image");
        profession("Architect", "Design & creative", "design", "image", "productivity");
        profession("Interior designer", "Design & creative", "design", "image");
        profession("Fashion designer", "Design & creative", "image", "design", "marketing");
        // --- Content & media ----------------------------------------------------------------
        profession("Content writer", "Content & media", "writing", "marketing", "research");
        profession("Copywriter", "Content & media", "writing", "marketing");
        profession("Journalist", "Content & media", "writing", "research", "audio");
        profession("Author / novelist", "Content & media", "writing", "research");
        profession("YouTuber / content creator", "Content & media", "video", "audio", "marketing");
        profession("Podcaster", "Content & media", "audio", "video", "writing");
        profession("Musician / music producer", "Content & media", "audio", "video");
        profession("Social media manager", "Content & media", "marketing", "image", "video");
        profession("Translator", "Content & media", "writing", "chatbots");
        // --- Business -----------------------------------------------------------------------
        profession("Marketing manager", "Business", "marketing", "writing", "data");
        profession("SEO specialist", "Business", "marketing", "writing", "data");
        profession("Sales executive", "Business", "meetings", "writing", "automation");
        profession("Business analyst", "Business", "data", "productivity", "meetings");
        profession("Entrepreneur / founder", "Business", "productivity", "marketing", "automation");
        profession("Project manager", "Business", "productivity", "meetings", "automation");
        profession("Operations manager", "Business", "automation", "productivity", "data");
        profession("HR / recruiter", "Business", "writing", "meetings", "automation");
        profession("Customer support agent", "Business", "chatbots", "automation", "writing");
        profession("Management consultant", "Business", "research", "meetings", "data");
        profession("E-commerce seller", "Business", "marketing", "image", "chatbots");
        profession("Real estate agent", "Business", "marketing", "image", "writing");
        // --- Finance & law ------------------------------------------------------------------
        profession("Accountant", "Finance & law", "data", "productivity", "automation");
        profession("Chartered accountant", "Finance & law", "data", "research", "productivity");
        profession("Financial analyst", "Finance & law", "data", "research");
        profession("Investment banker", "Finance & law", "data", "research", "writing");
        profession("Lawyer / advocate", "Finance & law", "research", "writing", "chatbots");
        profession("Paralegal", "Finance & law", "research", "writing", "productivity");
        // --- Education & research -----------------------------------------------------------
        profession("Teacher", "Education & research", "research", "writing", "design");
        profession("Professor / lecturer", "Education & research", "research", "writing",
                "meetings");
        profession("Student", "Education & research", "research", "writing", "productivity");
        profession("Researcher / scientist", "Education & research", "research", "data",
                "writing");
        profession("Tutor / coach", "Education & research", "research", "video", "meetings");
        // --- Health & public service --------------------------------------------------------
        profession("Doctor / physician", "Health & public service", "research", "meetings",
                "chatbots");
        profession("Nurse", "Health & public service", "research", "productivity", "chatbots");
        profession("Pharmacist", "Health & public service", "research", "data");
        profession("Psychologist / therapist", "Health & public service", "meetings", "writing",
                "research");
        profession("Civil servant", "Health & public service", "writing", "research",
                "productivity");
        profession("NGO / social worker", "Health & public service", "writing", "marketing",
                "productivity");

        industry("Software & SaaS", "coding", "automation", "chatbots", "data");
        industry("IT services & consulting", "coding", "meetings", "productivity", "automation");
        industry("E-commerce & retail", "marketing", "image", "chatbots", "data");
        industry("Marketing & advertising agency", "marketing", "writing", "image", "video");
        industry("Media & publishing", "writing", "video", "audio", "research");
        industry("Film, TV & entertainment", "video", "audio", "image");
        industry("Design & creative studio", "design", "image", "video");
        industry("Education & e-learning", "research", "video", "writing", "chatbots");
        industry("Healthcare & hospitals", "research", "chatbots", "meetings", "data");
        industry("Pharma & life sciences", "research", "data", "writing");
        industry("Banking & financial services", "data", "research", "chatbots", "automation");
        industry("Insurance", "chatbots", "data", "automation");
        industry("Legal services", "research", "writing", "chatbots");
        industry("Accounting & audit", "data", "automation", "productivity");
        industry("Real estate & construction", "design", "marketing", "image");
        industry("Manufacturing", "data", "automation", "productivity");
        industry("Logistics & supply chain", "automation", "data", "productivity");
        industry("Travel & hospitality", "chatbots", "marketing", "writing");
        industry("Telecom", "chatbots", "data", "automation");
        industry("Energy & utilities", "data", "research", "automation");
        industry("Agriculture & food", "data", "marketing", "research");
        industry("Government & public sector", "writing", "research", "chatbots", "productivity");
        industry("Non-profit & NGO", "writing", "marketing", "productivity");
        industry("Recruitment & HR services", "writing", "meetings", "automation");
        industry("Customer support / BPO", "chatbots", "automation", "meetings");
    }

    private ProfessionCatalog() {
    }

    private static void profession(String name, String group, String... categories) {
        PROFESSIONS.put(key(name), new Entry(name, group, List.of(categories)));
    }

    private static void industry(String name, String... categories) {
        INDUSTRIES.put(key(name), new Entry(name, "Industry", List.of(categories)));
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    public static List<Entry> professions() {
        return List.copyOf(PROFESSIONS.values());
    }

    public static List<Entry> industries() {
        return List.copyOf(INDUSTRIES.values());
    }

    /** Exact (case-insensitive) match on a profession or an industry name. */
    public static Optional<Entry> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String key = key(name);
        Entry entry = PROFESSIONS.get(key);
        return Optional.ofNullable(entry != null ? entry : INDUSTRIES.get(key));
    }

    /**
     * Finds a profession or industry mentioned inside free text such as "I'm a teacher", matching
     * each part of a name like "Lawyer / advocate" on its own. The longest match wins, so
     * "data scientist" is not mistaken for something shorter.
     */
    public static Optional<Entry> mentionedIn(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String haystack = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9&+/ ]", " ") + " ";
        Entry best = null;
        int bestLength = 0;
        for (Map<String, Entry> source : List.of(PROFESSIONS, INDUSTRIES)) {
            for (Entry entry : source.values()) {
                for (String alias : entry.name().toLowerCase(Locale.ROOT).split("\\s*/\\s*")) {
                    String needle = alias.trim();
                    if (needle.length() < 4) {
                        continue;
                    }
                    if ((haystack.contains(" " + needle + " ") || haystack.contains(" " + needle + "s "))
                            && needle.length() > bestLength) {
                        best = entry;
                        bestLength = needle.length();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
