package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.mips.mirilla.Mirilla;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Corre cada programa de {@code src/test/resources/mips} después de la mirilla, con los dos asignadores:
 * la salida y el código de salida tienen que ser los mismos que sin optimizar, y el programa no puede
 * quedar más largo (en modo ingenuo, siempre más corto). Cada {@code .asm} optimizado queda en {@code target/asm/<caso>_<modo>_opt.asm}.
 */
class MirillaMipsTest {

    private static final Path SALIDA_ASM = Path.of("target", "asm");

    static Stream<Arguments> casos() {
        return programas().stream().flatMap(caso -> Stream.of(
                Arguments.of(caso, "ingenuo", FabricaRegistros.INGENUO),
                Arguments.of(caso, "getreg", FabricaRegistros.GETREG)));
    }

    static List<String> programas() {
        return CasosMips.todos();
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("casos")
    void optimizadoImprimeLoMismoYEsMasCorto(String caso, String modo, FabricaRegistros fabrica) {
        ProgramaMips original = CasosMips.compilar(caso, fabrica, false);
        ProgramaMips optimizado = new Mirilla().optimizar(original).programa();
        guardar(caso + "_" + modo + "_opt.asm", optimizado.toString());

        SimuladorMips.Resultado resultado = SimuladorMips.ejecutar(optimizado.toString());

        assertEquals(CasosMips.salida(caso), resultado.salida());
        assertEquals(CasosMips.codigoEsperado(caso), resultado.codigoSalida());
        // GetReg ya evita casi todos los lw/sw de más: en programas sin saltos puede no quedar nada que quitar.
        int antes = original.cantidadInstrucciones();
        int despues = optimizado.cantidadInstrucciones();
        if (fabrica == FabricaRegistros.INGENUO) {
            assertTrue(despues < antes, () -> caso + ": " + despues + " >= " + antes);
        } else {
            assertTrue(despues <= antes, () -> caso + ": " + despues + " > " + antes);
        }
    }

    @Test
    void elAnalizadorEntregaLasDosVersionesYLasEstadisticas() {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(CasosMips.fuente("control_ciclos"));

        assertTrue(resultado.tieneMips());
        assertFalse(resultado.mirilla().isEmpty());
        assertTrue(Mirilla.eliminadas(resultado.mipsSinOptimizar(), resultado.mips()) > 0);
        assertEquals(new Mirilla().optimizar(resultado.mipsSinOptimizar()).programa().toString(),
                resultado.mips().toString());
    }

    @Test
    void entreTodosLosProgramasSeUsanAlMenosSeisReglas() {
        Set<String> usadas = new TreeSet<>();
        for (String caso : programas()) {
            for (FabricaRegistros modo : List.of(FabricaRegistros.INGENUO, FabricaRegistros.GETREG)) {
                usadas.addAll(new Mirilla().optimizar(CasosMips.compilar(caso, modo, false)).aplicaciones().keySet());
            }
        }
        assertTrue(usadas.size() >= 6, usadas::toString);
    }

    private static void guardar(String archivo, String asm) {
        try {
            Files.createDirectories(SALIDA_ASM);
            Files.writeString(SALIDA_ASM.resolve(archivo), asm);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
