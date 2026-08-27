package com.lexsynanalyzer.semantic;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/** Tabla de símbolos con resolución desde el entorno actual hacia sus padres. */
public final class TablaSimbolos {

    private final Entorno global;
    private Entorno actual;

    public TablaSimbolos() {
        global = new Entorno("global", null);
        actual = global;
    }

    public Entorno global() {
        return global;
    }

    public Entorno actual() {
        return actual;
    }

    public void entrarEntorno(String nombre) {
        actual = new Entorno(nombre, actual);
    }

    public void salirEntorno() {
        actual = actual.padre().orElseThrow(() ->
                new IllegalStateException("No se puede salir del entorno global"));
    }

    public boolean declarar(Simbolo simbolo) {
        Objects.requireNonNull(simbolo);
        return actual.declarar(simbolo);
    }

    public Optional<Simbolo> buscarLocalmente(String nombre) {
        return actual.buscarLocalmente(nombre);
    }

    public Optional<Simbolo> buscar(String nombre) {
        for (Entorno entorno = actual; entorno != null; entorno = entorno.padre().orElse(null)) {
            Optional<Simbolo> simbolo = entorno.buscarLocalmente(nombre);
            if (simbolo.isPresent()) {
                return simbolo;
            }
        }
        return Optional.empty();
    }

    public boolean actualizar(Simbolo simbolo) {
        Objects.requireNonNull(simbolo);
        for (Entorno entorno = actual; entorno != null; entorno = entorno.padre().orElse(null)) {
            if (entorno.actualizar(simbolo)) {
                return true;
            }
        }
        return false;
    }

    public Collection<Simbolo> simbolosDelEntornoActual() {
        return actual.simbolos();
    }
}
