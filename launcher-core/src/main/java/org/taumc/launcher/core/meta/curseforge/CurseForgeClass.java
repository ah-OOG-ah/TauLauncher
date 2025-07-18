package org.taumc.launcher.core.meta.curseforge;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
@Accessors(fluent = true)
public enum CurseForgeClass {
    MODS(6, "mods"),
    RESOURCEPACKS(12, "resourcepacks"),
    WORLDS(17, "worlds"),
    SHADERPACKS(6552, "shaderpacks"),
    DATAPACKS(6945, "datapacks"),
    MODPACKS(4471, null);

    private final int classId;
    private final String subfolder;

    private static final Map<Integer, CurseForgeClass> BY_CLASS_ID = Arrays.stream(CurseForgeClass.values())
            .collect(Collectors.toUnmodifiableMap(CurseForgeClass::classId, Function.identity()));

    public static CurseForgeClass byClassId(int id) {
        var clz = BY_CLASS_ID.get(id);
        if (clz == null) {
            throw new IllegalArgumentException("Unknown class ID: " + id);
        }
        return clz;
    }
}
