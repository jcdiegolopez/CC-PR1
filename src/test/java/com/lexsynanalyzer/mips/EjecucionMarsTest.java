package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Las mismas pruebas de punta a punta pero en MARS 4.5 real, no en {@code SimuladorMips}. Cada programa de
 * {@code src/test/resources/mips} se corre tal como lo entrega el IDE (GetReg + mirilla) y en la versión
 * más simple (ingenuo, sin mirilla). Si {@code tools/Mars4_5.jar} no está, todo se salta.
 */
class EjecucionMarsTest {

    @BeforeAll
    static void requiereMars() {
        assumeTrue(EjecutorMars.disponible(), "No está " + EjecutorMars.JAR + ": se saltan las pruebas en MARS");
    }

    static List<String> programas() {
        return CasosMips.todos();
    }

    /** El IDE nunca compila en modo traza, así que esos casos van solo en la versión ingenua. */
    static List<String> programasDelIde() {
        return CasosMips.todos().stream().filter(caso -> !CasosMips.usaTraza(caso)).toList();
    }

    @Test
    void imprimeLaSumaDeHitoCero() {
        EjecutorMars.Resultado resultado = EjecutorMars.ejecutar(LexSynAnalyzer.analizarTexto("print(1 + 2);")
                .mips().toString());

        assertEquals("3", resultado.salida());
        assertEquals(0, resultado.codigoSalida());
        assertEquals(EjecutorMars.Estado.TERMINO, resultado.estado());
    }

    @ParameterizedTest(name = "{0} (IDE: getreg + mirilla)")
    @MethodSource("programasDelIde")
    void loQueEntregaElIdeCorreEnMars(String caso) {
        AnalysisResult analisis = LexSynAnalyzer.analizarTexto(CasosMips.fuente(caso));
        assertTrue(analisis.tieneMips(), caso);

        assertCorre(caso, EjecutorMars.ejecutar(analisis.mips().toString()));
    }

    @ParameterizedTest(name = "{0} (ingenuo, sin mirilla)")
    @MethodSource("programas")
    void elAsignadorIngenuoCorreEnMars(String caso) {
        assertCorre(caso, EjecutorMars.ejecutar(CasosMips.compilar(caso, FabricaRegistros.INGENUO, false).toString()));
    }

    @Test
    void unCicloInfinitoSeCortaPorElLimiteDePasos() {
        EjecutorMars.Resultado resultado = EjecutorMars.ejecutar(
                LexSynAnalyzer.analizarTexto("while (true) { }").mips().toString(), 10_000);

        assertEquals(EjecutorMars.Estado.LIMITE_DE_PASOS, resultado.estado());
    }

    @Test
    void distingueErroresDeEnsambladoYDeEjecucion() {
        assertEquals(EjecutorMars.Estado.ERROR_ENSAMBLADO,
                EjecutorMars.ejecutar(".text\nmain:\n    noexiste $t0\n").estado());
        assertEquals(EjecutorMars.Estado.ERROR_EJECUCION,
                EjecutorMars.ejecutar(".text\nmain:\n    lw $t0, 0($zero)\n").estado());
    }

    private static void assertCorre(String caso, EjecutorMars.Resultado resultado) {
        assertEquals(EjecutorMars.Estado.TERMINO, resultado.estado(), () -> caso + "\n" + resultado.salida());
        assertEquals(CasosMips.salida(caso).stripTrailing(), resultado.salida(), caso);
        assertEquals(CasosMips.codigoEsperado(caso), resultado.codigoSalida(), caso);
    }
}
