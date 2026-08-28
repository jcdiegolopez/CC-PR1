package com.lexsynanalyzer.analyzer;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;

/**
 * Resultado del análisis léxico, sintáctico y semántico.
 *
 * <p>Transporta los errores detectados y el árbol sintáctico (ParseTree) junto con los nombres
 * de las reglas gramaticales para su visualización en herramientas gráficas o inspección programática,
 * manteniendo el núcleo del compilador completamente desacoplado de bibliotecas gráficas (Swing).
 */
public record AnalysisResult(List<AnalysisError> errores, ParseTree arbol, String[] nombresReglas) {

    public AnalysisResult(List<AnalysisError> errores) {
        this(errores, null, new String[0]);
    }

    public boolean exitoso() {
        return errores.isEmpty();
    }
}
