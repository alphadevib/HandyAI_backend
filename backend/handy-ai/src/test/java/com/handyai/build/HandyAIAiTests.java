package com.handyai.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.handyai.build.ai.AiProperties;
import com.handyai.build.ai.AiUsageMeter;
import com.handyai.build.ai.ClaudeClient;
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
 * Embeddings and vector search over the catalogue, and the Claude-written chat reply (RAG). The
 * Anthropic API is mocked, so these tests never spend tokens.
 */
@SpringBootTest(properties = "handyai.ai.api-key=test-key")
class HandyAIAiTests {

    private static final String CLAUDE_REPLY = """
            {"id":"msg_test","type":"message","role":"assistant","model":"claude-opus-5-5",
             "content":[{"type":"text","text":"Try **Descript**: you edit the video by editing its transcript."}],
             "stop_reason":"end_turn",
             "usage":{"input_tokens":900,"output_tokens":150}}
            """;

    @Autowired
    private ToolService toolService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private AiUsageMeter usageMeter;

    @Autowired
    private RestTemplate anthropicRestTemplate;

    private MockRestServiceServer anthropic;

    @BeforeEach
    void mockAnthropic() {
        anthropic = MockRestServiceServer.bindTo(anthropicRestTemplate).build();
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
    void claudeWritesTheReplyFromTheRetrievedTools() {
        long callsBefore = usageMeter.snapshot().today().calls();
        anthropic.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.model").value("claude-opus-5-5"))
                .andExpect(jsonPath("$.max_tokens").value(4096))
                .andExpect(jsonPath("$.output_config.effort").value("low"))
                .andExpect(jsonPath("$.system", containsString("Recommend only tools from <tools>")))
                // Context injection: the retrieved tools and the request travel in the prompt.
                .andExpect(jsonPath("$.messages[0].content", containsString("<tools>")))
                .andExpect(jsonPath("$.messages[0].content", containsString("shown as a card")))
                .andExpect(jsonPath("$.messages[0].content",
                        containsString("Latest message: Edit videos for YouTube")))
                .andRespond(withSuccess(CLAUDE_REPLY, MediaType.APPLICATION_JSON));

        ChatResponse response = chatService.reply(new ChatRequest("Edit videos for YouTube", null), null);

        anthropic.verify();
        assertThat(response.reply()).startsWith("Try **Descript**");
        assertThat(response.recommendations()).isNotEmpty();
        AiUsageMeter.Usage today = usageMeter.snapshot().today();
        assertThat(today.calls()).isEqualTo(callsBefore + 1);
        assertThat(today.costUsd()).isGreaterThanOrEqualTo(usageMeter.costUsd(900, 150));
    }

    @Test
    void aRefusalOrAnOutageFallsBackToTheRuleBasedReply() {
        anthropic.expect(ExpectedCount.once(), requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withSuccess("""
                        {"content":[],"stop_reason":"refusal","usage":{"input_tokens":900,"output_tokens":0}}
                        """, MediaType.APPLICATION_JSON));
        anthropic.expect(ExpectedCount.once(), requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withServerError());

        String refused = chatService.reply(new ChatRequest("Write blog posts faster", null), null).reply();
        String failed = chatService.reply(new ChatRequest("Write blog posts faster", null), null).reply();

        anthropic.verify();
        assertThat(refused).startsWith("Here are");
        assertThat(failed).isEqualTo(refused);
    }

    @Test
    void stopsCallingClaudeOnceTheDailyBudgetIsSpent() {
        AiProperties tightBudget = new AiProperties("test-key", "https://api.anthropic.com",
                "claude-opus-5-5", 4096, "low", 5, 1500, 0.01, 4.00, 20.00);
        AiUsageMeter meter = new AiUsageMeter(tightBudget);
        meter.record(1_000, 1_000); // ~$0.024, past the $0.01 budget

        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        ClaudeClient client = new ClaudeClient(http, tightBudget, meter);

        assertThat(client.available()).isFalse();
        assertThat(client.complete("system", "hello")).isEmpty();
        server.verify(); // no request was sent
    }
}
