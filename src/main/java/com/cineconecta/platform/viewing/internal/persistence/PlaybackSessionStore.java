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
import java.util.Optional;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

@Repository
public class PlaybackSessionStore {
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
                .append("revision", session.revision());

        if (session.episodeId() != null) {
            document.append("episodeId", new ObjectId(session.episodeId()));
        }
        collection().insertOne(document);
    }

    public Optional<PlaybackSession> find(String id, int accountId, int profileId) {
        Document document = collection().find(and(eq("_id", new ObjectId(id)),
                eq("accountId", accountId), eq("profileId", profileId))).first();
        return Optional.ofNullable(document).map(this::read);
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
                        set("finished", next.finished()), set("revision", next.revision()))).getMatchedCount() == 1;
    }

    private PlaybackSession read(Document document) {
        ObjectId episode = document.getObjectId("episodeId");
        return new PlaybackSession(document.getObjectId("_id").toHexString(),
                document.getInteger("accountId"), document.getInteger("profileId"),
                document.getObjectId("contentId").toHexString(),
                episode == null ? null : episode.toHexString(), readType(document.getString("contentType")),
                document.getInteger("planIdAtStart"), document.getInteger("durationSeconds"),
                toInstant(document.getDate("startedAt")), toInstant(document.getDate("playingSince")),
                toInstant(document.getDate("endedAt")), document.getInteger("watchedSeconds"),
                document.getInteger("lastPosition"), document.getBoolean("finished"), document.getInteger("revision", 0));
    }

    private MongoCollection<Document> collection() {
        return mongo.getCollection("playback_sessions");
    }

    private static String storedType(ContentType type) {
        return switch (type) {
            case MOVIE -> "PELICULA";
            case DOCUMENTARY -> "DOCUMENTAL";
            case SERIES -> "SERIE";
        };
    }

    private static ContentType readType(String type) {
        return switch (type) {
            case "PELICULA" -> ContentType.MOVIE;
            case "DOCUMENTAL" -> ContentType.DOCUMENTARY;
            case "SERIE" -> ContentType.SERIES;
            default -> throw new IllegalArgumentException("Tipo de contenido desconocido: " + type);
        };
    }

    private static Date toDate(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
