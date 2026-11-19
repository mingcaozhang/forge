package forge.screens.deckeditor.views;

import java.awt.BorderLayout;
import java.awt.Font;

import javax.swing.JComponent;
import javax.swing.JPanel;

import forge.gui.framework.DragCell;
import forge.gui.framework.DragTab;
import forge.gui.framework.EDocID;
import forge.gui.framework.IVDoc;
import forge.itemmanager.SItemManagerUtil.StatTypes;
import forge.localinstance.skin.FSkinProp;
import forge.screens.deckeditor.controllers.CStatistics;
import forge.toolbox.FLabel;
import forge.toolbox.FScrollPane;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * Assembles Swing components of the deck editor analysis ("statistics") tab.
 *
 * <p>
 * This revision presents the same data the original text-grid did, but through
 * modern rounded content cards and custom charts: a summary readout (total
 * cards / total mana cost / average mana cost / points), a stacked
 * {@link ManaCurveChart}, and horizontal {@link DistributionBarChart}s for the
 * color identity and card-type breakdowns. Only presentation changed — the
 * controller still computes every number and pushes it through the small
 * {@code setXxx} API below.
 *
 * <p>
 * Fixed row orders (shared by convention with {@code CStatistics}, which
 * computes counts in this exact order):
 * <ul>
 * <li>colors — white, blue, black, red, green, colorless, multicolor</li>
 * <li>types — creature, land, enchantment, artifact, instant, sorcery,
 * planeswalker</li>
 * <li>mana curve — mana value 0, 1, 2, 3, 4, 5, 6+ (each with a
 * {@code [W U B R G colorless]} symbol total)</li>
 * </ul>
 *
 * <br><br><i>(V at beginning of class name denotes a view class.)</i>
 */
public enum VStatistics implements IVDoc<CStatistics> {
    /** */
    SINGLETON_INSTANCE;

    // Fields used with interface IVDoc
    private DragCell parentCell;
    private final DragTab tab = new DragTab(Localizer.getInstance().getMessage("lblStatistics"));

    // Summary readouts
    private final FLabel lblTotal = new FLabel.Builder()
            .text(String.format("%s: 0", Localizer.getInstance().getMessage("lblTotalCards").toUpperCase()))
            .tooltip(Localizer.getInstance().getMessage("lblTotalCards")).fontStyle(Font.BOLD).fontSize(12).build();
    private final FLabel lblTMC = new FLabel.Builder()
            .text(String.format("%s: 0", Localizer.getInstance().getMessage("lblTotalManaCost").toUpperCase()))
            .tooltip(Localizer.getInstance().getMessage("lblTotalManaCost")).fontStyle(Font.BOLD).fontSize(12).build();
    private final FLabel lblAMC = new FLabel.Builder()
            .text(String.format("%s: 0.00", Localizer.getInstance().getMessage("lblAverageManaCost").toUpperCase()))
            .tooltip(Localizer.getInstance().getMessage("lblAverageManaCost")).fontStyle(Font.BOLD).fontSize(12).build();
    private final FLabel lblPoints = new FLabel.Builder().fontStyle(Font.BOLD).fontSize(12).build();

    // Charts
    private final ManaCurveChart manaCurveChart = new ManaCurveChart();
    private final DistributionBarChart colorChart;
    private final DistributionBarChart typeChart;

    // Layout containers
    private final JPanel contentPanel = new JPanel();
    private final FScrollPane scroller = new FScrollPane(contentPanel, false);

    private static final String CARD_CONSTRAINT = "w 96%!, gap 2% 0 0 16";

    //========== Constructor
    VStatistics() {
        // Row icon order — local arrays so they never depend on enum static-init order.
        final FSkinProp[] colorProps = {
                StatTypes.WHITE.skinProp, StatTypes.BLUE.skinProp, StatTypes.BLACK.skinProp,
                StatTypes.RED.skinProp, StatTypes.GREEN.skinProp, StatTypes.COLORLESS.skinProp,
                StatTypes.MULTICOLOR.skinProp };
        final FSkinProp[] typeProps = {
                StatTypes.CREATURE.skinProp, StatTypes.LAND.skinProp, StatTypes.ENCHANTMENT.skinProp,
                StatTypes.ARTIFACT.skinProp, StatTypes.INSTANT.skinProp, StatTypes.SORCERY.skinProp,
                StatTypes.PLANESWALKER.skinProp };

        colorChart = new DistributionBarChart(colorProps);
        typeChart = new DistributionBarChart(typeProps);

        scroller.getViewport().setBorder(null);
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new MigLayout("insets 6, gap 0, wrap 1", "[grow]", ""));

        contentPanel.add(buildSummaryCard(), CARD_CONSTRAINT);
        contentPanel.add(buildCard(Localizer.getInstance().getMessage("lblManaCurve"), manaCurveChart),
                CARD_CONSTRAINT);
        contentPanel.add(buildCard(Localizer.getInstance().getMessage("lblColors"), colorChart), CARD_CONSTRAINT);
        contentPanel.add(buildCard(Localizer.getInstance().getMessage("lblCardTypes"), typeChart), CARD_CONSTRAINT);

        // sensible starting state
        final int[] empty = new int[colorProps.length];
        colorChart.update(empty, texts(empty, 1));
        typeChart.update(empty, texts(empty, 1));
        manaCurveChart.update(new int[7], new int[7][6]);
        lblPoints.setVisible(false);
    }

    private static JComponent buildCard(final String header0, final JComponent chart0) {
        final JPanel inner = new JPanel(new BorderLayout(0, 4));
        inner.setOpaque(false);
        inner.add(new FLabel.Builder().text(header0).fontStyle(Font.BOLD).fontSize(13).build(), BorderLayout.NORTH);
        inner.add(chart0, BorderLayout.CENTER);
        return new RoundedContentCard(inner);
    }

    private JComponent buildSummaryCard() {
        final JPanel inner = new JPanel(new MigLayout("insets 8, gap 0, wrap 1", "[grow]", ""));
        inner.setOpaque(false);
        inner.add(lblTotal, "w 100%!");
        inner.add(lblTMC, "w 100%!");
        inner.add(lblAMC, "w 100%!");
        inner.add(lblPoints, "w 100%!, hidemode 3");
        return new RoundedContentCard(inner);
    }

    //========== Overridden methods

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getDocumentID()
     */
    @Override
    public EDocID getDocumentID() {
        return EDocID.EDITOR_STATISTICS;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getTabLabel()
     */
    @Override
    public DragTab getTabLabel() {
        return tab;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getLayoutControl()
     */
    @Override
    public CStatistics getLayoutControl() {
        return CStatistics.SINGLETON_INSTANCE;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#setParentCell(forge.gui.framework.DragCell)
     */
    @Override
    public void setParentCell(final DragCell cell0) {
        this.parentCell = cell0;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getParentCell()
     */
    @Override
    public DragCell getParentCell() {
        return this.parentCell;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#populate()
     */
    @Override
    public void populate() {
        parentCell.getBody().setLayout(new MigLayout("insets 0, gap 0"));
        parentCell.getBody().add(scroller, "w 96%!, h 96%!, gap 2% 0 2% 0");
    }

    //========== Data-injection API (used only by CStatistics)

    /**
     * Updates the summary readout.
     *
     * @param total0          number of cards in the deck
     * @param totalManaCost0  summed converted mana cost of all cards
     * @param averageManaCost0 average converted mana cost
     */
    public void setSummary(final int total0, final int totalManaCost0, final double averageManaCost0) {
        lblTotal.setText(String.format("%s: %d",
                Localizer.getInstance().getMessage("lblTotalCards").toUpperCase(), total0));
        lblTMC.setText(String.format("%s: %d",
                Localizer.getInstance().getMessage("lblTotalManaCost").toUpperCase(), totalManaCost0));
        lblAMC.setText(String.format("%s: %.2f",
                Localizer.getInstance().getMessage("lblAverageManaCost").toUpperCase(), averageManaCost0));
    }

    /**
     * @param points0 deck points summary text, or {@code null} to hide the line
     */
    public void setPoints(final String points0) {
        lblPoints.setText(points0 == null ? "" : points0.toUpperCase());
        lblPoints.setVisible(points0 != null);
    }

    /**
     * @param counts0 per-color card counts (white, blue, black, red, green, colorless, multicolor)
     * @param total0  total cards, used for the percentage readout
     */
    public void setColorDistribution(final int[] counts0, final int total0) {
        colorChart.update(counts0, texts(counts0, total0));
    }

    /**
     * @param counts0 per-card-type counts (creature, land, enchantment, artifact, instant,
     *                sorcery, planeswalker)
     * @param total0  total cards, used for the percentage readout
     */
    public void setCardTypeDistribution(final int[] counts0, final int total0) {
        typeChart.update(counts0, texts(counts0, total0));
    }

    /**
     * @param counts0 per-CMC bucket card counts, length 7 (0,1,2,3,4,5,6+)
     * @param shards0 per-CMC bucket colored-mana-symbol totals, {@code [7][W U B R G C]}
     */
    public void setManaCurve(final int[] counts0, final int[][] shards0) {
        manaCurveChart.update(counts0, shards0);
    }

    //========== Other methods

    private static String[] texts(final int[] counts0, final int total0) {
        final String[] out = new String[counts0.length];
        for (int i = 0; i < counts0.length; i++) {
            final int pct = total0 > 0 ? (int) Math.round((double) counts0[i] * 100 / (double) total0) : 0;
            out[i] = counts0[i] + " (" + pct + "%)";
        }
        return out;
    }
}
