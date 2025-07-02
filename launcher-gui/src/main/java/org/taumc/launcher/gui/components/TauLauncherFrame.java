package org.taumc.launcher.gui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;

public class TauLauncherFrame extends JFrame {
    private static final String PREFIX = "windowBounds.";
    private final String windowBoundsKey;

    public TauLauncherFrame() {
        this.windowBoundsKey = this.getClass().getName();

        loadWindowBounds();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveWindowBounds();
            }
        });
    }

    private static Preferences windowBounds() {
        return Preferences.userRoot().node("/org/taumc/launcher/windowBounds");
    }

    private void loadWindowBounds() {
        Preferences prefs = windowBounds();
        int x = prefs.getInt(PREFIX + windowBoundsKey + ".x", Integer.MIN_VALUE);
        int y = prefs.getInt(PREFIX + windowBoundsKey + ".y", Integer.MIN_VALUE);
        int w = prefs.getInt(PREFIX + windowBoundsKey + ".w", 800);
        int h = prefs.getInt(PREFIX + windowBoundsKey + ".h", 600);

        setSize(w, h);

        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE) {
            // No saved position, center it
            setLocationRelativeTo(null);
        } else {
            setLocation(x, y);
        }
    }

    private void saveWindowBounds() {
        Preferences prefs = windowBounds();
        Rectangle bounds = getBounds();
        prefs.putInt(PREFIX + windowBoundsKey + ".x", bounds.x);
        prefs.putInt(PREFIX + windowBoundsKey + ".y", bounds.y);
        prefs.putInt(PREFIX + windowBoundsKey + ".w", bounds.width);
        prefs.putInt(PREFIX + windowBoundsKey + ".h", bounds.height);
    }
}
