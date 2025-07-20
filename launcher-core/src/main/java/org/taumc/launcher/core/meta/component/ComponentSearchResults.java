package org.taumc.launcher.core.meta.component;

import java.util.List;

public record ComponentSearchResults(List<Result> results) {
    public record Result(String uid, ComponentMetaInfo metaInfo) {}
}
