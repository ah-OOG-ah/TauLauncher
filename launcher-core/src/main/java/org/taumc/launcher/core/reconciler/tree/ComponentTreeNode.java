package org.taumc.launcher.core.reconciler.tree;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class ComponentTreeNode implements Iterable<ComponentTreeNode>, Cloneable {
    @Getter
    @Nullable
    final ReconcilableGameComponent component;
    final List<ComponentTreeNode> children = new ArrayList<>();

    public ComponentTreeNode(@Nullable ReconcilableGameComponent component, List<ComponentTreeNode> children) {
        this.component = component;
        this.children.addAll(children);
    }

    public ComponentTreeNode(@Nullable ReconcilableGameComponent component) {
        this(null, List.of());
    }

    public void addChild(ComponentTreeNode child) {
        children.add(child);
    }

    public List<ComponentTreeNode> children() {
        return Collections.unmodifiableList(children);
    }

    public boolean isEmpty() {
        if (component != null) {
            return false;
        }
        return children.isEmpty() || children.stream().allMatch(ComponentTreeNode::isEmpty);
    }

    @Override
    public ComponentTreeNode clone() {
        var node = new ComponentTreeNode(this.component);
        for (var child : this.children) {
            node.children.add(child.clone());
        }
        return node;
    }

    @Override
    public @NotNull Iterator<ComponentTreeNode> iterator() {
        return new TreeNodeIterator(this);
    }

    public Map<String, ReconcilableGameComponent> buildIndex() {
        Map<String, ReconcilableGameComponent> map = new HashMap<>();
        for (var node : this) {
            if (node.getComponent() != null) {
                var comp = node.getComponent();
                comp.providedUids().forEach(uid -> map.put(uid, comp));
            }
        }
        return map;
    }

    private static class TreeNodeIterator implements Iterator<ComponentTreeNode> {
        private final Deque<ComponentTreeNode> stack = new ArrayDeque<>();

        public TreeNodeIterator(ComponentTreeNode root) {
            if (root != null) {
                stack.push(root);
            }
        }

        @Override
        public boolean hasNext() {
            return !stack.isEmpty();
        }

        @Override
        public ComponentTreeNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            ComponentTreeNode curr = stack.pop();

            // Push children in reverse to preserve left-to-right order
            List<ComponentTreeNode> kids = curr.children;
            for (int i = kids.size() - 1; i >= 0; i--) {
                stack.push(kids.get(i));
            }

            return curr;
        }
    }

    private static void buildTreeString(ComponentTreeNode node, String prefix, boolean isLast, StringBuilder sb) {
        sb.append(prefix)
                .append(isLast ? "\\-- " : "|-- ")
                .append(node.component != null ? node.component.toString() : "[none]")
                .append('\n');

        List<ComponentTreeNode> children = node.children;
        for (int i = 0; i < children.size(); i++) {
            boolean last = (i == children.size() - 1);
            String newPrefix = prefix + (isLast ? "    " : "|   ");
            buildTreeString(children.get(i), newPrefix, last, sb);
        }
    }

    public String toPrettyPrintedString() {
        StringBuilder sb = new StringBuilder();
        buildTreeString(this, "", true, sb);
        return sb.toString();
    }
}
