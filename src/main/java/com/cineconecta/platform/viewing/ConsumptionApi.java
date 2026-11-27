package com.cineconecta.platform.viewing;

import java.time.Instant;
import java.util.List;

/** Consumo de las sesiones iniciadas en el período [inicio, fin). */
public interface ConsumptionApi {
    List<ConsumptionRow> byPlanAndType(Instant fromInclusive, Instant toExclusive);
}
