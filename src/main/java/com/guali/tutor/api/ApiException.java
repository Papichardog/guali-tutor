package com.guali.tutor.api;

/**
 * Error al hablar con la API REST. Sustituye a SQLException en las capas que antes usaban JDBC: es una excepción
 * comprobada, así que el compilador obliga a manejarla, y su mensaje está pensado para mostrarse al usuario.
 *
 * <p>{@link Tipo} indica qué falló. Los tipos de transporte (configuración, conexión, TLS, tiempo agotado,
 * interrupción) los produce esta capa; {@code ERROR_HTTP} y {@code RESPUESTA_INVALIDA} los usarán las clases
 * que interpretan las respuestas de la API (con el {@code codigo} de negocio del contrato, p. ej. LECCION_DUPLICADA).
 */
public class ApiException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Tipo {
        /** api.properties o el certificado configurado no son válidos (se detecta al crear el cliente). */
        CONFIGURACION_INVALIDA,
        /** No se pudo abrir la conexión: el servidor no está en ejecución o no es alcanzable. */
        SIN_CONEXION,
        /** Falló el apretón de manos HTTPS: certificado no confiable o que no corresponde al servidor. */
        FALLO_TLS,
        /** Se agotó el tiempo al conectar o al esperar la respuesta. */
        TIEMPO_AGOTADO,
        /** El hilo fue interrumpido mientras esperaba la respuesta. */
        INTERRUMPIDO,
        /** La API respondió con un código de error (4xx o 5xx). */
        ERROR_HTTP,
        /** La API respondió, pero el contenido no tiene el formato esperado. */
        RESPUESTA_INVALIDA
    }

    private final Tipo tipo;
    private final int codigoHttp;
    private final String codigoNegocio;

    private ApiException(Tipo tipo, int codigoHttp, String codigoNegocio, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.tipo = tipo;
        this.codigoHttp = codigoHttp;
        this.codigoNegocio = codigoNegocio;
    }

    public static ApiException configuracion(String mensaje) {
        return new ApiException(Tipo.CONFIGURACION_INVALIDA, 0, null, mensaje, null);
    }

    public static ApiException configuracion(String mensaje, Throwable causa) {
        return new ApiException(Tipo.CONFIGURACION_INVALIDA, 0, null, mensaje, causa);
    }

    public static ApiException sinConexion(String urlBase, Throwable causa) {
        return new ApiException(Tipo.SIN_CONEXION, 0, null,
                "No se pudo conectar con la API en " + urlBase + ". Verifique que el servidor esté en ejecución.", causa);
    }

    public static ApiException falloTls(String urlBase, String detalle, Throwable causa) {
        return new ApiException(Tipo.FALLO_TLS, 0, null,
                "No se pudo establecer la conexión segura (HTTPS) con " + urlBase + ": " + explicarFalloTls(detalle)
                        + " Verifique que el certificado configurado (guali-dev.crt) sea el del servidor y que api.baseUrl"
                        + " use el mismo nombre que ese certificado (localhost o 127.0.0.1).", causa);
    }

    /** Traduce los mensajes internos del JDK a lo que significan; el detalle técnico queda en la causa de la excepción. */
    private static String explicarFalloTls(String detalle) {
        String d = detalle == null ? "" : detalle;
        if (d.contains("PKIX path") || d.contains("unable to find valid certification path")) {
            return "el servidor presentó un certificado distinto del configurado.";
        }
        if (d.contains("No subject alternative") || d.contains("No name matching")) {
            return "el certificado del servidor no corresponde al nombre de la URL.";
        }
        return d.isBlank() ? "error en el apretón de manos TLS." : d.replaceAll("\\.+$", "") + ".";
    }

    public static ApiException tiempoAgotado(String urlBase, boolean alConectar, Throwable causa) {
        return new ApiException(Tipo.TIEMPO_AGOTADO, 0, null, alConectar
                ? "Se agotó el tiempo al conectar con la API en " + urlBase + "."
                : "La API en " + urlBase + " no respondió a tiempo.", causa);
    }

    public static ApiException interrumpido(String urlBase, Throwable causa) {
        return new ApiException(Tipo.INTERRUMPIDO, 0, null,
                "La comunicación con la API en " + urlBase + " fue interrumpida.", causa);
    }

    /** La API respondió con un error; {@code codigoNegocio} es el campo {@code codigo} del cuerpo de error (puede ser null). */
    public static ApiException errorHttp(int codigoHttp, String codigoNegocio, String mensaje) {
        return new ApiException(Tipo.ERROR_HTTP, codigoHttp, codigoNegocio, mensaje, null);
    }

    public static ApiException respuestaInvalida(String mensaje, Throwable causa) {
        return new ApiException(Tipo.RESPUESTA_INVALIDA, 0, null, mensaje, causa);
    }

    public Tipo getTipo() {
        return tipo;
    }

    /** Código HTTP de la respuesta, o 0 si no hubo respuesta. */
    public int getCodigoHttp() {
        return codigoHttp;
    }

    /** Código de negocio del contrato (p. ej. LECCION_DUPLICADA), o null si no aplica. */
    public String getCodigoNegocio() {
        return codigoNegocio;
    }
}
