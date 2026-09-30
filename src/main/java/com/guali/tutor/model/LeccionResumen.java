package com.guali.tutor.model;

/**
 * Versión liviana de una lección almacenada (sin el contenido markdown), pensada
 * para poblar las listas de selección. Su toString() es el texto que ve el usuario.
 */
public record LeccionResumen(int id, String nombre, String objetivo) {

    @Override
    public String toString() {
        return nombre + " (" + objetivo + ")";
    }
}
