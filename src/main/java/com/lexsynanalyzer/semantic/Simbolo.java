package com.lexsynanalyzer.semantic;

import java.util.List;
import java.util.Objects;

/** Entrada independiente de un identificador en la tabla de símbolos. */
public record Simbolo(
        String nombre,
        CategoriaSimbolo categoria,
        TipoDato tipo,
        int linea,
        int columna,
        boolean constante,
        boolean inicializado,
        List<Parametro> parametros,
        TipoDato tipoRetorno
) {

    public Simbolo {
        Objects.requireNonNull(nombre);
        Objects.requireNonNull(categoria);
        Objects.requireNonNull(tipo);
        parametros = parametros == null ? List.of() : List.copyOf(parametros);
    }

    public static Simbolo variable(String nombre, TipoDato tipo, int linea, int columna,
                                   boolean constante, boolean inicializado) {
        return new Simbolo(nombre, constante ? CategoriaSimbolo.CONSTANTE : CategoriaSimbolo.VARIABLE,
                tipo, linea, columna, constante, inicializado, List.of(), null);
    }

    public static Simbolo funcion(String nombre, List<Parametro> parametros, TipoDato tipoRetorno,
                                  int linea, int columna) {
        return new Simbolo(nombre, CategoriaSimbolo.FUNCION, TipoDato.DESCONOCIDO,
                linea, columna, false, true, parametros, tipoRetorno);
    }

    public static Simbolo clase(String nombre, int linea, int columna) {
        return new Simbolo(nombre, CategoriaSimbolo.CLASE, TipoDato.clase(nombre),
                linea, columna, false, true, List.of(), null);
    }

    public record Parametro(String nombre, TipoDato tipo, int linea, int columna) {
        public Parametro {
            Objects.requireNonNull(nombre);
            Objects.requireNonNull(tipo);
        }
    }
}
