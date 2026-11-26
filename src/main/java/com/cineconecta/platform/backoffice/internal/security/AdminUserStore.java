package com.cineconecta.platform.backoffice.internal.security;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;

/**
 * Lectura de administradores en MongoDB. Es de solo lectura a propósito: el usuario de la
 * aplicación ({@code cineApp}) solo tiene permiso {@code find} sobre {@code admin_users}.
 * La búsqueda por correo usa el índice único {@code ux_admin_users_email}.
 */
@Component
public class AdminUserStore {

    static final String COLLECTION = "admin_users";

    private final MongoTemplate mongo;

    public AdminUserStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** @param normalizedEmail correo ya recortado y en minúsculas, como lo exige el validador */
    public Optional<AdminAccount> findByEmail(String normalizedEmail) {
        Document doc = mongo.getCollection(COLLECTION).find(eq("email", normalizedEmail)).first();
        if (doc == null) {
            return Optional.empty();
        }
        return Optional.of(new AdminAccount(
                doc.getObjectId("_id").toHexString(),
                doc.getString("name"),
                doc.getString("email"),
                doc.getString("passwordHash")));
    }
}
