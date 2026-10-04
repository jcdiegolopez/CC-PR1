package com.lexsynanalyzer.semantic;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ExpressionContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ProgramContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tabla de símbolos ampliada: direcciones, registros de activación y layout de clases. */
class AsignadorMemoriaTest {

    @Test
    void globales_vanEnLaRegionEstaticaAlineadasASuTamano() {
        Layout layout = layout("let a: integer = 1; let s: string = \"x\"; let b: boolean = true;");

        assertEquals(List.of(
                new Direccion("a", Direccion.Categoria.GLOBAL, 0, 4, TipoDato.INTEGER),
                new Direccion("s", Direccion.Categoria.GLOBAL, 8, 8, TipoDato.STRING),
                new Direccion("b", Direccion.Categoria.GLOBAL, 16, 4, TipoDato.BOOLEAN)),
                layout.globales().stream().map(Layout.Variable::direccion).toList());
    }

    @Test
    void frameDeFuncion_cabeceraParametrosYLocales() {
        Layout layout = layout("""
                function f(n: integer, s: string): integer {
                    let x: integer = n;
                    { let y: integer = x; }
                    return x;
                }
                """);

        Layout.Frame frame = layout.frames().get("f");
        assertEquals(List.of(
                        new Direccion("n", Direccion.Categoria.PARAM, 24, 4, TipoDato.INTEGER),
                        new Direccion("s", Direccion.Categoria.PARAM, 32, 8, TipoDato.STRING),
                        new Direccion("x", Direccion.Categoria.LOCAL, 40, 4, TipoDato.INTEGER),
                        new Direccion("y", Direccion.Categoria.LOCAL, 44, 4, TipoDato.INTEGER)),
                frame.variables().stream().map(Layout.Variable::direccion).toList());
        assertEquals(48, frame.tamanoBase());
    }

    @Test
    void metodo_reservaThisComoPrimerParametro() {
        Layout layout = layout("class C { function m(a: integer) { } }");

        Layout.Frame frame = layout.frames().get("C_m");
        assertEquals("this", frame.variables().get(0).nombre());
        assertEquals(24, frame.variables().get(0).direccion().offset());
        assertEquals(32, frame.variables().get(1).direccion().offset());
        assertEquals(36, frame.tamanoBase());
    }

    @Test
    void funcionAnidada_seRegistraComoPadreHijo() {
        Layout layout = layout("""
                function padre(): integer {
                    function hijo(k: integer): integer { return k; }
                    return hijo(1);
                }
                """);

        assertEquals(28, layout.tamanoBase("padre_hijo"));
    }

    @Test
    void variableSombreada_recibeNombreUnico() {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto("""
                let x: integer = 1;
                {
                    let x: integer = 2;
                    { let x: integer = 3; print(x); }
                    print(x);
                }
                print(x);
                """);

        assertEquals("""
                function main, 32
                x = 1
                x_1 = 2
                x_2 = 3
                print x_2
                print x_1
                print x
                endfunc""", resultado.tac().toString());
    }

    @Test
    void parametroQueSombreaUnaGlobal_recibeNombreUnico() {
        Layout layout = layout("let n: integer = 1; function f(n: integer): integer { return n; }");

        assertEquals("n_1", layout.frames().get("f").variables().get(0).direccion().nombreTac());
    }

    @Test
    void nombreConFormaDeTemporal_seRenombra() {
        Layout layout = layout("let t3: integer = 1;");

        assertEquals("t3_v", layout.globales().get(0).direccion().nombreTac());
    }

    @Test
    void clases_camposDelPadrePrimeroYTamanoDeInstancia() {
        Layout layout = layout("""
                class A { let x: integer = 1; let s: string; }
                class B : A { let y: integer = 2; }
                """);

        Layout.ClaseLayout b = layout.clase("B").orElseThrow();
        assertEquals(List.of("x", "s", "y"), b.campos().stream().map(Layout.Variable::nombre).toList());
        assertEquals(List.of(0, 8, 16), b.campos().stream().map(v -> v.direccion().offset()).toList());
        assertEquals(20, b.tamanoInstancia());
        assertEquals(16, layout.clase("A").orElseThrow().tamanoInstancia());
    }

    @Test
    void resultadoSemantico_conservaEntornosHijosYTiposPorNodo() {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto("let a: integer = 1 + 2; { let b: boolean = true; }");
        ResultadoSemantico semantico = resultado.layout().semantico();

        Entorno global = semantico.tabla().global();
        assertEquals(1, global.hijos().size());
        assertTrue(global.hijos().get(0).contieneLocalmente("b"));
        assertNotNull(global.direcciones().get("a"));
        ProgramContext programa = (ProgramContext) resultado.arbol();
        ExpressionContext unoMasDos = programa.statement(0).variableDeclaration().initializer().expression();
        assertEquals(TipoDato.INTEGER, semantico.tipo(unoMasDos));
    }

    private static Layout layout(String fuente) {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(fuente);
        assertTrue(resultado.exitoso(), () -> "El caso de prueba debe ser válido: " + resultado.errores());
        return resultado.layout();
    }
}
