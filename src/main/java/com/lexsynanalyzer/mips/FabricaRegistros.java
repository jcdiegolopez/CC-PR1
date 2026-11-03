package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;

import java.util.List;

/**
 * Crea el {@link AsignadorRegistros} de una función. Recibe el cuerpo TAC completo de la función
 * (de {@code function} a {@code endfunc}) porque {@link GetReg} lo analiza antes de traducir.
 */
@FunctionalInterface
public interface FabricaRegistros {

    AsignadorRegistros crear(ProgramaMips programa, ResolvedorUbicaciones ubicaciones, List<Instruccion> cuerpo);

    /** Carga y guarda en cada instrucción. Lento pero siempre correcto: es la referencia. */
    FabricaRegistros INGENUO = (programa, ubicaciones, cuerpo) -> new RegistrosIngenuos(programa, ubicaciones);

    /** {@code getReg} del Dragon Book con descriptores, derrame y variables vivas. */
    FabricaRegistros GETREG = GetReg::new;
}
