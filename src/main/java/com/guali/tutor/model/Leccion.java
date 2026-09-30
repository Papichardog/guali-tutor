package com.guali.tutor.model;

import java.net.URL;

/**
 * Representa una lección ya cargada en memoria: su nombre (nombre de la
 * carpeta), el contenido de leccion.md, el objetivo declarado en config.txt,
 * y la URL base de su carpeta (para que las imágenes referenciadas dentro
 * del markdown se resuelvan correctamente).
 */
public record Leccion(String nombre, String contenidoMarkdown, String objetivo, URL carpetaBase) {
}
