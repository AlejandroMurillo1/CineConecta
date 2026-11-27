package com.cineconecta.platform.catalog;

/**
 * Public catalog port for the authenticated viewer's age ceiling.
 * The accounts module supplies the production adapter using its own profile API.
 */
public interface ViewerContext {
    int maxAgeRating();
}
