package com.guali.tutor.service;

import java.io.File;

/**
 * Responsable únicamente de determinar si una ruta de filesystem corresponde
 * a una instalación de JDK utilizable. No valida una versión específica
 * (p. ej. que sea exactamente Java 25): solo confirma que la carpeta existe
 * y contiene los ejecutables de compilador (javac) y máquina virtual (java),
 * que es lo que GualiDev necesita para funcionar.
 */
public class ValidadorJdk {

    public boolean esJdkValido(String rutaJdk) {
        if (rutaJdk == null || rutaJdk.isBlank()) {
            return false;
        }
        File carpetaJdk = new File(rutaJdk);
        if (!carpetaJdk.isDirectory()) {
            return false;
        }
        File carpetaBin = new File(carpetaJdk, "bin");
        return existeEjecutable(carpetaBin, "javac") && existeEjecutable(carpetaBin, "java");
    }

    private boolean existeEjecutable(File carpetaBin, String nombreBase) {
        File ejecutableUnix = new File(carpetaBin, nombreBase);
        File ejecutableWindows = new File(carpetaBin, nombreBase + ".exe");
        return ejecutableUnix.isFile() || ejecutableWindows.isFile();
    }
}
