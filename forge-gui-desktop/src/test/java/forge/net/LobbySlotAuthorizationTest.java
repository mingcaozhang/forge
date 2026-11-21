package forge.net;

import forge.gamemodes.match.LobbySlotType;
import forge.gamemodes.net.event.UpdateLobbyPlayerEvent;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collections;

/**
 * A client may configure its own seat, but not the state that decides who
 * occupies it. Pure logic, no server, no socket — the regression this guards
 * against is a field being added to {@link UpdateLobbyPlayerEvent} later and
 * silently escaping the clearing.
 */
public class LobbySlotAuthorizationTest {

    @Test
    public void testClearsOnlyServerOwnedFields() {
        final UpdateLobbyPlayerEvent forged = UpdateLobbyPlayerEvent.create(
                LobbySlotType.OPEN, "Mallory", 4, true,
                Collections.emptySet(), "ai-profile");

        Assert.assertTrue(forged.clearServerOwnedFields(), "Server-owned fields were present");
        Assert.assertNull(forged.getType(), "Slot type is the server's to set");
        Assert.assertNull(forged.getAiProfile(), "AI profile does not belong to a REMOTE slot");

        // Anything a client may legitimately change must survive untouched,
        // including isDevMode, which is gated on mayEdit() and
        // is deliberately left alone.
        Assert.assertEquals(forged.getName(), "Mallory");
        Assert.assertEquals(forged.getDevMode(), Boolean.TRUE);

        // A no-op for honest traffic, which carries none of the three.
        Assert.assertFalse(UpdateLobbyPlayerEvent.isReadyUpdate(true).clearServerOwnedFields());
    }
}
