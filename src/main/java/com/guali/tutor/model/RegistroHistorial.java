package com.guali.tutor.model;

import java.time.LocalDateTime;

/**
 * Una fila del historial: un intento (apertura de una lección). El nombre de la
 * lección es la copia guardada en `historial.nombre_leccion`, por lo que sigue
 * disponible aunque la lección se elimine de la tabla `lecciones`.
 */
public record RegistroHistorial(int id, String nombreLeccion, LocalDateTime horaApertura, EstadoHistorial estado) {
}
