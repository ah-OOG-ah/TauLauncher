package org.taumc.launcher.core.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class HttpUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpUtils.class);

    public static InputStream obtainFile(HttpClient client, String url) throws IOException {
        HttpResponse<InputStream> response;
        try {
            response = client.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new IOException("Error reading file from " + url, e);
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

    public static CompletableFuture<InputStream> obtainFileAsync(HttpClient client, String url) {
        return client.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return response.body();
                    } else {
                        throw new IllegalStateException("Unexpected response status for " + url + ": " + response.statusCode());
                    }
                });
    }
}
