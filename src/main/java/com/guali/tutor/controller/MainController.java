package com.guali.tutor.controller;

import com.guali.tutor.dao.ConfiguracionDAO;
import com.guali.tutor.dao.HistorialDAO;
import com.guali.tutor.dao.LeccionDAO;
import com.guali.tutor.db.ConexionBD;
import com.guali.tutor.form.MainForm;
import com.guali.tutor.model.Leccion;
import com.guali.tutor.model.LeccionBD;
import com.guali.tutor.model.LeccionImportada;
import com.guali.tutor.model.LeccionResumen;
import com.guali.tutor.service.EvaluadorObjetivo;
import com.guali.tutor.service.GualiDevService;
import com.guali.tutor.service.HistorialService;
import com.guali.tutor.service.LeccionService;
import com.guali.tutor.service.ValidadorJdk;

import edu.umg.guali.compiler.CompileResult;
import edu.umg.guali.compiler.ExecuteResult;

import javax.swing.JOptionPane;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;

public class MainController {

    private static final float TAMANO_FUENTE_MIN = 8f;
    private static final float TAMANO_FUENTE_MAX = 28f;
    private static final String LECCION_INICIAL = "leccion-01";

    private final MainForm view;
    private final ConexionBD conexionBD = new ConexionBD();
    private final ConfiguracionDAO configuracionDAO = new ConfiguracionDAO(conexionBD);
    private final LeccionDAO leccionDAO = new LeccionDAO(conexionBD);
    private final HistorialService historialService =
            new HistorialService(new HistorialDAO(conexionBD), new EvaluadorObjetivo());
    private final ValidadorJdk validadorJdk = new ValidadorJdk();
    private final GualiDevService gualiDevService = new GualiDevService();
    private final LeccionService leccionService = new LeccionService();
    private float tamanoFuente = 13f;

    /** Ruta del JDK ya validada, lista para usarse con GualiDev. */
    private String rutaJdkActual;

    /** Resultado de la última compilación; null si todavía no se ha compilado. */
    private CompileResult ultimoResultadoCompilacion;

    public MainController(MainForm view) {
        this.view = view;
        wireListeners();
        // La precarga inicial no es una apertura explícita del usuario, así que no
        // debe registrarse en el historial (ver abrirLeccionPorNombre).
        abrirLeccionPorNombre(LECCION_INICIAL, false);
        probarConexionBD();
        inicializarConfiguracionJdk();
    }

    private void wireListeners() {
        view.getAbrirLeccion().addActionListener(e -> abrirLeccion());
        view.getCerrarLeccion().addActionListener(e -> logAccionMenu("Cerrar Lección"));
        view.getImportarLeccionBD().addActionListener(e -> importarLeccion());
        view.getSeleccionarLeccionBD().addActionListener(e -> seleccionarLeccionDesdeBD());
        view.getEliminarLeccionBD().addActionListener(e -> eliminarLeccionDesdeBD());
        view.getConfiguracion().addActionListener(e -> abrirConfiguracion());
        view.getHistorial().addActionListener(e -> verHistorial());
        view.getAyuda().addActionListener(e -> mostrarAyuda());

        view.getBtnCompilar().addActionListener(e -> compilarCodigo());
        view.getBtnEjecutar().addActionListener(e -> ejecutarCodigo());

        view.getBtnZoomIn().addActionListener(e -> ajustarZoom(1f));
        view.getBtnZoomOut().addActionListener(e -> ajustarZoom(-1f));
    }

    // ------------------------------------------------------------------
    // Abrir Lección (lecciones empaquetadas como recursos)
    // ------------------------------------------------------------------

    /**
     * Lista las lecciones disponibles y, si hay al menos una, deja que el
     * usuario elija cuál abrir.
     */
    private void abrirLeccion() {
        List<String> disponibles;
        try {
            disponibles = leccionService.listarLeccionesDisponibles();
        } catch (IOException e) {
            String mensaje = "No se pudieron listar las lecciones disponibles: " + e.getMessage();
            logConsola("[Lección] " + mensaje);
            JOptionPane.showMessageDialog(view, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (disponibles.isEmpty()) {
            logConsola("[Lección] No se encontraron lecciones en /lecciones/.");
            JOptionPane.showMessageDialog(view,
                    "No se encontraron lecciones disponibles.",
                    "Sin lecciones",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String nombreSeleccionado = view.solicitarSeleccionLeccion(disponibles);
        if (nombreSeleccionado != null) {
            // Acción explícita del usuario: sí se registra en el historial.
            abrirLeccionPorNombre(nombreSeleccionado, true);
        }
    }

    /**
     * Carga una lección por nombre de carpeta y la muestra. Si falta
     * leccion.md/config.txt o config.txt es inválido, avisa y no cambia la
     * lección que estuviera abierta.
     *
     * @param registrarEnHistorial si esta apertura debe crear una fila en el
     *                             historial. Es {@code false} únicamente para la
     *                             precarga automática del constructor; cualquier
     *                             apertura pedida por el usuario (menú Abrir
     *                             Lección) pasa {@code true}.
     */
    private void abrirLeccionPorNombre(String nombreLeccion, boolean registrarEnHistorial) {
        try {
            Leccion leccion = leccionService.cargarLeccion(nombreLeccion);
            mostrarLeccion(leccion, registrarEnHistorial);
        } catch (IOException e) {
            String mensaje = "No se pudo abrir la lección '" + nombreLeccion + "': " + e.getMessage();
            logConsola("[Lección] " + mensaje);
            JOptionPane.showMessageDialog(view, mensaje, "Lección inválida", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Lección local (recursos): no existe en la tabla `lecciones`, así que su leccion_id es null. */
    private void mostrarLeccion(Leccion leccion, boolean registrarEnHistorial) {
        mostrarContenidoLeccion(leccion.nombre(), leccion.contenidoMarkdown(), leccion.objetivo(),
                leccion.carpetaBase(), null, registrarEnHistorial);
    }

    /**
     * Muestra el contenido de una lección en la interfaz, sin importar de dónde
     * vino (recursos o MariaDB). {@code carpetaBase} es la carpeta contra la que
     * se resuelven las imágenes; es {@code null} para lecciones de BD (que no
     * guardan imágenes), y en ese caso se limpia la base anterior para que no se
     * resuelvan imágenes contra la carpeta de la lección previa. {@code leccionId}
     * es el id en la tabla `lecciones`, o {@code null} para lecciones locales.
     * {@code registrarEnHistorial} distingue una apertura explícita del usuario
     * (crea fila) de la precarga automática al arrancar (no crea fila; ver
     * HistorialService.descartarIntentoActivo).
     */
    private void mostrarContenidoLeccion(String nombre, String contenidoMarkdown, String objetivo,
                                         URL carpetaBase, Integer leccionId, boolean registrarEnHistorial) {
        view.setBaseLeccion(carpetaBase);
        view.setLeccionMarkdown(contenidoMarkdown);
        view.setLeccionActivaTexto(nombre);

        // Cambiar de lección invalida cualquier compilación anterior: el bytecode
        // de la lección previa no debe quedar disponible para ejecutarse bajo la
        // lección nueva.
        ultimoResultadoCompilacion = null;
        view.getBtnEjecutar().setEnabled(false);

        logConsola("[Lección] '" + nombre + "' cargada (objetivo: " + objetivo + ").");

        if (registrarEnHistorial) {
            registrarAperturaEnHistorial(leccionId, nombre, objetivo);
        } else {
            historialService.descartarIntentoActivo();
            logConsola("[Historial] Carga inicial automática: no se registra como intento "
                    + "(solo las aperturas explícitas de Abrir Lección/Seleccionar Lección de BD).");
        }
    }

    // ------------------------------------------------------------------
    // Gestión de lecciones en MariaDB (Importar / Seleccionar / Eliminar)
    // ------------------------------------------------------------------

    /**
     * Lee una carpeta local de lección y la guarda en MariaDB. No abre la
     * lección importada: importar y mostrar son acciones separadas. Si ya existe
     * una lección con ese nombre, no sobrescribe nada y avisa.
     */
    private void importarLeccion() {
        File carpeta = view.solicitarCarpetaLeccionLocal();
        if (carpeta == null) {
            return;
        }

        LeccionImportada leccion;
        try {
            leccion = leccionService.leerLeccionDesdeCarpeta(carpeta);
        } catch (IOException e) {
            notificar("[Lección]", "No se pudo importar la lección: " + e.getMessage(),
                    "Lección inválida", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            boolean insertada = leccionDAO.insertar(
                    leccion.nombre(), leccion.contenidoMarkdown(), leccion.objetivo());
            if (insertada) {
                logConsola("[BD] Lección '" + leccion.nombre() + "' importada (objetivo: " + leccion.objetivo() + ").");
            } else {
                notificar("[BD]",
                        "Ya existe una lección con el nombre '" + leccion.nombre()
                                + "' en la base de datos. No se importó ni se modificó nada.",
                        "Lección duplicada", JOptionPane.WARNING_MESSAGE);
            }
        } catch (SQLException e) {
            notificar("[BD]", "No se pudo guardar la lección en MariaDB: " + e.getMessage(),
                    "Error de base de datos", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Muestra las lecciones guardadas en MariaDB y carga la elegida en la interfaz.
     */
    private void seleccionarLeccionDesdeBD() {
        List<LeccionResumen> disponibles = listarLeccionesBD();
        if (disponibles == null) {
            return;
        }

        LeccionResumen elegida = view.solicitarSeleccionLeccionBD(disponibles, "Seleccionar Lección de BD");
        if (elegida == null) {
            return;
        }

        try {
            LeccionBD leccion = leccionDAO.obtenerPorId(elegida.id());
            if (leccion == null) {
                notificar("[BD]", "La lección '" + elegida.nombre() + "' ya no existe en la base de datos.",
                        "Lección no encontrada", JOptionPane.WARNING_MESSAGE);
                return;
            }
            logConsola("[BD] Lección '" + leccion.nombre() + "' leída desde MariaDB.");
            // Acción explícita del usuario: sí se registra en el historial.
            mostrarContenidoLeccion(leccion.nombre(), leccion.contenidoMarkdown(), leccion.objetivo(),
                    null, leccion.id(), true);
        } catch (SQLException e) {
            notificar("[BD]", "No se pudo leer la lección de MariaDB: " + e.getMessage(),
                    "Error de base de datos", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Muestra las lecciones guardadas en MariaDB, pide confirmación y elimina la
     * elegida. No toca la tabla historial: sus registros se conservan (el FK del
     * esquema usa ON DELETE SET NULL).
     */
    private void eliminarLeccionDesdeBD() {
        List<LeccionResumen> disponibles = listarLeccionesBD();
        if (disponibles == null) {
            return;
        }

        LeccionResumen elegida = view.solicitarSeleccionLeccionBD(disponibles, "Eliminar Lección de BD");
        if (elegida == null || !view.confirmarEliminacion(elegida.nombre())) {
            return;
        }

        try {
            if (leccionDAO.eliminarPorId(elegida.id())) {
                logConsola("[BD] Lección '" + elegida.nombre() + "' eliminada de MariaDB.");
            } else {
                notificar("[BD]", "La lección '" + elegida.nombre() + "' ya no existía en la base de datos.",
                        "Lección no encontrada", JOptionPane.WARNING_MESSAGE);
            }
        } catch (SQLException e) {
            notificar("[BD]", "No se pudo eliminar la lección de MariaDB: " + e.getMessage(),
                    "Error de base de datos", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Lee las lecciones guardadas en MariaDB para Seleccionar/Eliminar.
     *
     * @return la lista (nunca vacía), o {@code null} si hubo un error o no hay
     *         lecciones; en ambos casos ya se avisó al usuario.
     */
    private List<LeccionResumen> listarLeccionesBD() {
        List<LeccionResumen> disponibles;
        try {
            disponibles = leccionDAO.listarResumen();
        } catch (SQLException e) {
            notificar("[BD]", "No se pudieron leer las lecciones de MariaDB: " + e.getMessage(),
                    "Error de base de datos", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        if (disponibles.isEmpty()) {
            notificar("[BD]", "No hay lecciones importadas en la base de datos.",
                    "Sin lecciones", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return disponibles;
    }

    /**
     * Registra el mensaje en la consola (con su prefijo) y lo muestra en un diálogo.
     */
    private void notificar(String prefijoConsola, String mensaje, String titulo, int tipoMensaje) {
        logConsola(prefijoConsola + " " + mensaje);
        JOptionPane.showMessageDialog(view, mensaje, titulo, tipoMensaje);
    }

    // ------------------------------------------------------------------
    // Historial
    // ------------------------------------------------------------------

    /**
     * Abre el Panel de Historial de Sesión con lo persistido en MariaDB. Como es
     * una acción pedida por el usuario, un fallo de BD se muestra en un diálogo.
     */
    private void verHistorial() {
        try {
            view.mostrarHistorial(historialService.obtenerHistorial());
        } catch (SQLException e) {
            notificar("[Historial]", "No se pudo cargar el historial desde MariaDB: " + e.getMessage(),
                    "Error de base de datos", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Registra una apertura de lección (fila Pendiente). Un fallo de BD solo se
     * informa en la consola: nunca impide abrir la lección.
     */
    private void registrarAperturaEnHistorial(Integer leccionId, String nombre, String objetivo) {
        try {
            boolean objetivoReconocido = historialService.iniciarIntento(leccionId, nombre, objetivo);
            logConsola("[Historial] Apertura registrada: '" + nombre + "' (Pendiente).");
            if (!objetivoReconocido) {
                logConsola("[Historial] Aviso: el objetivo '" + objetivo + "' no es uno de los cuatro válidos; "
                        + "esta apertura no podrá marcarse como Completada.");
            }
        } catch (SQLException e) {
            logConsola("[Historial] No se pudo registrar la apertura: " + e.getMessage());
        }
    }

    /** Evalúa el resultado de Compilar contra el objetivo de la lección abierta. */
    private void registrarCompilacionEnHistorial(boolean compilacionExitosa) {
        try {
            avisarSiObjetivoAlcanzado(historialService.registrarCompilacion(compilacionExitosa));
        } catch (SQLException e) {
            logConsola("[Historial] No se pudo actualizar el historial: " + e.getMessage());
        }
    }

    /** Evalúa el resultado de Ejecutar contra el objetivo de la lección abierta. */
    private void registrarEjecucionEnHistorial(boolean timeoutAlcanzado, boolean ejecucionExitosa) {
        try {
            avisarSiObjetivoAlcanzado(historialService.registrarEjecucion(timeoutAlcanzado, ejecucionExitosa));
        } catch (SQLException e) {
            logConsola("[Historial] No se pudo actualizar el historial: " + e.getMessage());
        }
    }

    private void avisarSiObjetivoAlcanzado(boolean objetivoAlcanzado) {
        if (objetivoAlcanzado) {
            logConsola("[Historial] Objetivo alcanzado: la lección quedó Completada.");
        }
    }

    /** Muestra la ayuda de la aplicación. Contenido estático: no depende de la BD. */
    private void mostrarAyuda() {
        view.mostrarAyuda();
    }

    // ------------------------------------------------------------------
    // Configuración del JDK
    // ------------------------------------------------------------------

    private void abrirConfiguracion() {
        String ubicacionJdk = view.solicitarUbicacionJdk();
        if (ubicacionJdk != null) {
            guardarRutaJdk(ubicacionJdk);
        }
    }

    private void guardarRutaJdk(String rutaJdk) {
        try {
            configuracionDAO.guardarRutaJdk(rutaJdk);
            logConsola("[Configuración] Ruta del JDK guardada: " + rutaJdk);
        } catch (SQLException e) {
            logConsola("[Configuración] No se pudo guardar la ruta del JDK: " + e.getMessage());
            return;
        }
        aplicarEstadoJdk(rutaJdk);
    }

    private void inicializarConfiguracionJdk() {
        String rutaJdkGuardada;
        try {
            rutaJdkGuardada = configuracionDAO.obtenerRutaJdk();
        } catch (SQLException e) {
            logConsola("[Configuración] No se pudo leer la configuración: " + e.getMessage());
            aplicarEstadoJdk(null);
            return;
        }
        aplicarEstadoJdk(rutaJdkGuardada);
    }

    private void aplicarEstadoJdk(String rutaJdk) {
        boolean valido = validadorJdk.esJdkValido(rutaJdk);
        rutaJdkActual = valido ? rutaJdk : null;

        ultimoResultadoCompilacion = null;
        view.getBtnEjecutar().setEnabled(false);
        view.getBtnCompilar().setEnabled(valido);

        if (valido) {
            logConsola("[Configuración] JDK válido: " + rutaJdk);
            return;
        }

        String motivo = (rutaJdk == null || rutaJdk.isBlank())
                ? "No hay una ruta de JDK configurada."
                : "La ruta configurada no corresponde a un JDK válido: " + rutaJdk;
        logConsola("[Configuración] " + motivo + " Compilar y Ejecutar quedan deshabilitados.");
        JOptionPane.showMessageDialog(view,
                motivo + "\nConfigura un JDK válido desde el menú Configuración.",
                "JDK no configurado",
                JOptionPane.WARNING_MESSAGE);
    }

    // ------------------------------------------------------------------
    // Compilar / Ejecutar
    // ------------------------------------------------------------------

    private void compilarCodigo() {
        if (rutaJdkActual == null) {
            logConsola("[Compilar] No hay un JDK válido configurado.");
            return;
        }

        ultimoResultadoCompilacion = null;
        view.getBtnEjecutar().setEnabled(false);

        String codigoFuente = view.getCodigoFuente();
        CompileResult resultado = gualiDevService.compilar(codigoFuente, rutaJdkActual);
        ultimoResultadoCompilacion = resultado;

        if (resultado.isExitoso()) {
            logConsola("[Compilar] Compilación exitosa. Clase generada: " + resultado.getNombreClase());
            view.getBtnEjecutar().setEnabled(true);
        } else {
            logConsola("[Compilar] Error de compilación:");
            logConsola(resultado.getSalida());
            view.getBtnEjecutar().setEnabled(false);
        }

        registrarCompilacionEnHistorial(resultado.isExitoso());
    }

    private void ejecutarCodigo() {
        if (rutaJdkActual == null || ultimoResultadoCompilacion == null || !ultimoResultadoCompilacion.isExitoso()) {
            logConsola("[Ejecutar] No hay una compilación exitosa vigente para ejecutar.");
            return;
        }

        String nombreClase = ultimoResultadoCompilacion.getNombreClase();
        ExecuteResult resultado = gualiDevService.ejecutar(nombreClase, rutaJdkActual);

        if (resultado.isTimeoutAlcanzado()) {
            logConsola("[Ejecutar] El programa superó el tiempo límite de ejecución y fue detenido.");
        } else if (resultado.isExitoso()) {
            logConsola("[Ejecutar] Ejecución exitosa. Salida:");
            logConsola(resultado.getSalida());
        } else {
            logConsola("[Ejecutar] Error durante la ejecución:");
            logConsola(resultado.getErrores());
        }

        registrarEjecucionEnHistorial(resultado.isTimeoutAlcanzado(), resultado.isExitoso());
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void ajustarZoom(float delta) {
        tamanoFuente = Math.max(TAMANO_FUENTE_MIN, Math.min(TAMANO_FUENTE_MAX, tamanoFuente + delta));
        view.setTamanoFuente(tamanoFuente);
    }

    private void probarConexionBD() {
        try {
            conexionBD.probarConexion();
            logConsola("[BD] Conexión a MariaDB exitosa.");
        } catch (SQLException e) {
            logConsola("[BD] No se pudo conectar a MariaDB: " + e.getMessage());
        }
    }

    private void logAccionMenu(String opcion) {
        logConsola("[Menú] '" + opcion + "' seleccionado — aquí va la lógica del programa.");
    }

    private void logConsola(String mensaje) {
        view.appendConsola(mensaje);
    }
}