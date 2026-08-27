package com.lexsynanalyzer.semantic;

import java.util.Objects;

/** Representa un tipo del sistema de tipos de Compiscript. */
public final class TipoDato {

    public enum Clase {
        INTEGER,
        BOOLEAN,
        STRING,
        NULL,
        VOID,
        CLASE,
        ARREGLO,
        DESCONOCIDO,
        ERROR
    }

    public static final TipoDato INTEGER = new TipoDato(Clase.INTEGER, null, null);
    public static final TipoDato BOOLEAN = new TipoDato(Clase.BOOLEAN, null, null);
    public static final TipoDato STRING = new TipoDato(Clase.STRING, null, null);
    public static final TipoDato NULL = new TipoDato(Clase.NULL, null, null);
    public static final TipoDato VOID = new TipoDato(Clase.VOID, null, null);
    public static final TipoDato DESCONOCIDO = new TipoDato(Clase.DESCONOCIDO, null, null);
    public static final TipoDato ERROR = new TipoDato(Clase.ERROR, null, null);

    private final Clase clase;
    private final String nombreClase;
    private final TipoDato tipoElemento;

    private TipoDato(Clase clase, String nombreClase, TipoDato tipoElemento) {
        this.clase = Objects.requireNonNull(clase);
        this.nombreClase = nombreClase;
        this.tipoElemento = tipoElemento;
    }

    public static TipoDato clase(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de una clase no puede estar vacío");
        }
        return new TipoDato(Clase.CLASE, nombre, null);
    }

    public static TipoDato arreglo(TipoDato elemento) {
        return new TipoDato(Clase.ARREGLO, null, Objects.requireNonNull(elemento));
    }

    public Clase clase() {
        return clase;
    }

    public String nombreClase() {
        return nombreClase;
    }

    public TipoDato tipoElemento() {
        return tipoElemento;
    }

    public boolean esNumerico() {
        return clase == Clase.INTEGER;
    }

    public boolean esBooleano() {
        return clase == Clase.BOOLEAN;
    }

    public boolean esError() {
        return clase == Clase.ERROR;
    }

    /** Compatibilidad básica para asignaciones y comparaciones. */
    public boolean esCompatibleCon(TipoDato otro) {
        if (otro == null || esError() || otro.esError()) {
            return true;
        }
        if (this == DESCONOCIDO || otro == DESCONOCIDO) {
            return true;
        }
        if (clase == Clase.NULL && (otro.clase == Clase.CLASE || otro.clase == Clase.ARREGLO)) {
            return true;
        }
        if (otro.clase == Clase.NULL && (clase == Clase.CLASE || clase == Clase.ARREGLO)) {
            return true;
        }
        return equals(otro);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof TipoDato tipo)) return false;
        return clase == tipo.clase
                && Objects.equals(nombreClase, tipo.nombreClase)
                && Objects.equals(tipoElemento, tipo.tipoElemento);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clase, nombreClase, tipoElemento);
    }

    @Override
    public String toString() {
        return switch (clase) {
            case CLASE -> nombreClase;
            case ARREGLO -> tipoElemento + "[]";
            case INTEGER -> "integer";
            case BOOLEAN -> "boolean";
            case STRING -> "string";
            case NULL -> "null";
            case VOID -> "void";
            case DESCONOCIDO -> "desconocido";
            case ERROR -> "error";
        };
    }
}
