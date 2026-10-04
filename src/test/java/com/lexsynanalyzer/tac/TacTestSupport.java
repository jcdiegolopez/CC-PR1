package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;

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

    /** TAC que produce el pipeline completo: vacío si el programa tiene errores. */
    static ProgramaTAC generar(String caso) {
        return analizar(caso).tac();
    }

    private TacTestSupport() {
    }
}
