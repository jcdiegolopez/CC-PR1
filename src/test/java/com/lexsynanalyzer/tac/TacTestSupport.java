package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ProgramContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Genera TAC de un caso {@code src/test/resources/tac/<nombre>.cps}. */
final class TacTestSupport {

    private static final Path CASOS = Path.of("src", "test", "resources", "tac");

    static String fuente(String caso) {
        try {
            return Files.readString(CASOS.resolve(caso + ".cps"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String esperado(String caso) {
        try {
            return Files.readString(CASOS.resolve(caso + ".tac.esperado")).strip().replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static AnalysisResult analizar(String caso) {
        return LexSynAnalyzer.analizarTexto(fuente(caso));
    }

    /**
     * Misma regla que el pipeline: con errores no hay TAC. Cuando {@code LexSynAnalyzer} (C) enchufe
     * el generador, esto pasa a leer el TAC de {@link AnalysisResult}.
     */
    static ProgramaTAC generar(String caso) {
        AnalysisResult resultado = analizar(caso);
        if (!resultado.exitoso()) {
            return ProgramaTAC.vacio();
        }
        return new GeneradorTAC().generar((ProgramContext) resultado.arbol());
    }

    private TacTestSupport() {
    }
}
