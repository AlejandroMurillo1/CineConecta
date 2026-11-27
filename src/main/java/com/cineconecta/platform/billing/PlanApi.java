package com.cineconecta.platform.billing;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * API pública del módulo billing para consultar catálogo de planes.
 */
public interface PlanApi {

    List<PlanSummaryView> findActivePlans();

    Map<Integer, String> namesByIds(Collection<Integer> planIds);
}
