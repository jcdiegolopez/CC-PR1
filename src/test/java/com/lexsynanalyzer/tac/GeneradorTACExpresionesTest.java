package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.analyzer.TipoError;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ProgramContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneradorTACExpresionesTest {

    @ParameterizedTest
    @ValueSource(strings = {"aritmetica", "logicas", "arreglos", "temporales"})
    void generaElTacEsperado(String caso) {
        assertEquals(TacTestSupport.esperado(caso), TacTestSupport.generar(caso).toString().replace("\r\n", "\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"error_lexico", "error_sintactico", "error_semantico"})
    void conErrores_noHayTacYSeReportaElError(String caso) {
        AnalysisResult resultado = TacTestSupport.analizar(caso);

        assertTrue(TacTestSupport.generar(caso).estaVacio());
        assertTrue(resultado.errores().stream().anyMatch(e -> e.tipo() == esperadoPara(caso)));
    }

    private static TipoError esperadoPara(String caso) {
        return switch (caso) {
            case "error_lexico" -> TipoError.LEXICO;
            case "error_sintactico" -> TipoError.SINTACTICO;
            default -> TipoError.SEMANTICO;
        };
    }

    /** {@code (a+b)*(c-d)} necesita 2 temporales, no 3. */
    @Test
    void reciclaje_deLaExpresionDelEnunciado_usaDosTemporales() {
        GeneradorTAC generador = new GeneradorTAC();
        generador.generar(arbol("let a = 1; let b = 2; let c = 3; let d = 4; let r = (a + b) * (c - d);"));

        assertEquals(2, generador.temporales().totalCreados());
        assertEquals(2, generador.temporales().maximoSimultaneos());
    }

    @Test
    void cadenaDeSumas_reutilizaUnSoloTemporal() {
        GeneradorTAC generador = new GeneradorTAC();
        generador.generar(arbol("let a = 1; let r = a + a + a + a + a;"));

        assertEquals(1, generador.temporales().totalCreados());
    }

    @Test
    void tamanoDelFrameDeMain_incluyeLosTemporalesUsados() {
        ProgramaTAC tac = new GeneradorTAC().generar(arbol("let a = 1; let r = (a + a) * (a - a);"));

        assertEquals("function main, 32", tac.instrucciones().get(0).toString());
    }

    @Test
    void generarCodigo_noEjecutaElPrograma() {
        PrintStream original = System.out;
        ByteArrayOutputStream capturado = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturado));
        try {
            new GeneradorTAC().generar(arbol("print(\"hola\"); print(1 + 2);"));
        } finally {
            System.setOut(original);
        }

        assertEquals("", capturado.toString());
    }

    private static ProgramContext arbol(String fuente) {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(fuente);
        assertTrue(resultado.exitoso(), () -> "El caso de prueba debe ser válido: " + resultado.errores());
        return (ProgramContext) resultado.arbol();
    }
}
