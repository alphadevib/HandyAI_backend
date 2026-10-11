package com.handyai.build.security;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Minimal stateless HS256 JWT issuer/verifier. Stateless on purpose: there is no server-side
 * session map to lock, so concurrent requests from the same user never contend on shared state.
 */
@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    private final byte[] secret;
    private final Duration ttl;

    public JwtService(@Value("${handyai.jwt.secret}") String secret,
                      @Value("${handyai.jwt.ttl-minutes:720}") long ttlMinutes) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new IllegalStateException(
                    "handyai.jwt.secret must be at least 32 characters long");
        }
        this.secret = key;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public Duration getTtl() {
        return ttl;
    }

    public String issue(Long userId, String email, String role) {
        Instant now = Instant.now();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("sub", String.valueOf(userId));
        payload.put("email", email);
        payload.put("role", role);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plus(ttl).getEpochSecond());

        String header = encoder.encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String body = encoder.encodeToString(objectMapper.writeValueAsBytes(payload));
        String signingInput = header + "." + body;
        return signingInput + "." + encoder.encodeToString(sign(signingInput));
    }

    /** Returns empty for anything not provably valid: bad shape, bad signature or expired. */
    public Optional<AuthenticatedUser> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            byte[] expected = sign(parts[0] + "." + parts[1]);
            byte[] actual = decoder.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                return Optional.empty();
            }
            JsonNode payload = objectMapper.readTree(decoder.decode(parts[1]));
            long exp = payload.path("exp").asLong();
            if (exp <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            long userId = Long.parseLong(payload.path("sub").asString());
            return Optional.of(new AuthenticatedUser(userId, payload.path("email").asString(),
                    payload.path("role").asString("USER")));
        } catch (IllegalArgumentException | tools.jackson.core.JacksonException ex) {
            return Optional.empty();
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign token", ex);
        }
    }
}
