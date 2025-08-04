package org.taumc.launcher.gui.screens.instance;

import static java.lang.Math.max;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.components.FlatTextField;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

public class InstanceCreateFrame extends JFrame {
    private static final Border STD_BORDER = new LineBorder(Color.DARK_GRAY, 2, true);
    private static final Border STD_MARGIN = new EmptyBorder(8, 8, 8, 8);
    private static final Border HALF_MARGIN = new EmptyBorder(4, 4, 4, 4);
    private static final Dimension STD_DIM_TEXTFIELD = new Dimension(320, 32);
    private static final Dimension STD_DIM_MAX_TEXTFIELD = new Dimension(Integer.MAX_VALUE, 32);

    public InstanceCreateFrame() {
        var content = boxPanel(BoxLayout.Y_AXIS);
        setContentPane(content);

        content.add(createIconNameGroupPane());
        content.add(createTypeConfigPane());
    }

    private static JPanel createIconNameGroupPane() {
        var iconNameGroupPane = boxPanel(BoxLayout.X_AXIS);
        iconNameGroupPane.setBorder(STD_BORDER);

        var icon = new ChangeIconButton(new FlatSVGIcon("taulauncher/icons/poly/new.svg"));
        iconNameGroupPane.add(wrapWithMargin(icon, STD_MARGIN));

        iconNameGroupPane.add(createNameGroupPane());

        return iconNameGroupPane;
    }

    private static JPanel createNameGroupPane() {
        var nameGroupPane = boxPanel(BoxLayout.Y_AXIS);
        nameGroupPane.setBorder(STD_MARGIN);

        var nameSelect = new FlatTextField();
        nameSelect.setLeadingComponent(new JLabel("Name: ", null, SwingConstants.LEFT));
        nameSelect.setMinimumSize(STD_DIM_TEXTFIELD);
        nameSelect.setPreferredSize(STD_DIM_TEXTFIELD);
        nameSelect.setMaximumSize(STD_DIM_MAX_TEXTFIELD);
        nameGroupPane.add(wrapWithMargin(nameSelect, HALF_MARGIN));

        var groupSelect = new FlatTextField();
        groupSelect.setLeadingComponent(new JLabel("Group: ", null, SwingConstants.LEFT));
        groupSelect.setMinimumSize(STD_DIM_TEXTFIELD);
        groupSelect.setPreferredSize(STD_DIM_TEXTFIELD);
        groupSelect.setMaximumSize(STD_DIM_MAX_TEXTFIELD);
        nameGroupPane.add(wrapWithMargin(groupSelect, HALF_MARGIN));

        return nameGroupPane;
    }

    private static JPanel createTypeConfigPane() {
        var typeConfigPane = boxPanel(BoxLayout.X_AXIS);
        typeConfigPane.setBorder(STD_BORDER);

        typeConfigPane.add(createTypePane());

        var configPane = boxPanel(BoxLayout.Y_AXIS);
        configPane.setBorder(STD_BORDER);
        configPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        typeConfigPane.add(configPane);

        return typeConfigPane;
    }

    private static JPanel createTypePane() {
        var typePane = boxPanel(BoxLayout.Y_AXIS);
        typePane.setBorder(STD_BORDER);

        var maxSize = new Dimension(-1, -1);
        for (var type : InstanceCreationModel.TYPES) {
            var button = new JToggleButton(type.name, new FlatSVGIcon(type.iconName));

            var bMax = button.getMaximumSize();
            maxSize.width = max(maxSize.width, bMax.width);
            maxSize.height = max(maxSize.height, bMax.height);

            typePane.add(button);
        }

        for (var button : typePane.getComponents()) {
            button.setMaximumSize(maxSize);
        }

        typePane.setMaximumSize(new Dimension(maxSize.width, Integer.MAX_VALUE));
        return typePane;
    }

    /**
     * Wraps the given component with a panel, then adds the border to the panel. Avoids issues with adding borders to
     * components not designed to take them. While you can technically pass another JPanel in here, you should generally
     * prefer adding the border directly.
     *
     * @param comp The component to be wrapped
     * @param margin The border to apply to the wrapper
     * @return A JPanel wrapping the component
     */
    private static JPanel wrapWithMargin(JComponent comp, Border margin) {
        var panel = boxPanel(BoxLayout.X_AXIS);
        panel.add(comp);
        panel.setBorder(margin);
        return panel;
    }

    private static JPanel boxPanel(int axis) {
        var panel = new JPanel();
        //noinspection MagicConstant # intellij, this is *not a constant*
        panel.setLayout(new BoxLayout(panel, axis));
        return panel;
    }
}
