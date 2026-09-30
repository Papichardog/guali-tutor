package com.guali.tutor.service;

import com.guali.tutor.model.ObjetivoLeccion;

/**
 * Decide si una acción del estudiante cumple el objetivo de la lección. Es lógica
 * pura: recibe solo booleanos (no conoce guali-dev ni la base de datos), así que
 * puede probarse de forma aislada.
 */
public class EvaluadorObjetivo {

    /**
     * Evalúa el resultado de presionar Compilar. Los objetivos de ejecución
     * nunca se completan compilando.
     */
    public boolean cumpleTrasCompilar(ObjetivoLeccion objetivo, boolean compilacionExitosa) {
        return switch (objetivo) {
            case COMPILAR_EXITOSO -> compilacionExitosa;
            case COMPILAR_CON_ERROR -> !compilacionExitosa;
            case EJECUTAR_CON_ERROR, EJECUTAR_EXITOSO -> false;
        };
    }

    /**
     * Evalúa el resultado de presionar Ejecutar. Los objetivos de compilación
     * nunca se completan ejecutando. Un timeout no es ni éxito ni "error de
     * ejecución": no completa ninguno de los dos objetivos de ejecución.
     */
    public boolean cumpleTrasEjecutar(ObjetivoLeccion objetivo, boolean timeoutAlcanzado, boolean ejecucionExitosa) {
        return switch (objetivo) {
            case EJECUTAR_EXITOSO -> !timeoutAlcanzado && ejecucionExitosa;
            case EJECUTAR_CON_ERROR -> !timeoutAlcanzado && !ejecucionExitosa;
            case COMPILAR_CON_ERROR, COMPILAR_EXITOSO -> false;
        };
    }
}
