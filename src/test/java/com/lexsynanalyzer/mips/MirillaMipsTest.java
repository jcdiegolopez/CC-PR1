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

    private static final Path CASOS = Path.of("src", "test", "resources", "mips");
    private static final Path SALIDA_ASM = Path.of("target", "asm");
    private static final Set<String> TERMINAN_CON_ERROR = Set.of("division_cero", "error_indice");

    static Stream<Arguments> casos() {
        return programas().stream().flatMap(caso -> Stream.of(
                Arguments.of(caso, "ingenuo", FabricaRegistros.INGENUO),
                Arguments.of(caso, "getreg", FabricaRegistros.GETREG)));
    }

    static List<String> programas() {
        try (Stream<Path> archivos = Files.list(CASOS)) {
            return archivos.map(p -> p.getFileName().toString())
                    .filter(nombre -> nombre.endsWith(".cps"))
                    .map(nombre -> nombre.substring(0, nombre.length() - ".cps".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("casos")
    void optimizadoImprimeLoMismoYEsMasCorto(String caso, String modo, FabricaRegistros fabrica) {
        ProgramaMips original = MipsDePrueba.compilar(leer(caso + ".cps"), fabrica);
        ProgramaMips optimizado = new Mirilla().optimizar(original).programa();
        guardar(caso + "_" + modo + "_opt.asm", optimizado.toString());

        SimuladorMips.Resultado resultado = SimuladorMips.ejecutar(optimizado.toString());

        assertEquals(leer(caso + ".salida"), resultado.salida());
        assertEquals(TERMINAN_CON_ERROR.contains(caso) ? 1 : 0, resultado.codigoSalida());
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
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(leer("control_ciclos.cps"));

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
                usadas.addAll(new Mirilla().optimizar(MipsDePrueba.compilar(leer(caso + ".cps"), modo))
                        .aplicaciones().keySet());
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

    private static String leer(String archivo) {
        try {
            return Files.readString(CASOS.resolve(archivo)).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
