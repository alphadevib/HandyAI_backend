package com.handyai.build.service;

import com.handyai.build.exception.BadRequestException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The automatic half of organisation verification. It cannot prove an organisation is real, but
 * it rejects the obvious mismatches before a person spends time on the request: the work email has
 * to sit on the organisation's own domain and the registration number has to be well formed.
 * Requests that pass go to an admin for the final decision.
 */
@Component
public class OrganisationVerifier {

    /** GSTIN: 2-digit state code, PAN, entity number, "Z", checksum character. */
    private static final Pattern GSTIN =
            Pattern.compile("^\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    /** CIN: listing status, industry code, state, year, company type, registration number. */
    private static final Pattern CIN =
            Pattern.compile("^[LU]\\d{5}[A-Z]{2}\\d{4}[A-Z]{3}\\d{6}$");
    /** LLPIN, the registration number of a limited liability partnership. */
    private static final Pattern LLPIN = Pattern.compile("^[A-Z]{3}-?\\d{4}$");

    private static final Set<String> PERSONAL_EMAIL_DOMAINS = Set.of(
            "gmail.com", "googlemail.com", "yahoo.com", "yahoo.co.in", "outlook.com",
            "hotmail.com", "live.com", "icloud.com", "me.com", "aol.com", "proton.me",
            "protonmail.com", "rediffmail.com", "zoho.com", "yandex.com", "gmx.com", "mail.com");

    public record Checked(String name, String website, String registrationId) {
    }

    public Checked check(String email, String name, String website, String registrationId) {
        Map<String, String> errors = new LinkedHashMap<>();

        String cleanName = name == null ? "" : name.trim();
        if (cleanName.length() < 2) {
            errors.put("organisationName", "Enter the organisation's registered name");
        }

        String host = hostOf(website);
        if (host == null) {
            errors.put("organisationWebsite", "Enter the organisation's website, e.g. acme.com");
        }

        String emailDomain = domainOf(email);
        if (emailDomain != null && PERSONAL_EMAIL_DOMAINS.contains(emailDomain)) {
            errors.put("email", "Use your work email on the organisation's domain, not a personal "
                    + "address");
        } else if (host != null && emailDomain != null
                && !(emailDomain.equals(host) || emailDomain.endsWith("." + host)
                        || host.endsWith("." + emailDomain))) {
            errors.put("email", "Your email must be on " + host + " to verify this organisation");
        }

        String id = registrationId == null ? ""
                : registrationId.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!(GSTIN.matcher(id).matches() || CIN.matcher(id).matches()
                || LLPIN.matcher(id).matches())) {
            errors.put("organisationRegistrationId",
                    "Enter a valid GSTIN (15 characters), CIN (21 characters) or LLPIN");
        }

        if (!errors.isEmpty()) {
            throw new BadRequestException(errors.values().iterator().next(), errors);
        }
        return new Checked(cleanName, "https://" + host, id);
    }

    /** "https://www.Acme.com/about" and "acme.com" both become "acme.com". */
    static String hostOf(String website) {
        if (website == null || website.isBlank()) {
            return null;
        }
        String value = website.trim();
        if (!value.matches("(?i)^https?://.*")) {
            value = "https://" + value;
        }
        try {
            String host = URI.create(value).getHost();
            if (host == null || !host.contains(".")) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String domainOf(String email) {
        if (email == null) {
            return null;
        }
        int at = email.lastIndexOf('@');
        return at < 0 ? null : email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }
}
