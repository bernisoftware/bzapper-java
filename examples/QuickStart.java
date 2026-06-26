import com.bernisoftware.bzapper.BzapperClient;
import com.bernisoftware.bzapper.BzapperException;
import com.bernisoftware.bzapper.model.Button;
import com.bernisoftware.bzapper.model.Group;
import com.bernisoftware.bzapper.model.ListRow;
import com.bernisoftware.bzapper.model.ListSection;
import com.bernisoftware.bzapper.model.MediaInput;
import com.bernisoftware.bzapper.model.ParticipantAction;
import com.bernisoftware.bzapper.model.PresenceState;
import com.bernisoftware.bzapper.model.ProfileUpdate;
import com.bernisoftware.bzapper.model.SendOptions;
import com.bernisoftware.bzapper.model.SentMessage;

import java.time.Duration;
import java.util.List;

/**
 * Runnable example for the bZapper Java SDK.
 *
 * <p>Build the SDK, then run with the dependencies on the classpath, e.g.:
 *
 * <pre>{@code
 * mvn -q -DskipTests package
 * CP="target/bzapper-0.1.0.jar:$(find ~/.m2 -name 'jackson-*-2.17.2.jar' | tr '\n' ':')"
 * java -cp "$CP:examples" QuickStart
 * }</pre>
 *
 * <p>Configure via env vars {@code BZAPPER_BASE_URL} and {@code BZAPPER_API_KEY}.
 */
public class QuickStart {

    public static void main(String[] args) {
        String baseUrl = envOr("BZAPPER_BASE_URL", "http://localhost:8080");
        String apiKey = envOr("BZAPPER_API_KEY", "bz_live_xxx");
        String to = envOr("BZAPPER_TO", "+5511999999999");
        String instanceId = envOr("BZAPPER_INSTANCE_ID", "inst-uuid");
        String groupJid = envOr("BZAPPER_GROUP_JID", "123456789-987654@g.us");

        BzapperClient client = BzapperClient.builder(baseUrl, apiKey)
                .locale("pt-BR")
                .timeout(Duration.ofSeconds(30))
                .build();

        try {
            // Text
            SentMessage text = client.sendText(SendOptions.to(to), "Olá do bZapper Java SDK!");
            System.out.println("text -> " + text.messageId());

            // Image (by URL)
            client.sendImage(SendOptions.to(to),
                    MediaInput.url("https://example.com/cat.png", "A cat"));

            // Video
            client.sendVideo(SendOptions.to(to), MediaInput.url("https://example.com/clip.mp4"));

            // Document
            client.sendDocument(SendOptions.to(to),
                    MediaInput.url("https://example.com/invoice.pdf").withFilename("invoice.pdf"));

            // Audio as a voice note (ptt)
            client.sendAudio(SendOptions.to(to),
                    MediaInput.url("https://example.com/voice.ogg").asVoiceNote());

            // Sticker
            client.sendSticker(SendOptions.to(to), MediaInput.url("https://example.com/sticker.webp"));

            // Location
            client.sendLocation(SendOptions.to(to), -23.5505, -46.6333, "São Paulo", "Praça da Sé");

            // Contact
            client.sendContact(SendOptions.to(to), "Ada Lovelace",
                    "BEGIN:VCARD\nVERSION:3.0\nFN:Ada Lovelace\nTEL:+5511999999999\nEND:VCARD");

            // Poll
            client.sendPoll(SendOptions.to(to), "Lunch?", List.of("Pizza", "Sushi", "Salad"), 1);

            // Reaction (reply to a previous message)
            client.sendReaction(SendOptions.to(to), text.messageId(), "👍");

            // Buttons (falls back to a numbered text menu on WhatsApp)
            client.sendButtons(SendOptions.to(to), "Pick one", "Footer text",
                    List.of(Button.of("yes", "Yes"), Button.of("no", "No")));

            // List (also may fall back to a numbered text menu)
            client.sendList(SendOptions.to(to), "Our menu", "Tap to choose", "Open menu",
                    List.of(ListSection.of("Drinks", List.of(
                            ListRow.of("c", "Coffee", "Fresh"),
                            ListRow.of("t", "Tea", "Green")))));

            // Instances / keys / usage
            System.out.println("instances -> " + client.listInstances());
            System.out.println("usage -> " + client.getUsage(null, null));

            // --- Advanced: presence works in groups, too! ---
            client.presenceChat(instanceId, groupJid, PresenceState.TYPING);
            client.presenceChat(instanceId, groupJid, PresenceState.PAUSED);

            // Conversations & chat flags
            System.out.println("conversations -> " + client.listConversations(instanceId));
            client.archiveChat(groupJid, instanceId, false);

            // Groups
            System.out.println("groups -> " + client.listGroups(instanceId));
            Group group = client.createGroup(instanceId, "Project X",
                    List.of("5511999999999@s.whatsapp.net"));
            client.updateGroupParticipants(group.jid(), instanceId, ParticipantAction.ADD,
                    List.of("5511888888888@s.whatsapp.net"));
            System.out.println("invite -> " + client.groupInvite(group.jid(), instanceId).url());

            // Contacts on WhatsApp?
            System.out.println("check -> " + client.contactsCheck(instanceId, List.of(to)));

            // Profile
            client.setProfile(instanceId, ProfileUpdate.empty().withDisplayName("Support"));

        } catch (BzapperException e) {
            // Always branch on the stable code, never on the message text.
            System.err.println("API error code=" + e.getCode()
                    + " status=" + e.getStatusCode()
                    + " message=" + e.getMessage());
        }
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return v != null && !v.isEmpty() ? v : fallback;
    }
}
