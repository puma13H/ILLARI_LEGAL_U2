package com.illarilegal.ordenlegal;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/** A rejected request is not evidence that the catalog is unavailable. */
public class CatalogoBusinessException extends ResponseStatusException {
    public CatalogoBusinessException(HttpStatusCode status, String reason) {
        super(status, reason);
    }
}
