package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.analyzer.TipoError;
import com.lexsynanalyzer.tac.Instruccion;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Arreglos, matrices, objetos, herencia, {@code foreach} sobre strings y el error de índice, en los dos
 * asignadores de registros. Cada {@code .asm} queda en {@code target/asm/} para {@code tools/probar_mars.sh}.
 */
class MemoriaMipsTest {

    private static final Path CASOS = Path.of("src", "test", "resources", "mips");
    private static final Path SALIDA_ASM = Path.of("target", "asm");
    private static final List<String> EXITOSOS =
            List.of("arreglos", "matriz", "foreach_string", "indice_string", "clases", "herencia", "try_indice");

    static Stream<Arguments> exitosos() {
        return EXITOSOS.stream().flatMap(caso -> modos().map(modo -> Arguments.of(caso, modo.get()[0], modo.get()[1])));
    }

    static Stream<Arguments> modos() {
        return Stream.of(Arguments.of("ingenuo", FabricaRegistros.INGENUO),
                Arguments.of("getreg", FabricaRegistros.GETREG));
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("exitosos")
    void imprimeLaSalidaEsperada(String caso, String modo, FabricaRegistros fabrica) {
        SimuladorMips.Resultado resultado = SimuladorMips.ejecutar(compilar(caso, modo, fabrica));

        assertEquals(leer(caso + ".salida"), resultado.salida());
        assertEquals(0, resultado.codigoSalida());
    }

    @ParameterizedTest(name = "error_indice ({0})")
    @MethodSource("modos")
    void indiceFueraDeRangoSinTryTerminaConError(String modo, FabricaRegistros fabrica) {
        SimuladorMips.Resultado resultado = SimuladorMips.ejecutar(compilar("error_indice", modo, fabrica));

        assertEquals(leer("error_indice.salida"), resultado.salida());
        assertEquals(1, resultado.codigoSalida());
    }

    @Test
    void ningunaOperacionDeMemoriaQuedaPendiente() {
        for (String caso : EXITOSOS) {
            String asm = compilar(caso, "getreg", FabricaRegistros.GETREG);
            assertFalse(asm.contains("PENDIENTE"), caso);
        }
    }

    @Test
    void elAccesoAUnCampoUsaElOffsetDelTac() {
        String asm = MipsDePrueba.compilar("""
                class P { let a: integer = 1; let b: integer = 2; }
                let p: P = new P();
                print(p.b);
                """, FabricaRegistros.INGENUO).toString();

        assertTrue(asm.contains("li $a0, 8"), asm);
        assertTrue(asm.matches("(?s).*lw \\$t\\d, 4\\(\\$t\\d\\).*"), asm);
    }

    @Test
    void elAccesoConIndiceChequeaLosLimites() {
        String asm = MipsDePrueba.compilar("let v: integer[] = [1]; print(v[0]);", FabricaRegistros.GETREG).toString();

        assertTrue(asm.contains("bltz"), asm);
        assertTrue(asm.contains("bge"), asm);
        assertTrue(asm.contains(RuntimeMemoria.ERROR_INDICE + ":"), asm);
        assertTrue(asm.contains(RuntimeMemoria.ALLOC + ":"), asm);
    }

    @Test
    void indexarUnStringEsCharatYNoUnAccesoDeArreglo() {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto("let s: string = \"ab\"; print(s[1]);");

        assertTrue(resultado.exitoso(), () -> String.valueOf(resultado.errores()));
        assertTrue(resultado.tac().instrucciones().stream().anyMatch(i -> i.op().equals(Instruccion.OP_CARACTER_EN)));
        assertFalse(resultado.tac().instrucciones().stream().anyMatch(i -> i.op().equals(Instruccion.OP_LEER_INDICE)));
    }

    @Test
    void asignarAUnCaracterDeUnStringEsErrorSemantico() {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto("let s: string = \"ab\"; s[0] = \"x\";");

        assertTrue(resultado.errores().stream().anyMatch(e -> e.tipo() == TipoError.SEMANTICO
                && e.descripcion().contains("inmutables")), () -> String.valueOf(resultado.errores()));
        assertTrue(resultado.tac().estaVacio());
    }

    @Test
    void offsetCampo_leeElNumeroDespuesDeLaArroba() {
        assertEquals(16, TraductorMemoria.offsetCampo("raza @16"));
        assertEquals(0, TraductorMemoria.offsetCampo("nombre @0"));
        assertThrows(IllegalStateException.class, () -> TraductorMemoria.offsetCampo("nombre"));
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
