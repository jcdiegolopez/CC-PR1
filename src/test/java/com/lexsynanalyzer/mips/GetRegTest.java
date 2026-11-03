package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code GetReg} con el generador real: los programas TAC se escriben a mano para controlar
 * exactamente qué está vivo. Las direcciones son falsas ({@code nombre($fp)}).
 */
class GetRegTest {

    private static String asm(Instruccion... cuerpo) {
        List<Instruccion> instrucciones = new ArrayList<>();
        instrucciones.add(Instruccion.funcion("main", 64));
        instrucciones.addAll(List.of(cuerpo));
        instrucciones.add(Instruccion.finFuncion());
        com.lexsynanalyzer.tac.ProgramaTAC tac = new com.lexsynanalyzer.tac.ProgramaTAC();
        instrucciones.forEach(tac::emit);
        ProgramaMips programa = new GeneradorMIPS(f -> nombre -> nombre + "($fp)", FabricaRegistros.GETREG).generar(tac);
        return programa.texto().stream().filter(InstruccionMips::esInstruccion)
                .map(InstruccionMips::toString).map(String::strip).collect(Collectors.joining("\n"));
    }

    @Test
    void unValorQueYaEstaEnRegistroNoSeVuelveACargar() {
        String asm = asm(
                Instruccion.binaria("t0", "a", "+", "b"),
                Instruccion.binaria("t1", "t0", "*", "a"),
                Instruccion.asignar("x", "t1"));

        assertEquals("""
                lw $t0, a($fp)
                lw $t1, b($fp)
                addu $t2, $t0, $t1
                mul $t2, $t2, $t0
                sw $t2, x($fp)
                li $v0, 10
                syscall""", asm);
    }

    @Test
    void unaCopiaNoEmiteNadaYLosTemporalesMuertosNoSeGuardan() {
        String asm = asm(
                Instruccion.binaria("t0", "a", "-", "1"),
                Instruccion.asignar("y", "t0"),
                Instruccion.asignar("z", "y"));

        assertEquals("""
                lw $t0, a($fp)
                li $t1, 1
                subu $t1, $t0, $t1
                sw $t1, y($fp)
                sw $t1, z($fp)
                li $v0, 10
                syscall""", asm, "ni move ni sw de t0: solo se guardan y y z al final del bloque");
    }

    @Test
    void alCerrarElBloqueSeGuardanLasVariablesYSeOlvidanLosRegistros() {
        String asm = asm(
                Instruccion.asignar("x", "5"),
                Instruccion.etiqueta("L0"),
                Instruccion.imprimir("x"));

        assertTrue(asm.startsWith("li $t0, 5\nsw $t0, x($fp)\nlw $t0, x($fp)"), asm);
    }

    @Test
    void conMasDeOchoValoresVivosSeDerramaYSeRecarga() {
        List<Instruccion> cuerpo = new ArrayList<>();
        for (int k = 0; k < 10; k++) {
            cuerpo.add(Instruccion.binaria("t" + k, "v" + k, "*", "2"));
        }
        for (int k = 9; k > 0; k--) {
            cuerpo.add(Instruccion.binaria("t" + (k - 1), "t" + (k - 1), "+", "t" + k));
        }
        cuerpo.add(Instruccion.asignar("r", "t0"));

        String asm = asm(cuerpo.toArray(Instruccion[]::new));

        assertTrue(asm.contains("# derrame t0"), asm);
        long derrames = asm.lines().filter(l -> l.startsWith("sw") && l.contains("# derrame t")).count();
        long recargas = asm.lines().filter(l -> l.startsWith("lw") && l.contains("# derrame t")).count();
        assertEquals(derrames, recargas, "cada temporal derramado se recarga una vez:\n" + asm);
    }

    @Test
    void antesDeUnaDivisionSeGuardanLasVariablesPorSiSaltaAlCatch() {
        String asm = asm(
                Instruccion.asignar("x", "7"),
                Instruccion.binaria("t0", "x", "/", "y"));

        assertTrue(asm.startsWith("li $t0, 7\nsw $t0, x($fp)\nlw $t1, y($fp)\nbeq $t1, $zero, __error_div0"), asm);
    }

    /** Un traductor (por ejemplo el de return) puede cerrar el bloque antes de leer su operando. */
    @Test
    void cerrarElBloqueAntesDeLeerUnTemporalLoGuardaParaPoderCargarlo() {
        ProgramaMips programa = new ProgramaMips();
        GetReg regs = new GetReg(programa, n -> n + "($fp)", List.of(
                Instruccion.funcion("f", 24),
                Instruccion.binaria("t0", "a", "+", "1"),
                Instruccion.retorno("t0"),
                Instruccion.finFuncion()));
        regs.antesDeInstruccion(1);
        String ra = regs.leer("a");
        String r1 = regs.leer("1");
        String rt = regs.paraResultado("t0");
        programa.emit(InstruccionMips.de("addu", rt, ra, r1));
        regs.definido("t0", rt);
        regs.siguienteInstruccion();
        regs.antesDeInstruccion(2);
        regs.finDeBloque();
        regs.leer("t0");

        String asm = programa.texto().stream().map(InstruccionMips::toString).map(String::strip)
                .collect(Collectors.joining("\n"));
        assertTrue(asm.endsWith("sw " + rt + ", t0($fp)\nlw $t0, t0($fp)"), asm);
    }

    @Test
    void escribirEnElRegistroDeUnOperandoSinGuardarSeDetecta() {
        ProgramaMips programa = new ProgramaMips();
        GetReg regs = new GetReg(programa, n -> n + "($fp)", List.of(
                Instruccion.funcion("main", 24),
                Instruccion.asignar("x", "1"),
                Instruccion.binaria("t0", "x", "+", "1"),
                Instruccion.finFuncion()));
        regs.antesDeInstruccion(1);
        regs.copia("x", "1");
        regs.siguienteInstruccion();
        regs.antesDeInstruccion(2);
        String rx = regs.leer("x");

        assertThrows(IllegalStateException.class, () -> regs.definido("t0", rx));
        assertThrows(IllegalArgumentException.class, () -> regs.definido("t0", "$v0"));
    }
}
