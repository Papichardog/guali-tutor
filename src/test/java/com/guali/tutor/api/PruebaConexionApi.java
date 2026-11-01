package com.guali.tutor.api;

import java.util.List;

/**
 * Comprobación MANUAL de la conexión HTTPS con la API (no es parte de la aplicación ni se empaqueta en el jar).
 * Con la API corriendo, ejecute el método main (clic derecho -> Run). Hace tres lecturas (no modifica nada).
 */
public class PruebaConexionApi {

    public static void main(String[] args) {
        System.out.println("=== Prueba de conexión HTTPS con la API de Guali Tutor ===");
        GualiHttpClient cliente;
        try {
            cliente = GualiHttpClient.crear();
        } catch (ApiException e) {
            System.out.println("[CONFIGURACIÓN] " + e.getMessage());
            return;
        }
        System.out.println("URL base: " + cliente.urlBase());
        System.out.println("Certificado de confianza cargado correctamente.\n");

        for (String ruta : List.of("/api/configuracion", "/api/lecciones", "/api/historial")) {
            try {
                GualiHttpClient.Respuesta respuesta = cliente.get(ruta);
                System.out.printf("GET %-20s -> HTTP %d  %s%n", ruta, respuesta.codigo(), resumir(respuesta.cuerpo()));
            } catch (ApiException e) {
                System.out.printf("GET %-20s -> FALLO [%s] %s%n", ruta, e.getTipo(), e.getMessage());
            }
        }
    }

    private static String resumir(String cuerpo) {
        String linea = cuerpo.replace('\n', ' ');
        return linea.length() <= 110 ? linea : linea.substring(0, 110) + "… (" + cuerpo.length() + " caracteres)";
    }
}
