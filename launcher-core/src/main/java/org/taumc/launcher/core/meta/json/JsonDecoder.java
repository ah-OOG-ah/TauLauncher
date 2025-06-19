package org.taumc.launcher.core.meta.json;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class JsonDecoder {
    public static ObjectMapper make() {
        return JsonMapper.builder()
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .addModule(new Jdk8Module())
                .addModule(new JavaTimeModule())
                .build();
    }
}
