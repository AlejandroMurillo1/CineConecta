package com.cineconecta.platform.billing.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "plans", schema = "cineconecta")
public class PlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer planId;

    @Column(nullable = false, length = 50, unique = true)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal price;

    @Column(nullable = false, updatable = false)
    private Integer maxProfiles;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 1)
    private String isActive = "Y";

    protected PlanEntity() {
    }

    public PlanEntity(String name, BigDecimal price, Integer maxProfiles, String isActive) {
        this.name = name;
        this.price = price;
        this.maxProfiles = maxProfiles;
        this.isActive = isActive;
    }

    public Integer getPlanId() {
        return planId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getMaxProfiles() {
        return maxProfiles;
    }

    public String getIsActive() {
        return isActive;
    }

    public boolean isActiveFlag() {
        return "Y".equalsIgnoreCase(isActive);
    }

    // Solo name e isActive se pueden modificar (GRANT UPDATE (name, isActive) a cine_app).
    public void rename(String newName) {
        this.name = newName;
    }

    public void setActive(boolean active) {
        this.isActive = active ? "Y" : "N";
    }
}
