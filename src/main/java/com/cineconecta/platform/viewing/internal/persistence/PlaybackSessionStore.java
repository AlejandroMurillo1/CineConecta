package com.cineconecta.platform.viewing.internal.persistence;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.viewing.ConsumptionApi;
import com.cineconecta.platform.viewing.ConsumptionRow;
import com.cineconecta.platform.viewing.internal.service.PlaybackSession;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Sorts.*;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

@Repository
public class PlaybackSessionStore implements ConsumptionApi {
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
            document.append("episodeId", new ObjectId(session.episodeId()));
        }
        collection().insertOne(document);
    }

    public Optional<PlaybackSession> find(String id, int accountId, int profileId) {
        Document document = collection().find(and(eq("_id", new ObjectId(id)),
                eq("accountId", accountId), eq("profileId", profileId))).first();
        return Optional.ofNullable(document).map(this::read);
    }

    @Override
    public List<ConsumptionRow> byPlanAndType(Instant fromInclusive, Instant toExclusive) {
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("El inicio del período debe ser anterior al fin.");
        }

        // Agrupar primero por perfil permite contarlo una sola vez por plan y tipo.
        var planTypeAndProfile = new Document("planId", "$planIdAtStart").append("type", "$contentType")
                .append("profileId", "$profileId");
        return collection().aggregate(List.of(
                match(and(gte("startedAt", toDate(fromInclusive)), lt("startedAt", toDate(toExclusive)))),
                group(planTypeAndProfile, sum("seconds", "$watchedSeconds")),
                group(new Document("planId", "$_id.planId").append("type", "$_id.type"),
                        sum("seconds", "$seconds"), sum("distinctProfiles", 1)),
                sort(ascending("_id.planId", "_id.type"))
        )).map(document -> {
            Document key = document.get("_id", Document.class);
            long seconds = document.get("seconds", Number.class).longValue();
            BigDecimal minutes = BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            return new ConsumptionRow(key.getInteger("planId"), readType(key.getString("type")),
                    minutes, document.get("distinctProfiles", Number.class).longValue());
        }).into(new ArrayList<>());
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
        ObjectId episode = document.getObjectId("episodeId");
        return new PlaybackSession(document.getObjectId("_id").toHexString(),
                document.getInteger("accountId"), document.getInteger("profileId"),
                document.getObjectId("contentId").toHexString(),
                episode == null ? null : episode.toHexString(), readType(document.getString("contentType")),
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
