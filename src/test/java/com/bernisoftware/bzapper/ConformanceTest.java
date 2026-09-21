package com.bernisoftware.bzapper;

import com.bernisoftware.bzapper.model.BrandProfile;
import com.bernisoftware.bzapper.model.Button;
import com.bernisoftware.bzapper.model.ConnectCustomer;
import com.bernisoftware.bzapper.model.ListRow;
import com.bernisoftware.bzapper.model.ListSection;
import com.bernisoftware.bzapper.model.MediaInput;
import com.bernisoftware.bzapper.model.ParticipantAction;
import com.bernisoftware.bzapper.model.PresenceState;
import com.bernisoftware.bzapper.model.ProfileUpdate;
import com.bernisoftware.bzapper.model.Role;
import com.bernisoftware.bzapper.model.SendOptions;
import com.bernisoftware.bzapper.webhooks.Webhooks;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Conformance (BRIEF §7): runs EVERY case of {@code cases.json} (local copy in
 * {@code src/test/resources/conformance/}), one dynamic test per case.
 *
 * <p>For each case the {@link FakeServer} records each exchange and this runner checks: method;
 * path (RAW path split on "/", each segment percent-decoded and compared; raw has no space);
 * query (same set of pairs); JSON body (deep equality; {@code null} = no body; multipart = only
 * {@code Content-Type: multipart/form-data} + the filename in the body); {@code Authorization};
 * {@code X-Bzapper-Client}; {@code X-Request-Id}; {@code Idempotency-Key} on writes; same ids on
 * {@code retry: true}, new ids otherwise; and the case's exact request headers. Then it compares
 * the result or the error with {@code expect}.
 *
 * <p>An op of {@code ops} missing from {@link #OPS} FAILS the suite — a new endpoint without a
 * method breaks the build.
 */
class ConformanceTest {

    static final ObjectMapper JSON = new ObjectMapper();
    /**
     * Serializes the SDK's typed models by their FIELDS (the record components, named by their
     * {@code @JsonProperty}), so convenience getters such as {@code isActive()} don't leak in.
     */
    static final ObjectMapper FIELDS = new ObjectMapper()
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    private static final Map<String, Object> CASES = loadCases();
    private static final String API_KEY = (String) CASES.get("api_key");
    private static final int MAX_RETRIES = ((Number) CASES.get("max_retries")).intValue();
    private static final Pattern CLIENT_RE = Pattern.compile("^bzapper-java/" + Pattern.quote(Version.VERSION) + "$");
    private static final Pattern REQUEST_ID_RE = Pattern.compile("^[0-9a-f]{32}$");
    private static final Pattern SECONDS_RE = Pattern.compile("\\d+(\\.\\d+)?");
    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String SENT = "$sent";
    private static final Map<String, Class<? extends RuntimeException>> ERROR_TYPES = errorTypes();

    /** Both clients of a case, built with the case's api_key against the fake server. */
    static final class Ctx {
        final BzapperClient client;
        final BzapperPartner partner;

        Ctx(BzapperClient client, BzapperPartner partner) {
            this.client = client;
            this.partner = partner;
        }
    }

    @FunctionalInterface
    interface Op {
        Object call(Ctx ctx, Args a) throws Exception;
    }

    /** op (neutral, from {@code cases.json}) → idiomatic SDK call. */
    static final Map<String, Op> OPS = ops();

    private static FakeServer server;

    @BeforeAll
    static void startServer() throws IOException {
        server = new FakeServer();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    // ── table op → call ──────────────────────────────────────────────────────────────────────────────────

    private static Map<String, Op> ops() {
        Map<String, Op> m = new LinkedHashMap<>();

        // API keys (existing names: listKeys/createKey/revokeKey)
        m.put("listMyKeys", (c, a) -> c.client.listKeys());
        m.put("createMyKey", (c, a) -> c.client.createKey(a.b("name"), role(a.b("role"))));
        m.put("revokeMyKey", (c, a) -> { c.client.revokeKey(a.p("id")); return null; });

        // Account / profile
        m.put("getMe", (c, a) -> c.client.getMe());
        m.put("updateProfile", (c, a) -> c.client.updateProfile(a.b("name"), a.b("phone"), a.b("job_title"), a.b("locale")));
        m.put("updateAccount", (c, a) -> c.client.updateAccount(a.b("name")));
        m.put("getHealth", (c, a) -> c.client.getHealth());

        // Brand
        m.put("getBrand", (c, a) -> c.client.getBrand());
        m.put("setBrand", (c, a) -> c.client.setBrand(brand(a)));
        m.put("applyBrand", (c, a) -> c.client.applyBrand());
        m.put("uploadBrandLogo", (c, a) -> {
            FileArg f = a.file();
            return c.client.uploadBrandLogo(f.content, f.filename, f.contentType);
        });

        // Contacts (CRM)
        m.put("listContacts", (c, a) -> c.client.listContacts(a.queryAll()));
        m.put("createContact", (c, a) -> c.client.createContact(a.bodyAll()));
        m.put("getContact", (c, a) -> c.client.getContact(a.p("id")));
        m.put("updateContact", (c, a) -> c.client.updateContact(a.p("id"), a.bodyAll()));
        m.put("deleteContact", (c, a) -> { c.client.deleteContact(a.p("id")); return null; });
        m.put("getContactHistory", (c, a) -> c.client.getContactHistory(a.p("id"), a.qInt("limit")));
        m.put("addContactNote", (c, a) -> { c.client.addContactNote(a.p("id"), a.b("body")); return null; });
        m.put("mutateContactTags", (c, a) -> c.client.mutateContactTags(a.p("id"), a.bList("add"), a.bList("remove")));
        m.put("mutateContactGroups", (c, a) -> c.client.mutateContactGroups(a.p("id"), a.bList("add"), a.bList("remove")));
        m.put("optOutContact", (c, a) -> c.client.optOutContact(a.p("id")));
        m.put("suppressContact", (c, a) -> c.client.suppressContact(a.p("id")));
        m.put("optInContact", (c, a) -> c.client.optInContact(a.p("id")));
        m.put("listTags", (c, a) -> c.client.listTags());
        m.put("createTag", (c, a) -> c.client.createTag(a.b("key"), a.b("name"), a.b("color")));
        m.put("deleteTag", (c, a) -> { c.client.deleteTag(a.p("id")); return null; });
        m.put("listContactGroups", (c, a) -> c.client.listContactGroups());
        m.put("createContactGroup", (c, a) -> c.client.createContactGroup(a.b("key"), a.b("name"), a.b("color")));
        m.put("deleteContactGroup", (c, a) -> { c.client.deleteContactGroup(a.p("id")); return null; });
        m.put("listSuppressions", (c, a) -> c.client.listSuppressions(a.qInt("limit")));
        m.put("createSuppression", (c, a) -> { c.client.createSuppression(a.b("phone"), a.b("reason")); return null; });
        m.put("deleteSuppression", (c, a) -> { c.client.deleteSuppression(a.q("phone")); return null; });
        m.put("contactsCheck", (c, a) -> c.client.contactsCheck(a.b("instance_id"), a.bList("phones")));
        m.put("blockContact", (c, a) -> { c.client.blockContact(a.p("jid"), a.b("instance_id")); return null; });
        m.put("unblockContact", (c, a) -> { c.client.unblockContact(a.p("jid"), a.b("instance_id")); return null; });
        m.put("getBlocklist", (c, a) -> c.client.getBlocklist(a.q("instance_id")));

        // Projects, users, usage
        m.put("listProjects", (c, a) -> c.client.listProjects());
        m.put("createProject", (c, a) -> c.client.createProject(a.b("name"), a.b("api_mode")));
        m.put("getProjectsHealth", (c, a) -> c.client.getProjectsHealth());
        m.put("updateProject", (c, a) -> {
            c.client.updateProject(a.p("id"), a.b("name"), a.b("logo_url"), a.b("color"));
            return null;
        });
        m.put("deleteProject", (c, a) -> { c.client.deleteProject(a.p("id")); return null; });
        m.put("getProjectBrand", (c, a) -> c.client.getProjectBrand(a.p("id")));
        m.put("setProjectBrand", (c, a) -> c.client.setProjectBrand(a.p("id"), brand(a)));
        m.put("uploadProjectLogo", (c, a) -> {
            String id = a.p("id");
            FileArg f = a.file();
            return c.client.uploadProjectLogo(id, f.content, f.filename, f.contentType);
        });
        m.put("listUsers", (c, a) -> c.client.listUsers());
        m.put("inviteUser", (c, a) -> c.client.inviteUser(a.b("email"), a.b("name"), a.b("role")));
        m.put("updateUserRole", (c, a) -> { c.client.updateUserRole(a.p("id"), a.b("role")); return null; });
        m.put("removeUser", (c, a) -> { c.client.removeUser(a.p("id")); return null; });
        m.put("getAccountUsage", (c, a) -> c.client.getAccountUsage(a.q("from"), a.q("to")));
        m.put("getUsage", (c, a) -> c.client.getUsage(a.q("from"), a.q("to")));

        // Billing
        m.put("getMyEntitlements", (c, a) -> c.client.getMyEntitlements());
        m.put("upgradePlan", (c, a) -> c.client.upgradePlan());
        m.put("cancelPlan", (c, a) -> c.client.cancelPlan());
        m.put("uncancelPlan", (c, a) -> c.client.uncancelPlan());
        m.put("getMySubscription", (c, a) -> c.client.getMySubscription());
        m.put("changeAddon", (c, a) -> c.client.changeAddon(a.b("kind"), a.bInt("delta")));
        m.put("getAddonCart", (c, a) -> c.client.getAddonCart());
        m.put("clearAddonCart", (c, a) -> c.client.clearAddonCart());
        m.put("checkoutAddonCart", (c, a) -> c.client.checkoutAddonCart(a.bBool("save_card")));
        m.put("listMyInvoices", (c, a) -> c.client.listMyInvoices());
        m.put("payInvoice", (c, a) -> c.client.payInvoice(a.p("id")));
        m.put("getBillingConfig", (c, a) -> c.client.getBillingConfig());
        m.put("getPricing", (c, a) -> c.client.getPricing());

        // Advanced messages, profile, privacy, chats, labels, calls
        m.put("editMessage", (c, a) -> c.client.editMessage(a.p("id"), a.b("text")));
        m.put("revokeMessage", (c, a) -> { c.client.revokeMessage(a.p("id"), a.qBool("for_everyone")); return null; });
        m.put("forwardMessage", (c, a) -> c.client.forwardMessage(a.b("instance_id"), a.b("to"), a.b("from_chat"), a.b("wa_message_id")));
        m.put("markRead", (c, a) -> {
            c.client.markRead(a.p("id"), a.b("instance_id"), a.b("chat"), a.b("sender"), a.bList("wa_message_ids"));
            return null;
        });
        m.put("setProfile", (c, a) -> c.client.setProfile(a.p("id"),
                new ProfileUpdate(a.b("display_name"), a.b("status_message"), a.b("picture"))));
        m.put("setPrivacy", (c, a) -> { c.client.setPrivacy(a.p("id"), a.b("setting"), a.b("value")); return null; });
        m.put("archiveChat", (c, a) -> { c.client.archiveChat(a.p("jid"), a.b("instance_id"), a.bBool("on")); return null; });
        m.put("pinChat", (c, a) -> { c.client.pinChat(a.p("jid"), a.b("instance_id"), a.bBool("on")); return null; });
        m.put("markChat", (c, a) -> { c.client.markChat(a.p("jid"), a.b("instance_id"), a.bBool("on")); return null; });
        m.put("muteChat", (c, a) -> { c.client.muteChat(a.p("jid"), a.b("instance_id"), a.bBool("on")); return null; });
        m.put("applyChatLabel", (c, a) -> {
            c.client.applyChatLabel(a.p("jid"), a.b("instance_id"), a.b("label_id"), a.bBool("apply"));
            return null;
        });
        m.put("listLabels", (c, a) -> c.client.listLabels(a.q("instance_id")));
        m.put("createLabel", (c, a) -> c.client.createLabel(a.b("instance_id"), a.b("name"), a.b("color")));
        m.put("deleteLabel", (c, a) -> { c.client.deleteLabel(a.p("id"), a.q("instance_id")); return null; });
        m.put("rejectCall", (c, a) -> {
            c.client.rejectCall(a.b("instance_id"), a.b("call_id"), a.b("call_from"));
            return null;
        });
        m.put("offerCall", (c, a) -> c.client.offerCall(a.b("instance_id"), a.b("to"), a.bBool("video")));

        // Conversations
        m.put("listConversations", (c, a) -> c.client.listConversations(a.q("instance_id")));
        m.put("conversationHistory", (c, a) -> c.client.conversationHistory(a.p("jid"), a.q("instance_id"),
                a.q("before"), a.qInt("limit")));

        // Instances
        m.put("listInstances", (c, a) -> c.client.listInstances(a.q("project_id"), "1".equals(a.q("archived"))));
        m.put("createInstance", (c, a) -> c.client.createInstance(a.b("phone"), a.b("nickname"), a.b("proxy_url")));
        m.put("getInstance", (c, a) -> c.client.getInstance(a.p("id")));
        m.put("deleteInstance", (c, a) -> { c.client.deleteInstance(a.p("id")); return null; });
        m.put("connectInstance", (c, a) -> c.client.connectInstance(a.p("id"), a.q("method")));
        m.put("disconnectInstance", (c, a) -> { c.client.disconnectInstance(a.p("id")); return null; });
        m.put("logoutInstance", (c, a) -> { c.client.logoutInstance(a.p("id")); return null; });
        m.put("clearInstanceSession", (c, a) -> { c.client.clearInstanceSession(a.p("id")); return null; });
        m.put("archiveInstance", (c, a) -> { c.client.archiveInstance(a.p("id")); return null; });
        m.put("unarchiveInstance", (c, a) -> { c.client.unarchiveInstance(a.p("id")); return null; });
        m.put("setInstanceProxy", (c, a) -> { c.client.setInstanceProxy(a.p("id"), a.b("proxy_url")); return null; });
        m.put("setInboundFilters", (c, a) -> c.client.setInboundFilters(a.p("id"), a.bodyAll()));
        m.put("getOfficialAccount", (c, a) -> c.client.getOfficialAccount());
        m.put("connectOfficialAccount", (c, a) -> c.client.connectOfficialAccount(a.bodyAll()));
        m.put("disconnectOfficialAccount", (c, a) -> { c.client.disconnectOfficialAccount(); return null; });

        // Groups
        m.put("listGroups", (c, a) -> c.client.listGroups(a.q("instance_id")));
        m.put("createGroup", (c, a) -> c.client.createGroup(a.q("instance_id"), a.b("name"), a.bList("participants")));
        m.put("joinGroup", (c, a) -> c.client.joinGroup(a.q("instance_id"), a.b("code")));
        m.put("previewGroupInvite", (c, a) -> c.client.previewGroupInvite(a.q("instance_id"), a.b("code")));
        m.put("getGroup", (c, a) -> c.client.getGroup(a.p("jid"), a.q("instance_id")));
        m.put("updateGroup", (c, a) -> {
            c.client.updateGroup(a.p("jid"), a.q("instance_id"), a.b("name"), a.b("topic"), a.bBool("announce"), a.bBool("locked"));
            return null;
        });
        m.put("updateGroupParticipants", (c, a) -> c.client.updateGroupParticipants(a.p("jid"), a.q("instance_id"),
                participantAction(a.b("action")), a.bList("participants")));
        m.put("groupInviteLink", (c, a) -> c.client.groupInviteLink(a.p("jid"), a.q("instance_id"), a.qBool("reset")));
        m.put("leaveGroup", (c, a) -> { c.client.leaveGroup(a.p("jid"), a.q("instance_id")); return null; });
        m.put("listJoinRequests", (c, a) -> c.client.listJoinRequests(a.p("jid"), a.q("instance_id")));
        m.put("updateJoinRequests", (c, a) -> {
            c.client.updateJoinRequests(a.p("jid"), a.q("instance_id"), a.bList("participants"), a.bBool("approve"));
            return null;
        });

        // Sends
        m.put("sendText", (c, a) -> c.client.sendText(sendOptions(a), a.b("body")));
        m.put("sendImage", (c, a) -> c.client.sendImage(sendOptions(a), media(a)));
        m.put("sendVideo", (c, a) -> c.client.sendVideo(sendOptions(a), media(a)));
        m.put("sendDocument", (c, a) -> c.client.sendDocument(sendOptions(a), media(a)));
        m.put("sendAudio", (c, a) -> c.client.sendAudio(sendOptions(a), media(a)));
        m.put("sendSticker", (c, a) -> c.client.sendSticker(sendOptions(a), media(a)));
        m.put("sendLocation", (c, a) -> c.client.sendLocation(sendOptions(a), a.bDouble("latitude"),
                a.bDouble("longitude"), a.b("name"), a.b("address")));
        m.put("sendContact", (c, a) -> c.client.sendContact(sendOptions(a), a.b("contact_name"), a.b("contact_vcard")));
        m.put("sendPoll", (c, a) -> c.client.sendPoll(sendOptions(a), a.b("name"), a.bList("options"), a.bInt("selectable_count")));
        m.put("sendReaction", (c, a) -> {
            SendOptions o = sendOptions(a);
            return c.client.sendReaction(o, o.quotedMessageId(), a.b("emoji"));
        });
        m.put("sendButtons", (c, a) -> c.client.sendButtons(sendOptions(a), a.b("body"), a.b("footer"), buttons(a)));
        m.put("sendList", (c, a) -> c.client.sendList(sendOptions(a), a.b("body"), a.b("footer"), a.b("button_text"), sections(a)));
        m.put("sendOTP", (c, a) -> c.client.sendOTP(sendOptions(a), a.b("code"), a.b("body"), a.bInt("expiry_minutes")));
        m.put("listScheduled", (c, a) -> c.client.listScheduled(a.qInt("limit")));
        m.put("cancelScheduled", (c, a) -> c.client.cancelScheduled(a.p("id")));
        m.put("presenceChat", (c, a) -> {
            c.client.presenceChat(a.b("instance_id"), a.b("to"), presence(a.b("state")));
            return null;
        });

        // Campaigns
        m.put("listCampaigns", (c, a) -> c.client.listCampaigns(a.qInt("limit")));
        m.put("createCampaign", (c, a) -> c.client.createCampaign(a.bodyAll()));
        m.put("estimateCampaign", (c, a) -> c.client.estimateCampaign(a.qInt("recipients"), a.q("pacing"), a.q("pool_id")));
        m.put("getCampaignEligibility", (c, a) -> c.client.getCampaignEligibility(a.q("pool_id")));
        m.put("uploadCampaignMedia", (c, a) -> {
            FileArg f = a.file();
            return c.client.uploadCampaignMedia(f.content, f.filename, f.contentType);
        });
        m.put("getCampaign", (c, a) -> c.client.getCampaign(a.p("id")));
        m.put("updateCampaign", (c, a) -> c.client.updateCampaign(a.p("id"), a.bodyAll()));
        m.put("listCampaignRecipients", (c, a) -> c.client.listCampaignRecipients(a.p("id"), a.qInt("limit")));
        m.put("addCampaignRecipients", (c, a) -> c.client.addCampaignRecipients(a.p("id"), a.bodyAll()));
        m.put("startCampaign", (c, a) -> c.client.startCampaign(a.p("id")));
        m.put("pauseCampaign", (c, a) -> c.client.pauseCampaign(a.p("id")));
        m.put("resumeCampaign", (c, a) -> c.client.resumeCampaign(a.p("id")));
        m.put("cancelCampaign", (c, a) -> c.client.cancelCampaign(a.p("id")));
        m.put("dryRunCampaign", (c, a) -> c.client.dryRunCampaign(a.p("id")));

        // Pools
        m.put("listPools", (c, a) -> c.client.listPools());
        m.put("createPool", (c, a) -> c.client.createPool(a.b("name"), a.b("strategy"), a.bBool("is_default")));
        m.put("getPool", (c, a) -> c.client.getPool(a.p("id")));
        m.put("addPoolNumber", (c, a) -> { c.client.addPoolNumber(a.p("id"), a.b("instance_id")); return null; });

        // Advisories
        m.put("listAdvisories", (c, a) -> c.client.listAdvisories());
        m.put("markAdvisoryRead", (c, a) -> c.client.markAdvisoryRead(a.p("id")));

        // Webhooks (existing name for listWebhookDeliveries: webhookDeliveries)
        m.put("listWebhooks", (c, a) -> c.client.listWebhooks());
        m.put("createWebhook", (c, a) -> c.client.createWebhook(a.b("url"), a.b("secret"), a.bList("event_types"), a.b("number_filter")));
        m.put("updateWebhook", (c, a) -> c.client.updateWebhook(a.p("id"), a.b("url"), a.b("secret"),
                a.bList("event_types"), a.b("number_filter"), a.bBool("active")));
        m.put("deleteWebhook", (c, a) -> { c.client.deleteWebhook(a.p("id")); return null; });
        m.put("testWebhook", (c, a) -> c.client.testWebhook(a.p("id"), a.b("event_type")));
        m.put("listWebhookDeliveries", (c, a) -> c.client.webhookDeliveries(a.p("id"), a.qInt("limit")));
        m.put("triggerWebhookEvent", (c, a) -> c.client.triggerWebhookEvent(a.b("event_type")));

        // Connected apps (customer side of bZapper Connect)
        m.put("listConnectedApps", (c, a) -> c.client.listConnectedApps());
        m.put("revokeConnectedApp", (c, a) -> { c.client.revokeConnectedApp(a.p("id")); return null; });

        // bZapper Connect — partner client (existing names: me/exchangeCode/listConnections/...)
        m.put("getPartnerMe", (c, a) -> c.partner.me());
        m.put("createConnectSession", (c, a) -> c.partner.createConnectSession(a.b("external_id"), customer(a), a.b("locale")));
        m.put("exchangeConnectCode", (c, a) -> c.partner.exchangeCode(a.b("code")));
        m.put("listPartnerConnections", (c, a) -> c.partner.listConnections(a.q("external_id"), a.q("status")));
        m.put("getPartnerConnection", (c, a) -> c.partner.getConnection(a.p("id")));
        m.put("revokePartnerConnection", (c, a) -> { c.partner.revokeConnection(a.p("id")); return null; });
        m.put("rotatePartnerConnectionKey", (c, a) -> c.partner.rotateConnectionKey(a.p("id")));
        return Collections.unmodifiableMap(m);
    }

    // ── argument converters ──────────────────────────────────────────────────────────────────────────────

    private static SendOptions sendOptions(Args a) {
        SendOptions o = SendOptions.to(a.b("to"));
        if (a.hasB("instance_id")) o = o.withInstanceId(a.b("instance_id"));
        if (a.hasB("pool_id")) o = o.withPoolId(a.b("pool_id"));
        if (a.hasB("quoted_message_id")) o = o.withQuotedMessageId(a.b("quoted_message_id"));
        if (a.hasB("quoted_participant")) o = o.withQuotedParticipant(a.b("quoted_participant"));
        if (a.hasB("client_reference")) o = o.withClientReference(a.b("client_reference"));
        if (a.hasB("mentions")) o = o.withMentions(a.bList("mentions"));
        if (a.hasB("sticky")) o = o.withSticky(a.bBool("sticky"));
        if (a.hasB("scheduled_at")) o = o.withScheduledAt(a.b("scheduled_at"));
        if (a.hasB("groups")) o = o.withGroups(a.bList("groups"));
        if (a.hasB("tags")) o = o.withTags(a.bList("tags"));
        if (a.hasB("force")) o = o.withForce(a.bBool("force"));
        return o;
    }

    private static MediaInput media(Args a) {
        Map<String, Object> m = a.bMap("media");
        return new MediaInput((String) m.get("url"), (String) m.get("base64"), (String) m.get("caption"),
                (String) m.get("filename"), (String) m.get("mimetype"), (Boolean) m.get("ptt"));
    }

    @SuppressWarnings("unchecked")
    private static List<Button> buttons(Args a) {
        List<Button> out = new ArrayList<>();
        for (Object o : (List<Object>) a.bRaw("buttons")) {
            Map<String, Object> b = (Map<String, Object>) o;
            out.add(new Button((String) b.get("id"), (String) b.get("title")));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<ListSection> sections(Args a) {
        List<ListSection> out = new ArrayList<>();
        for (Object o : (List<Object>) a.bRaw("sections")) {
            Map<String, Object> s = (Map<String, Object>) o;
            List<ListRow> rows = new ArrayList<>();
            for (Object r : (List<Object>) s.get("rows")) {
                Map<String, Object> row = (Map<String, Object>) r;
                rows.add(new ListRow((String) row.get("id"), (String) row.get("title"), (String) row.get("description")));
            }
            out.add(new ListSection((String) s.get("title"), rows));
        }
        return out;
    }

    private static BrandProfile brand(Args a) {
        return new BrandProfile(a.b("about"), a.b("display_name"), a.b("logo_url"), a.b("website"),
                a.b("email"), a.b("phone"), a.b("address"), a.b("description"));
    }

    private static ConnectCustomer customer(Args a) {
        Map<String, Object> m = a.bMap("customer");
        return new ConnectCustomer((String) m.get("name"), (String) m.get("email"), (String) m.get("phone"),
                (String) m.get("company"), (String) m.get("country"), (String) m.get("locale"));
    }

    private static Role role(String value) {
        for (Role r : Role.values()) {
            if (r.value().equals(value)) return r;
        }
        throw new AssertionError("unknown role " + value);
    }

    private static ParticipantAction participantAction(String value) {
        for (ParticipantAction p : ParticipantAction.values()) {
            if (p.value().equals(value)) return p;
        }
        throw new AssertionError("unknown action " + value);
    }

    private static PresenceState presence(String value) {
        for (PresenceState p : PresenceState.values()) {
            if (p.value().equals(value)) return p;
        }
        throw new AssertionError("unknown presence " + value);
    }

    private static Map<String, Class<? extends RuntimeException>> errorTypes() {
        Map<String, Class<? extends RuntimeException>> m = new LinkedHashMap<>();
        m.put("authentication", AuthenticationException.class);
        m.put("permission_denied", PermissionDeniedException.class);
        m.put("not_found", NotFoundException.class);
        m.put("conflict", ConflictException.class);
        m.put("validation", ValidationException.class);
        m.put("rate_limit", RateLimitException.class);
        m.put("server", ServerException.class);
        m.put("network", NetworkException.class);
        m.put("api", BzapperException.class);
        m.put("argument", IllegalArgumentException.class);
        return Collections.unmodifiableMap(m);
    }

    // ── cases ────────────────────────────────────────────────────────────────────────────────────────────

    @TestFactory
    Stream<DynamicTest> cases() {
        List<Map<String, Object>> cases = mapList(CASES.get("cases"));
        assertFalse(cases.isEmpty(), "cases.json has no cases");
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> c : cases) {
            assertTrue(ids.add((String) c.get("id")), "duplicate case id: " + c.get("id"));
        }
        return cases.stream().map(c -> DynamicTest.dynamicTest((String) c.get("id"), () -> runCase(c)));
    }

    private static void runCase(Map<String, Object> c) throws Exception {
        String op = (String) c.get("op");
        assertTrue(OPS.containsKey(op), "op '" + op + "' has no SDK method in OPS — implement and map it");
        List<Map<String, Object>> exchanges = mapList(c.get("exchanges"));
        List<FakeServer.Reply> replies = new ArrayList<>();
        for (Map<String, Object> exchange : exchanges) {
            replies.add(FakeServer.Reply.fromCase(map(exchange.get("response"))));
        }
        server.reset(replies);

        List<Duration> sleeps = new ArrayList<>();
        BzapperClient client = BzapperClient.builder(server.baseUrl(), API_KEY).maxRetries(MAX_RETRIES).build();
        client.setSleeper(sleeps::add); // waits captured, never slept
        BzapperPartner partner = BzapperPartner.builder(server.baseUrl(), API_KEY).maxRetries(MAX_RETRIES).build();
        partner.setSleeper(sleeps::add);
        Map<String, Object> options = c.get("options") == null ? Map.of() : map(c.get("options"));
        if (options.get("idempotency_key") != null) {
            RequestOptions ro = RequestOptions.idempotencyKey((String) options.get("idempotency_key"));
            client = client.withOptions(ro);
            partner = partner.withOptions(ro);
        }

        Args args = new Args(map(c.get("args")));
        Object result = null;
        RuntimeException error = null;
        try {
            result = OPS.get(op).call(new Ctx(client, partner), args);
        } catch (BzapperException | IllegalArgumentException e) {
            error = e;
        }
        if (error == null || !(error instanceof IllegalArgumentException)) {
            args.assertAllUsed();
        }

        List<FakeServer.Recorded> requests = server.requests();
        checkExchanges(exchanges, requests, Boolean.TRUE.equals(c.get("multipart")));
        checkSleeps(exchanges, sleeps);

        Map<String, Object> expect = map(c.get("expect"));
        if (expect.containsKey("error")) {
            Map<String, Object> want = map(expect.get("error"));
            String type = (String) want.get("type");
            assertNotNull(error, "expected error " + type + ", got " + result);
            Class<? extends RuntimeException> cls = ERROR_TYPES.get(type);
            assertNotNull(cls, "unknown error type in case: " + type);
            if (cls == IllegalArgumentException.class) {
                assertInstanceOf(IllegalArgumentException.class, error, "argument error");
                assertTrue(requests.isEmpty(), "no request may be made on an argument error");
                return;
            }
            assertSame(cls, error.getClass(), "error class");
            BzapperException e = (BzapperException) error;
            assertEquals(want.get("code"), e.getCode(), "code");
            assertEquals(((Number) want.get("status")).intValue(), e.getStatusCode(), "status");
            assertEquals(e.getStatusCode(), e.getStatus(), "getStatus() == getStatusCode()");
            if (want.containsKey("request_id")) {
                Object expectedId = want.get("request_id");
                if (SENT.equals(expectedId)) {
                    expectedId = requests.get(requests.size() - 1).header("x-request-id");
                    assertNotNull(expectedId);
                }
                assertEquals(expectedId, e.getRequestId(), "request_id");
            }
            if (want.containsKey("retry_after")) {
                assertNotNull(e.getRetryAfter(), "retry_after");
                assertEquals(((Number) want.get("retry_after")).doubleValue(), e.getRetryAfter().toMillis() / 1000.0, 1e-9,
                        "retry_after (seconds)");
            }
            if (want.containsKey("required_scope")) {
                assertEquals(want.get("required_scope"), e.getRequiredScope(), "required_scope");
            }
        } else {
            if (error != null) {
                fail("unexpected error: " + error, error);
            }
            compareResult(expect.get("result"), result);
        }
    }

    private static void checkExchanges(List<Map<String, Object>> exchanges, List<FakeServer.Recorded> requests,
                                       boolean multipart) throws IOException {
        assertEquals(exchanges.size(), requests.size(), () -> "number of requests: " + requests);
        FakeServer.Recorded previous = null;
        for (int i = 0; i < exchanges.size(); i++) {
            Map<String, Object> exchange = exchanges.get(i);
            Map<String, Object> want = map(exchange.get("request"));
            FakeServer.Recorded got = requests.get(i);
            String where = "exchange " + i;

            assertEquals(want.get("method"), got.method, where + ": method");
            assertFalse(got.rawPath.contains(" "), where + ": raw path has a space: " + got.rawPath);
            assertEquals(segments((String) want.get("path")), segments(got.rawPath),
                    where + ": path segments (raw " + got.rawPath + ")");
            assertEquals(expectedQuery(map(want.get("query"))), got.sortedQuery(), where + ": query");

            Object body = want.get("body");
            String contentType = got.header("content-type");
            if (multipart) {
                assertNotNull(contentType, where + ": Content-Type");
                assertTrue(contentType.startsWith("multipart/form-data"), where + ": Content-Type " + contentType);
                assertTrue(got.text().contains("arquivo.png"), where + ": filename in the multipart body");
            } else if (body == null) {
                assertEquals(0, got.body.length, where + ": must not have a body");
                assertNull(contentType, where + ": Content-Type without body");
            } else {
                assertEquals("application/json", contentType, where + ": Content-Type");
                assertTrue(jsonEquals(body, got.json()), where + ": body\n want " + JSON.writeValueAsString(body)
                        + "\n got  " + got.text());
            }

            assertEquals("Bearer " + API_KEY, got.header("authorization"), where + ": Authorization");
            assertEquals("application/json", got.header("accept"), where + ": Accept");
            assertMatches(CLIENT_RE, got.header("x-bzapper-client"), where + ": X-Bzapper-Client");
            assertEquals(got.header("x-bzapper-client"), got.header("user-agent"), where + ": User-Agent");
            assertMatches(REQUEST_ID_RE, got.header("x-request-id"), where + ": X-Request-Id");
            if (WRITES.contains(want.get("method"))) {
                String key = got.header("idempotency-key");
                assertTrue(key != null && !key.isEmpty(), where + ": Idempotency-Key on a write");
            } else {
                assertNull(got.header("idempotency-key"), where + ": Idempotency-Key on a read");
            }
            Object wantHeaders = want.get("headers");
            if (wantHeaders != null) {
                for (Map.Entry<String, Object> h : map(wantHeaders).entrySet()) {
                    assertEquals(String.valueOf(h.getValue()), got.header(h.getKey()), where + ": header " + h.getKey());
                }
            }

            boolean retry = Boolean.TRUE.equals(exchange.get("retry"));
            if (i == 0) {
                assertFalse(retry, "the 1st exchange cannot be a retry");
            } else if (retry) {
                assertEquals(previous.header("x-request-id"), got.header("x-request-id"), where + ": X-Request-Id on retry");
                assertEquals(previous.header("idempotency-key"), got.header("idempotency-key"), where + ": Idempotency-Key on retry");
            } else {
                assertNotEquals(previous.header("x-request-id"), got.header("x-request-id"), where + ": new call needs a new X-Request-Id");
                if (got.header("idempotency-key") != null) {
                    assertNotEquals(previous.header("idempotency-key"), got.header("idempotency-key"),
                            where + ": new call needs a new Idempotency-Key");
                }
            }
            previous = got;
        }
    }

    /** One captured wait per retry: Retry-After (60 s cap) or the BRIEF §5 backoff. */
    private static void checkSleeps(List<Map<String, Object>> exchanges, List<Duration> sleeps) {
        int retries = 0;
        for (Map<String, Object> exchange : exchanges) {
            if (Boolean.TRUE.equals(exchange.get("retry"))) {
                retries++;
            }
        }
        assertEquals(retries, sleeps.size(), "one wait per retry");
        int k = 0;
        int attempt = 0;
        for (int i = 1; i < exchanges.size(); i++) {
            if (!Boolean.TRUE.equals(exchanges.get(i).get("retry"))) {
                attempt = 0;
                continue;
            }
            Duration slept = sleeps.get(k++);
            Object headers = map(exchanges.get(i - 1).get("response")).get("headers");
            String retryAfter = null;
            if (headers != null) {
                for (Map.Entry<String, Object> e : map(headers).entrySet()) {
                    if (e.getKey().equalsIgnoreCase("Retry-After")) retryAfter = String.valueOf(e.getValue());
                }
            }
            if (retryAfter != null && SECONDS_RE.matcher(retryAfter).matches()) {
                long expected = Math.round(Math.min(60.0, Double.parseDouble(retryAfter)) * 1000);
                assertEquals(expected, slept.toMillis(), "wait = Retry-After (60 s cap)");
            } else {
                double base = Math.min(8.0, 0.5 * Math.pow(2, attempt));
                double seconds = slept.toMillis() / 1000.0;
                assertTrue(seconds >= base - 0.001 && seconds <= base * 1.25 + 0.001, "backoff " + attempt + ": " + slept);
            }
            attempt++;
        }
    }

    // ── path / query ─────────────────────────────────────────────────────────────────────────────────────

    /** Splits the RAW path on "/" and percent-decodes each segment ('+' stays '+': not form encoding). */
    static List<String> segments(String rawPath) {
        List<String> out = new ArrayList<>();
        for (String s : rawPath.split("/", -1)) {
            out.add(URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static List<String> expectedQuery(Map<String, Object> query) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : query.entrySet()) {
            out.add(e.getKey() + "=" + e.getValue());
        }
        Collections.sort(out);
        return out;
    }

    // ── result comparison ────────────────────────────────────────────────────────────────────────────────

    private static void compareResult(Object expected, Object result) {
        if (result == null) {
            assertNull(expected, "result: SDK returned null");
            return;
        }
        Object want = expected;
        // BRIEF §4: an SDK that unwraps {"data": [...]} returns the list.
        if (result instanceof List && expected instanceof Map && map(expected).containsKey("data")) {
            want = map(expected).get("data");
        }
        boolean typed = isTyped(result);
        Object got = typed ? FIELDS.convertValue(result, Object.class) : JSON.convertValue(result, Object.class);
        boolean ok = typed ? projectionEquals(want, got) : jsonEquals(want, got);
        try {
            assertTrue(ok, "result (" + (typed ? "typed model, compared on its declared fields" : "JSON") + ")\n want "
                    + JSON.writeValueAsString(want) + "\n got  " + JSON.writeValueAsString(got));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** A typed model (record) of the SDK — or a list of them — as opposed to a raw Map/List. */
    private static boolean isTyped(Object result) {
        if (result instanceof Map) {
            return false;
        }
        if (result instanceof List) {
            for (Object item : (List<?>) result) {
                if (item != null && isTyped(item)) {
                    return true;
                }
            }
            return false;
        }
        return result.getClass().getName().startsWith("com.bernisoftware.bzapper.");
    }

    /** Deep JSON equality; numbers by value (1 == 1L == 1.0). */
    static boolean jsonEquals(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Number && b instanceof Number) {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        }
        if (a instanceof Map && b instanceof Map) {
            Map<?, ?> ma = (Map<?, ?>) a;
            Map<?, ?> mb = (Map<?, ?>) b;
            if (!ma.keySet().equals(mb.keySet())) {
                return false;
            }
            for (Object k : ma.keySet()) {
                if (!jsonEquals(ma.get(k), mb.get(k))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof List && b instanceof List) {
            List<?> la = (List<?>) a;
            List<?> lb = (List<?>) b;
            if (la.size() != lb.size()) {
                return false;
            }
            for (int i = 0; i < la.size(); i++) {
                if (!jsonEquals(la.get(i), lb.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return a.equals(b);
    }

    /**
     * Typed models ignore fields they don't declare (BRIEF §1: unknown fields are ignored, never
     * an error), so a typed result is compared on the fields it DECLARES: every declared field
     * must equal the expected value of the same wire name, and a declared field the response
     * didn't carry must be empty (null/false/0/[]). A field mapped to the wrong wire name fails.
     */
    static boolean projectionEquals(Object expected, Object actual) {
        if (actual instanceof Map) {
            if (!(expected instanceof Map)) {
                return false;
            }
            Map<?, ?> e = (Map<?, ?>) expected;
            for (Map.Entry<?, ?> f : ((Map<?, ?>) actual).entrySet()) {
                if (e.containsKey(f.getKey())) {
                    if (!projectionEquals(e.get(f.getKey()), f.getValue())) {
                        return false;
                    }
                } else if (!isEmptyValue(f.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if (actual instanceof List) {
            if (!(expected instanceof List) || ((List<?>) expected).size() != ((List<?>) actual).size()) {
                return false;
            }
            for (int i = 0; i < ((List<?>) actual).size(); i++) {
                if (!projectionEquals(((List<?>) expected).get(i), ((List<?>) actual).get(i))) {
                    return false;
                }
            }
            return true;
        }
        return jsonEquals(expected, actual);
    }

    private static boolean isEmptyValue(Object v) {
        return v == null || Boolean.FALSE.equals(v) || (v instanceof Number && ((Number) v).doubleValue() == 0)
                || (v instanceof List && ((List<?>) v).isEmpty()) || (v instanceof Map && ((Map<?, ?>) v).isEmpty());
    }

    // ── coverage ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void everyOpHasAMethod() {
        Set<String> missing = new TreeSet<>(stringList(CASES.get("ops")));
        for (Map<String, Object> c : mapList(CASES.get("cases"))) {
            missing.add((String) c.get("op"));
        }
        missing.removeAll(OPS.keySet());
        assertEquals(Set.of(), missing, "ops without an SDK method — implement and map them in OPS");
        assertEquals(159, stringList(CASES.get("ops")).size(), "ops in cases.json");
    }

    @Test
    void excludedOpsAreNotInTheTable() {
        Set<String> both = new TreeSet<>(stringList(CASES.get("sdk_excluded_ops")));
        both.retainAll(OPS.keySet());
        assertEquals(Set.of(), both);
        Set<String> extra = new TreeSet<>(OPS.keySet());
        extra.removeAll(stringList(CASES.get("ops")));
        assertEquals(Set.of(), extra, "table entries for ops that are not in cases.json");
    }

    /** The typed-model projection is only meaningful if models serialize by their wire names. */
    @Test
    void typedModelsSerializeByWireName() {
        Object sent = FIELDS.convertValue(new com.bernisoftware.bzapper.model.SentMessage("m1", "queued", "ref"), Object.class);
        assertEquals(Map.of("message_id", "m1", "status", "queued", "client_reference", "ref"), sent);
        Map<String, Object> conn = map(FIELDS.convertValue(new com.bernisoftware.bzapper.model.PartnerConnection(
                "c1", "x", com.bernisoftware.bzapper.model.ConnectionStatus.ACTIVE, null, null, null, null, null, null,
                null, null, null, null, null), Object.class));
        assertEquals("active", conn.get("status"));
        assertTrue(conn.containsKey("external_id"));
        assertFalse(conn.containsKey("active"), "convenience getters must not leak into the projection");
        // and the projection does catch a wrong value / a wrong wire name
        assertFalse(projectionEquals(Map.of("message_id", "other"), sent));
        assertFalse(projectionEquals(Map.of("messageId", "m1", "status", "queued", "client_reference", "ref"), sent));
    }

    @Test
    void copyOfCasesIsUpToDate() throws IOException {
        Path original = projectDir().resolve("../conformance/cases.json").normalize();
        assumeTrue(Files.isRegularFile(original), "the cases source only exists in the monorepo");
        Path copy = projectDir().resolve("src/test/resources/conformance/cases.json");
        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(copy),
                "src/test/resources/conformance/cases.json is stale — run `python3 clients/conformance/generate.py`");
    }

    @Test
    void signatureVectors() {
        List<Map<String, Object>> vectors = mapList(CASES.get("signatures"));
        assertFalse(vectors.isEmpty());
        for (Map<String, Object> v : vectors) {
            String secret = (String) v.get("secret");
            String body = (String) v.get("body");
            String sig = (String) v.get("signature");
            boolean valid = Boolean.TRUE.equals(v.get("valid"));
            assertEquals(valid, Webhooks.verify(secret, body, sig), "Webhooks.verify " + v.get("id"));
            assertEquals(valid, Webhooks.verify(secret, body.getBytes(StandardCharsets.UTF_8), sig), "bytes " + v.get("id"));
            assertEquals(valid, new Webhooks(secret).verify(body, sig), "instance verify " + v.get("id"));
        }
    }

    // ── support ──────────────────────────────────────────────────────────────────────────────────────────

    static Path projectDir() {
        String dir = System.getProperty("bzapper.projectDir");
        return Path.of(dir != null && !dir.isEmpty() ? dir : System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> loadCases() {
        try (InputStream in = ConformanceTest.class.getResourceAsStream("/conformance/cases.json")) {
            assertNotNull(in, "src/test/resources/conformance/cases.json is not on the test classpath");
            return JSON.readValue(in, Map.class);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    static List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : (List<?>) value) {
            out.add(map(item));
        }
        return out;
    }

    static List<String> stringList(Object value) {
        List<String> out = new ArrayList<>();
        if (value != null) {
            for (Object item : (List<?>) value) {
                out.add((String) item);
            }
        }
        return out;
    }

    private static void assertMatches(Pattern pattern, String value, String what) {
        assertTrue(value != null && pattern.matcher(value).matches(), what + ": '" + value + "' does not match " + pattern);
    }

    /** A file argument of a multipart case. */
    static final class FileArg {
        final byte[] content;
        final String filename;
        final String contentType;

        FileArg(byte[] content, String filename, String contentType) {
            this.content = content;
            this.filename = filename;
            this.contentType = contentType;
        }
    }

    /**
     * The case {@code args} ({@code path}, {@code query}, {@code body}), recording what the table
     * used: an argument the table does not forward to the SDK FAILS the case.
     */
    static final class Args {
        private final Map<String, Object> path;
        private final Map<String, Object> query;
        private final Map<String, Object> body;
        private final Set<String> used = new HashSet<>();

        Args(Map<String, Object> args) {
            this.path = args.get("path") == null ? Map.of() : map(args.get("path"));
            this.query = args.get("query") == null ? Map.of() : map(args.get("query"));
            this.body = args.get("body") instanceof Map ? map(args.get("body")) : Map.of();
        }

        String p(String name) {
            used.add("path." + name);
            return (String) path.get(name);
        }

        String q(String name) {
            used.add("query." + name);
            Object v = query.get(name);
            return v == null ? null : String.valueOf(v);
        }

        Integer qInt(String name) {
            used.add("query." + name);
            Object v = query.get(name);
            return v == null ? null : ((Number) v).intValue();
        }

        Boolean qBool(String name) {
            used.add("query." + name);
            return (Boolean) query.get(name);
        }

        Map<String, Object> queryAll() {
            for (String k : query.keySet()) {
                used.add("query." + k);
            }
            return query;
        }

        boolean hasB(String name) {
            return body.containsKey(name);
        }

        Object bRaw(String name) {
            used.add("body." + name);
            return body.get(name);
        }

        String b(String name) {
            return (String) bRaw(name);
        }

        Integer bInt(String name) {
            Object v = bRaw(name);
            return v == null ? null : ((Number) v).intValue();
        }

        double bDouble(String name) {
            return ((Number) bRaw(name)).doubleValue();
        }

        Boolean bBool(String name) {
            return (Boolean) bRaw(name);
        }

        @SuppressWarnings("unchecked")
        List<String> bList(String name) {
            return (List<String>) bRaw(name);
        }

        Map<String, Object> bMap(String name) {
            return map(bRaw(name));
        }

        Map<String, Object> bodyAll() {
            for (String k : body.keySet()) {
                used.add("body." + k);
            }
            return body;
        }

        FileArg file() {
            Map<String, Object> f = bMap("file");
            Object fields = bRaw("fields");
            assertTrue(fields == null || map(fields).isEmpty(), "multipart extra fields are not supported by the table");
            return new FileArg(Base64.getDecoder().decode((String) f.get("content_base64")),
                    (String) f.get("filename"), (String) f.get("content_type"));
        }

        void assertAllUsed() {
            Set<String> all = new TreeSet<>();
            for (String k : path.keySet()) all.add("path." + k);
            for (String k : query.keySet()) all.add("query." + k);
            for (String k : body.keySet()) all.add("body." + k);
            all.removeAll(used);
            assertEquals(Set.of(), all, "case arguments the OPS table did not forward to the SDK");
        }
    }
}
