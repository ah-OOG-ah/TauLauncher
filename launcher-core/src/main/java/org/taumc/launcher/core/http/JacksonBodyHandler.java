package org.taumc.launcher.core.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;

public class JacksonBodyHandler<T> implements BodyHandler<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JacksonBodyHandler.class);

    private final ObjectMapper objectMapper;
    private final TypeReference<T> typeReference;

    public JacksonBodyHandler(TypeReference<T> typeReference, ObjectMapper objectMapper) {
        this.typeReference = typeReference;
        this.objectMapper = objectMapper;
    }

    @Override
    public BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {
        var responseContents = BodySubscribers.ofString(StandardCharsets.UTF_8);
        if (responseInfo.statusCode() != 200) {
            return BodySubscribers.mapping(responseContents, str -> {
                throw new RuntimeException("Unexpected status code: " + responseInfo.statusCode());
            });
        }
        return BodySubscribers.mapping(
                responseContents,
                str -> {
                    try {
                        return objectMapper.readValue(str, typeReference);
                    } catch (Exception e) {
                        LOGGER.error("Unexpected response", e);
                        throw new RuntimeException("Failed to deserialize response", e);
                    }
                }
        );
    }
}