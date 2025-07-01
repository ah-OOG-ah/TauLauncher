package org.taumc.launcher.core.mods.modpacksch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record PackListing(List<Integer> packs) {
}
