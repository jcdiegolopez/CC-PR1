package com.lexsynanalyzer.mips;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Corre cada programa de {@code src/test/resources/mips} con los dos asignadores de registros y
 * compara la salida con {@code <caso>.salida}. Que ingenuo y {@code GetReg} den lo mismo es la prueba
 * de que {@code getReg} es correcto.
 *
 * <p>Cada {@code .asm} queda en {@code target/asm/} para abrirlo en MARS a mano.
 */
class EjecucionMipsTest {

    private static final Path CASOS = Path.of("src", "test", "resources", "mips");
    private static final Path SALIDA_ASM = Path.of("target", "asm");
    private static final List<String> PROGRAMAS = List.of("variables", "aritmetica", "logicas", "control_if",
            "control_ciclos", "control_switch", "strings", "registros_derrame", "funciones", "bloques", "literales");

    static Stream<Arguments> casos() {
        return PROGRAMAS.stream().flatMap(caso -> Stream.of(
                Arguments.of(caso, "ingenuo", FabricaRegistros.INGENUO),
                Arguments.of(caso, "getreg", FabricaRegistros.GETREG)));
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("casos")
    void imprimeLaSalidaEsperada(String caso, String modo, FabricaRegistros fabrica) {
        SimuladorMips.Resultado resultado = SimuladorMips.ejecutar(compilar(caso, modo, fabrica));

        assertEquals(leer(caso + ".salida"), resultado.salida());
        assertEquals(0, resultado.codigoSalida());
    }

    @ParameterizedTest(name = "division_cero ({0})")
    @MethodSource("modos")
    void divisionEntreCeroTerminaConError(String modo, FabricaRegistros fabrica) {
        SimuladorMips.Resultado resultado = SimuladorMips.ejecutar(compilar("division_cero", modo, fabrica));

        assertEquals(leer("division_cero.salida"), resultado.salida());
        assertEquals(1, resultado.codigoSalida());
    }

    static Stream<Arguments> modos() {
        return Stream.of(Arguments.of("ingenuo", FabricaRegistros.INGENUO),
                Arguments.of("getreg", FabricaRegistros.GETREG));
    }

    @Test
    @DisplayName("Con más de 8 valores vivos, GetReg derrama y aun así da la salida correcta")
    void getRegDerramaCuandoNoAlcanzanLosRegistros() {
        String asm = compilar("registros_derrame", "getreg", FabricaRegistros.GETREG);

        assertTrue(asm.contains("# derrame t"), asm);
    }

    @Test
    @DisplayName("GetReg ejecuta menos lw/sw que el asignador ingenuo en todos los programas")
    void getRegAccedeMenosAMemoria() {
        for (String caso : PROGRAMAS) {
            long ingenuo = accesosAMemoria(compilar(caso, "ingenuo", FabricaRegistros.INGENUO));
            long getReg = accesosAMemoria(compilar(caso, "getreg", FabricaRegistros.GETREG));
            assertTrue(getReg < ingenuo, caso + ": getreg=" + getReg + " ingenuo=" + ingenuo);
        }
    }

    /** {@code lw}/{@code sw} escritos en el código del programa (sin contar el runtime). */
    private static long accesosAMemoria(String asm) {
        return asm.lines().dropWhile(linea -> !linea.equals(".text")).skip(2)
                .takeWhile(linea -> !linea.startsWith("__"))
                .map(String::strip).filter(l -> l.startsWith("lw ") || l.startsWith("sw ")).count();
    }

    private static String compilar(String caso, String modo, FabricaRegistros fabrica) {
        String asm = MipsDePrueba.compilar(leer(caso + ".cps"), fabrica).toString();
        try {
            Files.createDirectories(SALIDA_ASM);
            Files.writeString(SALIDA_ASM.resolve(caso + "_" + modo + ".asm"), asm);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return asm;
    }

    private static String leer(String archivo) {
        try {
            return Files.readString(CASOS.resolve(archivo)).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
