package com.lexsynanalyzer.tac;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Asignación y reciclaje de temporales {@code t0, t1, ...}.
 *
 * <p>{@link #nuevo()} reutiliza el temporal libre de menor índice; si no hay, crea uno nuevo.
 * {@link #liberar(String)} solo acepta nombres de temporales: literales y variables se ignoran,
 * así el generador puede liberar cualquier operando sin distinguirlo antes.
 *
 * <p>Una instancia por función: el contador se reinicia con {@link #reiniciar()}.
 */
public final class TempPool {

    private static final Pattern TEMPORAL = Pattern.compile("t\\d+");

    private final TreeSet<Integer> libres = new TreeSet<>();
    private final Set<Integer> enUso = new HashSet<>();
    private int creados;
    private int maxEnUso;

    public static boolean esTemporal(String operando) {
        return operando != null && TEMPORAL.matcher(operando).matches();
    }

    public String nuevo() {
        int indice = libres.isEmpty() ? creados++ : libres.pollFirst();
        enUso.add(indice);
        maxEnUso = Math.max(maxEnUso, enUso.size());
        return "t" + indice;
    }

    /** Devuelve el temporal al pool. No hace nada si {@code operando} no es un temporal en uso. */
    public void liberar(String operando) {
        if (!esTemporal(operando)) {
            return;
        }
        int indice = Integer.parseInt(operando.substring(1));
        if (enUso.remove(indice)) {
            libres.add(indice);
        }
    }

    /** Cantidad de temporales distintos que se crearon (los reciclados no cuentan dos veces). */
    public int totalCreados() {
        return creados;
    }

    /** Máximo de temporales vivos a la vez; sirve para dimensionar el frame de la función. */
    public int maximoSimultaneos() {
        return maxEnUso;
    }

    public void reiniciar() {
        libres.clear();
        enUso.clear();
        creados = 0;
        maxEnUso = 0;
    }
}
