package com.lexsynanalyzer.mips;

/**
 * Decide en qué registro está cada operando del TAC (la función {@code getReg} del Dragon Book).
 * Los traductores nunca escriben {@code $tN} a mano: siempre piden el registro aquí.
 *
 * <p>Protocolo para traducir una instrucción TAC, por ejemplo {@code t0 = a + b}:
 * <pre>{@code
 * String ra = regs.leer("a");               // puede emitir lw / li / la
 * String rb = regs.leer("b");
 * String rt = regs.paraResultado("t0");
 * programa.emit(InstruccionMips.de("addu", rt, ra, rb));
 * regs.definido("t0", rt);                  // puede emitir sw
 * regs.siguienteInstruccion();
 * }</pre>
 *
 * <p>Solo se reparten {@code $t0}–{@code $t7}. {@code $t8} y {@code $t9} quedan libres para el
 * generador (direcciones, chequeos de límites).
 */
public interface AsignadorRegistros {

    /** Registro que contiene el valor de {@code operando}; si hace falta emite la carga. */
    String leer(String operando);

    /**
     * Registro donde la instrucción actual dejará el valor de {@code destino}. Puede coincidir con un
     * registro devuelto por {@link #leer} en la misma instrucción.
     */
    String paraResultado(String destino);

    /** Avisa que {@code destino} ya quedó en {@code registro}; desde aquí ese valor es el vigente. */
    void definido(String destino, String registro);

    /** Fin de la instrucción TAC actual: los registros que usó ya pueden reasignarse. */
    void siguienteInstruccion();

    /**
     * Fin de un bloque básico (antes de una etiqueta, un salto o un {@code return}): todo valor que
     * solo esté en un registro se guarda en memoria, porque el código al que se salta lo leerá de ahí.
     */
    void finDeBloque();

    /**
     * Antes de un {@code jal}: igual que {@link #finDeBloque} y además olvida lo que hay en
     * {@code $t0}–{@code $t7}, porque la función llamada puede sobrescribirlos (caller-saved).
     */
    void antesDeLlamada();
}
