package com.cineconecta.platform.billing.internal.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Formulario de creación de un plan (RF-12.2). Las reglas replican las restricciones de
 * {@code plans} en PostgreSQL: nombre de hasta 60 caracteres, precio mayor que cero con dos
 * decimales ({@code NUMERIC(12,2)}) y máximo de perfiles entre 1 y 10.
 */
public class AdminPlanForm {

    @NotBlank(message = "Escribe un nombre para el plan")
    @Size(max = 60, message = "El nombre no puede superar 60 caracteres")
    private String name;

    @NotNull(message = "Indica el precio mensual")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero")
    @Digits(integer = 10, fraction = 2, message = "El precio admite hasta 10 dígitos enteros y 2 decimales")
    private BigDecimal price;

    @NotNull(message = "Indica el máximo de perfiles")
    @Min(value = 1, message = "El plan debe permitir al menos 1 perfil")
    @Max(value = 10, message = "El plan no puede permitir más de 10 perfiles")
    private Integer maxProfiles;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getMaxProfiles() {
        return maxProfiles;
    }

    public void setMaxProfiles(Integer maxProfiles) {
        this.maxProfiles = maxProfiles;
    }
}
