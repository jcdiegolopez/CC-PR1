package com.lexsynanalyzer.semantic;

import com.lexsynanalyzer.analyzer.AnalysisError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lexsynanalyzer.semantic.TiposSemanticosTest.assertSinErrores;
import static com.lexsynanalyzer.semantic.TiposSemanticosTest.semanticos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Funciones: parámetros, retorno, llamadas, recursión y closures. */
class FuncionesSemanticasTest {

    @Test
    @DisplayName("Una función válida con parámetros y retorno no genera errores")
    void aceptaUnaFuncionCorrecta() {
        assertSinErrores("""
                function sumar(a: integer, b: integer): integer {
                    return a + b;
                }
                let total: integer = sumar(2, 3);
                """);
    }

    @Test
    @DisplayName("Los parámetros solo existen dentro del cuerpo de la función")
    void aislaLosParametrosEnElCuerpo() {
        List<AnalysisError> errores = semanticos("""
                function saludar(nombre: string): string {
                    return nombre;
                }
                print(nombre);
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertEquals("nombre", errores.getFirst().simbolo());
    }

    @Test
    @DisplayName("Una llamada con menos argumentos de los declarados es un error")
    void rechazaLlamadaConCantidadIncorrectaDeArgumentos() {
        List<AnalysisError> errores = semanticos("""
                function sumar(a: integer, b: integer): integer {
                    return a + b;
                }
                let total: integer = sumar(2);
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("espera 2 argumento(s)"), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("se recibieron 1"), errores.toString());
    }

    @Test
    @DisplayName("Un argumento de tipo incorrecto se reporta en su propia posición")
    void rechazaArgumentoDeTipoIncorrecto() {
        List<AnalysisError> errores = semanticos("""
                function sumar(a: integer, b: integer): integer {
                    return a + b;
                }
                let total: integer = sumar(2, "tres");
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertEquals(4, errores.getFirst().linea());
        assertEquals("b", errores.getFirst().simbolo());
        assertTrue(errores.getFirst().descripcion().contains("string"), errores.toString());
    }

    @Test
    @DisplayName("Llamar a algo que no es una función es un error")
    void rechazaLlamadaSobreUnaVariable() {
        List<AnalysisError> errores = semanticos("""
                let x: integer = 5;
                x();
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("no puede invocarse"), errores.toString());
    }

    @Test
    @DisplayName("Una función debe invocarse antes de utilizar su resultado como valor")
    void rechazaFuncionComoOperandoSinInvocarla() {
        List<AnalysisError> errores = semanticos("""
                function identidad(): integer {
                    return 1;
                }
                let resultado: integer = identidad * 2;
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertEquals("identidad", errores.getFirst().simbolo());
        assertTrue(errores.getFirst().descripcion().contains("debe invocarse con paréntesis"),
                errores.toString());

        assertSinErrores("""
                function identidad(): integer {
                    return 1;
                }
                let resultado: integer = identidad() * 2;
                """);
    }

    @Test
    @DisplayName("Llamar a una función no declarada es un error")
    void rechazaLlamadaAFuncionInexistente() {
        List<AnalysisError> errores = semanticos("let x: integer = calcular(1);");

        assertEquals(1, errores.size(), errores.toString());
        assertEquals("calcular", errores.getFirst().simbolo());
    }

    @Test
    @DisplayName("El tipo de retorno de la función determina el tipo de la llamada")
    void usaElTipoDeRetornoEnLaLlamada() {
        List<AnalysisError> errores = semanticos("""
                function nombre(): string {
                    return "Compiscript";
                }
                let x: integer = nombre();
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("string"), errores.toString());
    }

    @Test
    @DisplayName("El valor devuelto debe coincidir con el tipo de retorno declarado")
    void rechazaRetornoDeTipoIncorrecto() {
        List<AnalysisError> errores = semanticos("""
                function sumar(a: integer): integer {
                    return "no soy un entero";
                }
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertEquals(2, errores.getFirst().linea());
    }

    @Test
    @DisplayName("Una función con tipo de retorno declarado debe devolver un valor")
    void exigeRetornoEnFuncionConTipoDeclarado() {
        List<AnalysisError> errores = semanticos("""
                function sumar(a: integer): integer {
                    print(a);
                }
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("ninguna de sus rutas"), errores.toString());
    }

    @Test
    @DisplayName("Una función sin tipo declarado es void y no puede devolver un valor")
    void rechazaValorEnFuncionSinTipoDeRetorno() {
        assertSinErrores("""
                function registrar(mensaje: string) {
                    print(mensaje);
                    return;
                }
                registrar("hola");
                """);

        List<AnalysisError> errores = semanticos("""
                function registrar(mensaje: string) {
                    return mensaje;
                }
                """);
        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("no declara tipo de retorno"), errores.toString());
    }

    @Test
    @DisplayName("Los parámetros repetidos se reportan como error")
    void rechazaParametrosRepetidos() {
        List<AnalysisError> errores = semanticos("""
                function sumar(a: integer, a: integer): integer {
                    return a;
                }
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("repetido"), errores.toString());
    }

    @Test
    @DisplayName("Una función puede llamarse a sí misma y llamar a otra declarada más abajo")
    void permiteRecursionYDeclaracionPosterior() {
        assertSinErrores("""
                function factorial(n: integer): integer {
                    if (n <= 1) {
                        return 1;
                    }
                    return n * factorial(n - 1);
                }
                """);

        assertSinErrores("""
                function primera(n: integer): integer {
                    return segunda(n);
                }
                function segunda(n: integer): integer {
                    return n + 1;
                }
                """);
    }

    @Test
    @DisplayName("Una función anidada ve las variables y parámetros de la función que la contiene")
    void permiteClosures() {
        assertSinErrores("""
                function crearContador(inicio: integer): integer {
                    let paso: integer = 2;
                    function siguiente(): integer {
                        return inicio + paso;
                    }
                    return siguiente();
                }
                """);
    }

    @Test
    @DisplayName("Una función anidada no es visible fuera de la función que la declara")
    void ocultaLasFuncionesAnidadas() {
        List<AnalysisError> errores = semanticos("""
                function externa(): integer {
                    function interna(): integer {
                        return 1;
                    }
                    return interna();
                }
                let x: integer = interna();
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertEquals("interna", errores.getFirst().simbolo());
    }

    @Test
    @DisplayName("Declarar dos funciones con el mismo nombre en un ámbito es un error")
    void rechazaFuncionesDuplicadas() {
        List<AnalysisError> errores = semanticos("""
                function f(): integer {
                    return 1;
                }
                function f(): integer {
                    return 2;
                }
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("ya está declarada"), errores.toString());
    }
}
