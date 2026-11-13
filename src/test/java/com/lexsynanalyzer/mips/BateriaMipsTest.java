package com.lexsynanalyzer.mips;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Toda la batería de {@code src/test/resources/mips} (ver {@link CasosMips}) con los dos asignadores de
 * registros, sin mirilla. Los casos con nombre de la rúbrica ({@code recursion_*}, {@code try_*},
 * {@code control_*}, {@code traza_fib}, …) solo están aquí; los de {@code EjecucionMipsTest} y
 * {@code MemoriaMipsTest} se repiten, lo que no cuesta nada. Cada {@code .asm} queda en {@code target/asm/}.
 */
class BateriaMipsTest {

    private static final Path SALIDA_ASM = Path.of("target", "asm");

    static Stream<Arguments> casos() {
        return CasosMips.todos().stream().flatMap(caso -> Stream.of(
                Arguments.of(caso, "ingenuo", FabricaRegistros.INGENUO),
                Arguments.of(caso, "getreg", FabricaRegistros.GETREG)));
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("casos")
    void imprimeLaSalidaEsperada(String caso, String modo, FabricaRegistros fabrica) {
        String asm = CasosMips.compilar(caso, fabrica, false).toString();
        guardar(caso + "_" + modo + ".asm", asm);

        SimuladorMips.Resultado resultado = SimuladorMips.ejecutar(asm);

        assertEquals(CasosMips.salida(caso), resultado.salida(), caso);
        assertEquals(CasosMips.codigoEsperado(caso), resultado.codigoSalida(), caso);
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
