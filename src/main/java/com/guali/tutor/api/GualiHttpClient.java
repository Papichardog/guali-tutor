package com.guali.tutor.api;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Cliente HTTP base de Guali Tutor: la capa de transporte entre el Swing y la API REST. Solo habla HTTPS y confía
 * únicamente en el certificado configurado ({@link ConfianzaTls}); verifica además que el certificado corresponda
 * al nombre del servidor. No interpreta JSON: devuelve el código HTTP y el cuerpo en texto. Las clases que
 * traducen las respuestas (y los errores de negocio de la API) se apoyan en esta.
 *
 * <p>Una respuesta con código 4xx o 5xx NO es una excepción aquí: se devuelve como {@link Respuesta}. Solo lanzan
 * {@link ApiException} los fallos de transporte: sin conexión, TLS, tiempo agotado, interrupción o configuración.
 * Es seguro compartir una instancia entre hilos.
 */
public final class GualiHttpClient {

    /** Respuesta cruda: código HTTP, cuerpo (vacío en un 204) y cabecera Location (null si no vino). */
    public record Respuesta(int codigo, String cuerpo, String location) {
        public boolean esExitosa() {
            return codigo >= 200 && codigo < 300;
        }
    }

    private final HttpClient http;
    private final String urlBase;
    private final Duration timeoutRespuesta;

    /** Crea el cliente con la configuración de {@code /api.properties}. */
    public static GualiHttpClient crear() throws ApiException {
        return new GualiHttpClient(ApiConfig.cargar());
    }

    public GualiHttpClient(ApiConfig configuracion) throws ApiException {
        this(configuracion, ConfianzaTls.desdeUbicacion(configuracion.certificado()));
    }

    /** Para pruebas: permite inyectar el contexto TLS. */
    GualiHttpClient(ApiConfig configuracion, SSLContext contextoTls) {
        SSLParameters parametros = new SSLParameters();
        parametros.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        parametros.setEndpointIdentificationAlgorithm("HTTPS"); // el certificado debe corresponder al nombre del servidor

        this.http = HttpClient.newBuilder()
                .sslContext(contextoTls)
                .sslParameters(parametros)
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(configuracion.timeoutConexion())
                .build();
        this.urlBase = configuracion.urlBase();
        this.timeoutRespuesta = configuracion.timeoutRespuesta();
    }

    public String urlBase() {
        return urlBase;
    }

    public Respuesta get(String ruta) throws ApiException {
        return enviar("GET", ruta, null);
    }

    public Respuesta post(String ruta, String json) throws ApiException {
        return enviar("POST", ruta, Objects.requireNonNull(json, "json"));
    }

    public Respuesta put(String ruta, String json) throws ApiException {
        return enviar("PUT", ruta, Objects.requireNonNull(json, "json"));
    }

    public Respuesta patch(String ruta, String json) throws ApiException {
        return enviar("PATCH", ruta, Objects.requireNonNull(json, "json"));
    }

    public Respuesta delete(String ruta) throws ApiException {
        return enviar("DELETE", ruta, null);
    }

    // ------------------------------------------------------------------

    private Respuesta enviar(String metodo, String ruta, String json) throws ApiException {
        if (!ruta.startsWith("/")) {
            throw new IllegalArgumentException("La ruta debe empezar por '/': " + ruta);
        }
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(urlBase + ruta))
                .timeout(timeoutRespuesta)
                .header("Accept", "application/json");
        if (json != null) {
            peticion.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        } else {
            peticion.method(metodo, HttpRequest.BodyPublishers.noBody());
        }

        try {
            HttpResponse<String> respuesta = http.send(peticion.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Respuesta(respuesta.statusCode(), respuesta.body(),
                    respuesta.headers().firstValue("Location").orElse(null));
        } catch (IOException e) {
            throw traducir(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // se conserva la señal de interrupción para quien llamó
            throw ApiException.interrumpido(urlBase, e);
        }
    }

    /** Clasifica un fallo de red mirando toda la cadena de causas (el JDK suele envolver la excepción real). */
    private ApiException traducir(IOException e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof HttpTimeoutException) {
                return ApiException.tiempoAgotado(urlBase, causa instanceof HttpConnectTimeoutException, e);
            }
            if (causa instanceof SSLException) {
                return ApiException.falloTls(urlBase, causa.getMessage(), e);
            }
        }
        return ApiException.sinConexion(urlBase, e);
    }
}
