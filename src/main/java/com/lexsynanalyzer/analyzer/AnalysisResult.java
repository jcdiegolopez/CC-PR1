package com.lexsynanalyzer.analyzer;

import com.lexsynanalyzer.semantic.Layout;
import com.lexsynanalyzer.tac.ProgramaTAC;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;
import java.util.Objects;

/**
 * Resultado del análisis léxico, sintáctico y semántico y de la generación de código intermedio.
 *
 * <p>Transporta los errores detectados y el árbol sintáctico (ParseTree) junto con los nombres
 * de las reglas gramaticales para su visualización en herramientas gráficas o inspección programática,
 * manteniendo el núcleo del compilador completamente desacoplado de bibliotecas gráficas (Swing).
 *
 * <p>Si el programa tiene cualquier error, {@code tac} queda vacío y {@code layout} es {@code null}.
 */
public record AnalysisResult(List<AnalysisError> errores, ParseTree arbol, String[] nombresReglas,
                             ProgramaTAC tac, Layout layout) {

    public AnalysisResult {
        tac = Objects.requireNonNullElseGet(tac, ProgramaTAC::vacio);
    }

    public AnalysisResult(List<AnalysisError> errores, ParseTree arbol, String[] nombresReglas) {
        this(errores, arbol, nombresReglas, ProgramaTAC.vacio(), null);
    }

    public AnalysisResult(List<AnalysisError> errores) {
        this(errores, null, new String[0]);
    }

    public boolean exitoso() {
        return errores.isEmpty();
    }

    public boolean tieneTac() {
        return !tac.estaVacio();
    }
}
