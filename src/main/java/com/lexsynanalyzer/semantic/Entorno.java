package com.lexsynanalyzer.semantic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Un ámbito léxico y sus símbolos declarados directamente en él. */
public final class Entorno {

    private final Entorno padre;
    private final String nombre;
    private final Map<String, Simbolo> simbolos = new LinkedHashMap<>();
    private final List<Entorno> hijos = new ArrayList<>();
    /** Dirección de cada variable declarada aquí; la llena {@link AsignadorMemoria}. */
    private final Map<String, Direccion> direcciones = new LinkedHashMap<>();
    private int tamanoFrame;
    private int siguienteOffset;

    public Entorno(String nombre, Entorno padre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El entorno debe tener un nombre");
        }
        this.nombre = nombre;
        this.padre = padre;
        if (padre != null) {
            padre.hijos.add(this);
        }
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

    /** Entornos que se abrieron dentro de este, en el orden en que aparecen en el código. */
    public List<Entorno> hijos() {
        return Collections.unmodifiableList(hijos);
    }

    public Map<String, Direccion> direcciones() {
        return direcciones;
    }

    public int tamanoFrame() {
        return tamanoFrame;
    }

    public void setTamanoFrame(int tamanoFrame) {
        this.tamanoFrame = tamanoFrame;
    }

    public int siguienteOffset() {
        return siguienteOffset;
    }

    public void setSiguienteOffset(int siguienteOffset) {
        this.siguienteOffset = siguienteOffset;
    }
}
