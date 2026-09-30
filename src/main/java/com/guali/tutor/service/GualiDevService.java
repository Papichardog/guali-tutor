package com.guali.tutor.service;

import edu.umg.guali.compiler.CompileResult;
import edu.umg.guali.compiler.ExecuteResult;
import edu.umg.guali.compiler.GualiDev;

/**
 * Único punto de contacto con la librería guali-dev. No agrega lógica propia:
 * solo delega a la API real (GualiDev.compile / GualiDev.execute).
 */
public class GualiDevService {

    public CompileResult compilar(String codigoFuente, String jdkHome) {
        return GualiDev.compile(codigoFuente, jdkHome);
    }

    public ExecuteResult ejecutar(String nombreClase, String jdkHome) {
        return GualiDev.execute(nombreClase, jdkHome);
    }
}
