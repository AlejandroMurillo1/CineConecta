package com.cineconecta.platform.viewing;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.viewing.internal.persistence.PlaybackSessionStore;
import com.cineconecta.platform.viewing.internal.service.PlaybackSession;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class ConsumptionApiIT {
    @Container
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:8");

    @Test
    void sumsWatchedMinutesAndCountsEachProfileOncePerPlanAndType() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = from.plusSeconds(3600);
        try (var client = MongoClients.create(mongo.getReplicaSetUrl())) {
            var store = new PlaybackSessionStore(new MongoTemplate(client, "viewing_test"));
            insert(store, from, 2, ContentType.MOVIE, 42, 30);
            insert(store, from.plusSeconds(1), 2, ContentType.MOVIE, 42, 45);
            insert(store, from.plusSeconds(2), 2, ContentType.MOVIE, 43, 15);
            insert(store, from.plusSeconds(3), 2, ContentType.SERIES, 42, 60);
            insert(store, from.plusSeconds(4), 3, ContentType.MOVIE, 42, 20);
            insert(store, from.minusSeconds(1), 2, ContentType.MOVIE, 44, 60);
            insert(store, to, 2, ContentType.MOVIE, 44, 60);

            ConsumptionApi api = store;
            assertEquals(List.of(
                    new ConsumptionRow(2, ContentType.MOVIE, new BigDecimal("1.50"), 2),
                    new ConsumptionRow(2, ContentType.SERIES, new BigDecimal("1.00"), 1),
                    new ConsumptionRow(3, ContentType.MOVIE, new BigDecimal("0.33"), 1)
            ), api.byPlanAndType(from, to));
            assertEquals(List.of(), api.byPlanAndType(to.plusSeconds(1), to.plusSeconds(2)));
            assertThrows(IllegalArgumentException.class, () -> api.byPlanAndType(to, from));
        }
    }

    private void insert(PlaybackSessionStore store, Instant started, int plan, ContentType type,
                        int profile, int seconds) {
        String episodeId = type == ContentType.SERIES ? java.util.UUID.randomUUID().toString() : null;
        store.insert(new PlaybackSession(new ObjectId().toHexString(), 7, profile, new ObjectId().toHexString(),
                episodeId, type, plan, 180, started, null, started.plusSeconds(seconds),
                seconds, seconds, false, 0, started.plusSeconds(seconds)));
    }
}
