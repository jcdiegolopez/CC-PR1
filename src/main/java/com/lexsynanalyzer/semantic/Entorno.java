package com.lexsynanalyzer.semantic;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Un ámbito léxico y sus símbolos declarados directamente en él. */
public final class Entorno {

    private final Entorno padre;
    private final String nombre;
    private final Map<String, Simbolo> simbolos = new LinkedHashMap<>();

    public Entorno(String nombre, Entorno padre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El entorno debe tener un nombre");
        }
        this.nombre = nombre;
        this.padre = padre;
    }

    public String nombre() {
        return nombre;
    }

    public Optional<Entorno> padre() {
        return Optional.ofNullable(padre);
    }

    public boolean contieneLocalmente(String identificador) {
        return simbolos.containsKey(identificador);
    }

    public Optional<Simbolo> buscarLocalmente(String identificador) {
        return Optional.ofNullable(simbolos.get(identificador));
    }

    public boolean declarar(Simbolo simbolo) {
        if (contieneLocalmente(simbolo.nombre())) {
            return false;
        }
        simbolos.put(simbolo.nombre(), simbolo);
        return true;
    }

    public boolean actualizar(Simbolo simbolo) {
        if (!contieneLocalmente(simbolo.nombre())) {
            return false;
        }
        simbolos.put(simbolo.nombre(), simbolo);
        return true;
    }

    public Collection<Simbolo> simbolos() {
        return simbolos.values();
    }
}
