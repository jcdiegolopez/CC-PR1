package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;

/** Traduce una instrucción TAC a MIPS. Cada dueño registra los suyos en {@link GeneradorMIPS}. */
@FunctionalInterface
public interface Traductor {

    void traducir(Instruccion instruccion, ContextoFuncion contexto);
}
