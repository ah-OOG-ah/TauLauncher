package org.taumc.launcher.core.reconciler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InstanceFileTest {
    @Test
    void testResolveRelative() {
        InstanceFile parent = InstanceFile.fromPathString("a/b/c");
        InstanceFile child = InstanceFile.fromPathString("d/e/f");
        assertEquals("a/b/c/d/e/f", parent.resolve(child).toString());
    }
}
