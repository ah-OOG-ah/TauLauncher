package org.taumc.launcher.core.http;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class URIBuilder {
    private final String apiBase;

    public URIBuilder(String apiBase) {
        this.apiBase = apiBase;
    }

    public URI buildUri(String baseUrl) {
        return URI.create(apiBase + baseUrl);
    }

    public URI buildUri(String baseUrl, Map<String, ?> params) {
        String query = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
                        URLEncoder.encode(e.getValue().toString(), StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");

        return URI.create(apiBase + baseUrl + (baseUrl.contains("?") ? "&" : "?") + query);
    }
}
