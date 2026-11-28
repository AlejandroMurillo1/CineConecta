package com.cineconecta.platform.accounts.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Perfil de visualización ({@code PROFILES}). Nunca se borra: se desactiva ({@code isActive = 'N'})
 * para conservar su actividad histórica. {@code cine_app} solo puede actualizar nombre,
 * clasificación y estado; {@code accountId} no cambia.
 */
@Entity
@Table(name = "profiles", schema = "cineconecta")
public class ProfileEntity {

    public static final int MAX_NAME_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer profileId;

    @Column(nullable = false, updatable = false)
    private Integer accountId;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(nullable = false)
    private Integer maxAgeRating;

    /** CHAR(1) 'Y'/'N' en la base; sin @JdbcTypeCode Hibernate lo valida como VARCHAR y falla. */
    @Column(nullable = false, length = 1)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String isActive;

    protected ProfileEntity() {
    }

    public ProfileEntity(int accountId, String name, int maxAgeRating) {
        this.accountId = accountId;
        this.name = name;
        this.maxAgeRating = maxAgeRating;
        this.isActive = "Y";
    }

    public Integer getProfileId() {
        return profileId;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    public Integer getMaxAgeRating() {
        return maxAgeRating;
    }

    public boolean isActive() {
        return "Y".equals(isActive);
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public void changeMaxAgeRating(int rating) {
        this.maxAgeRating = rating;
    }

    public void activate() {
        this.isActive = "Y";
    }

    public void deactivate() {
        this.isActive = "N";
    }
}
