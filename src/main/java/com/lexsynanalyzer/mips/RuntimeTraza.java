package com.lexsynanalyzer.mips;

import java.util.List;

/**
 * Rutinas opcionales que imprimen las entradas y salidas de los frames de activación ({@code -> f} y
 * {@code <- f}). Las flechas son ASCII a propósito: MARS guarda cada carácter de un {@code .asciiz} en un
 * byte, así que {@code →} (U+2192) se imprimiría corrupto; las tildes (Latin-1) sí salen bien.
 */
final class RuntimeTraza {

    private static final String ENTRAR = "__trace_enter";
    private static final String SALIR = "__trace_leave";
    private static final String PROFUNDIDAD = "__trace_depth";

    private RuntimeTraza() {
    }

    static void registrarEn(GeneradorMIPS generador) {
        generador.registrarRutina(ENTRAR, programa -> emitirRutina(programa, true));
        generador.registrarRutina(SALIR, programa -> emitirRutina(programa, false));
    }

    static void entrar(ContextoFuncion contexto) {
        if (!contexto.traza()) {
            return;
        }
        ProgramaMips programa = contexto.programa();
        programa.dato(PROFUNDIDAD, ".word 0");
        programa.requerir(ENTRAR);
        programa.emit(InstruccionMips.de("la", "$a0", programa.cadena("-> " + contexto.funcion())));
        programa.emit(InstruccionMips.de("jal", ENTRAR));
    }

    static void salir(ContextoFuncion contexto) {
        if (!contexto.traza()) {
            return;
        }
        ProgramaMips programa = contexto.programa();
        programa.requerir(SALIR);
        programa.emit(InstruccionMips.de("la", "$a0", programa.cadena("<- " + contexto.funcion())));
        programa.emit(InstruccionMips.de("jal", SALIR));
    }

    private static void emitirRutina(ProgramaMips programa, boolean entrada) {
        programa.emit(InstruccionMips.etiqueta(entrada ? ENTRAR : SALIR));
        programa.emit(InstruccionMips.de("move", "$v1", "$a0"));
        if (!entrada) {
            programa.emit(InstruccionMips.de("move", "$a3", "$v0"));
        }
        programa.emit(InstruccionMips.de("lw", "$t8", PROFUNDIDAD));
        if (!entrada) {
            programa.emit(InstruccionMips.de("addiu", "$t8", "$t8", "-1"));
            programa.emit(InstruccionMips.de("sw", "$t8", PROFUNDIDAD));
        }
        programa.emit(InstruccionMips.de("move", "$t9", "$t8"));
        programa.emit(InstruccionMips.etiqueta(entrada ? "__trace_enter_indent" : "__trace_leave_indent"));
        programa.emit(InstruccionMips.de("beqz", "$t9",
                entrada ? "__trace_enter_print" : "__trace_leave_print"));
        programa.emit(InstruccionMips.de("li", "$a0", "32"));
        programa.emit(InstruccionMips.de("li", "$v0", "11"));
        programa.emit(InstruccionMips.de("syscall"));
        programa.emit(InstruccionMips.de("li", "$a0", "32"));
        programa.emit(InstruccionMips.de("li", "$v0", "11"));
        programa.emit(InstruccionMips.de("syscall"));
        programa.emit(InstruccionMips.de("addiu", "$t9", "$t9", "-1"));
        programa.emit(InstruccionMips.de("j",
                entrada ? "__trace_enter_indent" : "__trace_leave_indent"));
        programa.emit(InstruccionMips.etiqueta(entrada ? "__trace_enter_print" : "__trace_leave_print"));
        programa.emit(InstruccionMips.de("move", "$a0", "$v1"));
        programa.emit(InstruccionMips.de("li", "$v0", "4"));
        programa.emit(InstruccionMips.de("syscall"));
        programa.emit(InstruccionMips.de("li", "$a0", "10"));
        programa.emit(InstruccionMips.de("li", "$v0", "11"));
        programa.emit(InstruccionMips.de("syscall"));
        if (entrada) {
            programa.emit(InstruccionMips.de("lw", "$t8", PROFUNDIDAD));
            programa.emit(InstruccionMips.de("addiu", "$t8", "$t8", "1"));
            programa.emit(InstruccionMips.de("sw", "$t8", PROFUNDIDAD));
        } else {
            programa.emit(InstruccionMips.de("move", "$v0", "$a3"));
        }
        programa.emit(InstruccionMips.de("jr", "$ra"));
    }
}
