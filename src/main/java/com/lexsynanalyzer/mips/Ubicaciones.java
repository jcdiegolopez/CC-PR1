package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.semantic.Direccion;
import com.lexsynanalyzer.semantic.Layout;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resuelve operandos TAC a sus direcciones usando el {@link Layout} del análisis semántico. */
public final class Ubicaciones implements ResolvedorUbicaciones {

    private static final Pattern TEMPORAL = Pattern.compile("t(\\d+)");

    private final String funcion;
    private final int tamanoBase;
    private final Map<String, String> direcciones;

    public Ubicaciones(Layout layout, String funcion) {
        Objects.requireNonNull(layout);
        this.funcion = Objects.requireNonNull(funcion);
        tamanoBase = layout.tamanoBase(funcion);
        direcciones = new HashMap<>();
        layout.globales().forEach(variable -> direcciones.put(variable.direccion().nombreTac(),
                "g_" + variable.direccion().nombreTac()));
        Layout.Frame frame = layout.frames().get(funcion);
        if (frame != null) {
            frame.variables().forEach(variable -> {
                Direccion direccion = variable.direccion();
                if (direccion.categoria() != Direccion.Categoria.CAMPO) {
                    direcciones.put(direccion.nombreTac(), direccion.offset() + "($fp)");
                }
            });
        }
    }

    @Override
    public String direccion(String nombre) {
        Matcher temporal = TEMPORAL.matcher(nombre);
        if (temporal.matches()) {
            int indice = Integer.parseInt(temporal.group(1));
            return Math.addExact(tamanoBase, Math.multiplyExact(4, indice)) + "($fp)";
        }
        String direccion = direcciones.get(nombre);
        if (direccion == null) {
            throw new IllegalArgumentException("Sin dirección para " + nombre + " en la función " + funcion);
        }
        return direccion;
    }

    public static List<Integer> offsetsParametros(Layout layout, String funcion) {
        Layout.Frame frame = layout.frames().get(funcion);
        if (frame == null) {
            throw new IllegalArgumentException("No existe un frame para la función " + funcion);
        }
        return frame.variables().stream()
                .map(Layout.Variable::direccion)
                .filter(direccion -> direccion.categoria() == Direccion.Categoria.PARAM)
                .map(Direccion::offset)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public static void registrarGlobales(ProgramaMips programa, Layout layout) {
        layout.globales().forEach(variable ->
                programa.dato("g_" + variable.direccion().nombreTac(), ".word 0"));
    }
}
