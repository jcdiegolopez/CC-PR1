package com.lexsynanalyzer.mips;

/**
 * Lo que un traductor necesita mientras traduce el cuerpo de una función del TAC.
 *
 * @param funcion     etiqueta TAC de la función ({@code main}, {@code suma}, {@code Perro_hablar})
 * @param tamanoFrame bytes del registro de activación, tomados de {@code function f, N}
 */
public record ContextoFuncion(ProgramaMips programa, AsignadorRegistros registros, String funcion,
                              int tamanoFrame) {

    public boolean esMain() {
        return GeneradorMIPS.MAIN.equals(funcion);
    }
}
