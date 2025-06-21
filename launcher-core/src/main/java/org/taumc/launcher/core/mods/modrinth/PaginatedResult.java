package org.taumc.launcher.core.mods.modrinth;


import java.util.List;

public record PaginatedResult<T>(List<T> hits, int offset, int limit, int total_hits) {
}

