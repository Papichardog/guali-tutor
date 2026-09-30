package com.guali.tutor.model;

/**
 * Estado de un intento en el historial. El texto coincide con los valores del
 * ENUM de la columna `historial.estado` y es el que se muestra en el panel.
 */
public enum EstadoHistorial {
    PENDIENTE("Pendiente"),
    COMPLETADA("Completada");

    private final String texto;

    EstadoHistorial(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }

    /**
     * Convierte el valor almacenado en MariaDB. Como la columna es un ENUM solo
     * puede contener uno de los dos valores; ante cualquier otro se asume Pendiente.
     */
    public static EstadoHistorial desdeTexto(String texto) {
        for (EstadoHistorial estado : values()) {
            if (estado.texto.equalsIgnoreCase(texto)) {
                return estado;
            }
        }
        return PENDIENTE;
    }
}
