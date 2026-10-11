package com.handyai.build.mail;

/** Sends one transactional email. Implementations throw {@link EmailException} on failure. */
public interface EmailSender {

    void send(String toEmail, String toName, String subject, String html, String text);

    class EmailException extends RuntimeException {
        public EmailException(String message) {
            super(message);
        }

        public EmailException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
