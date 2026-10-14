package com.guali.tutor.dao;

import com.guali.tutor.db.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Acceso a la tabla `configuracion`, un único registro (id fijo, sin
 * auto_increment) con dos configuraciones independientes de la aplicación: la
 * ruta del JDK y la carpeta raíz de lecciones locales. Cada método toca
 * únicamente su propia columna: guardar una nunca sobrescribe la otra.
 */
public class ConfiguracionDAO {

    private static final int ID_CONFIGURACION_UNICA = 1;

    private final ConexionBD conexionBD;

    public ConfiguracionDAO(ConexionBD conexionBD) {
        this.conexionBD = conexionBD;
    }

    /**
     * @return la ruta del JDK guardada, o {@code null} si todavía no existe
     *         ninguna fila de configuración.
     */
    public String obtenerRutaJdk() throws SQLException {
        String sql = "SELECT jdk_path FROM configuracion WHERE id = ?";
        try (Connection conexion = conexionBD.obtenerConexion();
             PreparedStatement statement = conexion.prepareStatement(sql)) {
            statement.setInt(1, ID_CONFIGURACION_UNICA);
            try (ResultSet resultado = statement.executeQuery()) {
                return resultado.next() ? resultado.getString("jdk_path") : null;
            }
        }
    }

    /**
     * Inserta la fila de configuración si no existe, o actualiza solo jdk_path
     * si ya existía (no toca lecciones_root_path).
     */
    public void guardarRutaJdk(String rutaJdk) throws SQLException {
        String sql = "INSERT INTO configuracion (id, jdk_path) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE jdk_path = VALUES(jdk_path)";
        try (Connection conexion = conexionBD.obtenerConexion();
             PreparedStatement statement = conexion.prepareStatement(sql)) {
            statement.setInt(1, ID_CONFIGURACION_UNICA);
            statement.setString(2, rutaJdk);
            statement.executeUpdate();
        }
    }

    /**
     * @return la ruta de la carpeta raíz de lecciones locales guardada, o
     *         {@code null} si todavía no se ha configurado (o no existe fila).
     */
    public String obtenerRutaLecciones() throws SQLException {
        String sql = "SELECT lecciones_root_path FROM configuracion WHERE id = ?";
        try (Connection conexion = conexionBD.obtenerConexion();
             PreparedStatement statement = conexion.prepareStatement(sql)) {
            statement.setInt(1, ID_CONFIGURACION_UNICA);
            try (ResultSet resultado = statement.executeQuery()) {
                return resultado.next() ? resultado.getString("lecciones_root_path") : null;
            }
        }
    }

    /**
     * Inserta la fila de configuración si no existe, o actualiza solo
     * lecciones_root_path si ya existía (no toca jdk_path). Si la fila se crea
     * recién aquí, jdk_path queda en NULL (la columna es nullable), igual que
     * ya ocurre hoy con jdk_path cuando se configura antes que esta ruta.
     */
    public void guardarRutaLecciones(String ruta) throws SQLException {
        String sql = "INSERT INTO configuracion (id, lecciones_root_path) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE lecciones_root_path = VALUES(lecciones_root_path)";
        try (Connection conexion = conexionBD.obtenerConexion();
             PreparedStatement statement = conexion.prepareStatement(sql)) {
            statement.setInt(1, ID_CONFIGURACION_UNICA);
            statement.setString(2, ruta);
            statement.executeUpdate();
        }
    }
}
