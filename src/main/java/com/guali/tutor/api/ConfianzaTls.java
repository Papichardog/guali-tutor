package com.guali.tutor.api;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Prepara el contexto TLS del cliente: confía ÚNICAMENTE en el certificado de la API (guali-dev.crt). La API usa un
 * certificado autofirmado de desarrollo, que ninguna autoridad pública avala, así que la confianza se da de forma
 * explícita y no se desactiva ninguna verificación: el certificado se comprueba (vigencia, firma y que corresponda al
 * nombre del servidor) igual que uno oficial. Aceptar solo este certificado implica que el cliente rechaza
 * cualquier otro servidor, incluso uno con un certificado "válido".
 */
public final class ConfianzaTls {

    private ConfianzaTls() {
    }

    /**
     * Carga el certificado de {@code ubicacion} ("classpath:ruta" o "file:/ruta/absoluta") y devuelve un contexto
     * TLS que confía solo en él.
     */
    public static SSLContext desdeUbicacion(String ubicacion) throws ApiException {
        try (InputStream in = abrir(ubicacion)) {
            return contextoQueConfiaEn(in, ubicacion);
        } catch (NoSuchFileException e) {
            throw ApiException.configuracion("No existe el certificado configurado: " + ubicacion
                    + ". Copie guali-dev.crt del proyecto guali-tutor-api (carpeta certs) a esa ubicación.", e);
        } catch (IOException e) {
            throw ApiException.configuracion("No se pudo leer el certificado " + ubicacion + ": " + e.getMessage(), e);
        }
    }

    /** Construye el contexto TLS desde un certificado en formato PEM ("-----BEGIN CERTIFICATE-----") o DER. */
    public static SSLContext contextoQueConfiaEn(InputStream certificado, String origen) throws ApiException {
        try {
            Certificate leido = CertificateFactory.getInstance("X.509").generateCertificate(certificado);
            if (!(leido instanceof X509Certificate cert)) {
                throw ApiException.configuracion("El archivo " + origen + " no contiene un certificado X.509.");
            }
            comprobarVigencia(cert, origen);

            KeyStore almacen = KeyStore.getInstance("PKCS12");
            almacen.load(null, null);                       // almacén vacío, solo en memoria
            almacen.setCertificateEntry("guali-dev", cert); // el único certificado en el que se confía

            TrustManagerFactory fabrica = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            fabrica.init(almacen);
            SSLContext contexto = SSLContext.getInstance("TLS");
            contexto.init(null, fabrica.getTrustManagers(), null);
            return contexto;
        } catch (GeneralSecurityException | IOException e) {
            throw ApiException.configuracion("El archivo " + origen
                    + " no es un certificado válido (se esperaba guali-dev.crt en formato PEM): " + e.getMessage(), e);
        }
    }

    private static InputStream abrir(String ubicacion) throws ApiException, IOException {
        if (ubicacion == null || ubicacion.isBlank()) {
            throw ApiException.configuracion("No se indicó el certificado de la API (api.certificado).");
        }
        if (ubicacion.startsWith("classpath:")) {
            String recurso = ubicacion.substring("classpath:".length());
            InputStream in = ConfianzaTls.class.getClassLoader().getResourceAsStream(recurso);
            if (in == null) {
                throw ApiException.configuracion("No se encontró el certificado " + ubicacion
                        + " dentro de la aplicación. Copie guali-dev.crt del proyecto guali-tutor-api (carpeta certs) "
                        + "a src/main/resources/" + recurso + ".");
            }
            return in;
        }
        if (ubicacion.startsWith("file:")) {
            return Files.newInputStream(Path.of(ubicacion.substring("file:".length())));
        }
        throw ApiException.configuracion("api.certificado debe empezar por classpath: o file: (valor: " + ubicacion + ").");
    }

    private static void comprobarVigencia(X509Certificate cert, String origen) throws ApiException {
        DateTimeFormatter dia = DateTimeFormatter.ISO_LOCAL_DATE;
        try {
            cert.checkValidity();
        } catch (CertificateExpiredException e) {
            throw ApiException.configuracion("El certificado " + origen + " venció el "
                    + dia.format(cert.getNotAfter().toInstant().atZone(ZoneId.systemDefault()))
                    + ". Genere uno nuevo en guali-tutor-api (keytool) y vuelva a exportar guali-dev.crt.", e);
        } catch (CertificateNotYetValidException e) {
            throw ApiException.configuracion("El certificado " + origen + " aún no es válido (comienza el "
                    + dia.format(cert.getNotBefore().toInstant().atZone(ZoneId.systemDefault()))
                    + "). Revise la fecha del equipo.", e);
        }
    }
}
