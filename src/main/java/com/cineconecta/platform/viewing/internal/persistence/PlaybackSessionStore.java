package com.cineconecta.platform.viewing.internal.persistence;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.viewing.internal.service.PlaybackSession;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Sorts.*;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

@Repository
public class PlaybackSessionStore {
    static final String COLLECTION = "playback_sessions";

    private final MongoTemplate mongo;

    public PlaybackSessionStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void insert(PlaybackSession session) {
        Document document = new Document("_id", new ObjectId(session.id()))
                .append("accountId", session.accountId())
                .append("profileId", session.profileId())
                .append("contentId", new ObjectId(session.contentId()))
                .append("contentType", storedType(session.type()))
                .append("planIdAtStart", session.planIdAtStart())
                .append("durationSeconds", session.durationSeconds())
                .append("startedAt", toDate(session.startedAt()))
                .append("playingSince", toDate(session.playingSince()))
                .append("endedAt", toDate(session.endedAt()))
                .append("watchedSeconds", session.watchedSeconds())
                .append("lastPosition", session.lastPosition())
                .append("finished", session.finished())
                .append("revision", session.revision())
                .append("lastActivityAt", toDate(session.lastActivityAt()));

        if (session.episodeId() != null) {
            // Los episodios se identifican con un UUID en texto (contents.contentDetails...episodeId).
            document.append("episodeId", session.episodeId());
        }
        collection().insertOne(document);
    }

    public Optional<PlaybackSession> find(String id, int accountId, int profileId) {
        Document document = collection().find(and(eq("_id", new ObjectId(id)),
                eq("accountId", accountId), eq("profileId", profileId))).first();
        return Optional.ofNullable(document).map(this::read);
    }

    /**
     * Títulos (y episodios) que el perfil ya vio: sesiones finalizadas o con la posición en el 90 %
     * o más de la duración. Es la misma regla que {@code PlaybackSession.completed()}.
     */
    public List<com.cineconecta.platform.catalog.PlaybackKey> completedKeys(int accountId, int profileId) {
        var reachedNinetyPercent = expr(new Document("$gte", List.of(
                new Document("$multiply", List.of("$lastPosition", 10)),
                new Document("$multiply", List.of("$durationSeconds", 9)))));
        return collection()
                .find(and(eq("accountId", accountId), eq("profileId", profileId),
                        or(eq("finished", true), reachedNinetyPercent)))
                .projection(new Document("contentId", 1).append("episodeId", 1))
                .map(document -> new com.cineconecta.platform.catalog.PlaybackKey(
                        document.getObjectId("contentId").toHexString(), document.getString("episodeId")))
                .into(new ArrayList<>());
    }

    public List<PlaybackSession> latestPerTitle(int accountId, int profileId) {
        return collection().aggregate(List.of(
                match(and(eq("profileId", profileId), eq("accountId", accountId))),
                sort(descending("startedAt")),
                group(new Document("content", "$contentId").append("episode", "$episodeId"),
                        com.mongodb.client.model.Accumulators.first("session", "$$ROOT")),
                replaceRoot("$session")
        )).map(this::read).into(new ArrayList<>());
    }

    public void closeAbandoned(Instant now, Instant cutoff) {
        var candidates = and(eq("endedAt", null), lte("startedAt", toDate(cutoff)),
                or(lte("lastActivityAt", toDate(cutoff)), exists("lastActivityAt", false)));
        for (Document document : collection().find(candidates)) {
            PlaybackSession current = read(document);
            PlaybackSession closed = current.closeIfAbandoned(now);
            if (closed != current) {
                saveAction(current, closed);
            }
        }
    }

    public boolean saveAction(PlaybackSession previous, PlaybackSession next) {
        // La revisión impide que dos peticiones sumen el mismo tramo de reproducción.
        var revision = previous.revision() == 0
                ? or(eq("revision", 0), exists("revision", false))
                : eq("revision", previous.revision());
        return collection().updateOne(and(eq("_id", new ObjectId(previous.id())),
                        eq("accountId", previous.accountId()), eq("profileId", previous.profileId()),
                        eq("endedAt", null), revision),
                combine(set("playingSince", toDate(next.playingSince())), set("endedAt", toDate(next.endedAt())),
                        set("watchedSeconds", next.watchedSeconds()), set("lastPosition", next.lastPosition()),
                        set("finished", next.finished()), set("revision", next.revision()),
                        set("lastActivityAt", toDate(next.lastActivityAt())))).getMatchedCount() == 1;
    }

    private PlaybackSession read(Document document) {
        String episode = document.getString("episodeId");
        return new PlaybackSession(document.getObjectId("_id").toHexString(),
                document.getInteger("accountId"), document.getInteger("profileId"),
                document.getObjectId("contentId").toHexString(),
                episode, readType(document.getString("contentType")),
                document.getInteger("planIdAtStart"), document.getInteger("durationSeconds"),
                toInstant(document.getDate("startedAt")), toInstant(document.getDate("playingSince")),
                toInstant(document.getDate("endedAt")), document.getInteger("watchedSeconds"),
                document.getInteger("lastPosition"), document.getBoolean("finished"),
                document.getInteger("revision", 0), lastActivity(document));
    }

    private static Instant lastActivity(Document document) {
        for (String field : List.of("lastActivityAt", "endedAt", "playingSince", "startedAt")) {
            Date date = document.getDate(field);
            if (date != null) {
                return date.toInstant();
            }
        }
        throw new IllegalArgumentException("La sesión no tiene fecha de inicio.");
    }

    private MongoCollection<Document> collection() {
        return mongo.getCollection(COLLECTION);
    }

    /** Mismos valores que contents.type y que el validador de playback_sessions: MOVIE, SERIES, DOCUMENTARY. */
    private static String storedType(ContentType type) {
        return type.name();
    }

    static ContentType readType(String type) {
        try {
            return ContentType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IllegalArgumentException("Tipo de contenido desconocido: " + type, error);
        }
    }

    private static Date toDate(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
