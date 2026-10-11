package com.handyai.build.config;

import com.handyai.build.domain.AiTool;
import com.handyai.build.domain.Category;
import com.handyai.build.domain.PriceBook;
import com.handyai.build.domain.PricingModel;
import com.handyai.build.domain.Role;
import com.handyai.build.domain.User;
import com.handyai.build.repository.AiToolRepository;
import com.handyai.build.repository.CategoryRepository;
import com.handyai.build.repository.UserRepository;
import com.handyai.build.security.PasswordHasher;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the curated catalogue on first start.
 *
 * <p>Idempotent by slug: a restart inserts only what is missing, so the app can be run repeatedly
 * against a persistent MySQL database without duplicating rows or failing on unique constraints.
 */
@Component
public class CatalogueSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogueSeeder.class);

    private final CategoryRepository categoryRepository;
    private final AiToolRepository toolRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final boolean seedDemoUser;

    public CatalogueSeeder(CategoryRepository categoryRepository, AiToolRepository toolRepository,
                           UserRepository userRepository, PasswordHasher passwordHasher,
                           @Value("${handyai.seed.demo-user:true}") boolean seedDemoUser) {
        this.categoryRepository = categoryRepository;
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.seedDemoUser = seedDemoUser;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Category> categories = seedCategories();
        int inserted = seedTools(categories);
        seedPrices();
        seedDemoUser();
        log.info("Catalogue ready: {} categories, {} tools ({} new this start)",
                categoryRepository.count(), toolRepository.count(), inserted);
    }

    private Map<String, Category> seedCategories() {
        Map<String, Category> categories = new LinkedHashMap<>();
        category(categories, "coding", "Coding & Dev", "💻",
                "Pair programmers, code review and shipping helpers");
        category(categories, "writing", "Writing", "✍️",
                "Drafting, editing and tone for everyday written work");
        category(categories, "image", "Image Generation", "🎨",
                "Turn a description into artwork, mockups and photography");
        category(categories, "design", "Design & UI", "🖌️",
                "Interfaces, presentations, brand assets and prototypes");
        category(categories, "video", "Video", "🎬",
                "Editing, avatars, subtitles and clip generation");
        category(categories, "audio", "Audio & Voice", "🎧",
                "Voice cloning, music, transcription and clean-up");
        category(categories, "productivity", "Productivity", "⚡",
                "Notes, planning and the small tasks that eat your day");
        category(categories, "meetings", "Meetings", "🗓️",
                "Recording, summarising and following up on calls");
        category(categories, "research", "Research", "🔍",
                "Reading, citing and making sense of dense material");
        category(categories, "data", "Data & Analytics", "📊",
                "Spreadsheets, dashboards and asking questions of your data");
        category(categories, "marketing", "Marketing & SEO", "📣",
                "Campaigns, landing copy, ads and search visibility");
        category(categories, "automation", "Automation", "🤖",
                "Connect the apps you already use and let them run themselves");
        category(categories, "chatbots", "Assistants & Chatbots", "💬",
                "General purpose assistants you can talk to all day");
        return categories;
    }

    private void category(Map<String, Category> sink, String slug, String name, String icon,
                          String description) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseGet(() -> categoryRepository.save(
                        new Category(name, slug, description, icon)));
        sink.put(slug, category);
    }

    private int seedTools(Map<String, Category> categories) {
        int[] inserted = {0};
        ToolSink sink = (slug, name, categorySlug, tagline, description, url, pricing, priceNote,
                tags, popularity, featured) -> {
            if (toolRepository.existsBySlug(slug)) {
                return;
            }
            AiTool tool = new AiTool();
            tool.setSlug(slug);
            tool.setName(name);
            tool.setCategory(categories.get(categorySlug));
            tool.setTagline(tagline);
            tool.setDescription(description);
            tool.setWebsiteUrl(url);
            tool.setPricingModel(pricing);
            tool.setPriceNote(priceNote);
            tool.setTags(tags);
            tool.setPopularity(popularity);
            tool.setFeatured(featured);
            toolRepository.save(tool);
            inserted[0]++;
        };

        // --- Assistants -------------------------------------------------------------------
        sink.add("claude", "Claude", "chatbots",
                "A careful writing and reasoning partner with a very long memory",
                "Claude handles long documents in a single conversation, which makes it strong for "
                        + "reviewing contracts, refactoring a codebase or turning messy notes into a "
                        + "clean brief. Projects keep your reference material attached so you stop "
                        + "re-explaining context every morning.",
                "https://claude.ai", PricingModel.FREEMIUM, "Free tier, Pro around $20/month",
                "writing,coding,analysis,assistant,long documents,research", 96, true);
        sink.add("perplexity", "Perplexity", "research",
                "A search engine that answers, then shows its sources",
                "Instead of ten blue links you get a written answer with numbered citations you can "
                        + "click. Excellent for the first thirty minutes of any research task, "
                        + "competitor checks and technical questions where you need the source.",
                "https://perplexity.ai", PricingModel.FREEMIUM, "Free, Pro around $20/month",
                "research,search,citations,sources,answers", 88, true);
        sink.add("poe", "Poe", "chatbots",
                "Many different assistants behind one subscription",
                "Poe puts several leading models side by side so you can ask the same question twice "
                        + "and compare. A cheap way to work out which assistant suits your writing "
                        + "before committing to a yearly plan.",
                "https://poe.com", PricingModel.FREEMIUM, "Free tier, Pro around $20/month",
                "assistant,comparison,chat,models", 62, false);

        // --- Coding -----------------------------------------------------------------------
        sink.add("github-copilot", "GitHub Copilot", "coding",
                "Autocomplete that finishes whole functions inside your editor",
                "Copilot suggests the next few lines as you type and can explain unfamiliar code. "
                        + "The value shows up most in boilerplate, tests and languages you only "
                        + "touch occasionally.",
                "https://github.com/features/copilot", PricingModel.TRIAL,
                "About $10/month, free for students",
                "coding,autocomplete,ide,developer,tests", 92, true);
        sink.add("cursor", "Cursor", "coding",
                "An editor built around asking questions about your own repository",
                "Cursor indexes the project so you can ask why a module exists, request a change "
                        + "across several files and review the diff before it lands. Familiar if you "
                        + "have used VS Code, because it is a fork of it.",
                "https://cursor.com", PricingModel.FREEMIUM, "Free tier, Pro around $20/month",
                "coding,editor,refactor,repository,developer", 84, true);
        sink.add("codeium", "Windsurf (Codeium)", "coding",
                "A genuinely capable free autocomplete for individual developers",
                "Still the best-known free option for unlimited code completion across dozens of "
                        + "languages, with a chat panel for explaining errors. Worth knowing about if "
                        + "a paid seat is not an option.",
                "https://windsurf.com", PricingModel.FREE, "Free for individual use",
                "coding,autocomplete,free,developer", 71, false);
        sink.add("sourcegraph-cody", "Sourcegraph Cody", "coding",
                "Code search and answers across very large codebases",
                "Cody shines in the situation most tools struggle with: a monorepo nobody fully "
                        + "understands. Ask where a behaviour is implemented and get the actual files.",
                "https://sourcegraph.com/cody", PricingModel.FREEMIUM, "Free tier for individuals",
                "coding,search,monorepo,legacy,developer", 58, false);

        // --- Writing ----------------------------------------------------------------------
        sink.add("grammarly", "Grammarly", "writing",
                "Catches the mistakes you stop seeing in your own writing",
                "Beyond spelling, it flags tone that reads sharper than you intended and tightens "
                        + "long sentences. Works inside email, docs and most browser text boxes.",
                "https://grammarly.com", PricingModel.FREEMIUM, "Free tier, Premium around $12/month",
                "writing,grammar,editing,tone,email", 86, false);
        sink.add("notion-ai", "Notion AI", "writing",
                "Drafting and summarising inside the notes you already keep",
                "Because it sits in your workspace it can summarise a meeting page, turn bullets "
                        + "into a proposal and answer questions about documents your team wrote.",
                "https://notion.so/product/ai", PricingModel.PAID, "Add-on to a Notion plan",
                "writing,notes,summary,workspace,productivity", 74, true);
        sink.add("quillbot", "QuillBot", "writing",
                "Rewrites a sentence until it says what you meant",
                "Paraphrasing, shortening and citation help, aimed squarely at students and anyone "
                        + "writing in a second language. The free tier covers most coursework.",
                "https://quillbot.com", PricingModel.FREEMIUM, "Free tier, Premium around $10/month",
                "writing,paraphrase,student,citations,editing", 63, false);
        sink.add("sudowrite", "Sudowrite", "writing",
                "A fiction writer's room that never gets tired",
                "Built for novelists rather than marketers: describe a scene and it suggests where "
                        + "it could go, expands sparse passages and keeps character notes straight.",
                "https://sudowrite.com", PricingModel.TRIAL, "From around $19/month",
                "writing,fiction,story,creative,novel", 47, false);

        // --- Image ------------------------------------------------------------------------
        sink.add("midjourney", "Midjourney", "image",
                "The most distinctive look in text-to-image generation",
                "Strong at atmosphere, lighting and illustration styles that feel art-directed. "
                        + "Often used for concept art, book covers and campaign visuals.",
                "https://midjourney.com", PricingModel.PAID, "From around $10/month",
                "image,art,illustration,concept art,design", 90, true);
        sink.add("ideogram", "Ideogram", "image",
                "The image generator that can actually spell",
                "Legible text inside images is the long-standing weak point of this category. "
                        + "Ideogram handles posters, logos and signage where the words matter.",
                "https://ideogram.ai", PricingModel.FREEMIUM, "Free tier, paid from around $8/month",
                "image,text,poster,logo,typography,design", 66, true);
        sink.add("leonardo-ai", "Leonardo.Ai", "image",
                "Game and product assets with repeatable style",
                "Trained models and style presets let you produce a set of assets that look like "
                        + "they belong together, which matters more than any single image.",
                "https://leonardo.ai", PricingModel.FREEMIUM, "Daily free credits",
                "image,game assets,style,design,3d", 59, false);
        sink.add("clipdrop", "Clipdrop", "image",
                "Remove a background or clean a photo in one click",
                "A toolbox of small, fast fixes: background removal, relighting, upscaling and "
                        + "object removal. The kind of thing you use weekly without thinking.",
                "https://clipdrop.co", PricingModel.FREEMIUM, "Free tier, Pro around $9/month",
                "image,photo,background,upscale,cleanup", 61, false);

        // --- Design -----------------------------------------------------------------------
        sink.add("figma-ai", "Figma AI", "design",
                "Turns a rough idea into a first screen you can edit",
                "Generates layouts, renames layers sensibly and drafts placeholder copy inside the "
                        + "design tool teams already use, so nothing has to be re-imported.",
                "https://figma.com", PricingModel.FREEMIUM, "Included in Figma plans",
                "design,ui,prototype,layout,product", 79, false);
        sink.add("gamma", "Gamma", "design",
                "A presentation that designs itself from your outline",
                "Paste bullet points and get a deck that is already formatted, responsive and "
                        + "shareable as a web page. A relief for anyone who dreads slide night.",
                "https://gamma.app", PricingModel.FREEMIUM, "Free credits, Plus around $10/month",
                "design,presentation,slides,deck,pitch", 72, true);
        sink.add("uizard", "Uizard", "design",
                "Photograph a whiteboard sketch and get a clickable mockup",
                "Aimed at people who are not designers: describe or sketch a screen and get "
                        + "something you can put in front of a stakeholder the same afternoon.",
                "https://uizard.io", PricingModel.FREEMIUM, "Free tier available",
                "design,mockup,wireframe,prototype,ui", 48, false);

        // --- Video ------------------------------------------------------------------------
        sink.add("descript", "Descript", "video",
                "Edit video by editing the transcript",
                "Delete a sentence in the text and it disappears from the recording, filler words "
                        + "included. The fastest route from a raw recording to a watchable cut.",
                "https://descript.com", PricingModel.FREEMIUM, "Free tier, Hobbyist around $12/month",
                "video,editing,transcript,podcast,audio", 81, true);
        sink.add("heygen", "HeyGen", "video",
                "Presenter videos in forty languages without a camera",
                "Useful for training material and product updates that need to exist in several "
                        + "languages and get re-recorded every quarter.",
                "https://heygen.com", PricingModel.TRIAL, "Free trial, from around $24/month",
                "video,avatar,translation,training,localisation", 68, false);
        sink.add("runway", "Runway", "video",
                "Generate and retouch footage that would need a shoot",
                "Text to video, background replacement and frame interpolation in one place. "
                        + "Popular with editors who need a shot that does not exist.",
                "https://runwayml.com", PricingModel.FREEMIUM, "Free credits, paid from around $15/month",
                "video,generation,vfx,editing,creative", 76, false);
        sink.add("opus-clip", "Opus Clip", "video",
                "Finds the good bits of a long video and cuts them for you",
                "Feed it an hour-long recording and get captioned vertical clips ranked by how "
                        + "likely they are to hold attention. A weekly job reduced to minutes.",
                "https://opus.pro", PricingModel.FREEMIUM, "Free tier with watermark",
                "video,clips,shorts,social,captions,marketing", 64, false);

        // --- Audio ------------------------------------------------------------------------
        sink.add("elevenlabs", "ElevenLabs", "audio",
                "Speech that does not sound like a robot reading",
                "Narration for videos, audiobooks and accessibility, with control over pacing and "
                        + "emotion, plus dubbing that keeps the original voice.",
                "https://elevenlabs.io", PricingModel.FREEMIUM, "Free monthly characters",
                "audio,voice,narration,dubbing,tts", 83, true);
        sink.add("suno", "Suno", "audio",
                "Describe a song and hear it a minute later",
                "Original background music for videos and prototypes without licensing worries. "
                        + "Surprisingly good at matching a mood you describe in plain words.",
                "https://suno.com", PricingModel.FREEMIUM, "Free daily credits",
                "audio,music,song,soundtrack,creative", 70, false);
        sink.add("adobe-podcast", "Adobe Podcast Enhance", "audio",
                "Makes a phone recording sound like a studio",
                "Removes room echo and background noise from speech, free, in the browser. Worth "
                        + "knowing about before you buy a microphone.",
                "https://podcast.adobe.com", PricingModel.FREE, "Free",
                "audio,noise removal,podcast,recording,cleanup", 57, false);

        // --- Meetings ---------------------------------------------------------------------
        sink.add("fathom", "Fathom", "meetings",
                "Free meeting notes that write the follow-up email too",
                "Joins the call, records it and produces a summary with action items by the time "
                        + "you have closed the window. The free plan covers unlimited recordings.",
                "https://fathom.video", PricingModel.FREE, "Free for individuals",
                "meetings,notes,summary,transcription,action items", 67, true);
        sink.add("otter-ai", "Otter.ai", "meetings",
                "Live transcription you can search months later",
                "Long-running favourite for interviews and lectures: a searchable transcript with "
                        + "speaker labels and highlights you can share.",
                "https://otter.ai", PricingModel.FREEMIUM, "Free tier, Pro around $17/month",
                "meetings,transcription,notes,interview,research", 73, false);

        // --- Productivity -----------------------------------------------------------------
        sink.add("mem", "Mem", "productivity",
                "Notes that resurface themselves when they matter",
                "Write without filing anything. Mem connects related notes and brings back the one "
                        + "you wrote six months ago when you start on the same topic again.",
                "https://mem.ai", PricingModel.TRIAL, "From around $10/month",
                "productivity,notes,knowledge,search,organisation", 52, false);
        sink.add("reclaim-ai", "Reclaim.ai", "productivity",
                "Defends your calendar from your calendar",
                "Finds room for focus time, habits and tasks around your meetings, and moves them "
                        + "automatically when something gets booked over.",
                "https://reclaim.ai", PricingModel.FREEMIUM, "Free tier for individuals",
                "productivity,calendar,scheduling,focus,planning", 60, false);
        sink.add("raycast-ai", "Raycast AI", "productivity",
                "An assistant one keystroke away, everywhere on your Mac",
                "Rewrite the selected paragraph, explain an error in your terminal or run a saved "
                        + "prompt without leaving the app you are in.",
                "https://raycast.com/ai", PricingModel.FREEMIUM, "Free launcher, AI from around $8/month",
                "productivity,launcher,shortcuts,mac,assistant", 65, false);

        // --- Research & data --------------------------------------------------------------
        sink.add("elicit", "Elicit", "research",
                "Reads a hundred papers and builds the comparison table",
                "Ask a research question and get relevant studies with their methods and findings "
                        + "extracted into columns. A genuine time saver for literature reviews.",
                "https://elicit.com", PricingModel.FREEMIUM, "Free monthly credits",
                "research,papers,academic,literature,science,student", 55, true);
        sink.add("napkin-ai", "Napkin AI", "research",
                "Turns a paragraph into the diagram you were about to draw",
                "Paste text and pick from generated visuals: flows, comparisons and timelines that "
                        + "are editable rather than flat images.",
                "https://napkin.ai", PricingModel.FREEMIUM, "Free while in beta",
                "research,diagram,visual,presentation,explain", 54, false);
        sink.add("julius-ai", "Julius AI", "data",
                "Ask your spreadsheet a question in plain English",
                "Upload a CSV and ask for the trend, the outliers or a chart. It writes and runs "
                        + "the analysis, then shows the code so you can check the work.",
                "https://julius.ai", PricingModel.FREEMIUM, "Limited free messages",
                "data,analysis,spreadsheet,charts,csv,statistics", 62, true);
        sink.add("rows", "Rows", "data",
                "A spreadsheet that can call the internet and summarise it",
                "Familiar grid, but cells can fetch live data and run prompts, which makes lead "
                        + "lists and reporting tables far less manual.",
                "https://rows.com", PricingModel.FREEMIUM, "Generous free tier",
                "data,spreadsheet,automation,reporting,marketing", 50, false);

        // --- Marketing & automation -------------------------------------------------------
        sink.add("jasper", "Jasper", "marketing",
                "Campaign copy that stays on brand across a team",
                "Brand voice settings keep twenty people writing like one company. Built for ads, "
                        + "landing pages and email sequences at volume.",
                "https://jasper.ai", PricingModel.TRIAL, "From around $39/month",
                "marketing,copywriting,ads,brand,email,campaign", 69, false);
        sink.add("surfer-seo", "Surfer SEO", "marketing",
                "Tells you what the page has to cover to rank",
                "Compares your draft against what is already ranking and points at the gaps. "
                        + "Pairs well with whichever writing assistant you already use.",
                "https://surferseo.com", PricingModel.PAID, "From around $79/month",
                "marketing,seo,content,ranking,keywords", 58, false);
        sink.add("make", "Make", "automation",
                "Draw the workflow, and it runs every day without you",
                "A visual canvas for connecting apps: when a form is filled, add a row, notify the "
                        + "channel, draft the reply. Cheaper than the better-known alternative.",
                "https://make.com", PricingModel.FREEMIUM, "Free tier, paid from around $9/month",
                "automation,workflow,integration,no-code,productivity", 75, true);
        sink.add("zapier-ai", "Zapier AI", "automation",
                "Describe the automation and it wires up the apps",
                "Six thousand integrations, and now you can start by typing what should happen "
                        + "instead of hunting for the right trigger.",
                "https://zapier.com/ai", PricingModel.FREEMIUM, "Free tier, paid from around $20/month",
                "automation,workflow,integration,no-code,business", 80, false);
        sink.add("tidio-lyro", "Tidio Lyro", "chatbots",
                "A support agent trained on your own help pages",
                "Answers the repetitive questions from your existing documentation and hands the "
                        + "rest to a human with the context attached.",
                "https://tidio.com/lyro", PricingModel.FREEMIUM, "Free conversations each month",
                "chatbots,support,customer service,helpdesk,automation", 53, false);

        return inserted[0];
    }

    /**
     * Indicative list prices of each tool's entry paid plan in US dollars: {monthly, yearly}. A
     * yearly figure below twelve months reflects the vendor's annual discount; {0, 0} means there
     * is no paid plan. Backfilled onto existing rows too, so a database seeded before prices
     * existed picks them up on the next start.
     */
    private static final Map<String, double[]> LIST_PRICES_USD = Map.ofEntries(
            Map.entry("claude", new double[] {20, 204}),
            Map.entry("perplexity", new double[] {20, 200}),
            Map.entry("poe", new double[] {19.99, 199.99}),
            Map.entry("github-copilot", new double[] {10, 100}),
            Map.entry("cursor", new double[] {20, 192}),
            Map.entry("codeium", new double[] {15, 180}),
            Map.entry("sourcegraph-cody", new double[] {9, 108}),
            Map.entry("grammarly", new double[] {30, 144}),
            Map.entry("notion-ai", new double[] {10, 96}),
            Map.entry("quillbot", new double[] {19.95, 99.95}),
            Map.entry("sudowrite", new double[] {19, 192}),
            Map.entry("midjourney", new double[] {10, 96}),
            Map.entry("ideogram", new double[] {8, 84}),
            Map.entry("leonardo-ai", new double[] {12, 120}),
            Map.entry("clipdrop", new double[] {9, 84}),
            Map.entry("figma-ai", new double[] {20, 192}),
            Map.entry("gamma", new double[] {10, 96}),
            Map.entry("uizard", new double[] {19, 144}),
            Map.entry("descript", new double[] {24, 192}),
            Map.entry("heygen", new double[] {29, 288}),
            Map.entry("runway", new double[] {15, 144}),
            Map.entry("opus-clip", new double[] {15, 108}),
            Map.entry("elevenlabs", new double[] {5, 50}),
            Map.entry("suno", new double[] {10, 96}),
            Map.entry("adobe-podcast", new double[] {0, 0}),
            Map.entry("fathom", new double[] {19, 180}),
            Map.entry("otter-ai", new double[] {16.99, 99.96}),
            Map.entry("mem", new double[] {12, 120}),
            Map.entry("reclaim-ai", new double[] {10, 96}),
            Map.entry("raycast-ai", new double[] {10, 96}),
            Map.entry("elicit", new double[] {12, 120}),
            Map.entry("napkin-ai", new double[] {12, 108}),
            Map.entry("julius-ai", new double[] {20, 192}),
            Map.entry("rows", new double[] {8, 96}),
            Map.entry("jasper", new double[] {49, 468}),
            Map.entry("surfer-seo", new double[] {99, 948}),
            Map.entry("make", new double[] {10.59, 108}),
            Map.entry("zapier-ai", new double[] {29.99, 239.88}),
            Map.entry("tidio-lyro", new double[] {39, 348}));

    private void seedPrices() {
        for (AiTool tool : toolRepository.findAll()) {
            double[] price = LIST_PRICES_USD.get(tool.getSlug());
            if (price == null || tool.getPriceMonthlyUsd() != null) {
                continue;
            }
            tool.setPriceMonthlyUsd(price[0]);
            tool.setPriceAnnualUsd(price[1]);
            tool.setPriceMonthlyInr(PriceBook.toInr(price[0]));
            tool.setPriceAnnualInr(PriceBook.toInr(price[1]));
        }
    }

    private void seedDemoUser() {
        if (!seedDemoUser || userRepository.existsByEmailIgnoreCase("demo@handyai.app")) {
            return;
        }
        User demo = new User();
        demo.setName("Demo Explorer");
        demo.setEmail("demo@handyai.app");
        demo.setPasswordHash(passwordHasher.hash("demo12345"));
        demo.setProfession("Product designer");
        demo.setRole(Role.USER);
        userRepository.save(demo);
        log.info("Seeded demo account demo@handyai.app / demo12345");
    }

    /** Keeps the long catalogue below readable as a list of facts rather than object plumbing. */
    @FunctionalInterface
    private interface ToolSink {
        void add(String slug, String name, String categorySlug, String tagline, String description,
                 String url, PricingModel pricing, String priceNote, String tags, int popularity,
                 boolean featured);
    }
}
