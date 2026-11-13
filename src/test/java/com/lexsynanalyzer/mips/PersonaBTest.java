package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.tac.Instruccion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonaBTest {

    static Stream<Arguments> modos() {
        return Stream.of(
                Arguments.of("ingenuo", FabricaRegistros.INGENUO),
                Arguments.of("getreg", FabricaRegistros.GETREG));
    }

    @ParameterizedTest(name = "parámetros y resultados ({0})")
    @MethodSource("modos")
    void llamadasMuevenArgumentosAlFrameFuturo(String modo, FabricaRegistros fabrica) {
        SimuladorMips.Resultado resultado = ejecutar("""
                function sumar(a: integer, b: integer): integer {
                    return a + b;
                }
                print(sumar(1 + 2, 4 + 5));
                """, fabrica);

        assertEquals("12\n", resultado.salida());
        assertEquals(0, resultado.codigoSalida());
    }

    @ParameterizedTest(name = "recursión ({0})")
    @MethodSource("modos")
    void llamadasRecursivasRestauranCadaFrame(String modo, FabricaRegistros fabrica) {
        SimuladorMips.Resultado resultado = ejecutar("""
                function factorial(n: integer): integer {
                    if (n <= 1) {
                        return 1;
                    }
                    return n * factorial(n - 1);
                }
                print(factorial(5));
                """, fabrica);

        assertEquals("120\n", resultado.salida());
        assertEquals(0, resultado.codigoSalida());
    }

    @ParameterizedTest(name = "recursión mutua ({0})")
    @MethodSource("modos")
    void llamadasMutuamenteRecursivasRestauranCadaFrame(String modo, FabricaRegistros fabrica) {
        SimuladorMips.Resultado resultado = ejecutar("""
                function par(n: integer): boolean {
                    if (n == 0) {
                        return true;
                    }
                    return impar(n - 1);
                }
                function impar(n: integer): boolean {
                    if (n == 0) {
                        return false;
                    }
                    return par(n - 1);
                }
                print(par(8));
                """, fabrica);

        assertEquals("true\n", resultado.salida());
        assertEquals(0, resultado.codigoSalida());
    }

    @ParameterizedTest(name = "funciones anidadas ({0})")
    @MethodSource("modos")
    void llamadasAnidadasResuelvenLaEtiquetaTac(String modo, FabricaRegistros fabrica) {
        SimuladorMips.Resultado resultado = ejecutar("""
                function exterior(a: integer): integer {
                    function interior(b: integer): integer {
                        return b + 2;
                    }
                    return interior(a);
                }
                print(exterior(5));
                """, fabrica);

        assertEquals("7\n", resultado.salida());
        assertEquals(0, resultado.codigoSalida());
    }

    @ParameterizedTest(name = "captura local ({0})")
    @MethodSource("modos")
    void divisionPorCeroSaltaAlCatch(String modo, FabricaRegistros fabrica) {
        SimuladorMips.Resultado resultado = ejecutar("""
                try {
                    let valor: integer = 10 / 0;
                    print(valor);
                } catch (error) {
                    print(error);
                }
                """, fabrica);

        assertEquals("División entre cero\n", resultado.salida());
        assertEquals(0, resultado.codigoSalida());
    }

    @ParameterizedTest(name = "desenrollado entre funciones ({0})")
    @MethodSource("modos")
    void excepcionEnFuncionRestauraElFrameDelLlamador(String modo, FabricaRegistros fabrica) {
        SimuladorMips.Resultado resultado = ejecutar("""
                function fallar(): integer {
                    return 1 / 0;
                }
                try {
                    print(fallar());
                } catch (error) {
                    print(error);
                }
                """, fabrica);

        assertEquals("División entre cero\n", resultado.salida());
        assertEquals(0, resultado.codigoSalida());
    }

    @ParameterizedTest(name = "try anidado ({0})")
    @MethodSource("modos")
    void excepcionUsaElManejadorMasCercano(String modo, FabricaRegistros fabrica) {
        SimuladorMips.Resultado resultado = ejecutar("""
                try {
                    try {
                        let valor: integer = 1 / 0;
                    } catch (interno) {
                        print(interno);
                    }
                } catch (externo) {
                    print(externo);
                }
                """, fabrica);

        assertEquals("División entre cero\n", resultado.salida());
        assertEquals(0, resultado.codigoSalida());
    }

    @ParameterizedTest(name = "return dentro de try ({0})")
    @MethodSource("modos")
    void retornarDesdeTryDesactivaElManejador(String modo, FabricaRegistros fabrica) {
        SimuladorMips.Resultado resultado = ejecutar("""
                function valor(): integer {
                    try {
                        return 7;
                    } catch (error) {
                        return 9;
                    }
                }
                print(valor());
                """, fabrica);

        assertEquals("7\n", resultado.salida());
        assertEquals(0, resultado.codigoSalida());
    }

    @ParameterizedTest(name = "error sin catch ({0})")
    @MethodSource("modos")
    void errorSinManejadorImprimeMensajeYTerminaConCodigoUno(String modo, FabricaRegistros fabrica) {
        SimuladorMips.Resultado resultado = ejecutar("print(1 / 0);", fabrica);

        assertEquals("Error en tiempo de ejecución: División entre cero\n", resultado.salida());
        assertEquals(1, resultado.codigoSalida());
    }

    @Test
    void foreachDeCadenaUsaOperacionesTacTipadas() {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto("""
                let texto: string = "abc";
                foreach (caracter in texto) {
                    print(caracter);
                }
                """);

        assertTrue(resultado.exitoso(), () -> String.valueOf(resultado.errores()));
        assertTrue(resultado.tac().instrucciones().stream()
                .anyMatch(instruccion -> instruccion.op().equals(Instruccion.OP_LONGITUD_CADENA)));
        assertTrue(resultado.tac().instrucciones().stream()
                .anyMatch(instruccion -> instruccion.op().equals(Instruccion.OP_CARACTER_EN)));
    }

    @ParameterizedTest(name = "switch de strings ({0})")
    @MethodSource("modos")
    void switchDeStringsComparaContenidoYNoDirecciones(String modo, FabricaRegistros fabrica) {
        SimuladorMips.Resultado resultado = ejecutar("""
                let color: string = "az" + "ul";
                switch (color) {
                    case "rojo":
                        print(0);
                        break;
                    case "azul":
                        print(1);
                        break;
                    default:
                        print(2);
                }
                """, fabrica);

        assertEquals("1\n", resultado.salida());
        assertEquals(0, resultado.codigoSalida());
    }

    @Test
    void trazaMuestraAnidamientoDeFramesSinAlterarValoresDeRetorno() {
        ProgramaMips programa = MipsDePrueba.compilar("""
                function bajar(n: integer): integer {
                    if (n == 0) {
                        return 0;
                    }
                    return bajar(n - 1);
                }
                print(bajar(2));
                """, FabricaRegistros.GETREG, true);

        SimuladorMips.Resultado resultado = SimuladorMips.ejecutar(programa.toString());

        assertEquals("""
                -> main
                  -> bajar
                    -> bajar
                      -> bajar
                      <- bajar
                    <- bajar
                  <- bajar
                0
                <- main
                """, resultado.salida());
        assertEquals(0, resultado.codigoSalida());
    }

    private static SimuladorMips.Resultado ejecutar(String fuente, FabricaRegistros modo) {
        return SimuladorMips.ejecutar(MipsDePrueba.compilar(fuente, modo).toString());
    }
}
