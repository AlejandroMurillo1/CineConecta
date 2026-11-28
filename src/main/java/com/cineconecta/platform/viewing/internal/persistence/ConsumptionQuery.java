package com.cineconecta.platform.viewing.internal.persistence;

import com.cineconecta.platform.shared.persistence.ReportingMongo;
import com.cineconecta.platform.viewing.ConsumptionApi;
import com.cineconecta.platform.viewing.ConsumptionRow;
import org.bson.Document;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Aggregates.sort;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Sorts.ascending;

/**
 * Consumo por plan y tipo para el informe RF-34. Lee {@code playback_sessions} con el usuario
 * {@code cineReport} (solo lectura), no con {@code cineApp}: la conexión de informes no puede
 * modificar nada aunque la consulta tuviera un error.
 */
@Repository
public class ConsumptionQuery implements ConsumptionApi {

    private final ReportingMongo reportingMongo;

    public ConsumptionQuery(ReportingMongo reportingMongo) {
        this.reportingMongo = reportingMongo;
    }

    @Override
    public List<ConsumptionRow> byPlanAndType(Instant fromInclusive, Instant toExclusive) {
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("El inicio del período debe ser anterior al fin.");
        }

        // Agrupar primero por perfil permite contarlo una sola vez por plan y tipo.
        var planTypeAndProfile = new Document("planId", "$planIdAtStart").append("type", "$contentType")
                .append("profileId", "$profileId");
        return reportingMongo.template().getCollection(PlaybackSessionStore.COLLECTION).aggregate(List.of(
                match(and(gte("startedAt", Date.from(fromInclusive)), lt("startedAt", Date.from(toExclusive)))),
                group(planTypeAndProfile, sum("seconds", "$watchedSeconds")),
                group(new Document("planId", "$_id.planId").append("type", "$_id.type"),
                        sum("seconds", "$seconds"), sum("distinctProfiles", 1)),
                sort(ascending("_id.planId", "_id.type"))
        )).map(document -> {
            Document key = document.get("_id", Document.class);
            long seconds = document.get("seconds", Number.class).longValue();
            BigDecimal minutes = BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            return new ConsumptionRow(key.getInteger("planId"), PlaybackSessionStore.readType(key.getString("type")),
                    minutes, document.get("distinctProfiles", Number.class).longValue());
        }).into(new ArrayList<>());
    }
}
