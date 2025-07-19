package org.taumc.launcher.core.reconciler;

import java.nio.file.Path;

public record InstanceFile(InstanceFile parent, String name) {
    public InstanceFile {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException();
        }
    }

    public InstanceFile(String name) {
        this(null, name);
    }

    public InstanceFile resolve(String name) {
        return new InstanceFile(this, name);
    }

    public static InstanceFile fromPath(Path nioPath) {
        InstanceFile path = null;
        for (int i = 0; i < nioPath.getNameCount(); i++) {
            String s = nioPath.getName(i).toString();
            if (s.isEmpty()) {
                continue;
            }
            path = new InstanceFile(path, s);
        }
        return path;
    }

    public static InstanceFile fromPathString(String pathStr) {
        String[] components = pathStr.split("/");
        InstanceFile path = null;
        for (String s : components) {
            if (s.isEmpty()) {
                continue;
            }
            path = new InstanceFile(path, s);
        }
        return path;
    }

    public Path toPath(Path base) {
        if (parent == null) {
            return base.resolve(name);
        } else {
            return parent.toPath(base).resolve(name);
        }
    }

    private void toStringHelper(StringBuilder sb) {
        if (parent != null) {
            parent.toStringHelper(sb);
            sb.append('/');
        }
        sb.append(name);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        toStringHelper(sb);
        return sb.toString();
    }
}
