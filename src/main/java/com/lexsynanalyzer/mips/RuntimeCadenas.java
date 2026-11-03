package com.lexsynanalyzer.mips;

import java.util.List;

/**
 * Rutinas del runtime para strings. Un string es la dirección de sus bytes, terminados en {@code \0}
 * (como {@code .asciiz}). Cada rutina se emite solo si el programa la usa.
 *
 * <p>Solo usan {@code $a0}–{@code $a3}, {@code $v0}, {@code $t8} y {@code $t9} (convención de
 * {@link Rutina}). Los strings nuevos se piden al sistema con {@code sbrk} (syscall 9), redondeando
 * a múltiplo de 4 para que lo que se reserve después siga alineado a palabra.
 */
public final class RuntimeCadenas {

    /** {@code $a0} = a, {@code $a1} = b → {@code $v0} = string nuevo con a seguido de b. */
    public static final String CONCAT = "__concat";
    /** {@code $a0} = s → {@code $v0} = cantidad de caracteres. */
    public static final String STRLEN = "__strlen";
    /** {@code $a0} = a, {@code $a1} = b → {@code $v0} = 1 si tienen el mismo contenido, 0 si no. */
    public static final String STREQ = "__streq";
    /** {@code $a0} = s, {@code $a1} = i → {@code $v0} = string nuevo con el carácter i. */
    public static final String CHARAT = "__charat";
    /** {@code $a0} = 0/1 → imprime {@code false}/{@code true}. */
    public static final String PRINT_BOOL = "__print_bool";

    private RuntimeCadenas() {
    }

    static void registrarEn(GeneradorMIPS generador) {
        generador.registrarRutina(CONCAT, RuntimeCadenas::concat);
        generador.registrarRutina(STRLEN, RuntimeCadenas::strlen);
        generador.registrarRutina(STREQ, RuntimeCadenas::streq);
        generador.registrarRutina(CHARAT, RuntimeCadenas::charat);
        generador.registrarRutina(PRINT_BOOL, RuntimeCadenas::printBool);
    }

    /**
     * Mide los dos strings, reserva {@code len(a) + len(b) + 1} bytes y copia byte a byte. El
     * {@code \0} final se copia junto con b.
     */
    private static void concat(ProgramaMips p) {
        emitir(p, CONCAT,
                i("move", "$a2", "$zero"),                      // a2 = len(a)
                l("__concat_largo_a"),
                i("addu", "$t8", "$a0", "$a2"),
                i("lb", "$t8", "0($t8)"),
                i("beqz", "$t8", "__concat_medir_b"),
                i("addiu", "$a2", "$a2", "1"),
                i("j", "__concat_largo_a"),
                l("__concat_medir_b"),
                i("move", "$a3", "$zero"),                      // a3 = len(b)
                l("__concat_largo_b"),
                i("addu", "$t8", "$a1", "$a3"),
                i("lb", "$t8", "0($t8)"),
                i("beqz", "$t8", "__concat_reservar"),
                i("addiu", "$a3", "$a3", "1"),
                i("j", "__concat_largo_b"),
                l("__concat_reservar"),
                i("move", "$t9", "$a0"),                        // t9 = cursor de lectura de a
                i("addu", "$a0", "$a2", "$a3"),
                i("addiu", "$a0", "$a0", "4"),                  // + '\0', redondeado a 4
                i("srl", "$a0", "$a0", "2"),
                i("sll", "$a0", "$a0", "2"),
                i("li", "$v0", "9"),
                i("syscall"),
                i("move", "$a2", "$v0"),                        // a2 = cursor de escritura
                l("__concat_copiar_a"),
                i("lb", "$t8", "0($t9)"),
                i("beqz", "$t8", "__concat_copiar_b"),
                i("sb", "$t8", "0($a2)"),
                i("addiu", "$t9", "$t9", "1"),
                i("addiu", "$a2", "$a2", "1"),
                i("j", "__concat_copiar_a"),
                l("__concat_copiar_b"),
                i("lb", "$t8", "0($a1)"),
                i("sb", "$t8", "0($a2)"),
                i("beqz", "$t8", "__concat_fin"),
                i("addiu", "$a1", "$a1", "1"),
                i("addiu", "$a2", "$a2", "1"),
                i("j", "__concat_copiar_b"),
                l("__concat_fin"),
                i("jr", "$ra"));
    }

    private static void strlen(ProgramaMips p) {
        emitir(p, STRLEN,
                i("move", "$v0", "$zero"),
                l("__strlen_ciclo"),
                i("addu", "$t8", "$a0", "$v0"),
                i("lb", "$t8", "0($t8)"),
                i("beqz", "$t8", "__strlen_fin"),
                i("addiu", "$v0", "$v0", "1"),
                i("j", "__strlen_ciclo"),
                l("__strlen_fin"),
                i("jr", "$ra"));
    }

    /** Avanza mientras los dos bytes sean iguales; si llegan juntos a {@code \0}, son iguales. */
    private static void streq(ProgramaMips p) {
        emitir(p, STREQ,
                i("lb", "$t8", "0($a0)"),
                i("lb", "$t9", "0($a1)"),
                i("bne", "$t8", "$t9", "__streq_distintos"),
                i("beqz", "$t8", "__streq_iguales"),
                i("addiu", "$a0", "$a0", "1"),
                i("addiu", "$a1", "$a1", "1"),
                i("j", STREQ),
                l("__streq_iguales"),
                i("li", "$v0", "1"),
                i("jr", "$ra"),
                l("__streq_distintos"),
                i("move", "$v0", "$zero"),
                i("jr", "$ra"));
    }

    /** No valida el índice: quien la llama ya lo hizo (el {@code foreach} va de 0 a len - 1). */
    private static void charat(ProgramaMips p) {
        emitir(p, CHARAT,
                i("addu", "$t9", "$a0", "$a1"),
                i("lb", "$t9", "0($t9)"),
                i("li", "$a0", "4"),
                i("li", "$v0", "9"),
                i("syscall"),
                i("sb", "$t9", "0($v0)"),
                i("sb", "$zero", "1($v0)"),
                i("jr", "$ra"));
    }

    private static void printBool(ProgramaMips p) {
        String verdadero = p.cadena("true");
        String falso = p.cadena("false");
        emitir(p, PRINT_BOOL,
                i("beqz", "$a0", "__print_bool_falso"),
                i("la", "$a0", verdadero),
                i("j", "__print_bool_imprimir"),
                l("__print_bool_falso"),
                i("la", "$a0", falso),
                l("__print_bool_imprimir"),
                i("li", "$v0", "4"),
                i("syscall"),
                i("jr", "$ra"));
    }

    private static void emitir(ProgramaMips programa, String nombre, InstruccionMips... cuerpo) {
        programa.emit(InstruccionMips.etiqueta(nombre));
        List.of(cuerpo).forEach(programa::emit);
    }

    private static InstruccionMips i(String op, String... operandos) {
        return InstruccionMips.de(op, operandos);
    }

    private static InstruccionMips l(String etiqueta) {
        return InstruccionMips.etiqueta(etiqueta);
    }
}
