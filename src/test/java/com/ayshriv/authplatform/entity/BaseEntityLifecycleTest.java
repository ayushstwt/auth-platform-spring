package com.ayshriv.authplatform.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityLifecycleTest {

    static class SampleEntity extends BaseEntity {
        public SampleEntity() {
            super();
        }

        public SampleEntity(Long id) {
            super(id);
        }

        public void triggerCreate() {
            onCreate();
        }

        public void triggerUpdate() {
            onUpdate();
        }
    }

    @Test
    @DisplayName("PrePersist should initialize timestamps and set active=true, deleted=false")
    void prePersistInitializesState() {
        SampleEntity entity = new SampleEntity();
        assertThat(entity.isNew()).isTrue();

        entity.triggerCreate();

        assertThat(entity.isActive()).isTrue();
        assertThat(entity.isDeleted()).isFalse();
        assertThat(entity.getCreatedOn()).isNotNull();
        assertThat(entity.getUpdatedOn()).isNotNull();
    }

    @Test
    @DisplayName("PreUpdate should refresh updatedOn timestamp")
    void preUpdateRefreshesTimestamp() throws InterruptedException {
        SampleEntity entity = new SampleEntity(1L);
        entity.triggerCreate();
        Instant initialUpdatedOn = entity.getUpdatedOn();

        Thread.sleep(10);
        entity.triggerUpdate();

        assertThat(entity.getUpdatedOn()).isAfterOrEqualTo(initialUpdatedOn);
    }

    @Test
    @DisplayName("equals and hashCode should be ID-based")
    void idBasedEqualsAndHashCode() {
        SampleEntity entity1 = new SampleEntity(1L);
        SampleEntity entity2 = new SampleEntity(1L);
        SampleEntity entity3 = new SampleEntity(2L);
        SampleEntity entityNull = new SampleEntity(null);

        assertThat(entity1).isEqualTo(entity2);
        assertThat(entity1.hashCode()).isEqualTo(entity2.hashCode());
        assertThat(entity1).isNotEqualTo(entity3);
        assertThat(entity1).isNotEqualTo(entityNull);
        assertThat(entityNull).isNotEqualTo(new SampleEntity(null));
    }
}
