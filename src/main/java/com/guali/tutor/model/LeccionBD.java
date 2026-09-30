package com.guali.tutor.model;

/**
 * Fila completa de la tabla `lecciones`, tal como está almacenada en MariaDB.
 */
public record LeccionBD(int id, String nombre, String contenidoMarkdown, String objetivo) {
}
