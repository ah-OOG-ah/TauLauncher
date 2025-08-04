package org.taumc.launcher.gui.screens.instance;

public class InstanceCreationModel {
    public enum Type {
        VANILLA("Vanilla", "taulauncher/icons/poly/minecraft.svg"),
        ZIP_IMPORT("Import from zip", "taulauncher/icons/poly/viewfolder.svg"),;

        public final String name;
        public final String iconName;

        Type(String name, String iconName) {
            this.name = name;
            this.iconName = iconName;
        }
    }

    public static final Type[] TYPES = Type.values();
}
