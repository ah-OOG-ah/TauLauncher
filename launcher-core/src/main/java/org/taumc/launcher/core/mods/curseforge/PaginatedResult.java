package org.taumc.launcher.core.mods.curseforge;

import java.util.List;

public record PaginatedResult<T>(List<T> data, Pagination pagination) {
    public record Pagination(int index, int pageSize, int resultCount, int totalCount) {}
}
