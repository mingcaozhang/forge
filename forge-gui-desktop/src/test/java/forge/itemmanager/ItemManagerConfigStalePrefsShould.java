package forge.itemmanager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;

/**
 * Users upgrading from before the single-player Commander refactor have an
 * item_view.preferences file that still references configs and columns that
 * were removed (Oathbreaker, net commander decks, brackets, ...).
 * Loading such a file must skip the stale entries silently instead of
 * printing stack traces, and must keep applying every valid entry.
 */
public class ItemManagerConfigStalePrefsShould {

    private static final String STALE_PREFS =
            "<preferences type=\"item_view\">\n"
            + "  <config name=\"OATHBREAKER_SECTION\" viewIndex=\"1\"/>\n"
            + "  <config name=\"NET_COMMANDER_DECKS\" viewIndex=\"2\"/>\n"
            + "  <config name=\"NET_ARCHIVE_COMMANDER_DECKS\" viewIndex=\"3\"/>\n"
            + "  <config name=\"COMMANDER_SECTION\" groupBy=\"COLOR\" pileBy=\"DECK_BRACKET\" viewIndex=\"4\">\n"
            + "    <col name=\"DECK_BRACKET\" width=\"100\"/>\n"
            + "    <col name=\"NAME\" width=\"250\"/>\n"
            + "  </config>\n"
            + "  <config name=\"COMMANDER_DECKS\" viewIndex=\"2\">\n"
            + "    <col name=\"NAME\" width=\"300\"/>\n"
            + "  </config>\n"
            + "</preferences>\n";

    private static boolean initialized = false;

    private File prefsFile;
    private byte[] originalContent;
    private boolean fileExisted;

    private int commanderDecksViewIndex;
    private GroupDef commanderSectionGroupBy;
    private ColumnDef commanderSectionPileBy;
    private int commanderSectionNameWidth;
    private int commanderDecksNameWidth;

    @BeforeClass(alwaysRun = true)
    public void setUp() throws Exception {
        if (!initialized) {
            GuiBase.setInterface(new GuiDesktop());
            FModel.initialize(null, preferences -> {
                preferences.setPref(FPref.LOAD_CARD_SCRIPTS_LAZILY, false);
                preferences.setPref(FPref.UI_LANGUAGE, "en-US");
                return null;
            });
            initialized = true;
        }

        prefsFile = new File(ForgeConstants.ITEM_VIEW_PREFS_FILE);
        fileExisted = prefsFile.isFile();
        if (fileExisted) {
            originalContent = Files.readAllBytes(prefsFile.toPath());
        }

        commanderDecksViewIndex = ItemManagerConfig.COMMANDER_DECKS.getViewIndex();
        commanderSectionGroupBy = ItemManagerConfig.COMMANDER_SECTION.getGroupBy();
        commanderSectionPileBy = ItemManagerConfig.COMMANDER_SECTION.getPileBy();
        commanderSectionNameWidth =
                ItemManagerConfig.COMMANDER_SECTION.getCols().get(ColumnDef.NAME).getPreferredWidth();
        commanderDecksNameWidth =
                ItemManagerConfig.COMMANDER_DECKS.getCols().get(ColumnDef.NAME).getPreferredWidth();
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() throws Exception {
        ItemManagerConfig.COMMANDER_DECKS.setViewIndex(commanderDecksViewIndex);
        ItemManagerConfig.COMMANDER_SECTION.setGroupBy(commanderSectionGroupBy);
        ItemManagerConfig.COMMANDER_SECTION.setPileBy(commanderSectionPileBy);
        ItemManagerConfig.COMMANDER_SECTION.getCols().get(ColumnDef.NAME)
                .setPreferredWidth(commanderSectionNameWidth);
        ItemManagerConfig.COMMANDER_DECKS.getCols().get(ColumnDef.NAME)
                .setPreferredWidth(commanderDecksNameWidth);

        if (fileExisted) {
            Files.write(prefsFile.toPath(), originalContent);
        } else {
            prefsFile.delete();
        }
    }

    @Test
    public void skipStaleEntriesWithoutPrintingAndKeepValidOnes() throws Exception {
        File parent = prefsFile.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            Assert.assertTrue(parent.mkdirs(), "could not create " + parent);
        }
        Files.write(prefsFile.toPath(), STALE_PREFS.getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            ItemManagerConfig.load();
        } finally {
            System.setErr(originalErr);
        }

        String printed = err.toString(StandardCharsets.UTF_8);
        Assert.assertFalse(printed.contains("No enum constant"),
                "stale entries must be skipped silently, but stderr was: " + printed);
        Assert.assertFalse(printed.contains("IllegalArgumentException"),
                "stale entries must be skipped silently, but stderr was: " + printed);

        Assert.assertEquals(ItemManagerConfig.COMMANDER_DECKS.getViewIndex(), 2,
                "valid config entries must still be applied");
        Assert.assertEquals(ItemManagerConfig.COMMANDER_SECTION.getGroupBy(), GroupDef.COLOR,
                "valid groupBy must still be applied");
        Assert.assertNull(ItemManagerConfig.COMMANDER_SECTION.getPileBy(),
                "stale pileBy must be ignored, keeping the default");
        Assert.assertEquals(
                ItemManagerConfig.COMMANDER_SECTION.getCols().get(ColumnDef.NAME).getPreferredWidth(), 250,
                "valid column entries must still be applied");
        Assert.assertEquals(
                ItemManagerConfig.COMMANDER_DECKS.getCols().get(ColumnDef.NAME).getPreferredWidth(), 300,
                "valid column entries must still be applied");
    }
}
