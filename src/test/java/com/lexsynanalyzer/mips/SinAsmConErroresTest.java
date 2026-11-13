package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.analyzer.TipoError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Con cualquier error léxico, sintáctico o semántico no se genera TAC ni ensamblador. */
class SinAsmConErroresTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "LEXICO     | let x: integer = 5 @ 3;",
            "SINTACTICO | let x: integer = ;",
            "SEMANTICO  | let x: integer = \"hola\";"})
    void conErrores_noHayTacNiMips(TipoError tipo, String fuente) {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(fuente);

        assertTrue(resultado.errores().stream().anyMatch(e -> e.tipo() == tipo), () -> resultado.errores().toString());
        assertTrue(resultado.tac().estaVacio());
        assertTrue(resultado.mips().estaVacio());
        assertTrue(resultado.mipsSinOptimizar().estaVacio());
        assertEquals("", resultado.mips().toString());
        assertEquals(null, resultado.falloMips());
    }

    @Test
    void unClosureConservaElTacYExplicaPorQueNoHayMips() {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto("""
                function crearContador(inicio: integer): integer {
                    function siguiente(): integer { return inicio + 1; }
                    return siguiente();
                }
                print(crearContador(1));
                """);

        assertTrue(resultado.exitoso());
        assertTrue(resultado.tieneTac());
        assertTrue(resultado.mips().estaVacio());
        assertTrue(resultado.falloMips() != null && !resultado.falloMips().isBlank());
    }

    @Test
    void sinErrores_elPipelineEntregaElAsmDeGetReg() {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto("print(1 + 2);");

        assertTrue(resultado.tieneMips());
        assertEquals(new GeneradorMIPS(resultado.layout(), FabricaRegistros.GETREG).generar(resultado.tac()).toString(),
                resultado.mipsSinOptimizar().toString());
        assertTrue(SimuladorMips.ejecutar(resultado.mipsSinOptimizar().toString()).salida().startsWith("3"));
        assertTrue(SimuladorMips.ejecutar(resultado.mips().toString()).salida().startsWith("3"));
    }
}
