package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ProgramContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneradorTACFlujoTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "if_while_for",
            "foreach",
            "switch",
            "funciones",
            "recursion",
            "try_catch"
    })
    void generaElTacEsperado(String caso) {
        assertEquals(TacTestSupport.esperado(caso), TacTestSupport.generar(caso).toString().replace("\r\n", "\n"));
    }



    @Test
    @DisplayName("Llamada como sentencia pura no genera temporal de destino")
    void llamadaComoSentencia_noAsignaTemporal() {
        ProgramaTAC tac = generar("function saludar() { print(\"hola\"); } saludar();");
        assertTrue(tac.toString().contains("call saludar, 0"));
        assertTrue(!tac.toString().contains("= call saludar, 0"));
    }

    @Test
    @DisplayName("break dentro de try emite endtry antes del salto")
    void breakEnTry_emiteEndtryAntesDeSalto() {
        String fuente = """
                while (true) {
                    try {
                        break;
                    } catch (e) {
                        print(e);
                    }
                }
                """;
        ProgramaTAC tac = generar(fuente);
        String s = tac.toString();
        int idxTry = s.indexOf("try L");
        int idxBreakEndtry = s.indexOf("endtry\ngoto L");
        assertTrue(idxTry >= 0 && idxBreakEndtry > idxTry, "Debe emitir endtry antes del goto de break");
    }

    @Test
    @DisplayName("return dentro de try emite endtry antes del retorno")
    void returnEnTry_emiteEndtryAntesDeRetorno() {
        String fuente = """
                function f(): integer {
                    try {
                        return 42;
                    } catch (e) {
                        return 0;
                    }
                }
                """;
        ProgramaTAC tac = generar(fuente);
        String s = tac.toString();
        assertTrue(s.contains("endtry\nreturn 42"), "Debe emitir endtry antes de return 42:\n" + s);
    }

    @Test
    @DisplayName("Funciones anidadas se elevan con prefijo padre_hijo")
    void funcionesAnidadas_seElevanConNombrePadreHijo() {
        String fuente = """
                function padre(): integer {
                    function hijo(): integer {
                        return 1;
                    }
                    return hijo();
                }
                """;
        ProgramaTAC tac = generar(fuente);
        String s = tac.toString();
        assertTrue(s.contains("function padre_hijo, 24"), "Debe elevarse con nombre padre_hijo:\n" + s);
        assertTrue(s.contains("call padre_hijo, 0"), "La llamada interna debe resolver a padre_hijo:\n" + s);
    }

    private static ProgramaTAC generar(String fuente) {
        AnalysisResult res = LexSynAnalyzer.analizarTexto(fuente);
        assertTrue(res.exitoso(), () -> "El código fuente debe compilar sin errores: " + res.errores());
        return new GeneradorTAC().generar((ProgramContext) res.arbol());
    }
}
