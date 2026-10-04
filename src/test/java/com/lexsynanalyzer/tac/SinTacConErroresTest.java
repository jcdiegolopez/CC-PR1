package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.analyzer.TipoError;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Con cualquier error léxico, sintáctico o semántico el pipeline no genera código intermedio. */
class SinTacConErroresTest {

    @ParameterizedTest
    @CsvSource({
            "error_lexico, LEXICO",
            "error_sintactico, SINTACTICO",
            "error_semantico, SEMANTICO",
            "error_clase, SEMANTICO",
    })
    void conErrores_elResultadoNoTraeTacNiLayout(String caso, TipoError tipo) {
        AnalysisResult resultado = TacTestSupport.analizar(caso);

        assertFalse(resultado.exitoso());
        assertTrue(resultado.errores().stream().anyMatch(e -> e.tipo() == tipo));
        assertTrue(resultado.tac().estaVacio());
        assertFalse(resultado.tieneTac());
        assertNull(resultado.layout());
    }

    /** Los casos oficiales del P1: los que tienen errores no generan TAC; el válido sí. */
    @ParameterizedTest
    @ValueSource(strings = {
            "baja_errores_lexicos", "baja_errores_sintacticos", "baja_errores_mixto", "baja_sin_errores",
            "media_errores_lexicos", "media_errores_sintacticos", "media_errores_mixto", "media_sin_errores"})
    void casosDelProyectoAnterior_soloLosValidosGeneranTac(String caso) throws IOException {
        AnalysisResult resultado = LexSynAnalyzer.analyze(
                Path.of("src", "test", "resources", "casos", caso + ".cps").toFile());

        assertEquals(resultado.exitoso(), resultado.tieneTac());
        assertEquals(resultado.exitoso(), resultado.layout() != null);
        if (resultado.tieneTac()) {
            assertTrue(Files.exists(Path.of("src", "test", "resources", "casos", caso + ".cps")));
            assertTrue(resultado.tac().toString().startsWith("function main, "));
        }
    }
}
