package com.lexsynanalyzer.tac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Lista ordenada de instrucciones TAC.
 *
 * <p>{@link #reservar()} deja un hueco para una instrucción que solo se conoce al final, como
 * {@code function f, frameSize}: el tamaño del frame depende de los temporales usados en el cuerpo.
 * El hueco se completa con {@link #fijar(int, Instruccion)}.
 */
public final class ProgramaTAC {

    private final List<Instruccion> instrucciones = new ArrayList<>();

    public static ProgramaTAC vacio() {
        return new ProgramaTAC();
    }

    /** Agrega una instrucción al final y devuelve su posición. */
    public int emit(Instruccion instruccion) {
        instrucciones.add(Objects.requireNonNull(instruccion));
        return instrucciones.size() - 1;
    }

    /** Reserva una posición para completarla después con {@link #fijar}. */
    public int reservar() {
        instrucciones.add(null);
        return instrucciones.size() - 1;
    }

    public void fijar(int posicion, Instruccion instruccion) {
        if (posicion < 0 || posicion >= instrucciones.size() || instrucciones.get(posicion) != null) {
            throw new IllegalArgumentException("La posición " + posicion + " no es una reserva pendiente");
        }
        instrucciones.set(posicion, Objects.requireNonNull(instruccion));
    }

    public int tamano() {
        return instrucciones.size();
    }

    public boolean estaVacio() {
        return instrucciones.isEmpty();
    }

    public List<Instruccion> instrucciones() {
        verificarSinReservasPendientes();
        return Collections.unmodifiableList(instrucciones);
    }

    /** Una instrucción por línea, sin sangría. */
    @Override
    public String toString() {
        return instrucciones().stream().map(Instruccion::toString).collect(Collectors.joining("\n"));
    }

    private void verificarSinReservasPendientes() {
        int pendiente = instrucciones.indexOf(null);
        if (pendiente >= 0) {
            throw new IllegalStateException("La reserva en la posición " + pendiente + " nunca se completó");
        }
    }
}
