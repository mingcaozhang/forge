package forge.screens.home;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Guards the single-player Commander home-screen navigation registry contract.
 *
 * <p>The Arena-style dashboard hero tiles are built (in
 * {@code VHomeUI}) by iterating the registered {@code allSubmenus} list and
 * using each submenu's {@code IVSubmenu.getItemEnum()} as the tile key. That
 * means the set of {@code EDocID} values surfaced by the dashboard is, by
 * construction, exactly the set of {@code getItemEnum()} values of the
 * registered submenu views. Multiplayer/online submenus are intentionally not
 * registered, so their docs must never appear on the dashboard.</p>
 *
 * <p>The desktop view singletons cannot be instantiated in a bare unit-test
 * JVM because loading {@code EDocID} (and every {@code VSubmenu*} singleton it
 * eagerly constructs) requires the full skin/splash stack. This test therefore
 * verifies the registry against the source instead of runtime, so it stays
 * deterministic and headless-safe.</p>
 */
public class HomeNavigationRegistryTest {

    private static final String HOME_SOURCE_SUBPATH = "src/main/java/forge/screens/home";
    private static final String EDOCID_SOURCE_SUBPATH = "src/main/java/forge/gui/framework/EDocID.java";

    /** Single-player submenus that the dashboard is expected to surface. */
    private static final Set<String> EXPECTED_SURFACED_SUBMENUS = new TreeSet<>(List.of(
            "VSubmenuConstructed",
            "VSubmenuPreferences",
            "VSubmenuAchievements",
            "VSubmenuAvatars",
            "VSubmenuDownloaders",
            "VSubmenuReleaseNotes"));

    /** Multiplayer/online submenus that must remain hidden from the dashboard. */
    private static final Set<String> EXPECTED_ONLINE_SUBMENUS = new TreeSet<>(List.of(
            "VSubmenuOnlineLobby",
            "VSubmenuOnlineDecks"));

    /** Expected {@code getItemEnum()} doc id returned by each surfaced submenu. */
    private static final TreeMap<String, String> EXPECTED_SUBMENU_DOC_ID = new TreeMap<>();
    /** Expected {@code getItemEnum()} doc id returned by each online submenu. */
    private static final TreeMap<String, String> EXPECTED_ONLINE_DOC_ID = new TreeMap<>();
    /** Expected dashboard hero-tile doc id set. */
    private static final Set<String> EXPECTED_DASHBOARD_DOC_IDS = new TreeSet<>();

    static {
        EXPECTED_SUBMENU_DOC_ID.put("VSubmenuConstructed", "HOME_CONSTRUCTED");
        EXPECTED_SUBMENU_DOC_ID.put("VSubmenuPreferences", "HOME_PREFERENCES");
        EXPECTED_SUBMENU_DOC_ID.put("VSubmenuAchievements", "HOME_ACHIEVEMENTS");
        EXPECTED_SUBMENU_DOC_ID.put("VSubmenuAvatars", "HOME_AVATARS");
        EXPECTED_SUBMENU_DOC_ID.put("VSubmenuDownloaders", "HOME_UTILITIES");
        EXPECTED_SUBMENU_DOC_ID.put("VSubmenuReleaseNotes", "HOME_RELEASE_NOTES");

        EXPECTED_ONLINE_DOC_ID.put("VSubmenuOnlineLobby", "HOME_NETWORK");
        EXPECTED_ONLINE_DOC_ID.put("VSubmenuOnlineDecks", "HOME_NET_DECKS");

        EXPECTED_DASHBOARD_DOC_IDS.addAll(EXPECTED_SUBMENU_DOC_ID.values());
    }

    @Test
    public void dashboardSurfacesExactlyTheSinglePlayerSubmenus() {
        final Set<String> registered = parseRegisteredSubmenus(readHomeSource("VHomeUI.java"));
        Assert.assertEquals(registered, EXPECTED_SURFACED_SUBMENUS,
                "VHomeUI must register exactly the single-player submenus on the home dashboard");
    }

    @Test
    public void onlineSubmenusAreNeverSurfaced() {
        final Set<String> registered = parseRegisteredSubmenus(readHomeSource("VHomeUI.java"));
        for (final String online : EXPECTED_ONLINE_SUBMENUS) {
            Assert.assertFalse(registered.contains(online),
                    "Multiplayer/online submenu " + online + " must not be registered on the dashboard");
        }
    }

    @Test
    public void dashboardTileDocIdsMatchSubmenuGetItemEnum() {
        final Set<String> registered = parseRegisteredSubmenus(readHomeSource("VHomeUI.java"));

        final TreeMap<String, String> docIdsBySubmenu = new TreeMap<>();
        for (final Path source : findVSubmenuSources()) {
            final String simpleName = classNameOf(source);
            if (!registered.contains(simpleName)) {
                continue;
            }
            final String docId = parseGetItemEnumDocId(readSource(source));
            Assert.assertNotNull(docId,
                    "Could not resolve getItemEnum() doc id for " + simpleName);
            docIdsBySubmenu.put(simpleName, docId);
        }

        Assert.assertEquals(new TreeSet<>(docIdsBySubmenu.keySet()), EXPECTED_SURFACED_SUBMENUS,
                "Every registered submenu must be present and resolvable");

        final Set<String> dashboardDocIds = new TreeSet<>(docIdsBySubmenu.values());
        Assert.assertEquals(dashboardDocIds, EXPECTED_DASHBOARD_DOC_IDS,
                "Dashboard tile doc ids must equal the registered submenus' getItemEnum() values");
    }

    @Test
    public void eachSurfacedSubmenuReturnsItsExpectedDocId() {
        final TreeMap<String, String> actual = new TreeMap<>();
        for (final Path source : findVSubmenuSources()) {
            final String simpleName = classNameOf(source);
            if (EXPECTED_SUBMENU_DOC_ID.containsKey(simpleName) || EXPECTED_ONLINE_DOC_ID.containsKey(simpleName)) {
                actual.put(simpleName, parseGetItemEnumDocId(readSource(source)));
            }
        }

        final TreeMap<String, String> expected = new TreeMap<>();
        expected.putAll(EXPECTED_SUBMENU_DOC_ID);
        expected.putAll(EXPECTED_ONLINE_DOC_ID);
        Assert.assertEquals(actual, expected,
                "VSubmenu* getItemEnum() doc id mappings drifted from the navigation registry");
    }

    @Test
    public void onlineDocIdsAreExcludedFromDashboard() {
        final TreeSet<String> onlineDocIds = new TreeSet<>(EXPECTED_ONLINE_DOC_ID.values());
        for (final String onlineDocId : onlineDocIds) {
            Assert.assertFalse(EXPECTED_DASHBOARD_DOC_IDS.contains(onlineDocId),
                    "Online doc id " + onlineDocId + " must not be surfaced by the dashboard");
        }
    }

    @Test
    public void constructedDefaultStillResolves() {
        final Set<String> registered = parseRegisteredSubmenus(readHomeSource("VHomeUI.java"));
        Assert.assertTrue(registered.contains("VSubmenuConstructed"),
                "EDocID.HOME_CONSTRUCTED must stay surfaced as the default New Game submenu");
        Assert.assertEquals(EXPECTED_SUBMENU_DOC_ID.get("VSubmenuConstructed"), "HOME_CONSTRUCTED");

        final String edocidSource = readSource(locateSourceRoot().resolve(EDOCID_SOURCE_SUBPATH));
        Assert.assertTrue(edocidSource.contains("HOME_CONSTRUCTED (VSubmenuConstructed.SINGLETON_INSTANCE)"),
                "EDocID must declare HOME_CONSTRUCTED bound to VSubmenuConstructed");
    }

    @Test
    public void everyVSubmenuIsEitherSurfacedOrOnline() {
        final Set<String> accountedFor = new TreeSet<>();
        accountedFor.addAll(EXPECTED_SURFACED_SUBMENUS);
        accountedFor.addAll(EXPECTED_ONLINE_SUBMENUS);

        final Set<String> allSubmenuClasses = new TreeSet<>();
        for (final Path source : findVSubmenuSources()) {
            allSubmenuClasses.add(classNameOf(source));
        }
        Assert.assertEquals(allSubmenuClasses, accountedFor,
                "A new VSubmenu* class was added; classify it as surfaced or online in this registry");
    }

    // --- source helpers -----------------------------------------------------

    private static Path locateSourceRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            if (Files.exists(dir.resolve(HOME_SOURCE_SUBPATH).resolve("VHomeUI.java"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate the forge-gui-desktop source root from the test working directory");
    }

    private static String readHomeSource(final String fileName) {
        return readSource(locateSourceRoot().resolve(HOME_SOURCE_SUBPATH).resolve(fileName));
    }

    private static String readSource(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to read source " + path, e);
        }
    }

    private static List<Path> findVSubmenuSources() {
        final Path homeDir = locateSourceRoot().resolve(HOME_SOURCE_SUBPATH);
        final List<Path> result = new ArrayList<>();
        try (final var stream = Files.walk(homeDir)) {
            stream.filter(p -> p.getFileName().toString().matches("VSubmenu.*\\.java"))
                    .sorted()
                    .forEach(result::add);
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to scan home submenu sources under " + homeDir, e);
        }
        return result;
    }

    private static String classNameOf(final Path source) {
        final String name = source.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }

    /** Extracts the simple {@code EDocID} names from the {@code allSubmenus.add(VSubmenuX.SINGLETON_INSTANCE)} calls. */
    private static Set<String> parseRegisteredSubmenus(final String homeSource) {
        final Set<String> registered = new LinkedHashSet<>();
        final Matcher matcher = Pattern
                .compile("allSubmenus\\.add\\((VSubmenu[A-Za-z0-9_]+)\\.SINGLETON_INSTANCE\\)")
                .matcher(homeSource);
        while (matcher.find()) {
            registered.add(matcher.group(1));
        }
        return registered;
    }

    /**
     * Extracts the {@code EDocID.HOME_*} constant a class' {@code getItemEnum()} returns, following a single
     * level of method delegation (e.g. {@code getItemEnum() { return getDocumentID(); }}).
     */
    private static String parseGetItemEnumDocId(final String source) {
        return resolveDocId(source, "getItemEnum", 3);
    }

    private static String resolveDocId(final String source, final String methodName, final int depth) {
        if (depth <= 0) {
            return null;
        }
        final String body = extractMethodBody(source, methodName);
        if (body == null) {
            return null;
        }
        final Matcher docId = Pattern.compile("EDocID\\.(HOME_[A-Z_]+)").matcher(body);
        if (docId.find()) {
            return docId.group(1);
        }
        final Matcher delegate = Pattern.compile("return\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(\\s*\\)\\s*;").matcher(body);
        if (delegate.find()) {
            return resolveDocId(source, delegate.group(1), depth - 1);
        }
        return null;
    }

    private static String extractMethodBody(final String source, final String methodName) {
        final Matcher method = Pattern
                .compile("\\b" + Pattern.quote(methodName) + "\\s*\\(\\s*\\)\\s*\\{").matcher(source);
        while (method.find()) {
            final int braceClose = findMatchingBrace(source, method.end());
            if (braceClose >= 0) {
                return source.substring(method.end(), braceClose);
            }
        }
        return null;
    }

    private static int findMatchingBrace(final String source, final int openBraceStart) {
        int depth = 1;
        for (int i = openBraceStart; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
