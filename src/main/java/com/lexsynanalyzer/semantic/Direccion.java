package com.lexsynanalyzer.semantic;

import java.util.Objects;

/**
 * Lugar en memoria de una variable, parámetro o campo, y nombre con el que aparece en el TAC.
 *
 * <p>{@code offset} es relativo al inicio del frame ({@code LOCAL}, {@code PARAM}), de la región
 * estática ({@code GLOBAL}) o del objeto ({@code CAMPO}).
 */
public record Direccion(String nombreTac, Categoria categoria, int offset, int tamano, TipoDato tipo) {

    public enum Categoria {
        GLOBAL,
        LOCAL,
        PARAM,
        CAMPO
    }

    public Direccion {
        Objects.requireNonNull(nombreTac);
        Objects.requireNonNull(categoria);
        Objects.requireNonNull(tipo);
    }
}
