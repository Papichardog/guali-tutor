package com.guali.tutor.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

/**
 * Configuración de la conexión con la API, leída de {@code /api.properties} (el mismo patrón con el que
 * {@code ConexionBD} leía db.properties). Exige HTTPS: una URL base con otro esquema se rechaza.
 */
public record ApiConfig(URI baseUrl, String certificado, Duration timeoutConexion, Duration timeoutRespuesta) {

    public static final String RECURSO_PROPIEDADES = "/api.properties";
    private static final String CERTIFICADO_POR_DEFECTO = "classpath:certs/guali-dev.crt";
    private static final int TIMEOUT_CONEXION_POR_DEFECTO = 5;
    private static final int TIMEOUT_RESPUESTA_POR_DEFECTO = 60;

    /** Lee {@code /api.properties} del classpath. */
    public static ApiConfig cargar() throws ApiException {
        Properties propiedades = new Properties();
        try (InputStream in = ApiConfig.class.getResourceAsStream(RECURSO_PROPIEDADES)) {
            if (in == null) {
                throw ApiException.configuracion("No se encontró " + RECURSO_PROPIEDADES
                        + " (debe estar en src/main/resources).");
            }
            propiedades.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw ApiException.configuracion("No se pudo leer " + RECURSO_PROPIEDADES + ": " + e.getMessage(), e);
        }
        return desde(propiedades);
    }

    /** Valida las propiedades y arma la configuración. Separado de {@link #cargar()} para poder probarlo sin archivos. */
    public static ApiConfig desde(Properties propiedades) throws ApiException {
        String url = propiedades.getProperty("api.baseUrl", "").trim();
        if (url.isEmpty()) {
            throw ApiException.configuracion("Falta api.baseUrl en api.properties (por ejemplo, https://localhost:8443).");
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        URI base;
        try {
            base = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw ApiException.configuracion("api.baseUrl no es una URL válida: " + url, e);
        }
        if (base.getScheme() == null || !base.getScheme().equalsIgnoreCase("https") || base.getHost() == null) {
            throw ApiException.configuracion("api.baseUrl debe usar HTTPS (https://servidor:puerto): "
                    + "la comunicación con la API es siempre cifrada. Valor actual: " + url);
        }

        String certificado = propiedades.getProperty("api.certificado", CERTIFICADO_POR_DEFECTO).trim();
        if (!certificado.startsWith("classpath:") && !certificado.startsWith("file:")) {
            throw ApiException.configuracion("api.certificado debe empezar por classpath: o file: (valor actual: "
                    + certificado + ").");
        }
        return new ApiConfig(base, certificado,
                segundos(propiedades, "api.timeoutConexionSegundos", TIMEOUT_CONEXION_POR_DEFECTO),
                segundos(propiedades, "api.timeoutRespuestaSegundos", TIMEOUT_RESPUESTA_POR_DEFECTO));
    }

    /** La URL base como texto, sin barra final. */
    public String urlBase() {
        return baseUrl.toString();
    }

    private static Duration segundos(Properties propiedades, String clave, int porDefecto) throws ApiException {
        String texto = propiedades.getProperty(clave);
        if (texto == null || texto.isBlank()) {
            return Duration.ofSeconds(porDefecto);
        }
        try {
            int valor = Integer.parseInt(texto.trim());
            if (valor < 1) {
                throw new NumberFormatException();
            }
            return Duration.ofSeconds(valor);
        } catch (NumberFormatException e) {
            throw ApiException.configuracion(clave + " debe ser un entero positivo de segundos (valor actual: " + texto + ").");
        }
    }
}
