package com.cineconecta.platform.catalog.exceptions;

import java.io.Serial;

public class StaleContentException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public StaleContentException(String contentId) {
        super("El contenido " + contentId + " fue modificado por otra persona. Recarga la página y vuelve a intentarlo.");
    }
}
