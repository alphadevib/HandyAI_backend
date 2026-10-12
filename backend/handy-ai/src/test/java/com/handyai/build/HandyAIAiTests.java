package com.handyai.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.handyai.build.ai.AiProperties;
import com.handyai.build.ai.AiUsageMeter;
import com.handyai.build.ai.OllamaClient;
import com.handyai.build.dto.ChatRequest;
import com.handyai.build.dto.ChatResponse;
import com.handyai.build.dto.ToolResponse;
import com.handyai.build.service.ChatService;
import com.handyai.build.service.ToolService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Embeddings and vector search over the catalogue, and the LLM-written chat reply (RAG). Ollama is
 * mocked, so these tests neither need a model nor change with one.
 */
@SpringBootTest(properties = {"handyai.ai.enabled=true", "handyai.ai.base-url=http://ollama.test"})
class HandyAIAiTests {

    private static final String OLLAMA_URL = "http://ollama.test/api/chat";

    private static final String OLLAMA_REPLY = """
            {"model":"qwen3:8b","created_at":"2026-10-12T18:00:00Z",
             "message":{"role":"assistant","content":"Try **Descript**: you edit the video by editing its transcript."},
             "done":true,"done_reason":"stop","prompt_eval_count":900,"eval_count":150}
            """;

    @Autowired
    private ToolService toolService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private AiUsageMeter usageMeter;

    @Autowired
    private RestTemplate ollamaRestTemplate;

    private MockRestServiceServer ollama;

    @BeforeEach
    void mockOllama() {
        ollama = MockRestServiceServer.bindTo(ollamaRestTemplate).build();
    }

    @Test
    void semanticSearchMatchesMeaningNotJustTitles() {
        List<ToolService.SemanticMatch> matches = toolService.semanticSearch(
                "clean up a podcast recording and remove background noise", 3, null);
        assertThat(matches).isNotEmpty();
        assertThat(matches.get(0).tool().slug()).isEqualTo("adobe-podcast");
        assertThat(matches).isSortedAccordingTo((a, b) -> Double.compare(b.score(), a.score()));
    }

    @Test
    void similarToolsComeFromTheSameNeighbourhood() {
        List<ToolResponse> similar = toolService.similar("midjourney", 3, null);
        assertThat(similar).hasSize(3)
                .noneMatch(tool -> tool.slug().equals("midjourney"))
                .anyMatch(tool -> tool.categorySlug().equals("image"));
    }

    @Test
    void ollamaWritesTheReplyFromTheRetrievedTools() {
        long callsBefore = usageMeter.snapshot().today().calls();
        ollama.expect(requestTo(OLLAMA_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("qwen3:8b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.think").value(false))
                .andExpect(jsonPath("$.options.num_predict").value(400))
                .andExpect(jsonPath("$.options.num_ctx").value(4096))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content", containsString("Recommend only tools from <tools>")))
                // Context injection: the retrieved tools and the request travel in the prompt.
                .andExpect(jsonPath("$.messages[1].content", containsString("<tools>")))
                .andExpect(jsonPath("$.messages[1].content", containsString("shown as a card")))
                .andExpect(jsonPath("$.messages[1].content",
                        containsString("Latest message: Edit videos for YouTube")))
                .andRespond(withSuccess(OLLAMA_REPLY, MediaType.APPLICATION_JSON));

        ChatResponse response = chatService.reply(new ChatRequest("Edit videos for YouTube", null), null);

        ollama.verify();
        assertThat(response.reply()).startsWith("Try **Descript**");
        assertThat(response.recommendations()).isNotEmpty();
        AiUsageMeter.Usage today = usageMeter.snapshot().today();
        assertThat(today.calls()).isEqualTo(callsBefore + 1);
        assertThat(today.totalTokens()).isGreaterThanOrEqualTo(1050);
    }

    @Test
    void aCutOffReplyOrAnOutageFallsBackToTheRuleBasedReply() {
        ollama.expect(ExpectedCount.once(), requestTo(OLLAMA_URL))
                .andRespond(withSuccess("""
                        {"message":{"role":"assistant","content":"Here is a reply that got cut"},
                         "done":true,"done_reason":"length","prompt_eval_count":900,"eval_count":400}
                        """, MediaType.APPLICATION_JSON));
        ollama.expect(ExpectedCount.once(), requestTo(OLLAMA_URL))
                .andRespond(withServerError());

        String cutOff = chatService.reply(new ChatRequest("Write blog posts faster", null), null).reply();
        String failed = chatService.reply(new ChatRequest("Write blog posts faster", null), null).reply();

        ollama.verify();
        assertThat(cutOff).startsWith("Here are");
        assertThat(failed).isEqualTo(cutOff);
    }

    @Test
    void dropsInlineReasoningFromThinkingModels() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        AiProperties props = properties(1_000_000);
        server.expect(requestTo("http://ollama.test/api/chat")).andRespond(withSuccess("""
                {"message":{"role":"assistant","content":"<think>The user wants...</think>\\n\\nUse **Gamma**."},
                 "done":true,"done_reason":"stop","prompt_eval_count":10,"eval_count":5}
                """, MediaType.APPLICATION_JSON));

        OllamaClient client = new OllamaClient(http, props, new AiUsageMeter(props));

        assertThat(client.complete("system", "hello")).get()
                .extracting(OllamaClient.Reply::text).isEqualTo("Use **Gamma**.");
    }

    @Test
    void stopsCallingTheModelOnceTheDailyTokenBudgetIsSpent() {
        AiProperties tightBudget = properties(1_000);
        AiUsageMeter meter = new AiUsageMeter(tightBudget);
        meter.record(900, 200); // 1,100 tokens, past the 1,000 budget

        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        OllamaClient client = new OllamaClient(http, tightBudget, meter);

        assertThat(client.available()).isFalse();
        assertThat(client.complete("system", "hello")).isEmpty();
        server.verify(); // no request was sent
    }

    private static AiProperties properties(long dailyTokenBudget) {
        return new AiProperties(true, "http://ollama.test", "qwen3:8b", false, 400, 4096, 5, 1500,
                dailyTokenBudget, 0, 0);
    }
}
