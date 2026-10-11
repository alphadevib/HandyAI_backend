package com.handyai.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.handyai.build.domain.User;
import com.handyai.build.dto.AuthResponse;
import com.handyai.build.dto.LoginRequest;
import com.handyai.build.dto.RegisterRequest;
import com.handyai.build.exception.BadRequestException;
import com.handyai.build.exception.ConflictException;
import com.handyai.build.exception.EmailNotVerifiedException;
import com.handyai.build.mail.EmailSender;
import com.handyai.build.repository.UserRepository;
import com.handyai.build.service.AuthService;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** With a Brevo key configured, new accounts confirm their email before they can sign in. */
@SpringBootTest(properties = {
        "handyai.mail.brevo-api-key=test-key",
        "handyai.mail.from-email=hello@handyai.test",
        "handyai.frontend-url=https://shop.handyai.test/"
})
class HandyAIEmailVerificationTests {

    private static final Pattern LINK = Pattern.compile(
            "https://shop\\.handyai\\.test/verify-email\\?token=([A-Za-z0-9_-]+)");

    @MockitoBean
    private EmailSender emailSender;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void reset() {
        clearInvocations(emailSender);
    }

    private String lastToken() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(emailSender, org.mockito.Mockito.atLeastOnce())
                .send(anyString(), any(), anyString(), anyString(), text.capture());
        Matcher matcher = LINK.matcher(text.getValue());
        assertThat(matcher.find()).as("the email carries a confirmation link").isTrue();
        return matcher.group(1);
    }

    private void ageLink(String email, Duration by) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailTokenSentAt(Instant.now().minus(by));
        userRepository.save(user);
    }

    @Test
    void aNewAccountSignsInOnlyAfterConfirmingItsEmail() {
        AuthResponse pending = authService.register(new RegisterRequest(
                "Nina", "nina@handyai.test", "supersecret", "Writer"));
        assertThat(pending.verificationRequired()).isTrue();
        assertThat(pending.token()).isNull();
        assertThat(pending.emailSent()).isTrue();
        verify(emailSender).send(eq("nina@handyai.test"), eq("Nina"), eq("Confirm your HandyAI email"),
                anyString(), anyString());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("nina@handyai.test", "supersecret"), "192.0.2.60"))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasMessageContaining("nina@handyai.test");

        String token = lastToken();
        AuthResponse confirmed = authService.verifyEmail(token);
        assertThat(confirmed.token()).isNotBlank();
        assertThat(confirmed.user().email()).isEqualTo("nina@handyai.test");

        assertThat(authService.login(new LoginRequest("nina@handyai.test", "supersecret"),
                "192.0.2.60").token()).isNotBlank();
        // A link works once.
        assertThatThrownBy(() -> authService.verifyEmail(token))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> authService.verifyEmail("not-a-real-token"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void aNewLinkReplacesTheOldOneAtMostOnceAMinute() {
        authService.register(new RegisterRequest("Omar", "omar@handyai.test", "supersecret", "Teacher"));
        String first = lastToken();
        clearInvocations(emailSender);

        authService.resendVerification("omar@handyai.test");
        verify(emailSender, never()).send(anyString(), any(), anyString(), anyString(), anyString());

        ageLink("omar@handyai.test", Duration.ofMinutes(2));
        authService.resendVerification("OMAR@handyai.test");
        String second = lastToken();
        assertThat(second).isNotEqualTo(first);
        assertThatThrownBy(() -> authService.verifyEmail(first)).isInstanceOf(BadRequestException.class);
        assertThat(authService.verifyEmail(second).token()).isNotBlank();

        // Nothing is sent for confirmed or unknown addresses, and nothing says which is which.
        clearInvocations(emailSender);
        authService.resendVerification("omar@handyai.test");
        authService.resendVerification("nobody@handyai.test");
        verify(emailSender, never()).send(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void anUnconfirmedSignUpStopsHoldingTheAddressOnceItsLinkExpires() {
        authService.register(new RegisterRequest("Squatter", "taken@handyai.test", "supersecret", null));
        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "Owner", "taken@handyai.test", "anothersecret", "Lawyer")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("waiting to be confirmed");

        ageLink("taken@handyai.test", Duration.ofHours(25));
        AuthResponse owner = authService.register(new RegisterRequest(
                "Owner", "taken@handyai.test", "anothersecret", "Lawyer"));
        assertThat(owner.verificationRequired()).isTrue();
        assertThat(userRepository.findByEmailIgnoreCase("taken@handyai.test").orElseThrow().getName())
                .isEqualTo("Owner");
    }

    @Test
    void aFailedEmailKeepsTheAccountSoTheLinkCanBeResent() {
        doThrow(new EmailSender.EmailException("Brevo is down")).when(emailSender)
                .send(anyString(), any(), anyString(), anyString(), anyString());

        AuthResponse pending = authService.register(new RegisterRequest(
                "Pat", "pat@handyai.test", "supersecret", "Nurse"));
        assertThat(pending.verificationRequired()).isTrue();
        assertThat(pending.emailSent()).isFalse();
        assertThat(userRepository.findByEmailIgnoreCase("pat@handyai.test")).isPresent();
        verify(emailSender, times(1)).send(anyString(), any(), anyString(), anyString(), anyString());
    }
}
