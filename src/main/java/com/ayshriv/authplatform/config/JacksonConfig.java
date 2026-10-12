package com.ayshriv.authplatform.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.TimeZone;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        com.fasterxml.jackson.core.StreamReadConstraints constraints = com.fasterxml.jackson.core.StreamReadConstraints.builder()
                .maxNestingDepth(64)
                .maxStringLength(5_000_000)
                .build();

        com.fasterxml.jackson.core.JsonFactory factory = com.fasterxml.jackson.core.JsonFactory.builder()
                .streamReadConstraints(constraints)
                .build();

        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));
        return mapper;
    }
}
