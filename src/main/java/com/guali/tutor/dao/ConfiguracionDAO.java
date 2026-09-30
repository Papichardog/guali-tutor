package com.guali.tutor.dao;

import com.guali.tutor.db.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Acceso a la tabla `configuracion`. Por ahora la aplicación solo guarda un
 * único valor: la ruta raíz del JDK. Esa fila vive siempre bajo el mismo id
 * fijo (la tabla no usa auto_increment), así que guardar es un upsert.
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
     * Inserta la fila de configuración si no existe, o actualiza el
     * jdk_path si ya existía.
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
}
