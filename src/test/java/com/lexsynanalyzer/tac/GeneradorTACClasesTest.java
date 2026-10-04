package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.analyzer.TipoError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneradorTACClasesTest {

    @ParameterizedTest
    @ValueSource(strings = {"clases", "herencia"})
    void generaElTacEsperado(String caso) {
        assertEquals(TacTestSupport.esperado(caso), TacTestSupport.generar(caso).toString().replace("\r\n", "\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"error_clase", "error_herencia"})
    void conErroresDeClases_noHayTac(String caso) {
        AnalysisResult resultado = TacTestSupport.analizar(caso);

        assertTrue(resultado.tac().estaVacio());
        assertTrue(resultado.errores().stream().anyMatch(e -> e.tipo() == TipoError.SEMANTICO));
    }

    @Test
    void new_emiteInicializadoresDelPadrePrimeroYLuegoElConstructorHeredado() {
        String tac = tac("""
                class A { let x: integer = 1; function constructor(v: integer) { this.x = v; } }
                class B : A { let y: integer = 2; }
                let b: B = new B(5);
                """);

        assertTrue(tac.contains("""
                t0 = new B, 8
                t0.x = 1
                t0.y = 2
                param t0
                param 5
                call A_constructor, 2
                b = t0"""), tac);
    }

    @Test
    void sinConstructorEnLaJerarquia_soloSeReservaElObjeto() {
        String tac = tac("class Vacia {} let v: Vacia = new Vacia();");

        assertTrue(tac.contains("t0 = new Vacia, 0\nv = t0"), tac);
        assertFalse(tac.contains("call"), tac);
    }

    @Test
    void metodo_recibeThisComoParametroCero() {
        String tac = tac("""
                class C { function f(a: integer, b: integer): integer { return a + b; } }
                let c: C = new C();
                let r: integer = c.f(1, 2);
                """);

        assertTrue(tac.contains("param c\nparam 1\nparam 2\nt0 = call C_f, 3"), tac);
        assertTrue(tac.contains("function C_f, 44"), "24 + this(8) + a(4) + b(4) + 1 temporal(4):\n" + tac);
    }

    @Test
    void sobrescritura_seResuelveConElTipoDeclarado() {
        String tac = tac("""
                class A { function h(): integer { return 1; } }
                class B : A { function h(): integer { return 2; } }
                let b: B = new B();
                let a: A = b;
                let x: integer = b.h();
                let y: integer = a.h();
                """);

        assertTrue(tac.contains("param b\nt0 = call B_h, 1"), tac);
        assertTrue(tac.contains("param a\nt0 = call A_h, 1"), tac);
    }

    @Test
    void metodoHeredadoSinSobrescribir_llamaAlDelPadre() {
        String tac = tac("""
                class A { function h(): integer { return 1; } }
                class B : A { }
                let b: B = new B();
                let x: integer = b.h();
                """);

        assertTrue(tac.contains("t0 = call A_h, 1"), tac);
    }

    private static String tac(String fuente) {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(fuente);
        assertTrue(resultado.exitoso(), () -> "El caso de prueba debe ser válido: " + resultado.errores());
        return resultado.tac().toString();
    }
}
