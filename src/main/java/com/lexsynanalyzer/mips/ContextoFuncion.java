package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.semantic.Layout;

import java.util.Map;
import java.util.Objects;

/**
 * Lo que un traductor necesita mientras traduce el cuerpo de una función del TAC.
 *
 * @param funcion     etiqueta TAC de la función ({@code main}, {@code suma}, {@code Perro_hablar})
 * @param tamanoFrame bytes del registro de activación, tomados de {@code function f, N}
 */
public record ContextoFuncion(ProgramaMips programa, AsignadorRegistros registros, String funcion,
                              int tamanoFrame, ResolvedorUbicaciones ubicaciones, Layout layout,
                              Map<String, Integer> tamanosFrames, boolean traza) {

    public ContextoFuncion {
        Objects.requireNonNull(programa);
        Objects.requireNonNull(registros);
        Objects.requireNonNull(funcion);
        Objects.requireNonNull(ubicaciones);
        tamanosFrames = Map.copyOf(tamanosFrames);
    }

    public ContextoFuncion(ProgramaMips programa, AsignadorRegistros registros, String funcion, int tamanoFrame) {
        this(programa, registros, funcion, tamanoFrame, nombre -> {
            throw new IllegalStateException("No hay ubicaciones configuradas para " + nombre);
        }, null, Map.of(), false);
    }

    public boolean esMain() {
        return GeneradorMIPS.MAIN.equals(funcion);
    }
}
