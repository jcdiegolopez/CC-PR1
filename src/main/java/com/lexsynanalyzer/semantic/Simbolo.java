package com.lexsynanalyzer.semantic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
        TipoDato tipoRetorno,
        String clasePadre,
        Entorno entornoMiembros
) {

    public Simbolo(
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
        this(nombre, categoria, tipo, linea, columna, constante, inicializado, parametros, tipoRetorno, null, null);
    }

    public Simbolo {
        Objects.requireNonNull(nombre);
        Objects.requireNonNull(categoria);
        Objects.requireNonNull(tipo);
        parametros = parametros == null ? List.of() : List.copyOf(parametros);
    }

    public static Simbolo variable(String nombre, TipoDato tipo, int linea, int columna,
                                   boolean constante, boolean inicializado) {
        return new Simbolo(nombre, constante ? CategoriaSimbolo.CONSTANTE : CategoriaSimbolo.VARIABLE,
                tipo, linea, columna, constante, inicializado, List.of(), null, null, null);
    }

    public static Simbolo funcion(String nombre, List<Parametro> parametros, TipoDato tipoRetorno,
                                  int linea, int columna) {
        return new Simbolo(nombre, CategoriaSimbolo.FUNCION, TipoDato.DESCONOCIDO,
                linea, columna, false, true, parametros, tipoRetorno, null, null);
    }

    public static Simbolo clase(String nombre, int linea, int columna) {
        return new Simbolo(nombre, CategoriaSimbolo.CLASE, TipoDato.clase(nombre),
                linea, columna, false, true, List.of(), null, null, new Entorno("clase:" + nombre, null));
    }

    public static Simbolo clase(String nombre, String clasePadre, int linea, int columna) {
        return new Simbolo(nombre, CategoriaSimbolo.CLASE, TipoDato.clase(nombre),
                linea, columna, false, true, List.of(), null, clasePadre, new Entorno("clase:" + nombre, null));
    }

    /** Profundidad máxima de la cadena de herencia que se recorre al resolver un miembro. */
    private static final int MAX_NIVELES_HERENCIA = 100;

    public Optional<Simbolo> buscarMiembro(String nombreMiembro, TablaSimbolos tabla) {
        return buscarMiembro(nombreMiembro, tabla, MAX_NIVELES_HERENCIA);
    }

    /**
     * Sube por la cadena de herencia con un límite de niveles: una jerarquía cíclica
     * ({@code class A : B} y {@code class B : A}) ya se reporta como error aparte, pero sin este
     * límite la búsqueda de un miembro inexistente no terminaría nunca.
     */
    private Optional<Simbolo> buscarMiembro(String nombreMiembro, TablaSimbolos tabla, int nivelesRestantes) {
        if (nivelesRestantes <= 0) {
            return Optional.empty();
        }
        if (entornoMiembros != null) {
            Optional<Simbolo> local = entornoMiembros.buscarLocalmente(nombreMiembro);
            if (local.isPresent()) {
                return local;
            }
        }
        if (clasePadre != null && tabla != null) {
            Optional<Simbolo> padre = tabla.buscar(clasePadre);
            if (padre.isPresent() && padre.get().categoria() == CategoriaSimbolo.CLASE) {
                return padre.get().buscarMiembro(nombreMiembro, tabla, nivelesRestantes - 1);
            }
        }
        return Optional.empty();
    }

    public record Parametro(String nombre, TipoDato tipo, int linea, int columna) {
        public Parametro {
            Objects.requireNonNull(nombre);
            Objects.requireNonNull(tipo);
        }
    }
}
