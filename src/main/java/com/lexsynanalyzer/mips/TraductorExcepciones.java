package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;

import java.util.HashSet;
import java.util.Set;

/** Traduce los manejadores TAC a una pila global que permite desenrollar frames en runtime. */
public final class TraductorExcepciones {

    private static final String TOPE = "__tope_man";
    private static final String MANEJADORES = "__manejadores";
    private static final int MAXIMO_NIVELES = 16;

    private final Set<String> etiquetasCatch = new HashSet<>();

    private TraductorExcepciones() {
    }

    static void registrarEn(GeneradorMIPS generador) {
        TraductorExcepciones traductor = new TraductorExcepciones();
        generador.registrar(Instruccion.OP_TRY, traductor::iniciarTry);
        generador.registrar(Instruccion.OP_FIN_TRY, TraductorExcepciones::finalizarTry);
        generador.registrar(Instruccion.OP_CATCH, TraductorExcepciones::capturar);
        generador.registrar(Instruccion.OP_ETIQUETA, traductor::etiqueta);
    }

    private void iniciarTry(Instruccion instruccion, ContextoFuncion contexto) {
        ProgramaMips programa = contexto.programa();
        etiquetasCatch.add(instruccion.a1());
        programa.requerir(GeneradorMIPS.LANZAR);
        programa.emit(InstruccionMips.de("lw", "$v1", TOPE));
        programa.emit(InstruccionMips.de("slti", "$t9", "$v1", String.valueOf(MAXIMO_NIVELES)));
        programa.emit(InstruccionMips.de("beqz", "$t9", "__error_try_overflow"));
        programa.emit(InstruccionMips.de("sll", "$t9", "$v1", "3"));
        programa.emit(InstruccionMips.de("sll", "$t8", "$v1", "2"));
        programa.emit(InstruccionMips.de("addu", "$t9", "$t9", "$t8"));
        programa.emit(InstruccionMips.de("la", "$t8", MANEJADORES));
        programa.emit(InstruccionMips.de("addu", "$t8", "$t8", "$t9"));
        programa.emit(InstruccionMips.de("la", "$t9", instruccion.a1()));
        programa.emit(InstruccionMips.de("sw", "$t9", "0($t8)"));
        programa.emit(InstruccionMips.de("sw", "$sp", "4($t8)"));
        programa.emit(InstruccionMips.de("sw", "$fp", "8($t8)"));
        programa.emit(InstruccionMips.de("addiu", "$t9", "$v1", "1"));
        programa.emit(InstruccionMips.de("sw", "$t9", TOPE));
    }

    private static void finalizarTry(Instruccion instruccion, ContextoFuncion contexto) {
        ProgramaMips programa = contexto.programa();
        programa.emit(InstruccionMips.de("lw", "$t8", TOPE));
        programa.emit(InstruccionMips.de("beqz", "$t8", "__error_endtry_sin_try"));
        programa.emit(InstruccionMips.de("addiu", "$t8", "$t8", "-1"));
        programa.emit(InstruccionMips.de("sw", "$t8", TOPE));
    }

    private static void capturar(Instruccion instruccion, ContextoFuncion contexto) {
        contexto.programa().emit(InstruccionMips.de("sw", "$v1", contexto.ubicaciones().direccion(instruccion.a1())));
    }

    private void etiqueta(Instruccion instruccion, ContextoFuncion contexto) {
        if (etiquetasCatch.remove(instruccion.a1())) {
            contexto.registros().descartarEnCaptura();
        } else {
            contexto.registros().finDeBloque();
        }
        contexto.programa().emit(InstruccionMips.etiqueta(instruccion.a1()));
    }
}
