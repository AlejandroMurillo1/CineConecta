package com.cineconecta.platform.accounts.internal.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Formulario de registro (RF-01). Clase con getters/setters para que funcione con th:field. */
public class RegisterForm {

    @NotBlank(message = "Escribe tu nombre completo")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String fullName;

    @NotBlank(message = "Escribe tu correo")
    @Email(message = "El correo no es válido")
    @Size(max = 254, message = "El correo no puede superar 254 caracteres")
    private String email;

    @NotBlank(message = "Escribe una contraseña")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String password;

    private String confirmPassword;

    @AssertTrue(message = "Las contraseñas no coinciden")
    public boolean isPasswordConfirmed() {
        return password == null || password.equals(confirmPassword);
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
