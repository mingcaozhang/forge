package forge.toolbox.special;

import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import forge.game.phase.PhaseType;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinColor;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

public class PhaseIndicator extends JPanel {
    private static final long serialVersionUID = -863730022835609252L;

    private static final String CONSTRAINTS = "w 94%!, h 7.2%, gaptop 1%, gapleft 3%";

    private final Map<PhaseType, PhaseLabel> phaseLabels = new EnumMap<>(PhaseType.class);
    /** Labels in visual (top-to-bottom) order — used to identify the pill's rounded ends. */
    private final List<PhaseLabel> orderedLabels = new ArrayList<>();

    /**
     * Arena-style pill look: merges the per-phase chips into one unified
     * capsule, driven by the existing {@code FPref.UI_UNIFIED_PHASE_LABEL}.
     * Read once at construction (guarded so startup never breaks);
     * {@code false} keeps the classic separated-chip look.
     */
    private final boolean unifiedPhasePill = readUnifiedPhasePref();

    public PhaseIndicator() {
        this.setOpaque(false);
        // The unified capsule paints its own chrome, so give the segments a little side room.
        this.setLayout(new MigLayout(unifiedPhasePill
                ? "insets 2 4 3 4, gap 0, wrap" : "insets 0 0 1% 0, gap 0, wrap"));
        addPhaseLabel("UP", PhaseType.UPKEEP);
        addPhaseLabel("DR", PhaseType.DRAW);
        addPhaseLabel("M1", PhaseType.MAIN1);
        addPhaseLabel("BC", PhaseType.COMBAT_BEGIN);
        addPhaseLabel("DA", PhaseType.COMBAT_DECLARE_ATTACKERS);
        addPhaseLabel("DB", PhaseType.COMBAT_DECLARE_BLOCKERS);
        addPhaseLabel("FS", PhaseType.COMBAT_FIRST_STRIKE_DAMAGE);
        addPhaseLabel("CD", PhaseType.COMBAT_DAMAGE);
        addPhaseLabel("EC", PhaseType.COMBAT_END);
        addPhaseLabel("M2", PhaseType.MAIN2);
        addPhaseLabel("ET", PhaseType.END_OF_TURN);
        addPhaseLabel("CL", PhaseType.CLEANUP);
        applyPillEdges();
    }

    /** Tells the end segments (where the capsule is rounded) apart from the middle ones. */
    private void applyPillEdges() {
        if (!unifiedPhasePill) {
            return;
        }
        for (int i = 0; i < orderedLabels.size(); i++) {
            orderedLabels.get(i).setPillEdge(i == 0 || i == orderedLabels.size() - 1);
        }
    }

    private void addPhaseLabel(String caption, PhaseType phaseType) {
        PhaseLabel lbl = new PhaseLabel(caption);
        lbl.setUnified(unifiedPhasePill);
        orderedLabels.add(lbl);
        lbl.setToolTipText(Localizer.getInstance().getMessage("htmlPhaseTooltipFmt", phaseType.nameForUi));
        phaseLabels.put(phaseType, lbl);
        add(lbl, CONSTRAINTS);
    }

    private static boolean readUnifiedPhasePref() {
        try {
            return FModel.getPreferences().getPrefBoolean(FPref.UI_UNIFIED_PHASE_LABEL);
        } catch (Throwable t) {
            return true; // matches the FPref default; never break match startup
        }
    }

    @Override
    protected void paintComponent(final Graphics g) {
        super.paintComponent(g);
        if (unifiedPhasePill) {
            paintPillBackdrop(g);
        }
    }

    /** One rounded capsule (the "unified pill") drawn behind all phase segments. */
    private void paintPillBackdrop(final Graphics graphics) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 8 || h <= 8) {
            return;
        }
        final Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            final SkinColor theme = FSkin.getColor(FSkin.Colors.CLR_THEME);
            final int arc = w - 8;
            g2d.setPaint(new GradientPaint(0f, 2f, theme.stepColor(-8).getColor(),
                    0f, h - 2f, theme.stepColor(-16).getColor()));
            g2d.fillRoundRect(4, 2, w - 8, h - 4, arc, arc);
            FSkin.setGraphicsColor(g2d,
                    FSkin.getColor(FSkin.Colors.CLR_BORDERS).getContrastColor(25).alphaColor(200));
            g2d.drawRoundRect(4, 2, w - 8, h - 4, arc, arc);
        } finally {
            g2d.dispose();
        }
    }

    public PhaseLabel getLabelFor(final PhaseType phaseType) {
        return phaseLabels.get(phaseType);
    }

    public Iterable<PhaseLabel> allLabels() {
        return phaseLabels.values();
    }

    /** Resets all phase buttons to "inactive". "Enabled" state is preserved. */
    public void resetPhaseButtons() {
        for (PhaseLabel lbl : phaseLabels.values()) {
            lbl.setActive(false);
        }
    }
}
