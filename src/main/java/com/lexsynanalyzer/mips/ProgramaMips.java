package com.lexsynanalyzer.mips;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Programa MIPS listo para MARS: sección {@code .data} y sección {@code .text}.
 *
 * <p>Los literales string se guardan una sola vez ({@link #cadena}) con etiquetas {@code str0, str1, …}.
 * Las demás entradas de datos (globales, mensajes y tablas del runtime) se registran con
 * {@link #dato}. La mirilla trabaja sobre {@link #texto()} y lo cambia con {@link #reemplazarTexto}.
 */
public final class ProgramaMips {

    private final Map<String, String> cadenas = new LinkedHashMap<>();
    private final Map<String, String> datos = new LinkedHashMap<>();
    private final List<InstruccionMips> texto = new ArrayList<>();
    private final Set<String> rutinas = new LinkedHashSet<>();

    public static ProgramaMips vacio() {
        return new ProgramaMips();
    }

    /** Agrega una línea al final de {@code .text}. */
    public void emit(InstruccionMips instruccion) {
        texto.add(Objects.requireNonNull(instruccion));
    }

    /**
     * Etiqueta del literal string con contenido {@code valor} (sin comillas). Si el mismo contenido
     * ya se registró, devuelve la misma etiqueta.
     */
    public String cadena(String valor) {
        return cadenas.computeIfAbsent(Objects.requireNonNull(valor), v -> "str" + cadenas.size());
    }

    /**
     * Registra una entrada de {@code .data}, por ejemplo {@code dato("g_x", ".word 0")}. Registrar dos
     * veces la misma etiqueta con la misma directiva no hace nada; con otra directiva es un error.
     */
    public void dato(String etiqueta, String directiva) {
        String anterior = datos.putIfAbsent(Objects.requireNonNull(etiqueta), Objects.requireNonNull(directiva));
        if (anterior != null && !anterior.equals(directiva)) {
            throw new IllegalArgumentException("La etiqueta de datos " + etiqueta + " ya existe con otra directiva");
        }
    }

    /**
     * Pide que la rutina del runtime {@code nombre} (por ejemplo {@code __concat}) se incluya en el
     * programa. {@link GeneradorMIPS} la emite una sola vez, al final de {@code .text}.
     */
    public void requerir(String rutina) {
        rutinas.add(Objects.requireNonNull(rutina));
    }

    /** Rutinas pedidas con {@link #requerir}, en el orden en que se pidieron. */
    public Set<String> rutinasRequeridas() {
        return Collections.unmodifiableSet(rutinas);
    }

    public List<InstruccionMips> texto() {
        return Collections.unmodifiableList(texto);
    }

    /**
     * Programa con los mismos datos y rutinas pero con otro {@code .text}. La mirilla lo usa para devolver
     * la versión optimizada sin tocar la original, que la GUI sigue mostrando como "sin optimizar".
     */
    public ProgramaMips conTexto(List<InstruccionMips> nuevoTexto) {
        ProgramaMips copia = new ProgramaMips();
        copia.cadenas.putAll(cadenas);
        copia.datos.putAll(datos);
        copia.rutinas.addAll(rutinas);
        copia.texto.addAll(List.copyOf(nuevoTexto));
        return copia;
    }

    public void reemplazarTexto(List<InstruccionMips> nuevoTexto) {
        List<InstruccionMips> copia = List.copyOf(nuevoTexto);
        texto.clear();
        texto.addAll(copia);
    }

    /** Cantidad de instrucciones reales en {@code .text} (sin etiquetas ni comentarios). */
    public int cantidadInstrucciones() {
        return (int) texto.stream().filter(InstruccionMips::esInstruccion).count();
    }

    public boolean estaVacio() {
        return texto.isEmpty();
    }

    /**
     * Archivo {@code .asm} completo. {@code .globl main} indica a MARS dónde empezar cuando se ejecuta
     * con la opción {@code sm}; el generador siempre emite la etiqueta {@code main}.
     */
    @Override
    public String toString() {
        if (estaVacio()) {
            return "";
        }
        StringBuilder asm = new StringBuilder();
        if (!cadenas.isEmpty() || !datos.isEmpty()) {
            asm.append(".data\n");
            cadenas.forEach((valor, etiqueta) ->
                    asm.append(etiqueta).append(": .asciiz \"").append(escapar(valor)).append("\"\n"));
            datos.forEach((etiqueta, directiva) -> asm.append(etiqueta).append(": ").append(directiva).append('\n'));
            asm.append('\n');
        }
        asm.append(".text\n.globl main\n");
        texto.forEach(instruccion -> asm.append(instruccion).append('\n'));
        return asm.toString();
    }

    /**
     * Compiscript no tiene secuencias de escape: el texto del literal se imprime tal cual. Por eso
     * {@code \} y {@code "} se escapan para que MARS no los interprete.
     */
    static String escapar(String valor) {
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
