package com.guali.tutor.controller;

import com.guali.tutor.form.MainForm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class MainController {

    private static final float TAMANO_FUENTE_MIN = 8f;
    private static final float TAMANO_FUENTE_MAX = 28f;
    private static final String CARPETA_LECCION_ACTIVA = "/lecciones/leccion-01/";

    private final MainForm view;
    private float tamanoFuente = 13f;

    public MainController(MainForm view) {
        this.view = view;
        wireListeners();
        view.setBaseLeccion(getClass().getResource(CARPETA_LECCION_ACTIVA));
        view.setLeccionMarkdown(cargarMarkdownLeccion(CARPETA_LECCION_ACTIVA + "example.md"));
    }

    private String cargarMarkdownLeccion(String recurso) {
        try (InputStream in = getClass().getResourceAsStream(recurso)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void wireListeners() {
        view.getAbrirLeccion().addActionListener(e -> logAccionMenu("Abrir Lección"));
        view.getCerrarLeccion().addActionListener(e -> logAccionMenu("Cerrar Lección"));
        view.getConfiguracion().addActionListener(e -> abrirConfiguracion());
        view.getHistorial().addActionListener(e -> logAccionMenu("Historial"));
        view.getAyuda().addActionListener(e -> logAccionMenu("Ayuda"));

        view.getBtnCompilar().addActionListener(e -> logAccionBoton("Compilar"));
        view.getBtnEjecutar().addActionListener(e -> logAccionBoton("Ejecutar"));

        view.getBtnZoomIn().addActionListener(e -> ajustarZoom(1f));
        view.getBtnZoomOut().addActionListener(e -> ajustarZoom(-1f));
    }

    private void abrirConfiguracion() {
        String ubicacionJdk = view.solicitarUbicacionJdk();
        if (ubicacionJdk != null) {
            logConsola("[Menú] 'Configuración' — JDK: " + ubicacionJdk);
        }
    }

    private void ajustarZoom(float delta) {
        tamanoFuente = Math.max(TAMANO_FUENTE_MIN, Math.min(TAMANO_FUENTE_MAX, tamanoFuente + delta));
        view.setTamanoFuente(tamanoFuente);
    }

    private void logAccionMenu(String opcion) {
        logConsola("[Menú] '" + opcion + "' seleccionado — aquí va la lógica del programa.");
    }

    private void logAccionBoton(String boton) {
        logConsola("[Botón] '" + boton + "' presionado — aquí va la lógica del programa.");
    }

    private void logConsola(String mensaje) {
        view.appendConsola(mensaje);
    }
}
