package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Etiquetas de funciones anidadas y nombres de las variables de {@code foreach} y {@code catch}. */
class GeneradorTACRegresionTest {

    @Test
    @DisplayName("Una función anidada recursiva se llama con su etiqueta padre_hijo")
    void recursionEnFuncionAnidada() {
        String tac = tac("""
                function padre(n: integer): integer {
                    function hijo(k: integer): integer {
                        if (k <= 0) { return 0; }
                        return hijo(k - 1);
                    }
                    return hijo(n);
                }
                """);

        assertEquals(2, ocurrencias(tac, "call padre_hijo, 1"), tac);
        assertFalse(tac.contains("call hijo,"), tac);
    }

    @Test
    @DisplayName("Una función anidada llama a su hermana con la etiqueta de la hermana")
    void llamadaEntreHermanasAnidadas() {
        String tac = tac("""
                function padre(): integer {
                    function a(): integer { return b(); }
                    function b(): integer { return 1; }
                    return a();
                }
                """);

        assertTrue(tac.contains("function padre_a, 28\nt0 = call padre_b, 0"), tac);
    }

    @Test
    @DisplayName("La llamada a una anidada antes de su declaración usa padre_hijo")
    void llamadaAntesDeLaDeclaracion() {
        String tac = tac("""
                function padre(): integer {
                    let r: integer = hijo();
                    function hijo(): integer { return 1; }
                    return r;
                }
                """);

        assertTrue(tac.contains("t0 = call padre_hijo, 0"), tac);
    }

    @Test
    @DisplayName("Dos funciones con el mismo nombre en ámbitos distintos no comparten etiqueta")
    void funcionesHomonimasEnBloquesDistintos() {
        String tac = tac("""
                { function h(): integer { return 1; } print(h()); }
                { function h(): integer { return 2; } print(h()); }
                """);

        assertTrue(tac.contains("t0 = call h, 0\nprint t0\nt0 = call h_1, 0"), tac);
        assertTrue(tac.contains("function h, 24\nreturn 1"), tac);
        assertTrue(tac.contains("function h_1, 24\nreturn 2"), tac);
    }

    @Test
    @DisplayName("La variable de un foreach que sombrea a otra no pisa la exterior")
    void foreachConVariableSombreada() {
        String tac = tac("""
                let x: integer = 0;
                let v: integer[] = [1, 2];
                foreach (x in v) { print(x); }
                print(x);
                """);

        assertTrue(tac.contains("x_1 = t3\nprint x_1"), tac);
        assertFalse(tac.contains("\nx = t"), tac);
    }

    @Test
    @DisplayName("Una variable de foreach con forma de temporal no pisa los temporales del ciclo")
    void foreachConVariableConFormaDeTemporal() {
        String tac = tac("let v: integer[] = [7]; foreach (t0 in v) { print(t0); }");

        assertTrue(tac.contains("t0_v = t3\nprint t0_v"), tac);
        assertFalse(tac.contains("t0 = t3"), tac);
    }

    @Test
    @DisplayName("La variable del catch usa su nombre TAC")
    void catchConVariableSombreada() {
        String tac = tac("""
                let err: string = "nada";
                let v: integer[] = [1];
                try { print(v[3]); } catch (err) { print(err); }
                print(err);
                """);

        assertTrue(tac.contains("catch err_1\nprint err_1"), tac);
        assertTrue(tac.endsWith("L1:\nprint err\nendfunc"), tac);
    }

    @Test
    @DisplayName("foreach itera sobre una copia del arreglo aunque el cuerpo reasigne la variable")
    void foreachIteraSobreUnaCopia() {
        String tac = tac("let v: integer[] = [1, 2]; foreach (e in v) { v = [9]; print(e); }");

        assertTrue(tac.contains("t0 = v\nt1 = len t0"), tac);
        assertTrue(tac.contains("t3 = t0[t2]"), tac);
        assertFalse(tac.contains("v[t"), tac);
    }

    private static String tac(String fuente) {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(fuente);
        assertTrue(resultado.exitoso(), () -> "El caso de prueba debe ser válido: " + resultado.errores());
        return resultado.tac().toString();
    }

    private static int ocurrencias(String texto, String buscado) {
        return texto.split(java.util.regex.Pattern.quote(buscado), -1).length - 1;
    }
}
