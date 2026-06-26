package com.bernisoftware.bzapper.webhooks;

/**
 * Raised when a webhook signature is missing or does not match the HMAC of the
 * raw request body. Never process a delivery that fails verification.
 */
public class WebhookSignatureException extends RuntimeException {

    public WebhookSignatureException(String message) {
        super(message);
    }

    public WebhookSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
