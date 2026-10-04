package com.lexsynanalyzer.semantic;

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resultado de {@link AsignadorMemoria}: dirección de cada variable, registro de activación de cada
 * función y distribución en memoria de cada clase.
 */
public final class Layout {

    /** Una variable del código fuente con la dirección que se le asignó. */
    public record Variable(String nombre, Direccion direccion) {
    }

    /**
     * Registro de activación de una función, método o de {@code main}. {@code tamanoBase} incluye la
     * cabecera, los parámetros y las locales; los temporales los suma el generador de TAC.
     */
    public record Frame(String etiqueta, int tamanoBase, List<Variable> variables) {
        public Frame {
            variables = List.copyOf(variables);
        }
    }

    /** Campos de una clase (los heredados primero) y sus métodos declarados en ella misma. */
    public record ClaseLayout(String nombre, String padre, List<Variable> campos, int tamanoInstancia,
                              Set<String> metodos) {
        public ClaseLayout {
            campos = List.copyOf(campos);
            metodos = Set.copyOf(metodos);
        }
    }

    private final ResultadoSemantico semantico;
    private final List<Variable> globales;
    private final Map<String, Frame> frames;
    private final Map<String, ClaseLayout> clases;

    Layout(ResultadoSemantico semantico, List<Variable> globales, Map<String, Frame> frames,
           Map<String, ClaseLayout> clases) {
        this.semantico = semantico;
        this.globales = List.copyOf(globales);
        this.frames = Collections.unmodifiableMap(frames);
        this.clases = Collections.unmodifiableMap(clases);
    }

    public ResultadoSemantico semantico() {
        return semantico;
    }

    /** Variables de la región estática, con offset desde el inicio de esa región. */
    public List<Variable> globales() {
        return globales;
    }

    /** Frames por etiqueta TAC; {@code main} va primero. */
    public Map<String, Frame> frames() {
        return frames;
    }

    public Map<String, ClaseLayout> clases() {
        return clases;
    }

    /** Bytes del frame sin temporales. Una etiqueta desconocida solo tiene cabecera. */
    public int tamanoBase(String etiqueta) {
        Frame frame = frames.get(etiqueta);
        return frame == null ? AsignadorMemoria.CABECERA : frame.tamanoBase();
    }

    /** Nombre TAC de la variable que nombra {@code identificador}, si es una variable con dirección. */
    public Optional<String> nombreTac(Token identificador) {
        return semantico.entornoDeclarante(identificador)
                .map(entorno -> entorno.direcciones().get(identificador.getText()))
                .map(Direccion::nombreTac);
    }

    public Optional<ClaseLayout> clase(String nombre) {
        return Optional.ofNullable(clases.get(nombre));
    }

    /** Clases desde la raíz de la jerarquía hasta {@code clase}, inclusive. */
    public List<ClaseLayout> jerarquia(String clase) {
        List<ClaseLayout> cadena = new ArrayList<>();
        for (ClaseLayout actual = clases.get(clase); actual != null && cadena.size() < clases.size();
             actual = actual.padre() == null ? null : clases.get(actual.padre())) {
            cadena.add(0, actual);
        }
        return cadena;
    }

    /**
     * Etiqueta {@code Clase_metodo} de la clase más cercana a {@code clase} (ella o un ancestro) que
     * declara {@code metodo}. Es el despacho estático: se resuelve con el tipo declarado.
     */
    public Optional<String> etiquetaMetodo(String clase, String metodo) {
        List<ClaseLayout> cadena = jerarquia(clase);
        for (int i = cadena.size() - 1; i >= 0; i--) {
            if (cadena.get(i).metodos().contains(metodo)) {
                return Optional.of(cadena.get(i).nombre() + "_" + metodo);
            }
        }
        return Optional.empty();
    }

    /** Constructor propio o heredado: {@code constructor} o un método con el nombre de la clase. */
    public Optional<String> etiquetaConstructor(String clase) {
        return etiquetaMetodo(clase, "constructor").or(() -> etiquetaMetodo(clase, clase));
    }
}
