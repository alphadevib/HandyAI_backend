package com.handyai.build.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Brevo when an API key is configured; otherwise (local development, tests) emails are written to
 * the log so the confirmation link can be copied from there.
 */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    EmailSender emailSender(@Value("${handyai.mail.brevo-api-key:}") String apiKey,
                            @Value("${handyai.mail.from-email:}") String fromEmail,
                            @Value("${handyai.mail.from-name:HandyAI}") String fromName) {
        if (apiKey.isBlank()) {
            return (toEmail, toName, subject, html, text) ->
                    log.info("Email not sent (no Brevo key configured) to {}: {}\n{}", toEmail, subject, text);
        }
        if (fromEmail.isBlank()) {
            throw new IllegalStateException(
                    "MAIL_FROM must be set to a sender address verified in Brevo");
        }
        return new BrevoEmailSender(apiKey.trim(), fromEmail.trim(), fromName);
    }
}
