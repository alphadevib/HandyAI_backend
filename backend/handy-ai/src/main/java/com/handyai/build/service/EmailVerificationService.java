package com.handyai.build.service;

import com.handyai.build.domain.User;
import com.handyai.build.mail.EmailSender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Confirmation emails for new sign-ups. Switched on by configuring a Brevo API key; with none (local
 * development, tests) new accounts are confirmed straight away, as before.
 *
 * <p>The link carries a random one-time token; only its SHA-256 is stored, so a database leak does
 * not hand out working links. A link lasts {@link #LINK_VALID_FOR}; a new one may be requested
 * every {@link #RESEND_AFTER}.
 */
@Service
public class EmailVerificationService {

    static final Duration LINK_VALID_FOR = Duration.ofHours(24);
    static final Duration RESEND_AFTER = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final EmailSender sender;
    private final boolean required;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(EmailSender sender,
                                    @Value("${handyai.mail.brevo-api-key:}") String apiKey,
                                    @Value("${handyai.frontend-url}") String frontendUrl) {
        this.sender = sender;
        this.required = !apiKey.isBlank();
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public boolean required() {
        return required;
    }

    /**
     * Gives the user a fresh token and emails the link. Returns false if the email could not be
     * sent; the account is kept, and the user can ask for another link.
     */
    public boolean issue(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.setEmailTokenHash(hash(token));
        user.setEmailTokenSentAt(Instant.now());

        String link = frontendUrl + "/verify-email?token=" + token;
        try {
            sender.send(user.getEmail(), user.getName(), "Confirm your HandyAI email",
                    html(user.getName(), link), text(user.getName(), link));
            return true;
        } catch (EmailSender.EmailException ex) {
            log.warn("Could not send the confirmation email to {}: {}", user.getEmail(), ex.getMessage());
            return false;
        }
    }

    public boolean linkExpired(User user) {
        return user.getEmailTokenSentAt() == null
                || user.getEmailTokenSentAt().plus(LINK_VALID_FOR).isBefore(Instant.now());
    }

    public boolean mayResend(User user) {
        return user.getEmailTokenSentAt() == null
                || user.getEmailTokenSentAt().plus(RESEND_AFTER).isBefore(Instant.now());
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String text(String name, String link) {
        return "Hi " + name + ",\n\n"
                + "Confirm your email to finish setting up your HandyAI profile:\n" + link + "\n\n"
                + "The link works for 24 hours. If you did not sign up, ignore this email and the "
                + "account will not be activated.\n\n- HandyAI";
    }

    private static String html(String name, String link) {
        String safeName = HtmlUtils.htmlEscape(name);
        String safeLink = HtmlUtils.htmlEscape(link);
        return """
                <div style="background:#0c0c0d;padding:32px 16px;font-family:Arial,Helvetica,sans-serif">
                  <div style="max-width:480px;margin:0 auto;background:#141416;border-radius:20px;padding:32px;color:#a9a9b1">
                    <p style="margin:0 0 24px;font-size:20px;font-weight:900;letter-spacing:.02em;color:#f4f4f1">
                      HANDY<span style="color:#d4f25a">.</span>AI</p>
                    <h1 style="margin:0 0 12px;font-size:22px;color:#f4f4f1">Confirm your email</h1>
                    <p style="margin:0 0 24px;line-height:1.6">Hi %s, one click and your HandyAI
                      profile is ready: picks for your work, saved tools and every renewal in one place.</p>
                    <a href="%s" style="display:inline-block;background:#f4f4f1;color:#0c0c0d;
                      text-decoration:none;font-weight:700;padding:14px 28px;border-radius:999px">Confirm my email</a>
                    <p style="margin:24px 0 0;font-size:13px;line-height:1.6">The link works for 24 hours.
                      If you did not sign up, ignore this email and nothing happens.</p>
                  </div>
                </div>
                """.formatted(safeName, safeLink);
    }
}
