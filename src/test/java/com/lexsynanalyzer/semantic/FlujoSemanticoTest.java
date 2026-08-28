package com.lexsynanalyzer.semantic;

import com.lexsynanalyzer.analyzer.AnalysisError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lexsynanalyzer.semantic.TiposSemanticosTest.assertSinErrores;
import static com.lexsynanalyzer.semantic.TiposSemanticosTest.semanticos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Control de flujo: condiciones, switch, break, continue, return y código inalcanzable. */
class FlujoSemanticoTest {

    @Test
    @DisplayName("La condición del if debe ser boolean")
    void exigeCondicionBooleanaEnIf() {
        assertSinErrores("""
                let x: integer = 5;
                if (x > 3) {
                    print("mayor");
                } else {
                    print("menor");
                }
                """);

        List<AnalysisError> errores = semanticos("""
                let x: integer = 5;
                if (x) {
                    print("algo");
                }
                """);
        assertEquals(1, errores.size(), errores.toString());
        assertEquals(2, errores.getFirst().linea());
        assertTrue(errores.getFirst().descripcion().contains("'if'"), errores.toString());
    }

    @Test
    @DisplayName("La condición del while debe ser boolean")
    void exigeCondicionBooleanaEnWhile() {
        List<AnalysisError> errores = semanticos("""
                while ("texto") {
                    print("nunca");
                }
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("'while'"), errores.toString());
    }

    @Test
    @DisplayName("La condición del do-while debe ser boolean")
    void exigeCondicionBooleanaEnDoWhile() {
        assertSinErrores("""
                let x: integer = 3;
                do {
                    x = x - 1;
                } while (x > 0);
                """);

        List<AnalysisError> errores = semanticos("""
                let x: integer = 3;
                do {
                    x = x - 1;
                } while (x);
                """);
        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("do-while"), errores.toString());
    }

    @Test
    @DisplayName("La condición del for debe ser boolean, no su avance")
    void exigeCondicionBooleanaEnFor() {
        assertSinErrores("""
                for (let i: integer = 0; i < 3; i = i + 1) {
                    print(i);
                }
                """);

        List<AnalysisError> errores = semanticos("""
                for (let i: integer = 0; i; i = i + 1) {
                    print(i);
                }
                """);
        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("'for'"), errores.toString());
    }

    @Test
    @DisplayName("El selector y los case del switch deben ser compatibles")
    void exigeCasesCompatiblesConElSelector() {
        assertSinErrores("""
                let x: integer = 2;
                switch (x) {
                    case 1:
                        print("uno");
                    default:
                        print("otro");
                }
                """);

        List<AnalysisError> errores = semanticos("""
                let x: integer = 2;
                switch (x) {
                    case "uno":
                        print("uno");
                }
                """);
        assertEquals(1, errores.size(), errores.toString());
        assertEquals(3, errores.getFirst().linea());
        assertTrue(errores.getFirst().descripcion().contains("selector"), errores.toString());
    }

    @Test
    @DisplayName("break y continue son válidos dentro de un ciclo")
    void aceptaBreakYContinueDentroDeCiclos() {
        assertSinErrores("""
                let i: integer = 0;
                while (i < 10) {
                    i = i + 1;
                    if (i == 3) {
                        continue;
                    }
                    if (i == 5) {
                        break;
                    }
                }
                """);
    }

    @Test
    @DisplayName("break es válido dentro de un switch, continue no")
    void permiteBreakEnSwitchPeroNoContinue() {
        assertSinErrores("""
                let x: integer = 1;
                switch (x) {
                    case 1:
                        print("uno");
                        break;
                    default:
                        print("otro");
                }
                """);

        List<AnalysisError> errores = semanticos("""
                let x: integer = 1;
                switch (x) {
                    case 1:
                        continue;
                }
                """);
        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("'continue'"), errores.toString());
    }

    @Test
    @DisplayName("break y continue fuera de un ciclo son errores")
    void rechazaBreakYContinueFueraDeCiclo() {
        List<AnalysisError> breakSuelto = semanticos("break;");
        assertEquals(1, breakSuelto.size(), breakSuelto.toString());
        assertTrue(breakSuelto.getFirst().descripcion().contains("'break'"), breakSuelto.toString());

        List<AnalysisError> continueSuelto = semanticos("continue;");
        assertEquals(1, continueSuelto.size(), continueSuelto.toString());
        assertTrue(continueSuelto.getFirst().descripcion().contains("'continue'"), continueSuelto.toString());
    }

    @Test
    @DisplayName("break y continue no cruzan el límite de una función anidada en un ciclo")
    void noPropagaElCicloHaciaFuncionesAnidadas() {
        List<AnalysisError> errores = semanticos("""
                while (true) {
                    function interna() {
                        break;
                    }
                }
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("'break'"), errores.toString());
    }

    @Test
    @DisplayName("return fuera de una función es un error")
    void rechazaReturnFueraDeFuncion() {
        List<AnalysisError> errores = semanticos("return 1;");

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("'return'"), errores.toString());
    }

    @Test
    @DisplayName("Las sentencias posteriores a un return son código inalcanzable")
    void detectaCodigoInalcanzableTrasReturn() {
        List<AnalysisError> errores = semanticos("""
                function f(): integer {
                    return 1;
                    print("nunca");
                }
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertEquals(3, errores.getFirst().linea());
        assertTrue(errores.getFirst().descripcion().contains("nunca se ejecuta"), errores.toString());
    }

    @Test
    @DisplayName("Las sentencias posteriores a un break son código inalcanzable")
    void detectaCodigoInalcanzableTrasBreak() {
        List<AnalysisError> errores = semanticos("""
                while (true) {
                    break;
                    print("nunca");
                }
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertEquals(3, errores.getFirst().linea());
    }

    @Test
    @DisplayName("Un return dentro de un if no vuelve inalcanzable al resto de la función")
    void noMarcaComoInalcanzableUnReturnCondicional() {
        assertSinErrores("""
                function absoluto(n: integer): integer {
                    if (n < 0) {
                        return 0 - n;
                    }
                    return n;
                }
                """);
    }

    @Test
    @DisplayName("El código inalcanzable se reporta una sola vez por ámbito")
    void reportaElCodigoInalcanzableUnaSolaVez() {
        List<AnalysisError> errores = semanticos("""
                function f(): integer {
                    return 1;
                    print("a");
                    print("b");
                    print("c");
                }
                """);

        assertEquals(1, errores.size(), errores.toString());
    }
}
