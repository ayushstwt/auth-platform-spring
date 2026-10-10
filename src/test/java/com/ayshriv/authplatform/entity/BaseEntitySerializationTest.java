package com.ayshriv.authplatform.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ayshriv.authplatform.config.JacksonConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntitySerializationTest {

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    static class TestEntity extends BaseEntity {
        private String name;

        public TestEntity(Long id, String name) {
            super(id);
            this.name = name;
            setCreatedOn(Instant.parse("2026-01-01T12:00:00Z"));
            setUpdatedOn(Instant.parse("2026-01-01T12:00:00Z"));
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    @DisplayName("Should serialize entity without exposing active or deleted internal flags")
    void shouldNotExposeActiveOrDeletedInJson() throws Exception {
        TestEntity entity = new TestEntity(100L, "Sample Entity");
        entity.setActive(true);
        entity.setDeleted(false);

        String json = objectMapper.writeValueAsString(entity);

        assertThat(json).contains("\"id\":100");
        assertThat(json).contains("\"name\":\"Sample Entity\"");
        assertThat(json).contains("\"created_on\":\"2026-01-01T12:00:00Z\"");
        assertThat(json).contains("\"updated_on\":\"2026-01-01T12:00:00Z\"");

        assertThat(json).doesNotContain("\"active\"");
        assertThat(json).doesNotContain("\"isActive\"");
        assertThat(json).doesNotContain("\"deleted\"");
        assertThat(json).doesNotContain("\"isDeleted\"");
        assertThat(json).doesNotContain("\"is_active\"");
        assertThat(json).doesNotContain("\"is_deleted\"");
        assertThat(json).doesNotContain("\"new\"");
        assertThat(json).doesNotContain("\"isNew\"");
    }
}
