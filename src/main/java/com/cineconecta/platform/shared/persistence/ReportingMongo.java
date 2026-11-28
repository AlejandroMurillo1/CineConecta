package com.cineconecta.platform.shared.persistence;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Conexión de solo lectura a MongoDB con el usuario {@code cineReport} (rol {@code read}), para los informes.
 *
 * <p>La aplicación opera con {@code cineApp}; los informes de consumo y analítica leen con
 * {@code cineReport}, que no puede escribir en ninguna colección. Es la contraparte de
 * {@link ReportingJdbc} en PostgreSQL.</p>
 *
 * <p>No se publica como bean {@code MongoTemplate} ni {@code MongoClient} a propósito: un segundo bean
 * de esos tipos desactivaría la autoconfiguración de Spring Boot para la conexión principal.</p>
 */
public final class ReportingMongo implements AutoCloseable {

    private final MongoClient client;
    private final MongoTemplate template;

    /** Envuelve un cliente ya creado (en las pruebas, por ejemplo). Al cerrar, cierra el cliente. */
    public ReportingMongo(MongoClient client, String database) {
        this.client = client;
        this.template = new MongoTemplate(client, database);
    }

    /**
     * Toma servidor y opciones de {@code uri} (la misma de la conexión principal) y reemplaza
     * solo el usuario y la contraseña. La base de autenticación es la de la URI o, si no trae, {@code database}.
     */
    public static ReportingMongo connect(String uri, String database, String username, String password,
                                         long timeoutMs) {
        ConnectionString connection = new ConnectionString(uri);
        String authSource = connection.getCredential() != null
                ? connection.getCredential().getSource()
                : database;
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connection)
                .credential(MongoCredential.createCredential(username, authSource, password.toCharArray()))
                // RNF-07: igual que la conexión principal, fallar pronto si MongoDB no responde (página 503).
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(timeoutMs, TimeUnit.MILLISECONDS))
                .applyToSocketSettings(socket -> socket.connectTimeout((int) timeoutMs, TimeUnit.MILLISECONDS))
                .build();
        return new ReportingMongo(MongoClients.create(settings), database);
    }

    public MongoTemplate template() {
        return template;
    }

    @Override
    public void close() {
        client.close();
    }
}
