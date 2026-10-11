package com.handyai.build.mail;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Sends through Brevo's HTTPS API rather than SMTP: hosts such as Render's free plan block the
 * SMTP ports, but ordinary HTTPS always gets out.
 */
public class BrevoEmailSender implements EmailSender {

    private static final URI ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");

    private final String apiKey;
    private final String fromEmail;
    private final String fromName;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final JsonMapper json = JsonMapper.builder().build();

    public BrevoEmailSender(String apiKey, String fromEmail, String fromName) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    @Override
    public void send(String toEmail, String toName, String subject, String html, String text) {
        ObjectNode body = json.createObjectNode();
        body.putObject("sender").put("email", fromEmail).put("name", fromName);
        ObjectNode to = body.putArray("to").addObject().put("email", toEmail);
        if (toName != null && !toName.isBlank()) {
            to.put("name", toName);
        }
        body.put("subject", subject);
        body.put("htmlContent", html);
        body.put("textContent", text);

        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(15))
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new EmailException("Brevo refused the email (" + response.statusCode() + "): "
                        + response.body());
            }
        } catch (IOException ex) {
            throw new EmailException("Could not reach Brevo", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EmailException("Interrupted while sending email", ex);
        }
    }
}
