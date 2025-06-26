package org.taumc.launcher.core.mods.modrinth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.taumc.launcher.core.mods.Mod;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Project(String slug, String title, String description, String project_type, int downloads, String icon_url, String project_id, List<String> versions) implements Mod {
    @Override
    public String name() {
        return title;
    }

    @Override
    public String modId() {
        return project_id;
    }

    @Override
    public String summary() {
        return description;
    }

    @Override
    public String smallIconUrl() {
        return icon_url;
    }

    @Override
    public int downloadCount() {
        return downloads;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return Objects.equals(project_id, project.project_id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(project_id);
    }
}
