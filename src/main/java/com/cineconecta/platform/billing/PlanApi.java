package com.cineconecta.platform.billing;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * API pública del módulo billing para consultar catálogo de planes.
 */
public interface PlanApi {

    List<PlanSummaryView> findActivePlans();

    Map<Integer, String> namesByIds(Collection<Integer> planIds);

    // Gestión de planes por el administrador (RF-12.1 a RF-12.4)

    /** RF-12.1: todos los planes (activos e inactivos) con su número de suscripciones vigentes. */
    List<AdminPlanView> listPlansForAdmin();

    /** RF-12.2: crea un plan activo. */
    AdminPlanView createPlan(String name, BigDecimal price, int maxProfiles);

    /** RF-12.3: desactiva o reactiva un plan. */
    void setPlanActive(int planId, boolean active);

    /** RF-12.4: cambia el nombre de un plan. */
    void renamePlan(int planId, String newName);
}
