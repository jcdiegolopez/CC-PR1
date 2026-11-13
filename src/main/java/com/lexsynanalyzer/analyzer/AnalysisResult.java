package com.lexsynanalyzer.analyzer;

import com.lexsynanalyzer.mips.ProgramaMips;
import com.lexsynanalyzer.semantic.Layout;
import com.lexsynanalyzer.tac.ProgramaTAC;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resultado del análisis léxico, sintáctico y semántico y de la generación de código intermedio.
 *
 * <p>Transporta los errores detectados y el árbol sintáctico (ParseTree) junto con los nombres
 * de las reglas gramaticales para su visualización en herramientas gráficas o inspección programática,
 * manteniendo el núcleo del compilador completamente desacoplado de bibliotecas gráficas (Swing).
 *
 * <p>Si el programa tiene cualquier error, {@code tac}, {@code mips} y {@code mipsSinOptimizar} quedan
 * vacíos y {@code layout} es {@code null}.
 *
 * @param mips             ensamblador final para MARS, ya pasado por la mirilla
 * @param mipsSinOptimizar el mismo programa antes de la mirilla, para compararlos en la GUI
 * @param mirilla          veces que se aplicó cada regla de la mirilla, en el orden de las reglas
 * @param falloMips        por qué no se pudo generar ensamblador para un programa sin errores (una
 *                         construcción que el backend no soporta, como los closures); {@code null} si no falló
 */
public record AnalysisResult(List<AnalysisError> errores, ParseTree arbol, String[] nombresReglas,
                             ProgramaTAC tac, Layout layout, ProgramaMips mips, ProgramaMips mipsSinOptimizar,
                             Map<String, Integer> mirilla, String falloMips) {

    public AnalysisResult {
        tac = Objects.requireNonNullElseGet(tac, ProgramaTAC::vacio);
        mips = Objects.requireNonNullElseGet(mips, ProgramaMips::vacio);
        mipsSinOptimizar = Objects.requireNonNullElse(mipsSinOptimizar, mips);
        mirilla = mirilla == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(mirilla));
    }

    public AnalysisResult(List<AnalysisError> errores, ParseTree arbol, String[] nombresReglas,
                          ProgramaTAC tac, Layout layout) {
        this(errores, arbol, nombresReglas, tac, layout, null, null, null, null);
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

    public boolean tieneMips() {
        return !mips.estaVacio();
    }
}
