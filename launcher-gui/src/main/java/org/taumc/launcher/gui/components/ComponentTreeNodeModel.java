package org.taumc.launcher.gui.components;

import org.taumc.launcher.core.reconciler.tree.ComponentTreeNode;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

public class ComponentTreeNodeModel implements TreeModel {
    private final ComponentTreeNode root;
    private final List<TreeModelListener> listeners = new ArrayList<>();

    public ComponentTreeNodeModel(ComponentTreeNode root) {
        this.root = root;
    }

    @Override
    public Object getRoot() {
        return root;
    }

    @Override
    public Object getChild(Object parent, int index) {
        return ((ComponentTreeNode)parent).children().get(index);
    }

    @Override
    public int getChildCount(Object parent) {
        return ((ComponentTreeNode)parent).children().size();
    }

    @Override
    public boolean isLeaf(Object node) {
        return ((ComponentTreeNode)node).children().isEmpty();
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {

    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        return ((ComponentTreeNode)parent).children().indexOf((ComponentTreeNode)child);
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        listeners.add(l);
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        listeners.remove(l);
    }

    public void postEvent() {

    }
}
