package com.guali.tutor.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Responsable únicamente de entregar conexiones JDBC a MariaDB.
 * No conoce lecciones, historial ni configuración del JDK: esas
 * responsabilidades vivirán en clases DAO que dependan de esta (Etapa 2+).
 */
public class ConexionBD {

    private static final String ARCHIVO_CONFIGURACION = "/db.properties";

    private final String url;
    private final String usuario;
    private final String password;

    public ConexionBD() {
        Properties propiedades = cargarPropiedades();
        String host = propiedades.getProperty("db.host");
        String puerto = propiedades.getProperty("db.port");
        String nombreEsquema = propiedades.getProperty("db.name");
        this.usuario = propiedades.getProperty("db.user");
        this.password = propiedades.getProperty("db.password");
        this.url = "jdbc:mariadb://" + host + ":" + puerto + "/" + nombreEsquema;
    }

    private Properties cargarPropiedades() {
        Properties propiedades = new Properties();
        try (InputStream in = getClass().getResourceAsStream(ARCHIVO_CONFIGURACION)) {
            if (in == null) {
                throw new IllegalStateException(
                        "No se encontró " + ARCHIVO_CONFIGURACION + " en el classpath (src/main/resources).");
            }
            propiedades.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return propiedades;
    }

    /**
     * Abre una nueva conexión a MariaDB. Quien la use es responsable de
     * cerrarla (idealmente con try-with-resources).
     */
    public Connection obtenerConexion() throws SQLException {
        return DriverManager.getConnection(url, usuario, password);
    }

    /**
     * Intenta abrir y cerrar una conexión de prueba.
     *
     * @throws SQLException con el motivo exacto si la conexión falla
     *         (host inválido, credenciales incorrectas, esquema inexistente, etc.)
     */
    public void probarConexion() throws SQLException {
        try (Connection conexion = obtenerConexion()) {
            // Si no lanzó excepción, la conexión se abrió y cerró correctamente.
        }
    }
}
