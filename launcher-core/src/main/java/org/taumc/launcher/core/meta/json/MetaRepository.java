package org.taumc.launcher.core.meta.json;

import java.io.Closeable;
import java.io.IOException;

public interface MetaRepository extends Closeable {
    RootIndex getRootIndex() throws IOException;
    PackageIndex getPackageIndex(String pkgName) throws IOException;
    Component getComponent(String pkgName, String version) throws IOException;
    void close();
}
