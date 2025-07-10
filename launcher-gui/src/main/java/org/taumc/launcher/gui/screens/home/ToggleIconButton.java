package org.taumc.launcher.gui.screens.home;

import javax.swing.*;
import net.miginfocom.swing.MigLayout;
import org.taumc.launcher.gui.icon.IconRegistry;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ToggleIconButton extends JToggleButton {
    private final JLabel iconLabel = new JLabel();
    private final JLabel textLabel = new JLabel();

    private static final Color LABEL_BG_UNSELECTED = new Color(70, 70, 70); // light gray
    private static final Color LABEL_BG_SELECTED = new Color(100, 120, 250);   // light blue

    private final String text;

    private ImageIcon normalIcon, tintedIcon;

    public ToggleIconButton(ImageIcon icon, String text) {
        this.normalIcon = icon;
        this.text = text;
        this.tintedIcon = tintIcon(icon, Color.BLUE);

        setLayout(new MigLayout("wrap 1, align center, insets 5", "[center]", "[]5[]"));
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);

        iconLabel.setPreferredSize(new Dimension(IconRegistry.ICON_HEIGHT, IconRegistry.ICON_HEIGHT));
        iconLabel.setMinimumSize(iconLabel.getPreferredSize());
        iconLabel.setMaximumSize(iconLabel.getPreferredSize());
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(iconLabel, "growx, aligny top");

        textLabel.setHorizontalAlignment(SwingConstants.CENTER);
        textLabel.setVerticalAlignment(SwingConstants.TOP);
        textLabel.setOpaque(true);
        add(textLabel, "growx");

        // Listen to toggle state
        addChangeListener(e -> updateView());

        updateView();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension pref = super.getPreferredSize();
        return new Dimension(Math.min(pref.width, 100), pref.height);
    }

    private static ImageIcon tintIcon(ImageIcon originalIcon, Color color) {
        if (originalIcon == null) {
            return null;
        }

        Image img = originalIcon.getImage();

        // Create buffered image with transparency
        BufferedImage tinted = new BufferedImage(
                img.getWidth(null), img.getHeight(null),
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = tinted.createGraphics();
        // Draw original image
        g.drawImage(img, 0, 0, null);

        // Apply tint: multiply the color with the alpha
        g.setComposite(AlphaComposite.SrcAtop.derive(0.5f)); // 50% opacity
        g.setColor(color);
        g.fillRect(0, 0, img.getWidth(null), img.getHeight(null));
        g.dispose();

        return new ImageIcon(tinted);
    }

    private void updateView() {
        boolean selected = isSelected();
        iconLabel.setIcon(selected ? tintedIcon : normalIcon);
        textLabel.setText("<html><div style='text-align: center;'>" + text + "</div></html>");
        textLabel.setBackground(selected ? LABEL_BG_SELECTED : LABEL_BG_UNSELECTED);
    }
}