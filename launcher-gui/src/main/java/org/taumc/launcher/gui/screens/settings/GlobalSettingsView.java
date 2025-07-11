package org.taumc.launcher.gui.screens.settings;

import org.taumc.launcher.gui.components.MultiSectionFrame;

public class GlobalSettingsView extends MultiSectionFrame {
    public GlobalSettingsView() {
        this.addPage("Accounts", AccountManagerPanel::new);
        this.setVisible(true);
    }
}
