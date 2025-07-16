package org.taumc.launcher.gui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

public class MultiSectionFrame extends TauLauncherFrame {
    private final JList<String> sidebar;
    private final JPanel cardPanel;
    private final LinkedHashMap<String, JPanel> panels;
    private final LinkedHashMap<String, Supplier<JPanel>> panelConstructors;
    private final DefaultListModel<String> pageNames;

    protected MultiSectionFrame() {
        super();

        // Sidebar: list of settings pages
        this.pageNames = new DefaultListModel<>();
        this.sidebar = new JList<>(this.pageNames);
        sidebar.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sidebar.setSelectedIndex(0);

        JScrollPane sidebarScroll = new JScrollPane(sidebar);
        sidebarScroll.setPreferredSize(new Dimension(150, 0));

        // Main panel with CardLayout
        this.cardPanel = new JPanel(new CardLayout());
        this.panels = new LinkedHashMap<>();
        this.panelConstructors = new LinkedHashMap<>();

        // Change card on selection
        sidebar.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = sidebar.getSelectedValue();
                setCurrentPage(selected);
            }
        });

        // Split layout
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarScroll, cardPanel);
        splitPane.setDividerLocation(150);
        splitPane.setResizeWeight(0);

        this.add(splitPane);

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                super.windowClosed(e);
                panels.values().forEach(p -> p.getParent().remove(p));
            }
        });
    }


    protected void refreshCurrentPanel() {
        String page = this.sidebar.getSelectedValue();
        if (page == null) {
            page = this.panels.keySet().iterator().next();
        }
        JPanel prev = this.panels.remove(page);
        if (prev != null) {
            this.cardPanel.remove(prev);
        }
        JPanel newPanel = panelConstructors.get(page).get();
        this.cardPanel.add(newPanel, page);
        ((CardLayout)this.cardPanel.getLayout()).show(this.cardPanel, page);
    }

    protected void addPage(String page, Supplier<JPanel> panelSupplier) {
        var panel = panelSupplier.get();
        this.panelConstructors.put(page, panelSupplier);
        this.panels.put(page, panel);
        cardPanel.add(panel, page);
        this.pageNames.addElement(page);
    }


    public void setCurrentPage(String page) {
        if (!panels.containsKey(page)) {
            throw new IllegalArgumentException();
        }
        CardLayout cl = (CardLayout) cardPanel.getLayout();
        cl.show(cardPanel, page);
    }
}
