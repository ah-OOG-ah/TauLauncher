package org.taumc.launcher.gui.screens.instance.creation;

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

public class Utils {
    static final Border STD_BORDER = new LineBorder(Color.DARK_GRAY, 2, true);
    static final Border THIN_BORDER = new LineBorder(Color.BLACK, 1, false);
    static final Border STD_MARGIN = new EmptyBorder(8, 8, 8, 8);
    static final Border HALF_MARGIN = new EmptyBorder(4, 4, 4, 4);
    static final Dimension STD_DIM_TEXTFIELD = new Dimension(320, 32);
    static final Dimension STD_DIM_MAX_TEXTFIELD = new Dimension(Integer.MAX_VALUE, 32);

    /**
     * Wraps the given component with a panel, then adds the border to the panel. Avoids issues with adding borders to
     * components not designed to take them. While you can technically pass another JPanel in here, you should generally
     * prefer adding the border directly.
     *
     * @param comp The component to be wrapped
     * @param margin The border to apply to the wrapper
     * @return A JPanel wrapping the component
     */
    static JPanel wrapWithMargin(JComponent comp, Border margin) {
        var panel = boxPanel(BoxLayout.X_AXIS);
        panel.add(comp);
        panel.setBorder(margin);
        return panel;
    }

    static JPanel boxPanel(int axis) {
        var panel = new JPanel();
        //noinspection MagicConstant # intellij, this is *not a constant*
        panel.setLayout(new BoxLayout(panel, axis));
        return panel;
    }
}
