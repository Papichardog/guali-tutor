package com.guali.tutor.dao;

import com.guali.tutor.db.ConexionBD;
import com.guali.tutor.model.LeccionBD;
import com.guali.tutor.model.LeccionResumen;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a la tabla `lecciones`. No toca la tabla `historial`: al eliminar una
 * lección, el FOREIGN KEY ... ON DELETE SET NULL definido en el esquema conserva
 * los registros del historial (solo pierden el leccion_id; nombre_leccion queda).
 */
public class LeccionDAO {

    /** Código de error de MariaDB para "Duplicate entry" (violación de UNIQUE). */
    private static final int CODIGO_ERROR_ENTRADA_DUPLICADA = 1062;

    private final ConexionBD conexionBD;

    public LeccionDAO(ConexionBD conexionBD) {
        this.conexionBD = conexionBD;
    }

    /**
     * Inserta una lección nueva. Nunca sobrescribe: si ya existe una lección con
     * ese nombre (UNIQUE sobre `nombre`), no modifica nada.
     *
     * @return {@code true} si se insertó; {@code false} si ya existía una lección
     *         con ese nombre.
     */
    public boolean insertar(String nombre, String contenidoMarkdown, String objetivo) throws SQLException {
        String sql = "INSERT INTO lecciones (nombre, contenido_markdown, objetivo) VALUES (?, ?, ?)";
        try (Connection conexion = conexionBD.obtenerConexion();
             PreparedStatement statement = conexion.prepareStatement(sql)) {
            statement.setString(1, nombre);
            statement.setString(2, contenidoMarkdown);
            statement.setString(3, objetivo);
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() == CODIGO_ERROR_ENTRADA_DUPLICADA) {
                return false;
            }
            throw e;
        }
    }

    /**
     * @return todas las lecciones almacenadas, sin su contenido markdown, en
     *         orden alfabético por nombre.
     */
    public List<LeccionResumen> listarResumen() throws SQLException {
        String sql = "SELECT id, nombre, objetivo FROM lecciones ORDER BY nombre";
        List<LeccionResumen> resultado = new ArrayList<>();
        try (Connection conexion = conexionBD.obtenerConexion();
             PreparedStatement statement = conexion.prepareStatement(sql);
             ResultSet filas = statement.executeQuery()) {
            while (filas.next()) {
                resultado.add(new LeccionResumen(
                        filas.getInt("id"),
                        filas.getString("nombre"),
                        filas.getString("objetivo")));
            }
        }
        return resultado;
    }

    /**
     * @return la lección completa, o {@code null} si no existe una con ese id.
     */
    public LeccionBD obtenerPorId(int id) throws SQLException {
        String sql = "SELECT id, nombre, contenido_markdown, objetivo FROM lecciones WHERE id = ?";
        try (Connection conexion = conexionBD.obtenerConexion();
             PreparedStatement statement = conexion.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet fila = statement.executeQuery()) {
                if (!fila.next()) {
                    return null;
                }
                return new LeccionBD(
                        fila.getInt("id"),
                        fila.getString("nombre"),
                        fila.getString("contenido_markdown"),
                        fila.getString("objetivo"));
            }
        }
    }

    /**
     * @return {@code true} si se eliminó una fila; {@code false} si no existía.
     */
    public boolean eliminarPorId(int id) throws SQLException {
        String sql = "DELETE FROM lecciones WHERE id = ?";
        try (Connection conexion = conexionBD.obtenerConexion();
             PreparedStatement statement = conexion.prepareStatement(sql)) {
            statement.setInt(1, id);
            return statement.executeUpdate() > 0;
        }
    }
}
