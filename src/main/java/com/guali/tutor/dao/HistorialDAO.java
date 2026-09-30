package com.guali.tutor.dao;

import com.guali.tutor.db.ConexionBD;
import com.guali.tutor.model.EstadoHistorial;
import com.guali.tutor.model.RegistroHistorial;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a la tabla `historial`. Cada fila es un intento (una apertura de
 * lección). El estado y la hora de apertura los asigna MariaDB por sus valores
 * DEFAULT ('Pendiente' y CURRENT_TIMESTAMP).
 */
public class HistorialDAO {

    private final ConexionBD conexionBD;

    public HistorialDAO(ConexionBD conexionBD) {
        this.conexionBD = conexionBD;
    }

    /**
     * Inserta un intento nuevo en estado Pendiente.
     *
     * @param leccionId      id de la lección en la tabla `lecciones`, o {@code null}
     *                       para lecciones locales (que no existen en la BD).
     * @param nombreLeccion  copia del nombre de la lección.
     * @return el id generado para el nuevo registro.
     */
    public int insertarIntento(Integer leccionId, String nombreLeccion) throws SQLException {
        String sql = "INSERT INTO historial (leccion_id, nombre_leccion) VALUES (?, ?)";
        try (Connection conexion = conexionBD.obtenerConexion();
             PreparedStatement statement = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (leccionId == null) {
                statement.setNull(1, Types.INTEGER);
            } else {
                statement.setInt(1, leccionId);
            }
            statement.setString(2, nombreLeccion);
            statement.executeUpdate();
            try (ResultSet claves = statement.getGeneratedKeys()) {
                if (claves.next()) {
                    return claves.getInt(1);
                }
            }
            throw new SQLException("MariaDB no devolvió el id del registro de historial insertado.");
        }
    }

    /**
     * Marca el intento como Completada. Es idempotente: solo actualiza si la fila
     * todavía está en Pendiente.
     *
     * @return {@code true} si esta llamada actualizó realmente una fila (es decir,
     *         la fila existía y estaba Pendiente); {@code false} si no afectó
     *         ninguna (ya estaba Completada, o el id ya no existe en la tabla).
     */
    public boolean marcarCompletada(int id) throws SQLException {
        String sql = "UPDATE historial SET estado = ? WHERE id = ? AND estado = ?";
        try (Connection conexion = conexionBD.obtenerConexion();
             PreparedStatement statement = conexion.prepareStatement(sql)) {
            statement.setString(1, EstadoHistorial.COMPLETADA.getTexto());
            statement.setInt(2, id);
            statement.setString(3, EstadoHistorial.PENDIENTE.getTexto());
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * @return todo el historial persistido, del intento más antiguo al más
     *         reciente (el id desempata las aperturas del mismo segundo).
     */
    public List<RegistroHistorial> listarTodos() throws SQLException {
        String sql = "SELECT id, nombre_leccion, hora_apertura, estado FROM historial ORDER BY hora_apertura, id";
        List<RegistroHistorial> resultado = new ArrayList<>();
        try (Connection conexion = conexionBD.obtenerConexion();
             PreparedStatement statement = conexion.prepareStatement(sql);
             ResultSet filas = statement.executeQuery()) {
            while (filas.next()) {
                resultado.add(new RegistroHistorial(
                        filas.getInt("id"),
                        filas.getString("nombre_leccion"),
                        filas.getTimestamp("hora_apertura").toLocalDateTime(),
                        EstadoHistorial.desdeTexto(filas.getString("estado"))));
            }
        }
        return resultado;
    }
}
