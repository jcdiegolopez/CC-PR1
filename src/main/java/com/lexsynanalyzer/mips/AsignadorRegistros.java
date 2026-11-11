package com.lexsynanalyzer.mips;

/**
 * Decide en qué registro está cada operando del TAC (la función {@code getReg} del Dragon Book).
 * Los traductores nunca escriben {@code $tN} a mano: siempre piden el registro aquí.
 *
 * <p>Protocolo para traducir una instrucción TAC, por ejemplo {@code t0 = a + b}:
 * <pre>{@code
 * String ra = regs.leer("a");               // puede emitir lw / li / la
 * String rb = regs.leer("b");
 * String rt = regs.paraResultado("t0");     // puede emitir sw (derrame)
 * programa.emit(InstruccionMips.de("addu", rt, ra, rb));
 * regs.definido("t0", rt);                  // puede emitir sw
 * }</pre>
 * {@link GeneradorMIPS} llama a {@link #antesDeInstruccion} y a {@link #siguienteInstruccion}
 * alrededor de cada instrucción TAC; los traductores no lo hacen.
 *
 * <p>Reglas para los traductores:
 * <ul>
 *   <li>El resultado siempre va en el registro de {@link #paraResultado}, nunca en uno de
 *       {@link #leer}: ese registro puede tener el valor de otra variable que todavía no se guardó.</li>
 *   <li>Una copia {@code x = y} se avisa con {@link #copia}, no con {@code leer} + {@code definido}.</li>
 *   <li>Antes de saltar ({@code j}, {@code beqz}, {@code bnez}, {@code return}) se llama a
 *       {@link #finDeBloque}; antes de {@code jal} a una función Compiscript, a {@link #antesDeLlamada}.
 *       Las rutinas del runtime ({@code __concat}, …) no tocan {@code $t0}–{@code $t7} y no lo necesitan.</li>
 *   <li>Antes de una instrucción que puede lanzar una excepción (división, índice) se llama a
 *       {@link #antesDePosibleExcepcion}.</li>
 * </ul>
 *
 * <p>Solo se reparten {@code $t0}–{@code $t7}. {@code $t8} y {@code $t9} quedan libres para el
 * generador (direcciones, chequeos de límites).
 */
public interface AsignadorRegistros {

    /** Índice, dentro del cuerpo de la función, de la instrucción TAC que se va a traducir. */
    default void antesDeInstruccion(int indice) {
    }

    /** Registro que contiene el valor de {@code operando}; si hace falta emite la carga. */
    String leer(String operando);

    /**
     * Registro donde la instrucción actual dejará el valor de {@code destino}. Puede coincidir con un
     * registro devuelto por {@link #leer} en la misma instrucción, si ese valor ya no se necesita.
     */
    String paraResultado(String destino);

    /** Avisa que la instrucción emitida escribió {@code destino} en {@code registro}. */
    void definido(String destino, String registro);

    /** {@code destino = origen}: después de esto {@code destino} tiene el valor de {@code origen}. */
    void copia(String destino, String origen);

    /** Fin de la instrucción TAC actual: los registros que usó ya pueden reasignarse. */
    void siguienteInstruccion();

    /**
     * Fin de un bloque básico (antes de una etiqueta, un salto o un {@code return}): todo valor que
     * solo esté en un registro se guarda en memoria, porque el código al que se salta lo leerá de ahí.
     * Después de esto ningún registro tiene un valor conocido.
     */
    void finDeBloque();

    /**
     * Antes de un {@code jal}: igual que {@link #finDeBloque}, porque la función llamada puede
     * sobrescribir {@code $t0}–{@code $t7} (caller-saved) y puede leer globales de memoria.
     */
    void antesDeLlamada();

    /**
     * Antes de una instrucción que puede saltar a un {@code catch}: las variables que solo estén en
     * un registro se guardan, porque el {@code catch} las lee de memoria. Los registros siguen valiendo.
     */
    void antesDePosibleExcepcion();

    /**
     * Descarta los descriptores al entrar en un {@code catch}. El salto de excepción puede haber
     * omitido escrituras y resultados que el camino normal sí habría producido.
     */
    default void descartarEnCaptura() {
        finDeBloque();
    }
}
