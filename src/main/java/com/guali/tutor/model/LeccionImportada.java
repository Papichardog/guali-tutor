package com.guali.tutor.model;

/**
 * Lección leída desde una carpeta local elegida por el usuario, lista para
 * guardarse en MariaDB. No tiene id porque todavía no existe en la base de datos.
 */
public record LeccionImportada(String nombre, String contenidoMarkdown, String objetivo) {
}
