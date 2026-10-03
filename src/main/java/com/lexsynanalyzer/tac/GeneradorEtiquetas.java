package com.lexsynanalyzer.tac;

/** Genera etiquetas {@code L0, L1, ...} únicas por programa. */
public final class GeneradorEtiquetas {

    private int siguiente;

    public String nueva() {
        return "L" + siguiente++;
    }
}
