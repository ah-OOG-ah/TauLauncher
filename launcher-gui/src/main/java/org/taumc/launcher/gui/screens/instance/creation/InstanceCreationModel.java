package org.taumc.launcher.gui.screens.instance.creation;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.util.function.Supplier;
import javax.swing.ImageIcon;
import javax.swing.JPanel;

public class InstanceCreationModel {
    public enum Type {
        VANILLA("Vanilla", "taulauncher/icons/poly/minecraft.svg", VanillaPane::new),
        ZIP_IMPORT("Import from zip", "taulauncher/icons/poly/viewfolder.svg", JPanel::new),;

        public final String name;
        public final ImageIcon icon;
        public final Supplier<JPanel> panelSupplier;

        Type(String name, String iconName, Supplier<JPanel> panelSupplier) {
            this.name = name;
            icon = new FlatSVGIcon(iconName);
            this.panelSupplier = panelSupplier;
        }
    }

    public static final Type[] TYPES = Type.values();
}
