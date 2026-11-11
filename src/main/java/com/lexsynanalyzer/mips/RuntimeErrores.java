package com.lexsynanalyzer.mips;

/** Implementa el lanzamiento de errores y el desenrollado hasta el manejador activo más cercano. */
public final class RuntimeErrores {

    private static final String PREFIJO = "Error en tiempo de ejecución: ";
    private static final String MENSAJE_OVERFLOW = "Demasiados bloques try anidados";
    private static final String MENSAJE_UNDERFLOW = "Error interno: pila de excepciones vacía";

    private RuntimeErrores() {
    }

    static void registrarEn(GeneradorMIPS generador) {
        generador.registrarRutina(GeneradorMIPS.LANZAR, RuntimeErrores::emitir);
    }

    private static void emitir(ProgramaMips programa) {
        programa.dato("__tope_man", ".word 0");
        programa.dato("__manejadores", ".space 192");
        programa.emit(InstruccionMips.etiqueta(GeneradorMIPS.LANZAR));
        programa.emit(InstruccionMips.de("lw", "$t8", "__tope_man"));
        programa.emit(InstruccionMips.de("beqz", "$t8", "__lanzar_sin_manejador"));
        programa.emit(InstruccionMips.de("addiu", "$t8", "$t8", "-1"));
        programa.emit(InstruccionMips.de("sw", "$t8", "__tope_man"));
        programa.emit(InstruccionMips.de("sll", "$t9", "$t8", "3"));
        programa.emit(InstruccionMips.de("sll", "$t8", "$t8", "2"));
        programa.emit(InstruccionMips.de("addu", "$t9", "$t9", "$t8"));
        programa.emit(InstruccionMips.de("la", "$t8", "__manejadores"));
        programa.emit(InstruccionMips.de("addu", "$t9", "$t9", "$t8"));
        programa.emit(InstruccionMips.de("lw", "$t8", "0($t9)"));
        programa.emit(InstruccionMips.de("lw", "$sp", "4($t9)"));
        programa.emit(InstruccionMips.de("lw", "$fp", "8($t9)"));
        programa.emit(InstruccionMips.de("move", "$v1", "$a0"));
        programa.emit(InstruccionMips.de("jr", "$t8"));

        programa.emit(InstruccionMips.etiqueta("__lanzar_sin_manejador"));
        programa.emit(InstruccionMips.de("move", "$t8", "$a0"));
        programa.emit(InstruccionMips.de("la", "$a0", programa.cadena(PREFIJO)));
        programa.emit(InstruccionMips.de("li", "$v0", "4"));
        programa.emit(InstruccionMips.de("syscall"));
        programa.emit(InstruccionMips.de("move", "$a0", "$t8"));
        programa.emit(InstruccionMips.de("li", "$v0", "4"));
        programa.emit(InstruccionMips.de("syscall"));
        saltoDeLinea(programa);
        programa.emit(InstruccionMips.de("li", "$a0", "1"));
        programa.emit(InstruccionMips.de("li", "$v0", "17"));
        programa.emit(InstruccionMips.de("syscall"));

        programa.emit(InstruccionMips.etiqueta("__error_try_overflow"));
        programa.emit(InstruccionMips.de("la", "$a0", programa.cadena(MENSAJE_OVERFLOW)));
        programa.emit(InstruccionMips.de("j", GeneradorMIPS.LANZAR));
        programa.emit(InstruccionMips.etiqueta("__error_endtry_sin_try"));
        programa.emit(InstruccionMips.de("la", "$a0", programa.cadena(MENSAJE_UNDERFLOW)));
        programa.emit(InstruccionMips.de("j", GeneradorMIPS.LANZAR));
    }

    private static void saltoDeLinea(ProgramaMips programa) {
        programa.emit(InstruccionMips.de("li", "$a0", "10"));
        programa.emit(InstruccionMips.de("li", "$v0", "11"));
        programa.emit(InstruccionMips.de("syscall"));
    }
}
