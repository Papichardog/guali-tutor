package com.guali.tutor.service;

import com.guali.tutor.model.Leccion;
import com.guali.tutor.model.LeccionImportada;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Descubre y carga las lecciones empaquetadas como recursos del classpath en
 * /lecciones/, y también lee una lección desde una carpeta local del disco
 * (para importarla a MariaDB). Cada lección es una carpeta con leccion.md y
 * config.txt (imagen opcional, referenciada desde el propio markdown). Esta
 * clase no conoce MariaDB: solo lee archivos.
 */
public class LeccionService {

    private static final String CARPETA_BASE = "/lecciones";
    private static final String ARCHIVO_MARKDOWN = "leccion.md";
    private static final String ARCHIVO_CONFIG = "config.txt";
    private static final String CLAVE_OBJETIVO = "objetivo";

    /** Objetivos permitidos por el enunciado; coinciden con el ENUM de la tabla `lecciones`. */
    private static final List<String> OBJETIVOS_VALIDOS = List.of(
            "COMPILAR_CON_ERROR", "COMPILAR_EXITOSO", "EJECUTAR_CON_ERROR", "EJECUTAR_EXITOSO");

    /**
     * @return los nombres de carpeta de las lecciones disponibles, en orden
     *         alfabético. Lista vacía si no existe la carpeta /lecciones/.
     */
    public List<String> listarLeccionesDisponibles() throws IOException {
        URL urlBase = getClass().getResource(CARPETA_BASE);
        if (urlBase == null) {
            return List.of();
        }
        try {
            return "jar".equals(urlBase.getProtocol())
                    ? listarDesdeJar()
                    : listarDesdeDirectorio(urlBase);
        } catch (URISyntaxException e) {
            throw new IOException("Ruta de recursos de lecciones inválida", e);
        }
    }

    /**
     * Carga por completo una lección: markdown, objetivo y carpeta base para
     * resolver imágenes. Lanza IOException con un mensaje claro si falta
     * leccion.md, falta config.txt, o config.txt no define 'objetivo'.
     */
    public Leccion cargarLeccion(String nombreLeccion) throws IOException {
        String carpetaLeccion = CARPETA_BASE + "/" + nombreLeccion + "/";

        String contenidoMarkdown = leerTextoObligatorio(carpetaLeccion + ARCHIVO_MARKDOWN,
                "La lección '" + nombreLeccion + "' no tiene el archivo " + ARCHIVO_MARKDOWN + ".");

        String objetivo = leerObjetivo(carpetaLeccion + ARCHIVO_CONFIG, nombreLeccion);

        URL carpetaBase = getClass().getResource(carpetaLeccion);
        return new Leccion(nombreLeccion, contenidoMarkdown, objetivo, carpetaBase);
    }

    /**
     * Lee una lección desde una carpeta local del disco (no del classpath). El
     * nombre de la lección es el nombre de la carpeta. Lanza IOException con un
     * mensaje claro si la ruta no es una carpeta, falta leccion.md o config.txt,
     * los archivos no están en UTF-8, o el objetivo no es uno de los cuatro
     * permitidos por el enunciado.
     */
    public LeccionImportada leerLeccionDesdeCarpeta(File carpeta) throws IOException {
        if (carpeta == null || !carpeta.isDirectory()) {
            throw new IOException("La ruta seleccionada no es una carpeta.");
        }
        String nombreLeccion = carpeta.getName();

        File archivoMarkdown = new File(carpeta, ARCHIVO_MARKDOWN);
        if (!archivoMarkdown.isFile()) {
            throw new IOException("La carpeta '" + nombreLeccion + "' no contiene el archivo " + ARCHIVO_MARKDOWN + ".");
        }
        File archivoConfig = new File(carpeta, ARCHIVO_CONFIG);
        if (!archivoConfig.isFile()) {
            throw new IOException("La carpeta '" + nombreLeccion + "' no contiene el archivo " + ARCHIVO_CONFIG + ".");
        }

        String contenidoMarkdown = leerTextoUtf8(archivoMarkdown);
        String objetivo = extraerObjetivoValido(leerTextoUtf8(archivoConfig), nombreLeccion);
        return new LeccionImportada(nombreLeccion, contenidoMarkdown, objetivo);
    }

    private String leerTextoUtf8(File archivo) throws IOException {
        try {
            String texto = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
            // Algunos editores anteponen un BOM UTF-8; sin quitarlo, la primera clave de config.txt no se reconocería.
            return texto.startsWith("\uFEFF") ? texto.substring(1) : texto;
        } catch (CharacterCodingException e) {
            throw new IOException("El archivo " + archivo.getName() + " no está codificado en UTF-8.", e);
        }
    }

    private String extraerObjetivoValido(String contenidoConfig, String nombreLeccion) throws IOException {
        Properties propiedades = new Properties();
        propiedades.load(new StringReader(contenidoConfig));
        String objetivo = propiedades.getProperty(CLAVE_OBJETIVO);
        if (objetivo == null || objetivo.isBlank()) {
            throw new IOException(
                    "El archivo " + ARCHIVO_CONFIG + " de '" + nombreLeccion + "' no define 'objetivo'.");
        }
        objetivo = objetivo.trim();
        if (!OBJETIVOS_VALIDOS.contains(objetivo)) {
            throw new IOException("El objetivo '" + objetivo + "' de '" + nombreLeccion
                    + "' no es válido. Valores permitidos: " + String.join(", ", OBJETIVOS_VALIDOS) + ".");
        }
        return objetivo;
    }

    private String leerTextoObligatorio(String ruta, String mensajeSiFalta) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(ruta)) {
            if (in == null) {
                throw new IOException(mensajeSiFalta);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String leerObjetivo(String rutaConfig, String nombreLeccion) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(rutaConfig)) {
            if (in == null) {
                throw new IOException("La lección '" + nombreLeccion + "' no tiene el archivo " + ARCHIVO_CONFIG + ".");
            }
            Properties propiedades = new Properties();
            propiedades.load(in);
            String objetivo = propiedades.getProperty(CLAVE_OBJETIVO);
            if (objetivo == null || objetivo.isBlank()) {
                throw new IOException(
                        "El archivo " + ARCHIVO_CONFIG + " de '" + nombreLeccion + "' no define 'objetivo'.");
            }
            return objetivo.trim();
        }
    }

    private List<String> listarDesdeDirectorio(URL urlBase) throws URISyntaxException {
        File carpeta = new File(urlBase.toURI());
        File[] subcarpetas = carpeta.listFiles(File::isDirectory);
        if (subcarpetas == null) {
            return List.of();
        }
        return Arrays.stream(subcarpetas)
                .map(File::getName)
                .sorted()
                .toList();
    }

    private List<String> listarDesdeJar() throws IOException, URISyntaxException {
        CodeSource origen = getClass().getProtectionDomain().getCodeSource();
        if (origen == null) {
            return List.of();
        }
        String prefijo = CARPETA_BASE.substring(1) + "/";
        File archivoJar = new File(origen.getLocation().toURI());
        TreeSet<String> nombres = new TreeSet<>();
        try (JarFile jarFile = new JarFile(archivoJar)) {
            Enumeration<JarEntry> entradas = jarFile.entries();
            while (entradas.hasMoreElements()) {
                String nombreEntrada = entradas.nextElement().getName();
                if (nombreEntrada.startsWith(prefijo) && nombreEntrada.length() > prefijo.length()) {
                    String resto = nombreEntrada.substring(prefijo.length());
                    int indiceBarra = resto.indexOf('/');
                    if (indiceBarra > 0) {
                        nombres.add(resto.substring(0, indiceBarra));
                    }
                }
            }
        }
        return new ArrayList<>(nombres);
    }
}
