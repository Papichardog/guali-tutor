package com.guali.tutor.service;

import com.guali.tutor.model.Leccion;
import com.guali.tutor.model.LeccionImportada;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Descubre y carga lecciones desde tres caminos, todos apoyados en la misma
 * validación interna (leerCarpeta):
 * <ul>
 *   <li>Classpath, bajo /lecciones/ ({@link #listarLeccionesDisponibles()},
 *       {@link #cargarLeccion(String)}) — se conservan por compatibilidad,
 *       pero MainController ya no los usa para Abrir Lección; la fuente activa
 *       es la carpeta raíz externa configurable.</li>
 *   <li>Una carpeta raíz externa en disco ({@link #listarLeccionesDisponibles(File)},
 *       {@link #cargarLeccionDesdeCarpeta(File)}) — fuente actual de Abrir
 *       Lección y de la precarga inicial.</li>
 *   <li>Una única carpeta local elegida manualmente ({@link #leerLeccionDesdeCarpeta(File)})
 *       — usada solo por Importar Lección a BD.</li>
 * </ul>
 *
 * <p>Archivo markdown de una lección: se usa {@code leccion.md} (nombre del
 * enunciado). Si no existe, se acepta el único archivo {@code .md} de la
 * carpeta (p. ej. {@code example.md}, el nombre que traía el proyecto base).
 */
public class LeccionService {

    private static final String CARPETA_BASE = "/lecciones";
    private static final String ARCHIVO_MARKDOWN = "leccion.md";
    private static final String EXTENSION_MARKDOWN = ".md";
    private static final String ARCHIVO_CONFIG = "config.txt";
    private static final String CLAVE_OBJETIVO = "objetivo";

    /** Objetivos permitidos por el enunciado; coinciden con el ENUM de la tabla `lecciones`. */
    private static final List<String> OBJETIVOS_VALIDOS = List.of(
            "COMPILAR_CON_ERROR", "COMPILAR_EXITOSO", "EJECUTAR_CON_ERROR", "EJECUTAR_EXITOSO");

    /** Resultado interno de leer/validar una carpeta, antes de envolverlo en Leccion o LeccionImportada. */
    private record LecturaCarpetaLeccion(String nombre, String contenidoMarkdown, String objetivo) {
    }

    /** Una subcarpeta de una raíz que no es una lección válida, y por qué se ignoró. */
    public record CarpetaLeccionInvalida(String nombreCarpeta, String motivo) {
    }

    /** Resultado de listar una carpeta raíz: las lecciones válidas y las carpetas ignoradas. */
    public record ResultadoListadoLocal(List<String> validas, List<CarpetaLeccionInvalida> ignoradas) {
    }

    // ------------------------------------------------------------------
    // Classpath (se conserva por compatibilidad; MainController ya no lo usa)
    // ------------------------------------------------------------------

    /**
     * @return los nombres de carpeta de las lecciones disponibles en el
     *         classpath, en orden alfabético. Lista vacía si no existe /lecciones/.
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
     * Carga por completo una lección del classpath. Lanza IOException con un
     * mensaje claro si falta leccion.md, falta config.txt, o config.txt no
     * define 'objetivo'.
     */
    public Leccion cargarLeccion(String nombreLeccion) throws IOException {
        String carpetaLeccion = CARPETA_BASE + "/" + nombreLeccion + "/";

        String contenidoMarkdown = leerTextoObligatorio(carpetaLeccion + ARCHIVO_MARKDOWN,
                "La lección '" + nombreLeccion + "' no tiene el archivo " + ARCHIVO_MARKDOWN + ".");

        String objetivo = leerObjetivo(carpetaLeccion + ARCHIVO_CONFIG, nombreLeccion);

        URL carpetaBase = getClass().getResource(carpetaLeccion);
        return new Leccion(nombreLeccion, contenidoMarkdown, objetivo, carpetaBase);
    }

    // ------------------------------------------------------------------
    // Carpeta raíz externa (fuente actual de Abrir Lección y de la precarga)
    // ------------------------------------------------------------------

    /**
     * Lista las subcarpetas de {@code carpetaRaiz} que son lecciones válidas
     * (tienen su markdown, config.txt y un objetivo reconocido), y por separado
     * las que se ignoraron junto con el motivo. No lanza excepción si la raíz
     * no es válida: en ese caso devuelve ambas listas vacías.
     */
    public ResultadoListadoLocal listarLeccionesDisponibles(File carpetaRaiz) {
        if (carpetaRaiz == null || !carpetaRaiz.isDirectory()) {
            return new ResultadoListadoLocal(List.of(), List.of());
        }
        File[] subcarpetas = carpetaRaiz.listFiles(File::isDirectory);
        if (subcarpetas == null) {
            return new ResultadoListadoLocal(List.of(), List.of());
        }

        List<String> validas = new ArrayList<>();
        List<CarpetaLeccionInvalida> ignoradas = new ArrayList<>();
        for (File subcarpeta : ordenadasPorNombre(subcarpetas)) {
            try {
                leerCarpeta(subcarpeta);
                validas.add(subcarpeta.getName());
            } catch (IOException e) {
                ignoradas.add(new CarpetaLeccionInvalida(subcarpeta.getName(), e.getMessage()));
            }
        }
        return new ResultadoListadoLocal(validas, ignoradas);
    }

    /**
     * Detecta el error típico de elegir como carpeta raíz la carpeta de UNA
     * lección en vez de la carpeta que las contiene. Es {@code true} solo si la
     * carpeta tiene pinta de lección (un config.txt y algún archivo .md) y a la
     * vez no contiene ninguna subcarpeta que sea una lección válida; una raíz
     * correcta (con lecciones dentro) nunca se marca.
     */
    public boolean pareceLeccionIndividualEnVezDeRaiz(File carpeta) {
        if (carpeta == null || !carpeta.isDirectory()) {
            return false;
        }
        boolean tienePintaDeLeccion = new File(carpeta, ARCHIVO_CONFIG).isFile()
                && (new File(carpeta, ARCHIVO_MARKDOWN).isFile() || !archivosMarkdown(carpeta).isEmpty());
        return tienePintaDeLeccion && listarLeccionesDisponibles(carpeta).validas().isEmpty();
    }

    /**
     * Carga por completo una lección desde una carpeta de la raíz externa:
     * markdown, objetivo, y la carpeta como base para resolver imágenes relativas.
     */
    public Leccion cargarLeccionDesdeCarpeta(File carpeta) throws IOException {
        LecturaCarpetaLeccion lectura = leerCarpeta(carpeta);
        return new Leccion(lectura.nombre(), lectura.contenidoMarkdown(), lectura.objetivo(),
                carpetaComoUrl(carpeta, lectura.nombre()));
    }

    // ------------------------------------------------------------------
    // Importar Lección a BD (una única carpeta elegida manualmente)
    // ------------------------------------------------------------------

    /**
     * Lee una carpeta local de lección para importarla a MariaDB (sin carpeta
     * base: la BD no guarda imágenes). Lanza IOException con un mensaje claro
     * si la ruta no es una carpeta, falta el markdown o config.txt, los
     * archivos no están en UTF-8, o el objetivo no es uno de los cuatro permitidos.
     */
    public LeccionImportada leerLeccionDesdeCarpeta(File carpeta) throws IOException {
        LecturaCarpetaLeccion lectura = leerCarpeta(carpeta);
        return new LeccionImportada(lectura.nombre(), lectura.contenidoMarkdown(), lectura.objetivo());
    }

    // ------------------------------------------------------------------
    // Validación compartida por los tres caminos que leen desde disco
    // ------------------------------------------------------------------

    private LecturaCarpetaLeccion leerCarpeta(File carpeta) throws IOException {
        if (carpeta == null || !carpeta.isDirectory()) {
            throw new IOException("La ruta seleccionada no es una carpeta.");
        }
        String nombreLeccion = carpeta.getName();

        File archivoMarkdown = resolverArchivoMarkdown(carpeta, nombreLeccion);
        File archivoConfig = new File(carpeta, ARCHIVO_CONFIG);
        if (!archivoConfig.isFile()) {
            throw new IOException("La carpeta '" + nombreLeccion + "' no contiene el archivo " + ARCHIVO_CONFIG + ".");
        }

        String contenidoMarkdown = leerTextoUtf8(archivoMarkdown);
        String objetivo = extraerObjetivoValido(leerTextoUtf8(archivoConfig), nombreLeccion);
        return new LecturaCarpetaLeccion(nombreLeccion, contenidoMarkdown, objetivo);
    }

    /**
     * Devuelve el markdown de la lección: {@code leccion.md} si existe; si no,
     * el único archivo .md de la carpeta. Con ninguno, o con varios y sin
     * leccion.md, lanza un error que lo explica.
     */
    private File resolverArchivoMarkdown(File carpeta, String nombreLeccion) throws IOException {
        File oficial = new File(carpeta, ARCHIVO_MARKDOWN);
        if (oficial.isFile()) {
            return oficial;
        }
        List<File> candidatos = archivosMarkdown(carpeta);
        if (candidatos.isEmpty()) {
            throw new IOException("La carpeta '" + nombreLeccion + "' no contiene el archivo " + ARCHIVO_MARKDOWN
                    + " ni ningún otro archivo " + EXTENSION_MARKDOWN + ".");
        }
        if (candidatos.size() > 1) {
            String nombres = candidatos.stream().map(File::getName).collect(Collectors.joining(", "));
            throw new IOException("La carpeta '" + nombreLeccion + "' no contiene " + ARCHIVO_MARKDOWN
                    + " y tiene varios archivos " + EXTENSION_MARKDOWN + " (" + nombres + "). Renombre el correcto a "
                    + ARCHIVO_MARKDOWN + ".");
        }
        return candidatos.get(0);
    }

    /** Archivos .md de la carpeta, sin contar los ocultos (p. ej. los "._algo.md" que deja macOS al copiar). */
    private List<File> archivosMarkdown(File carpeta) {
        File[] archivos = carpeta.listFiles(f -> f.isFile()
                && !f.getName().startsWith(".")
                && f.getName().toLowerCase(Locale.ROOT).endsWith(EXTENSION_MARKDOWN));
        if (archivos == null) {
            return List.of();
        }
        return Arrays.stream(archivos)
                .sorted(Comparator.comparing(File::getName))
                .toList();
    }

    private URL carpetaComoUrl(File carpeta, String nombreLeccion) throws IOException {
        try {
            // File.toURI() agrega '/' final para directorios, igual que necesita
            // HTMLDocument.setBase(...) para resolver imágenes relativas.
            return carpeta.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IOException("No se pudo resolver la ruta de la carpeta '" + nombreLeccion + "'.", e);
        }
    }

    private List<File> ordenadasPorNombre(File[] carpetas) {
        return Arrays.stream(carpetas)
                .sorted(Comparator.comparing(File::getName))
                .toList();
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

    // ------------------------------------------------------------------
    // Helpers exclusivos del camino de classpath
    // ------------------------------------------------------------------

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
