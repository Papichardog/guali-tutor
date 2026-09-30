package com.guali.tutor.service;

import com.guali.tutor.dao.HistorialDAO;
import com.guali.tutor.model.ObjetivoLeccion;
import com.guali.tutor.model.RegistroHistorial;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Lleva el historial de intentos. Mantiene el "intento activo" (la fila creada al
 * abrir la lección que está en pantalla) y, tras cada Compilar/Ejecutar, decide si
 * el objetivo se alcanzó para marcarla Completada.
 *
 * <p>Reglas: cada apertura explícita crea una fila nueva; compilar o ejecutar
 * varias veces dentro de la misma apertura no crea filas; una fila Completada no
 * vuelve a Pendiente. Los errores de base de datos se propagan como
 * {@link SQLException} para que el llamador los informe sin interrumpir la
 * aplicación.
 */
public class HistorialService {

    private final HistorialDAO historialDAO;
    private final EvaluadorObjetivo evaluador;

    /** Id de la fila del intento activo; null si no hay ninguno (o falló crearlo). */
    private Integer intentoActivoId;

    /** Objetivo del intento activo; null si el config.txt declaró un valor desconocido. */
    private ObjetivoLeccion objetivoActivo;

    /** Si el intento activo ya se marcó Completada (confirmado por la BD). */
    private boolean intentoCompletado;

    public HistorialService(HistorialDAO historialDAO, EvaluadorObjetivo evaluador) {
        this.historialDAO = historialDAO;
        this.evaluador = evaluador;
    }

    /**
     * Registra una apertura de lección (fila nueva en estado Pendiente) y la
     * convierte en el intento activo. Si no se puede insertar, no queda ningún
     * intento activo: así un Compilar/Ejecutar posterior nunca actualiza la fila
     * de una lección anterior.
     *
     * @param leccionId     id en la tabla `lecciones`, o {@code null} si es local.
     * @param objetivoTexto texto del objetivo tal como viene del config.txt.
     * @return {@code true} si el objetivo es uno de los cuatro válidos; {@code false}
     *         si no lo es (el intento se registra igual, pero no podrá completarse).
     */
    public boolean iniciarIntento(Integer leccionId, String nombreLeccion, String objetivoTexto) throws SQLException {
        intentoActivoId = null;
        objetivoActivo = null;
        intentoCompletado = false;

        Optional<ObjetivoLeccion> objetivo = ObjetivoLeccion.desdeTexto(objetivoTexto);
        int id = historialDAO.insertarIntento(leccionId, nombreLeccion);

        intentoActivoId = id;
        objetivoActivo = objetivo.orElse(null);
        return objetivo.isPresent();
    }

    /**
     * Descarta el intento activo sin crear ninguna fila. Se usa para una carga de
     * lección que no cuenta como apertura explícita del usuario (la precarga
     * inicial al arrancar la aplicación): así un Compilar/Ejecutar posterior sobre
     * esa lección no actualiza, por accidente, la fila de un intento anterior.
     */
    public void descartarIntentoActivo() {
        intentoActivoId = null;
        objetivoActivo = null;
        intentoCompletado = false;
    }

    /**
     * Evalúa el resultado de un Compilar sobre el intento activo.
     *
     * @return {@code true} solo si esta acción completó el intento (la BD
     *         confirmó que la fila cambió a Completada).
     */
    public boolean registrarCompilacion(boolean compilacionExitosa) throws SQLException {
        if (!hayIntentoPendiente()) {
            return false;
        }
        return completarSi(evaluador.cumpleTrasCompilar(objetivoActivo, compilacionExitosa));
    }

    /**
     * Evalúa el resultado de un Ejecutar sobre el intento activo.
     *
     * @return {@code true} solo si esta acción completó el intento (la BD
     *         confirmó que la fila cambió a Completada).
     */
    public boolean registrarEjecucion(boolean timeoutAlcanzado, boolean ejecucionExitosa) throws SQLException {
        if (!hayIntentoPendiente()) {
            return false;
        }
        return completarSi(evaluador.cumpleTrasEjecutar(objetivoActivo, timeoutAlcanzado, ejecucionExitosa));
    }

    /**
     * @return todo el historial persistido, del más antiguo al más reciente.
     */
    public List<RegistroHistorial> obtenerHistorial() throws SQLException {
        return historialDAO.listarTodos();
    }

    private boolean hayIntentoPendiente() {
        return intentoActivoId != null && objetivoActivo != null && !intentoCompletado;
    }

    private boolean completarSi(boolean objetivoCumplido) throws SQLException {
        if (!objetivoCumplido) {
            return false;
        }
        boolean actualizada = historialDAO.marcarCompletada(intentoActivoId);
        // Solo se marca en memoria si el UPDATE afectó realmente una fila. Si no
        // (por ejemplo la fila ya no existe), la próxima acción que cumpla el
        // objetivo reintentará el UPDATE en vez de darlo por hecho silenciosamente.
        intentoCompletado = actualizada;
        return actualizada;
    }
}
