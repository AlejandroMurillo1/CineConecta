package com.cineconecta.platform.viewing;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.viewing.internal.persistence.PlaybackSessionStore;
import com.cineconecta.platform.viewing.internal.service.PlaybackSession;
import com.mongodb.client.MongoClients;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.MountableFile;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Las sesiones que escribe {@link PlaybackSessionStore} cumplen el validador real de
 * {@code playback_sessions} (db/mongo/init/01_collections.js). Sin esta prueba, un cambio en el
 * formato del documento solo se descubre al reproducir en la aplicación.
 */
@Testcontainers
class PlaybackSessionValidatorIT {

    @Container
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:8");

    @BeforeAll
    static void createCollectionsWithValidators() throws Exception {
        mongo.copyFileToContainer(MountableFile.forHostPath("db/mongo/init/01_collections.js"), "/tmp/01_collections.js");
        ExecResult result = mongo.execInContainer("mongosh", "--quiet", "cineconecta", "/tmp/01_collections.js");
        assertEquals(0, result.getExitCode(), result.getStdout() + result.getStderr());
    }

    @Test
    void movieAndEpisodeSessionsPassTheValidatorAndReadBack() {
        Instant started = Instant.parse("2026-11-27T20:00:00Z");
        String episodeId = UUID.randomUUID().toString();
        try (var client = MongoClients.create(mongo.getReplicaSetUrl())) {
            var store = new PlaybackSessionStore(new MongoTemplate(client, "cineconecta"));

            var movie = session(ContentType.MOVIE, null, started);
            var episode = session(ContentType.SERIES, episodeId, started);
            store.insert(movie);
            store.insert(episode);

            assertEquals(ContentType.MOVIE, store.find(movie.id(), 7, 42).orElseThrow().type());
            var readEpisode = store.find(episode.id(), 7, 42).orElseThrow();
            assertEquals(ContentType.SERIES, readEpisode.type());
            assertEquals(episodeId, readEpisode.episodeId());
        }
    }

    @Test
    void completedKeysAreFinishedSessionsOrAtLeastNinetyPercent() {
        Instant started = Instant.parse("2026-11-27T21:00:00Z");
        try (var client = MongoClients.create(mongo.getReplicaSetUrl())) {
            var store = new PlaybackSessionStore(new MongoTemplate(client, "cineconecta"));
            String finishedMovie = new ObjectId().toHexString();
            String ninetyPercent = new ObjectId().toHexString();
            String halfway = new ObjectId().toHexString();
            // Otro perfil (99) para comprobar que solo cuenta lo del perfil pedido.
            store.insert(watchedAt(finishedMovie, 10, true, 43, started));
            store.insert(watchedAt(ninetyPercent, 162, false, 43, started));
            store.insert(watchedAt(halfway, 90, false, 43, started));
            store.insert(watchedAt(new ObjectId().toHexString(), 180, true, 99, started));

            var keys = store.completedKeys(8, 43).stream().map(key -> key.contentId()).sorted().toList();

            assertEquals(java.util.stream.Stream.of(finishedMovie, ninetyPercent).sorted().toList(), keys);
        }
    }

    private static PlaybackSession watchedAt(String contentId, int position, boolean finished, int profileId,
                                             Instant started) {
        return new PlaybackSession(new ObjectId().toHexString(), 8, profileId, contentId, null, ContentType.MOVIE,
                2, 180, started, null, started.plusSeconds(position), position, position, finished, 1, started);
    }

    private static PlaybackSession session(ContentType type, String episodeId, Instant started) {
        return new PlaybackSession(new ObjectId().toHexString(), 7, 42, new ObjectId().toHexString(),
                episodeId, type, 2, 180, started, started, null, 0, 0, false, 0, started);
    }
}
