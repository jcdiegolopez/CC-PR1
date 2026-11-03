package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Instrucciones TAC que llevan el tipo porque el código máquina cambia según él. */
class GeneradorTACTipadoTest {

    private static String tac(String fuente) {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(fuente);
        assertTrue(resultado.exitoso(), () -> "El caso debe compilar: " + resultado.errores());
        return resultado.tac().toString().replace("\r\n", "\n");
    }

    @Test
    @DisplayName("+ entre strings es concat, también en cadena")
    void concatenacion() {
        String tac = tac("let a: string = \"x\"; let b: string = a + \"y\" + \"z\";");

        assertTrue(tac.contains("t0 = concat a, \"y\"\nt0 = concat t0, \"z\"\nb = t0"), tac);
    }

    @Test
    @DisplayName("+ entre enteros sigue siendo +")
    void sumaEntera() {
        assertTrue(tac("let a: integer = 1; let b: integer = a + 2;").contains("t0 = a + 2"));
    }

    @Test
    @DisplayName("== y != entre strings comparan contenido")
    void comparacionDeStrings() {
        String tac = tac("""
                let s: string = "a";
                let e: boolean = s == "a";
                let n: boolean = s != "b";
                let k: boolean = 1 == 2;
                """);

        assertTrue(tac.contains("t0 = streq s, \"a\""), tac);
        assertTrue(tac.contains("t0 = strne s, \"b\""), tac);
        assertTrue(tac.contains("t0 = 1 == 2"), tac);
    }

    @Test
    @DisplayName("print elige la instrucción según el tipo")
    void printPorTipo() {
        String tac = tac("""
                let s: string = "hola";
                print(s);
                print(true);
                print(1 < 2);
                print(3);
                print(s + "!");
                """);

        assertEquals("""
                function main, 28
                s = "hola"
                prints s
                printb true
                t0 = 1 < 2
                printb t0
                print 3
                t0 = concat s, "!"
                prints t0
                endfunc""", tac);
    }

    @Test
    @DisplayName("El resultado de un método string se imprime como string")
    void printDeLlamadaString() {
        String tac = tac("""
                class A { function f(): string { return "a"; } }
                let a: A = new A();
                print(a.f());
                """);

        assertTrue(tac.contains("t0 = call A_f, 1\nprints t0"), tac);
    }

    @Test
    @DisplayName("Sin análisis semántico (GeneradorTAC simple) no hay tipos: TAC sin tipo")
    void sinTiposSeEmiteSinTipo() {
        var parser = new com.lexsynanalyzer.parser.LexSynAnalyzerParser(new org.antlr.v4.runtime.CommonTokenStream(
                new com.lexsynanalyzer.parser.LexSynAnalyzerLexer(
                        org.antlr.v4.runtime.CharStreams.fromString("print(\"a\" + \"b\");"))));
        String tac = new GeneradorTAC().generar(parser.program()).toString();

        assertTrue(tac.contains("t0 = \"a\" + \"b\"\nprint t0"), tac);
    }
}
