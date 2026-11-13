package com.lexsynanalyzer.mips;

/**
 * Rutinas del runtime para memoria dinámica: reservar bloques en el heap y el error de índice.
 *
 * <p>Arreglos y objetos viven en el heap que crece con {@code sbrk} (syscall 9). MARS entrega esa
 * memoria en cero, así que los campos sin inicializador empiezan en {@code 0} / {@code null}.
 */
public final class RuntimeMemoria {

    /** {@code $a0} = bytes → {@code $v0} = dirección del bloque, alineada a palabra. */
    public static final String ALLOC = "__alloc";
    /** Rutina a la que salta un acceso {@code a[i]} con {@code i < 0} o {@code i >= len(a)}. */
    public static final String ERROR_INDICE = "__error_indice";
    public static final String MENSAJE_INDICE = "Índice fuera de rango";

    private RuntimeMemoria() {
    }

    static void registrarEn(GeneradorMIPS generador) {
        generador.registrarRutina(ALLOC, RuntimeMemoria::alloc);
        generador.registrarRutina(ERROR_INDICE, RuntimeMemoria::errorIndice);
    }

    /**
     * Redondea el tamaño a múltiplo de 4 antes de pedirlo, para que el siguiente bloque también quede
     * alineado a palabra ({@code lw}/{@code sw} fallan con direcciones desalineadas). Solo usa
     * {@code $a0} y {@code $v0}, así que no obliga a vaciar {@code $t0}–{@code $t7}.
     */
    private static void alloc(ProgramaMips programa) {
        programa.emit(InstruccionMips.etiqueta(ALLOC));
        programa.emit(InstruccionMips.de("addiu", "$a0", "$a0", "3"));
        programa.emit(InstruccionMips.de("srl", "$a0", "$a0", "2"));
        programa.emit(InstruccionMips.de("sll", "$a0", "$a0", "2"));
        programa.emit(InstruccionMips.de("li", "$v0", "9"));
        programa.emit(InstruccionMips.de("syscall"));
        programa.emit(InstruccionMips.de("jr", "$ra"));
    }

    private static void errorIndice(ProgramaMips programa) {
        programa.requerir(GeneradorMIPS.LANZAR);
        programa.emit(InstruccionMips.etiqueta(ERROR_INDICE));
        programa.emit(InstruccionMips.de("la", "$a0", programa.cadena(MENSAJE_INDICE)));
        programa.emit(InstruccionMips.de("j", GeneradorMIPS.LANZAR));
    }
}
