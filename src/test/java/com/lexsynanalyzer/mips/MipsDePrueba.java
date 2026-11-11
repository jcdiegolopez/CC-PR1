package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compila Compiscript con el backend de producción para las pruebas MIPS. */
final class MipsDePrueba {

    private MipsDePrueba() {
    }

    static ProgramaMips compilar(String fuente, FabricaRegistros modo) {
        return compilar(fuente, modo, false);
    }

    static ProgramaMips compilar(String fuente, FabricaRegistros modo, boolean traza) {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(fuente);
        assertTrue(resultado.exitoso(), () -> "El programa de prueba debe compilar: " + resultado.errores());
        return new GeneradorMIPS(resultado.layout(), modo, traza).generar(resultado.tac());
    }
}
