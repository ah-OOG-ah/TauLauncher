package org.taumc.launcher.core.mods.curseforge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FingerprintMatches(boolean isCacheBuilt, List<FingerprintMatch> exactMatches, List<Long> exactFingerprints, List<FingerprintMatch> partialMatches) {
    public record FingerprintMatch(int id, File file, List<File> latestFiles) {}
}
