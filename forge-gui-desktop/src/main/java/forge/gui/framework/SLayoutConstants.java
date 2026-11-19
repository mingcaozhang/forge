package forge.gui.framework;

/** 
 * Holds static constants used throughout layout.
 *
 * <br><br><i>(S at beginning of class name denotes a static factory.)</i>
 */
public class SLayoutConstants {
    /** Height of head area in drag panel. */
    public static final int HEAD_H = 20;

    /**
     * Height of head area when a cell opts into the slim (Arena-style) tab
     * strip. Only the tab chrome is reduced, so label text keeps exactly the
     * same vertical room it has at {@link #HEAD_H}.
     */
    public static final int HEAD_H_SLIM = 16;

    /** Thickness of resize border in drag panel. */
    public static final int BORDER_T = 5;
}
