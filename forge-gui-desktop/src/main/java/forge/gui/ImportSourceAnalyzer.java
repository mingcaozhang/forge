/*
 * Forge: Play Magic: the Gathering.
 * Copyright (c) 2013  Forge Team
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
package forge.gui;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import forge.card.CardDb;
import forge.item.PaperCardPredicates;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import forge.card.CardEdition;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.util.FileUtil;

public class ImportSourceAnalyzer {

    public enum OpType {
        CONSTRUCTED_DECK,
        UNKNOWN_DECK,
        DEFAULT_CARD_PIC,
        SET_CARD_PIC,
        POSSIBLE_SET_CARD_PIC,
        TOKEN_PIC,
        QUEST_DATA,
        PREFERENCE_FILE,
        DB_FILE
    }

    public interface AnalysisCallback {
        boolean checkCancel();
        void addOp(OpType type, File src, File dest);
    }

    private final File source;
    private final AnalysisCallback cb;
    private final int numFilesToAnalyze;

    private int numFilesAnalyzed;

    public ImportSourceAnalyzer(final String source, final AnalysisCallback cb) {
        this.source = new File(source);
        this.cb = cb;
        numFilesToAnalyze = countFiles(this.source);
    }

    public int getNumFilesToAnalyze() { return numFilesToAnalyze; }
    public int getNumFilesAnalyzed()  { return numFilesAnalyzed;  }

    public void doAnalysis() {
        identifyAndAnalyze(this.source);
    }

    private void identifyAndAnalyze(final File root) {
        // see if we can figure out the likely identity of the source folder and
        // dispatch to the best analysis subroutine to handle it
        final String dirname = root.getName();

        if ("res".equalsIgnoreCase(dirname))               { analyzeOldResDir(root);          }
        else if ("constructed".equalsIgnoreCase(dirname))  { analyzeConstructedDeckDir(root); }
        else if (StringUtils.containsIgnoreCase(dirname, "deck")) { analyzeDecksDir(root);    }
        else if ("layouts".equalsIgnoreCase(dirname))      { analyzeLayoutsDir(root);         }
        else if ("pics".equalsIgnoreCase(dirname))         { analyzeCardPicsDir(root);        }
        else if ("preferences".equalsIgnoreCase(dirname))  { analyzePreferencesDir(root);     }
        else if ("quest".equalsIgnoreCase(dirname))        { analyzeQuestDir(root);           }
        else if (null != FModel.getMagicDb().getEditions().get(dirname)) { analyzeCardPicsSetDir(root); }
        else {
            // look at files in directory and make a semi-educated guess based on file extensions
            int numUnhandledFiles = 0;
            File[] files = root.listFiles();
            assert files != null;
            for (final File file : files) {
                if (cb.checkCancel()) { return; }

                if (file.isFile()) {
                    final String filename = file.getName();
                    if (StringUtils.endsWithIgnoreCase(filename, ".dck")) {
                        analyzeDecksDir(root);
                        numUnhandledFiles = 0;
                        break;
                    } else if (StringUtils.endsWithIgnoreCase(filename, ".jpg")) {
                        analyzeCardPicsDir(root);
                        numUnhandledFiles = 0;
                        break;
                    }

                    ++numUnhandledFiles;
                } else if (file.isDirectory()) {
                    identifyAndAnalyze(file);
                }
            }
            numFilesAnalyzed += numUnhandledFiles;
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // pre-profile res dir
    //

    private void analyzeOldResDir(final File root) {
        analyzeDir(root, new Analyzer() {
            @Override
            boolean onDir(final File dir) {
                final String dirname = dir.getName();
                if ("decks".equalsIgnoreCase(dirname)) {
                    analyzeDecksDir(dir);
                } else if ("layouts".equalsIgnoreCase(dirname)) {
                    analyzeLayoutsDir(dir);
                } else if ("pics".equalsIgnoreCase(dirname)) {
                    analyzeCardPicsDir(dir);
                } else if ("preferences".equalsIgnoreCase(dirname)) {
                    analyzePreferencesDir(dir);
                } else if ("quest".equalsIgnoreCase(dirname)) {
                    analyzeQuestDir(dir);
                } else {
                    return false;
                }
                return true;
            }
        });
    }

    //////////////////////////////////////////////////////////////////////////
    // decks
    //

    private void analyzeDecksDir(final File root) {
        analyzeDir(root, new Analyzer() {
            @Override
            void onFile(final File file) {
                // we don't really expect any files in here, but if we find a .dck file, add it to the unknown list
                final String filename = file.getName();
                if (StringUtils.endsWithIgnoreCase(filename, ".dck")) {
                    final File targetFile = new File(lcaseExt(filename));
                    cb.addOp(OpType.UNKNOWN_DECK, file, targetFile);
                }
            }

            @Override
            boolean onDir(final File dir) {
                final String dirname = dir.getName();
                if ("constructed".equalsIgnoreCase(dirname)) {
                    analyzeConstructedDeckDir(dir);
                } else if ("cube".equalsIgnoreCase(dirname)) {
                    return false;
                } else {
                    analyzeKnownDeckDir(dir, null, OpType.UNKNOWN_DECK);
                }
                return true;
            }
        });
    }

    private void analyzeConstructedDeckDir(final File root) {
        analyzeKnownDeckDir(root, ForgeConstants.DECK_CONSTRUCTED_DIR, OpType.CONSTRUCTED_DECK);
    }

    private void analyzeKnownDeckDir(final File root, final String targetDir, final OpType opType) {
        analyzeDir(root, new Analyzer() {
            @Override
            void onFile(final File file) {
                final String filename = file.getName();
                if (StringUtils.endsWithIgnoreCase(filename, ".dck")) {
                    final File targetFile = new File(targetDir, lcaseExt(filename));
                    if (!file.equals(targetFile)) {
                        cb.addOp(opType, file, targetFile);
                    }
                }
            }

            @Override
            boolean onDir(final File dir) {
                // if there's a dir beneath a known directory, assume the same kind of decks are in there
                analyzeKnownDeckDir(dir, targetDir, opType);
                return true;
            }
        });
    }

    //////////////////////////////////////////////////////////////////////////
    // layouts
    //

    private void analyzeLayoutsDir(final File root) {
        analyzeDir(root, new Analyzer() {
            @Override
            void onFile(final File file) {
                // find *_preferred.xml files
                final String filename = file.getName();
                if (StringUtils.endsWithIgnoreCase(filename, "_preferred.xml")) {
                    final File targetFile = new File(ForgeConstants.USER_PREFS_DIR,
                            file.getName().toLowerCase(Locale.ENGLISH).replace("_preferred", ""));
                    cb.addOp(OpType.PREFERENCE_FILE, file, targetFile);
                }
            }
        });
    }

    //////////////////////////////////////////////////////////////////////////
    // default card pics
    //

    private static String oldCleanString(final String in) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < in.length(); i++) {
            final char c = in.charAt(i);
            if ((c == ' ') || (c == '-')) {
                out.append('_');
            } else if (Character.isLetterOrDigit(c) || (c == '_')) {
                out.append(c);
            }
        }

        // usually we would want to pass Locale.ENGLISH to the toLowerCase() method to prevent unintentional
        // character mangling on some system locales, but we want to replicate the old code here exactly
        return out.toString().toLowerCase();
    }
    
    @Deprecated
    private void addDefaultPicNames(final PaperCard c, final boolean backFace) {
        return;
    }


    private Map<String, String> defaultPicNames;
    private Map<String, String> defaultPicOldNameToCurrentName;
    private void analyzeCardPicsDir(final File root) {
        if (null == defaultPicNames) {
            defaultPicNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            defaultPicOldNameToCurrentName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

            for (final PaperCard c : FModel.getMagicDb().getCommonCards().getAllCards()) {
                addDefaultPicNames(c, false);
                if (c.hasBackFace()) {
                    addDefaultPicNames(c, true);
                }
            }

            for (final PaperCard c : FModel.getMagicDb().getVariantCards().getAllCards()) {
                addDefaultPicNames(c, false);
                // variants never have backfaces
            }
        }

        analyzeListedDir(root, ForgeConstants.CACHE_CARD_PICS_DIR, new ListedAnalyzer() {
            @Override
            public String map(final String filename) {
                if (defaultPicOldNameToCurrentName.containsKey(filename)) {
                    return defaultPicOldNameToCurrentName.get(filename);
                }
                return defaultPicNames.get(filename);
            }

            @Override
            public OpType getOpType(final String filename) {
                return OpType.DEFAULT_CARD_PIC;
            }

            @Override
            boolean onDir(final File dir) {
                if ("tokens".equalsIgnoreCase(dir.getName())) {
                    analyzeTokenPicsDir(dir);
                } else {
                    analyzeCardPicsSetDir(dir);
                }
                return true;
            }
        });
    }

    //////////////////////////////////////////////////////////////////////////
    // set card pics
    //

    private static void addSetCards(final Map<String, String> cardFileNames, final CardDb library, final Predicate<PaperCard> filter) {
        library.streamAllCards().filter(filter).forEach(c -> {
            String filename = c.getCardImageKey() + ".jpg";
            cardFileNames.put(filename, filename);
            if (c.hasBackFace()) {
                filename = c.getCardAltImageKey() + ".jpg";
                cardFileNames.put(filename, filename);
            }
        });
    }

    Map<String, Map<String, String>> cardFileNamesBySet;
    Map<String, String>              nameUpdates;
    private void analyzeCardPicsSetDir(final File root) {
        if (null == cardFileNamesBySet) {
            cardFileNamesBySet = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (final CardEdition ce : FModel.getMagicDb().getEditions()) {
                final Map<String, String> cardFileNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                final Predicate<PaperCard> filter = PaperCardPredicates.printedInSet(ce.getCode());
                addSetCards(cardFileNames, FModel.getMagicDb().getCommonCards(), filter);
                addSetCards(cardFileNames, FModel.getMagicDb().getVariantCards(), filter);
                cardFileNamesBySet.put(ce.getCode2(), cardFileNames);
            }

            // planar cards now don't have the ".full" part in their filenames
            nameUpdates = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            final Predicate<PaperCard> predPlanes = arg0 -> arg0.getRules().getType().isPlane() || arg0.getRules().getType().isPhenomenon();

            FModel.getMagicDb().getVariantCards().streamAllCards().filter(predPlanes).forEach(c -> {
                String baseName = c.getCardImageKey();
                nameUpdates.put(baseName + ".full.jpg", baseName + ".jpg");
                if (c.hasBackFace()) {
                    baseName = c.getCardAltImageKey();
                    nameUpdates.put(baseName + ".full.jpg", baseName + ".jpg");
                }
            });
        }

        final CardEdition.Collection editions = FModel.getMagicDb().getEditions();
        final String editionCode = root.getName();
        final CardEdition edition = editions.get(editionCode);
        if (null == edition) {
            // not a valid set name, skip
            numFilesAnalyzed += countFiles(root);
            return;
        }

        final String editionCode2 = edition.getCode2();
        final Map<String, String> validFilenames = cardFileNamesBySet.get(editionCode2);
        analyzeListedDir(root, ForgeConstants.CACHE_CARD_PICS_DIR, new ListedAnalyzer() {
            @Override
            public String map(String filename) {
                filename = editionCode2 + "/" + filename;
                if (nameUpdates.containsKey(filename)) {
                    filename = nameUpdates.get(filename);
                }
                if (validFilenames.containsKey(filename)) {
                    return validFilenames.get(filename);
                } else if (StringUtils.endsWithIgnoreCase(filename, ".jpg")
                        || StringUtils.endsWithIgnoreCase(filename, ".png")) {
                    return filename;
                }
                return null;
            }

            @Override
            public OpType getOpType(final String filename) {
                return validFilenames.containsKey(filename) ? OpType.SET_CARD_PIC : OpType.POSSIBLE_SET_CARD_PIC;
            }
        });
    }

    //////////////////////////////////////////////////////////////////////////
    // other image dirs
    //

    Map<String, String> tokenFileNames;
    private void analyzeTokenPicsDir(final File root) {
        if (null == tokenFileNames) {
            tokenFileNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (final Pair<String, String> nameurl : FileUtil.readNameUrlFile(ForgeConstants.IMAGE_LIST_TOKENS_FILE)) {
                tokenFileNames.put(nameurl.getLeft(), nameurl.getLeft());
            }
        }

        analyzeListedDir(root, ForgeConstants.CACHE_TOKEN_PICS_DIR, new ListedAnalyzer() {
            @Override
            public String map(final String filename) {
                if (tokenFileNames.containsKey(filename)) {
                    return tokenFileNames.get(filename);
                }
                return null;
            }

            @Override
            public OpType getOpType(final String filename) {
                return OpType.TOKEN_PIC;
            }
        });
    }

    //////////////////////////////////////////////////////////////////////////
    // preferences
    //

    private void analyzePreferencesDir(final File root) {
        analyzeDir(root, new Analyzer() {
            @Override
            void onFile(final File file) {
                final String filename = file.getName();
                if ("editor.preferences".equalsIgnoreCase(filename) || "forge.preferences".equalsIgnoreCase(filename)) {
                    final File targetFile = new File(ForgeConstants.USER_PREFS_DIR, filename.toLowerCase(Locale.ENGLISH));
                    if (!file.equals(targetFile)) {
                        cb.addOp(OpType.PREFERENCE_FILE, file, targetFile);
                    }
                }
            }
        });
    }

    //////////////////////////////////////////////////////////////////////////
    // quest data
    //

    private void analyzeQuestDir(final File root) {
        analyzeDir(root, new Analyzer() {
            @Override
            void onFile(final File file) {
                final String filename = file.getName();
                if ("all-prices.txt".equalsIgnoreCase(filename)) {
                    final File targetFile = new File(ForgeConstants.DB_DIR, filename.toLowerCase(Locale.ENGLISH));
                    if (!file.equals(targetFile)) {
                        cb.addOp(OpType.DB_FILE, file, targetFile);
                    }
                }
            }

            @Override
            boolean onDir(final File dir) {
                if ("data".equalsIgnoreCase(dir.getName())) {
                    analyzeQuestDataDir(dir);
                    return true;
                }
                return false;
            }
        });
    }

    private void analyzeQuestDataDir(final File root) {
        analyzeDir(root, new Analyzer() {
            @Override
            void onFile(final File file) {
                final String filename = file.getName();
                if (StringUtils.endsWithIgnoreCase(filename, ".dat")) {
                    final File targetFile = new File(ForgeConstants.QUEST_SAVE_DIR, lcaseExt(filename));
                    if (!file.equals(targetFile)) {
                        cb.addOp(OpType.QUEST_DATA, file, targetFile);
                    }
                }
            }
        });
    }

    //////////////////////////////////////////////////////////////////////////
    // utility functions
    //

    private class Analyzer {
        void onFile(final File file) { }

        // returns whether the directory has been handled
        boolean onDir(final File dir) { return false; }
    }

    private void analyzeDir(final File root, final Analyzer analyzer) {
        File[] files = root.listFiles();
        assert files != null;
        for (final File file : files) {
            if (cb.checkCancel()) { return; }

            if (file.isFile()) {
                ++numFilesAnalyzed;
                analyzer.onFile(file);
            } else if (file.isDirectory()) {
                if (!analyzer.onDir(file)) {
                    numFilesAnalyzed += countFiles(file);
                }
            }
        }
    }


    private abstract class ListedAnalyzer {
        abstract String map(String filename);
        abstract OpType getOpType(String filename);

        // returns whether the directory has been handled
        boolean onDir(final File dir) { return false; }
    }

    private void analyzeListedDir(final File root, final String targetDir, final ListedAnalyzer listedAnalyzer) {
        analyzeDir(root, new Analyzer() {
            @Override
            void onFile(final File file) {
                final String filename = listedAnalyzer.map(file.getName());
                if (null != filename) {
                    final File targetFile = new File(targetDir, filename);
                    if (!file.equals(targetFile)) {
                        cb.addOp(listedAnalyzer.getOpType(filename), file, targetFile);
                    }
                }
            }

            @Override
            boolean onDir(final File dir) {
                return listedAnalyzer.onDir(dir);
            }
        });
    }

    private int countFiles(final File root) {
        int count = 0;
        File[] files = root.listFiles();
        assert files != null;
        for (final File file : files) {
            if (cb.checkCancel()) { return 0; }

            if (file.isFile()) {
                ++count;
            } else if (file.isDirectory()) {
                count += countFiles(file);
            }
        }
        return count;
    }

    private static String lcaseExt(final String filename) {
        final int lastDotIdx = filename.lastIndexOf('.');
        if (0 > lastDotIdx) {
            return filename;
        }
        final String basename = filename.substring(0, lastDotIdx);
        final String ext      = filename.substring(lastDotIdx).toLowerCase(Locale.ENGLISH);
        if (filename.endsWith(ext)) {
            return filename;
        }
        return basename + ext;
    }
}
