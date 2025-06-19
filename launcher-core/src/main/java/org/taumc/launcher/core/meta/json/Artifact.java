package org.taumc.launcher.core.meta.json;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Artifact(Optional<String> sha1, OptionalInt size, OptionalInt totalSize, String url, @JsonAnySetter Map<String, Object> properties) {}
