/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.model;

import forge.deck.Deck;
import forge.deck.io.DeckStorage;
import forge.localinstance.properties.ForgeConstants;
import forge.util.storage.IStorage;
import forge.util.storage.StorageBase;
import forge.util.storage.StorageImmediatelySerialized;

import java.io.File;

/**
 * Holds editable maps of decks saved to disk. Adding or removing items to(from)
 * such map turns into immediate file update
 */
public class CardCollections {
    // Note: These are loaded lazily.
    private IStorage<Deck> constructed;
    private IStorage<Deck> commander;
    private IStorage<Deck> commanderPrecons;

    public CardCollections() {
    }

    public final IStorage<Deck> getConstructed() {
        if (constructed == null) {
            constructed = new StorageImmediatelySerialized<>("Constructed decks",
                    new DeckStorage(new File(ForgeConstants.DECK_CONSTRUCTED_DIR), ForgeConstants.DECK_BASE_DIR, true),
                    true);
        }
        return constructed;
    }

    public IStorage<Deck> getCommander() {
        if (commander == null) {
            commander = new StorageImmediatelySerialized<>("Commander decks",
                    new DeckStorage(new File(ForgeConstants.DECK_COMMANDER_DIR), ForgeConstants.DECK_BASE_DIR),
                    true);
        }
        return commander;
    }

    public IStorage<Deck> getCommanderPrecons() {
        if (commanderPrecons == null) {
            // Read-only: these ship with Forge, and saving one (a sleeve change, say) would rewrite the file in res
            commanderPrecons = new StorageBase<>("Commander Precon decks",
                    new DeckStorage(new File(ForgeConstants.COMMANDER_PRECON_DIR), ForgeConstants.COMMANDER_PRECON_DIR));
        }
        return commanderPrecons;
    }

}
