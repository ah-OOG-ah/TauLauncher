package org.taumc.launcher.core.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpUtils {
    public static InputStream obtainFile(HttpClient client, String url) throws IOException {
        HttpResponse<InputStream> response;
        try {
            response = client.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading");
        }
        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new IOException("Unexpected response status for " + url + ": " + response.statusCode());
        }
    }
}
