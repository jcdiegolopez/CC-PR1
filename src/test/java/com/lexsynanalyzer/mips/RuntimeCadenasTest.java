package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;
import com.lexsynanalyzer.tac.ProgramaTAC;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Llama a las rutinas de strings directamente con {@code jal}, como lo harán los traductores de
 * {@code strlen}/{@code charat} (personas B y C). Comprueba también la convención del runtime: llenar
 * {@code $t0}–{@code $t7} antes y sumarlos al final debe dar lo mismo (las rutinas no los tocan).
 *
 * <p>El {@code .asm} queda en {@code target/asm/runtime_cadenas.asm} para correrlo en MARS.
 */
class RuntimeCadenasTest {

    @Test
    void strlenCharatYConvencionDeRegistros() throws IOException {
        ProgramaTAC tac = new ProgramaTAC();
        tac.emit(Instruccion.funcion("main", 24));
        tac.emit(Instruccion.param("prueba"));
        tac.emit(Instruccion.finFuncion());
        GeneradorMIPS generador = new GeneradorMIPS(f -> n -> "0($fp)", FabricaRegistros.INGENUO)
                .registrar(Instruccion.OP_PARAM, (ins, ctx) -> arnes(ctx.programa()));

        String asm = generador.generar(tac).toString();
        Files.createDirectories(Path.of("target", "asm"));
        Files.writeString(Path.of("target", "asm", "runtime_cadenas.asm"), asm);

        String esperado = Files.readString(Path.of("src", "test", "resources", "mips", "runtime_cadenas.salida"))
                .replace("\r\n", "\n");
        SimuladorMips.Resultado resultado = SimuladorMips.ejecutar(asm);
        assertEquals(esperado, resultado.salida(), asm);
    }

    private static void arnes(ProgramaMips p) {
        p.requerir(RuntimeCadenas.STRLEN);
        p.requerir(RuntimeCadenas.CHARAT);
        p.requerir(RuntimeCadenas.CONCAT);
        p.requerir(RuntimeCadenas.STREQ);
        String hola = p.cadena("hola");
        for (int r = 0; r < 8; r++) {
            p.emit(InstruccionMips.de("li", "$t" + r, String.valueOf(r + 1)));
        }
        // strlen("hola") = 4 y strlen("") = 0
        p.emit(InstruccionMips.de("la", "$a0", hola));
        p.emit(InstruccionMips.de("jal", RuntimeCadenas.STRLEN));
        imprimirEntero(p, "$v0");
        p.emit(InstruccionMips.de("la", "$a0", p.cadena("")));
        p.emit(InstruccionMips.de("jal", RuntimeCadenas.STRLEN));
        imprimirEntero(p, "$v0");
        // charat("hola", 0) = "h"
        p.emit(InstruccionMips.de("la", "$a0", hola));
        p.emit(InstruccionMips.de("li", "$a1", "0"));
        p.emit(InstruccionMips.de("jal", RuntimeCadenas.CHARAT));
        imprimirCadena(p, "$v0");
        // charat("hola", 3) + "!" = "a!" y su largo es 1
        p.emit(InstruccionMips.de("la", "$a0", hola));
        p.emit(InstruccionMips.de("li", "$a1", "3"));
        p.emit(InstruccionMips.de("jal", RuntimeCadenas.CHARAT));
        p.emit(InstruccionMips.de("move", "$s0", "$v0"));
        p.emit(InstruccionMips.de("move", "$a0", "$s0"));
        p.emit(InstruccionMips.de("la", "$a1", p.cadena("!")));
        p.emit(InstruccionMips.de("jal", RuntimeCadenas.CONCAT));
        imprimirCadena(p, "$v0");
        p.emit(InstruccionMips.de("move", "$a0", "$s0"));
        p.emit(InstruccionMips.de("jal", RuntimeCadenas.STRLEN));
        imprimirEntero(p, "$v0");
        // charat("hola", 2) == "l"
        p.emit(InstruccionMips.de("la", "$a0", hola));
        p.emit(InstruccionMips.de("li", "$a1", "2"));
        p.emit(InstruccionMips.de("jal", RuntimeCadenas.CHARAT));
        p.emit(InstruccionMips.de("move", "$a1", "$v0"));
        p.emit(InstruccionMips.de("la", "$a0", p.cadena("l")));
        p.emit(InstruccionMips.de("jal", RuntimeCadenas.STREQ));
        imprimirEntero(p, "$v0");
        // $t0..$t7 siguen valiendo 1..8: suma 36
        p.emit(InstruccionMips.de("move", "$s1", "$zero"));
        for (int r = 0; r < 8; r++) {
            p.emit(InstruccionMips.de("addu", "$s1", "$s1", "$t" + r));
        }
        imprimirEntero(p, "$s1");
    }

    private static void imprimirEntero(ProgramaMips p, String registro) {
        p.emit(InstruccionMips.de("move", "$a0", registro));
        p.emit(InstruccionMips.de("li", "$v0", "1"));
        p.emit(InstruccionMips.de("syscall"));
        TraductorExpr.saltoDeLinea(p);
    }

    private static void imprimirCadena(ProgramaMips p, String registro) {
        p.emit(InstruccionMips.de("move", "$a0", registro));
        p.emit(InstruccionMips.de("li", "$v0", "4"));
        p.emit(InstruccionMips.de("syscall"));
        TraductorExpr.saltoDeLinea(p);
    }
}
