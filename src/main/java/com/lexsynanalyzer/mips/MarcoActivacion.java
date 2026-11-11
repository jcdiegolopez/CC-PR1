package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;

/** Traduce los límites de función y los retornos a prólogos y epílogos MIPS. */
public final class MarcoActivacion {

    private MarcoActivacion() {
    }

    static void registrarEn(GeneradorMIPS generador) {
        generador.registrar(Instruccion.OP_FUNCION, MarcoActivacion::iniciar);
        generador.registrar(Instruccion.OP_FIN_FUNCION, MarcoActivacion::finalizar);
        generador.registrar(Instruccion.OP_RETORNO, MarcoActivacion::retornar);
        RuntimeTraza.registrarEn(generador);
    }

    private static void iniciar(Instruccion instruccion, ContextoFuncion contexto) {
        ProgramaMips programa = contexto.programa();
        if (contexto.esMain() && contexto.layout() != null) {
            Ubicaciones.registrarGlobales(programa, contexto.layout());
        }
        programa.emit(InstruccionMips.etiqueta(GeneradorMIPS.etiquetaFuncion(contexto.funcion())));
        programa.emit(InstruccionMips.de("addiu", "$sp", "$sp", String.valueOf(-contexto.tamanoFrame())));
        programa.emit(InstruccionMips.de("sw", "$ra", "0($sp)"));
        programa.emit(InstruccionMips.de("sw", "$fp", "8($sp)"));
        programa.emit(InstruccionMips.de("sw", "$zero", "16($sp)"));
        programa.emit(InstruccionMips.de("move", "$fp", "$sp"));
        RuntimeTraza.entrar(contexto);
    }

    private static void finalizar(Instruccion instruccion, ContextoFuncion contexto) {
        ProgramaMips programa = contexto.programa();
        contexto.registros().finDeBloque();
        programa.emit(InstruccionMips.etiqueta(etiquetaFin(contexto.funcion())));
        RuntimeTraza.salir(contexto);
        if (contexto.esMain()) {
            programa.emit(InstruccionMips.de("li", "$v0", "10"));
            programa.emit(InstruccionMips.de("syscall"));
            return;
        }
        programa.emit(InstruccionMips.de("lw", "$ra", "0($fp)"));
        programa.emit(InstruccionMips.de("addiu", "$sp", "$fp", String.valueOf(contexto.tamanoFrame())));
        programa.emit(InstruccionMips.de("lw", "$fp", "8($fp)"));
        programa.emit(InstruccionMips.de("jr", "$ra"));
    }

    private static void retornar(Instruccion instruccion, ContextoFuncion contexto) {
        String valor = instruccion.a1() == null ? null : contexto.registros().leer(instruccion.a1());
        contexto.registros().finDeBloque();
        if (valor != null) {
            contexto.programa().emit(InstruccionMips.de("move", "$v0", valor));
        }
        contexto.programa().emit(InstruccionMips.de("j", etiquetaFin(contexto.funcion())));
    }

    static String etiquetaFin(String funcion) {
        return GeneradorMIPS.etiquetaFuncion(funcion) + "_fin";
    }
}
