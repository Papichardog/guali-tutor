package com.guali.tutor.model;

import java.util.Optional;

/**
 * Los cuatro tipos de objetivo que puede declarar el config.txt de una lección
 * (línea "objetivo=..."). Definen qué acción del estudiante completa la lección.
 */
public enum ObjetivoLeccion {
    COMPILAR_CON_ERROR,
    COMPILAR_EXITOSO,
    EJECUTAR_CON_ERROR,
    EJECUTAR_EXITOSO;

    /**
     * @return el objetivo correspondiente al texto, o vacío si el texto es
     *         {@code null} o no es uno de los cuatro valores permitidos.
     */
    public static Optional<ObjetivoLeccion> desdeTexto(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(texto.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
