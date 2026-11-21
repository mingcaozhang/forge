/**
 * 
 */
package forge.gui.framework;

import java.util.EnumMap;
import java.util.Map;

import com.google.common.collect.ObjectArrays;

import forge.game.zone.ZoneType;
import forge.screens.deckeditor.views.*;
import forge.screens.home.online.VSubmenuOnlineDecks;
import forge.screens.home.online.VSubmenuOnlineLobby;
import forge.screens.home.sanctioned.VSubmenuConstructed;
import forge.screens.home.settings.VSubmenuAchievements;
import forge.screens.home.settings.VSubmenuPreferences;

/**
 * These are the identifiers for tabs found in the drag layout.
 * These IDs are used in the save XML and card layouts.
 * 
 * <br><br><i>(E at beginning of class name denotes an enum.)</i>
 */
public enum EDocID {
    CARD_PICTURE (),
    CARD_DETAIL (),

    EDITOR_ALLDECKS (VAllDecks.SINGLETON_INSTANCE),
    EDITOR_STATISTICS (VStatistics.SINGLETON_INSTANCE),
    EDITOR_PROBABILITIES (VProbabilities.SINGLETON_INSTANCE),
    EDITOR_CATALOG (VCardCatalog.SINGLETON_INSTANCE),
    EDITOR_CURRENTDECK (VCurrentDeck.SINGLETON_INSTANCE),
    EDITOR_DECKGEN (VDeckgen.SINGLETON_INSTANCE),
    EDITOR_COMMANDER (VCommanderDecks.SINGLETON_INSTANCE),
    EDITOR_LOG(VEditorLog.SINGLETON_INSTANCE),

    HOME_PREFERENCES (VSubmenuPreferences.SINGLETON_INSTANCE),
    HOME_ACHIEVEMENTS (VSubmenuAchievements.SINGLETON_INSTANCE),
    HOME_CONSTRUCTED (VSubmenuConstructed.SINGLETON_INSTANCE),
    HOME_NETWORK (VSubmenuOnlineLobby.SINGLETON_INSTANCE),
    HOME_NET_DECKS (VSubmenuOnlineDecks.SINGLETON_INSTANCE),

    REPORT_MESSAGE (),
    REPORT_STACK (),
    REPORT_COMBAT (),
    REPORT_DEPENDENCIES (),
    REPORT_LOG (),

    DEV_MODE (),
    BUTTON_DOCK (),

    // Battlefields, use setDoc to register.
    FIELD_0 (),
    FIELD_1 (),
    FIELD_2 (),
    FIELD_3 (),
    FIELD_4 (),
    FIELD_5 (),
    FIELD_6 (),
    FIELD_7 (),

    // Hands, use setDoc to register.
    HAND_0 (),
    HAND_1 (),
    HAND_2 (),
    HAND_3 (),
    HAND_4 (),
    HAND_5 (),
    HAND_6 (),
    HAND_7 (),

    // Dockable zones, use setDoc to register.
    ZONE_HAND (),
    ZONE_LIBRARY (),
    ZONE_GRAVEYARD (),
    ZONE_EXILE (),
    ZONE_FLASHBACK (),
    ZONE_COMMAND (),
    ZONE_ANTE (),
    ZONE_SIDEBOARD (),
    ZONE_PLANAR_DECK (),
    ZONE_SCHEME_DECK (),
    ZONE_ATTRACTION_DECK (),
    ZONE_CONTRAPTION_DECK (),
    ZONE_JUNKYARD (),
    ZONE_STICKER_SHEETS ();

    public final static EDocID[] Fields = new EDocID[] {FIELD_0, FIELD_1, FIELD_2, FIELD_3, FIELD_4, FIELD_5, FIELD_6, FIELD_7};
    public final static EDocID[] Hands = new EDocID[] {HAND_0, HAND_1, HAND_2, HAND_3, HAND_4, HAND_5, HAND_6, HAND_7};
    static {
        for (int i = 0; i < 8; i++) EDocID.Fields[i].setDoc(new VEmptyDoc(EDocID.Fields[i]));
        for (int i = 0; i < 8; i++) EDocID.Hands[i].setDoc(new VEmptyDoc(EDocID.Hands[i]));
    }
    public final static EDocID[] VarDocs = ObjectArrays.concat(Fields, Hands, EDocID.class);

    // End enum declarations, start enum methods.
    private IVDoc<? extends ICDoc> vDoc;

    EDocID() {
        this(null);
    }

    /** @param doc0 &emsp; {@link forge.gui.framework.IVDoc} */
    EDocID(final IVDoc<? extends ICDoc> doc0) {
        this.vDoc = doc0;
    }

    /** @param doc0 &emsp; {@link forge.gui.framework.IVDoc} */
    public void setDoc(final IVDoc<? extends ICDoc> doc0) {
        this.vDoc = doc0;
    }

    /** @return {@link forge.gui.framework.IVDoc} */
    public IVDoc<? extends ICDoc> getDoc() {
        //if (vDoc == null) { throw new NullPointerException("No document found for " + this.name() + "."); }
        return vDoc;
    }

    private static final Map<ZoneType, EDocID> ZONE_DOC_IDS = new EnumMap<>(ZoneType.class);
    static {
        ZONE_DOC_IDS.put(ZoneType.Hand, ZONE_HAND);
        ZONE_DOC_IDS.put(ZoneType.Library, ZONE_LIBRARY);
        ZONE_DOC_IDS.put(ZoneType.Graveyard, ZONE_GRAVEYARD);
        ZONE_DOC_IDS.put(ZoneType.Exile, ZONE_EXILE);
        ZONE_DOC_IDS.put(ZoneType.Flashback, ZONE_FLASHBACK);
        ZONE_DOC_IDS.put(ZoneType.Command, ZONE_COMMAND);
        ZONE_DOC_IDS.put(ZoneType.Ante, ZONE_ANTE);
        ZONE_DOC_IDS.put(ZoneType.Sideboard, ZONE_SIDEBOARD);
        ZONE_DOC_IDS.put(ZoneType.PlanarDeck, ZONE_PLANAR_DECK);
        ZONE_DOC_IDS.put(ZoneType.SchemeDeck, ZONE_SCHEME_DECK);
        ZONE_DOC_IDS.put(ZoneType.AttractionDeck, ZONE_ATTRACTION_DECK);
        ZONE_DOC_IDS.put(ZoneType.ContraptionDeck, ZONE_CONTRAPTION_DECK);
        ZONE_DOC_IDS.put(ZoneType.Junkyard, ZONE_JUNKYARD);
        ZONE_DOC_IDS.put(ZoneType.StickerSheets, ZONE_STICKER_SHEETS);
    }

    /** Returns the EDocID for a dockable zone type, or null if the zone type has no EDocID. */
    public static EDocID fromZoneType(final ZoneType zone) {
        return ZONE_DOC_IDS.get(zone);
    }
}
