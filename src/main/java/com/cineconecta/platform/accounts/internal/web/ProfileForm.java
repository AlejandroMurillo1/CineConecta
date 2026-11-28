package com.cineconecta.platform.accounts.internal.web;

import com.cineconecta.platform.accounts.ProfileView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Formulario de crear/editar perfil (RF-05, RF-06). */
public class ProfileForm {

    @NotBlank(message = "Escribe un nombre para el perfil")
    @Size(max = 50, message = "El nombre no puede superar 50 caracteres")
    private String name;

    @NotNull(message = "Elige una clasificación")
    @Min(value = 0, message = "La clasificación debe estar entre 0 y 18")
    @Max(value = 18, message = "La clasificación debe estar entre 0 y 18")
    private Integer maxAgeRating;

    public static ProfileForm of(ProfileView profile) {
        ProfileForm form = new ProfileForm();
        form.setName(profile.name());
        form.setMaxAgeRating(profile.maxAgeRating());
        return form;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getMaxAgeRating() {
        return maxAgeRating;
    }

    public void setMaxAgeRating(Integer maxAgeRating) {
        this.maxAgeRating = maxAgeRating;
    }
}
