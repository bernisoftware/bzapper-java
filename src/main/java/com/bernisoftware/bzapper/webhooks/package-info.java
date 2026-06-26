/**
 * Webhook receiver for the bZapper API.
 *
 * <p>{@link com.bernisoftware.bzapper.webhooks.Webhooks} verifies the HMAC-SHA256
 * signature over the raw request body, parses the envelope into a typed
 * {@link com.bernisoftware.bzapper.webhooks.WebhookEvent}, and routes it to
 * handlers registered per event type. To <em>manage</em> webhook subscriptions
 * (create/list/update/delete), use {@link com.bernisoftware.bzapper.BzapperClient}.
 */
package com.bernisoftware.bzapper.webhooks;
