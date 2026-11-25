package com.cineconecta.platform.shared;

import java.util.OptionalInt;

/**
 * Provee el identificador de la cuenta de cliente autenticada en el contexto actual.
 */
public interface CurrentAccountProvider {

    OptionalInt currentAccountId();

    default int requireCurrentAccountId() {
        return currentAccountId().orElseThrow(
                () -> new IllegalStateException("No hay una cuenta autenticada en la sesión actual")
        );
    }
}
