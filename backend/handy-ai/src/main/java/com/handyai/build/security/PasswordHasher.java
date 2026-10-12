package com.handyai.build.security;

import com.handyai.build.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Password hashing with the BCrypt {@link PasswordEncoder} from {@code SecurityConfig}.
 *
 * <p>Accounts created before the switch to BCrypt hold PBKDF2-WithHmacSHA256 hashes in the form
 * {@code pbkdf2$<iterations>$<salt-b64>$<hash-b64>}. Those still verify, and {@link #rehash} hands
 * sign-in a BCrypt replacement, so every account moves over the next time its owner signs in.
 */
@Component
public class PasswordHasher {

    /** BCrypt reads only the first 72 bytes; anything longer would be silently truncated. */
    static final int MAX_PASSWORD_BYTES = 72;

    private static final String LEGACY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String LEGACY_PREFIX = "pbkdf2";
    private static final int LEGACY_KEY_LENGTH_BITS = 256;

    private final PasswordEncoder encoder;

    public PasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    public String hash(String rawPassword) {
        if (!fits(rawPassword)) {
            throw new BadRequestException("Password is too long",
                    Map.of("password", "Use at most 72 characters (fewer with emoji or accents)"));
        }
        return encoder.encode(rawPassword);
    }

    /** Returns false instead of throwing on a malformed stored value. */
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        if (isLegacy(storedHash)) {
            return matchesLegacy(rawPassword, storedHash);
        }
        try {
            return encoder.matches(rawPassword, storedHash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * A BCrypt hash to store in place of {@code storedHash}, when that one is PBKDF2 or uses a
     * weaker BCrypt cost. Call only after {@link #matches} succeeded for {@code rawPassword}.
     */
    public Optional<String> rehash(String rawPassword, String storedHash) {
        boolean outdated = isLegacy(storedHash) || encoder.upgradeEncoding(storedHash);
        return outdated && fits(rawPassword) ? Optional.of(encoder.encode(rawPassword)) : Optional.empty();
    }

    private static boolean fits(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }

    private static boolean isLegacy(String storedHash) {
        return storedHash.startsWith(LEGACY_PREFIX + "$");
    }

    /** Constant-time comparison against a PBKDF2 hash from before the BCrypt switch. */
    private static boolean matchesLegacy(String rawPassword, String storedHash) {
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = deriveLegacy(rawPassword, salt, iterations);
            return java.security.MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static byte[] deriveLegacy(String rawPassword, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations,
                LEGACY_KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(LEGACY_ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Unable to hash password", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
