package org.taumc.launcher.core.mods.curseforge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.taumc.launcher.core.meta.component.ComponentMetaInfo;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Mod(int id, int classId, String name, String slug, Links links, String summary, int downloadCount, List<Author> authors, Logo logo)
        implements org.taumc.launcher.core.mods.Mod, ComponentMetaInfo {
    @Override
    public String smallIconUrl() {
        if (logo != null) {
            return logo.thumbnailUrl;
        } else {
            return null;
        }
    }

    @Override
    public String modId() {
        return String.valueOf(id);
    }

    @Override
    public boolean isUserInstallable() {
        return true;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Links(String websiteUrl, String wikiUrl, String issuesUrl, String sourceUrl) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(int id, String name, String url, String avatarUrl) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Logo(int id, int modId, String title, String thumbnailUrl, String url) { }
}