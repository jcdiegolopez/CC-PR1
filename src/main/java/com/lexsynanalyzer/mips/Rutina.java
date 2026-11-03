package com.lexsynanalyzer.mips;

/**
 * Rutina del runtime ({@code __concat}, {@code __lanzar}, …). Emite su etiqueta y su cuerpo al final
 * de {@code .text}; puede registrar datos y {@link ProgramaMips#requerir requerir} otras rutinas.
 *
 * <p>Convención: una rutina solo usa {@code $a0}–{@code $a3}, {@code $v0}, {@code $v1}, {@code $t8}
 * y {@code $t9}. Así llamarla con {@code jal} no obliga a vaciar {@code $t0}–{@code $t7}.
 */
@FunctionalInterface
public interface Rutina {

    void emitir(ProgramaMips programa);
}
