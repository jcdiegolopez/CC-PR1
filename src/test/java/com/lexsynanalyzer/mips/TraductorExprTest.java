package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;
import com.lexsynanalyzer.tac.ProgramaTAC;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Forma del MIPS de cada instrucción, en modo ingenuo (todo se carga y se guarda). */
class TraductorExprTest {

    private static ProgramaMips generar(Instruccion instruccion) {
        ProgramaTAC tac = new ProgramaTAC();
        tac.emit(Instruccion.funcion("main", 24));
        tac.emit(instruccion);
        tac.emit(Instruccion.finFuncion());
        return new GeneradorMIPS(f -> nombre -> nombre + "($fp)", FabricaRegistros.INGENUO).generar(tac);
    }

    /** Solo las instrucciones de la línea TAC (sin main ni el syscall de salida ni el runtime). */
    private static String mips(Instruccion instruccion) {
        return generar(instruccion).texto().stream()
                .dropWhile(i -> i.tipo() != InstruccionMips.Tipo.COMENTARIO)
                .skip(1)
                .takeWhile(i -> i.tipo() != InstruccionMips.Tipo.COMENTARIO && !i.esEtiqueta())
                .map(InstruccionMips::toString).map(String::strip).collect(Collectors.joining("\n"));
    }

    @Test
    void sumaYComparacion() {
        assertEquals("lw $t0, a($fp)\nli $t1, 3\naddu $t2, $t0, $t1\nsw $t2, t0($fp)",
                mips(Instruccion.binaria("t0", "a", "+", "3")));
        assertEquals("lw $t0, a($fp)\nlw $t1, b($fp)\nsle $t2, $t0, $t1\nsw $t2, t0($fp)",
                mips(Instruccion.binaria("t0", "a", "<=", "b")));
    }

    @Test
    void divisionYModuloChequeanElDivisor() {
        assertEquals("lw $t0, a($fp)\nlw $t1, b($fp)\nbeq $t1, $zero, __error_div0\ndiv $t0, $t1\n"
                + "mflo $t2\nsw $t2, t0($fp)", mips(Instruccion.binaria("t0", "a", "/", "b")));
        assertTrue(mips(Instruccion.binaria("t0", "a", "%", "b")).contains("mfhi $t2"));
        String asm = generar(Instruccion.binaria("t0", "a", "/", "b")).toString();
        assertTrue(asm.contains("__error_div0:\n    la $a0, str"), asm);
        assertTrue(asm.contains("__lanzar:"), asm);
        assertTrue(asm.contains(".asciiz \"División entre cero\""), asm);
    }

    @Test
    void unarias() {
        assertEquals("lw $t0, a($fp)\nsubu $t1, $zero, $t0\nsw $t1, t0($fp)", mips(Instruccion.negar("t0", "a")));
        assertEquals("lw $t0, a($fp)\nxori $t1, $t0, 1\nsw $t1, t0($fp)", mips(Instruccion.no("t0", "a")));
    }

    @Test
    void asignacionDeLiteralesYVariables() {
        assertEquals("li $t0, 1\nsw $t0, x($fp)", mips(Instruccion.asignar("x", "true")));
        assertEquals("lw $t0, y($fp)\nsw $t0, x($fp)", mips(Instruccion.asignar("x", "y")));
        assertEquals("la $t0, str0\nsw $t0, x($fp)", mips(Instruccion.asignar("x", "\"hola\"")));
    }

    @Test
    void saltos() {
        assertEquals("j L3", mips(Instruccion.salto("L3")));
        assertEquals("lw $t0, t0($fp)\nbnez $t0, L1", mips(Instruccion.saltoSi("t0", "L1")));
        assertEquals("lw $t0, t0($fp)\nbeqz $t0, L1", mips(Instruccion.saltoSiFalso("t0", "L1")));
    }

    @Test
    void printsPorTipoTerminanConSaltoDeLinea() {
        String salto = "\nli $a0, 10\nli $v0, 11\nsyscall";
        assertEquals("lw $t0, x($fp)\nmove $a0, $t0\nli $v0, 1\nsyscall" + salto, mips(Instruccion.imprimir("x")));
        assertEquals("la $t0, str0\nmove $a0, $t0\nli $v0, 4\nsyscall" + salto,
                mips(Instruccion.imprimirString("\"hola\"")));
        assertEquals("lw $t0, b($fp)\nmove $a0, $t0\njal __print_bool" + salto,
                mips(Instruccion.imprimirBooleano("b")));
    }

    @Test
    void operacionesDeStringsLlamanAlRuntime() {
        assertEquals("lw $t0, a($fp)\nla $t1, str0\nmove $a0, $t0\nmove $a1, $t1\njal __concat\n"
                + "move $t2, $v0\nsw $t2, t0($fp)", mips(Instruccion.concat("t0", "a", "\"!\"")));
        assertTrue(mips(Instruccion.strne("t0", "a", "b")).contains("jal __streq\nxori $t2, $v0, 1"));
        String asm = generar(Instruccion.concat("t0", "a", "b")).toString();
        assertTrue(asm.contains("__concat:"), asm);
        assertTrue(!asm.contains("__streq:"), "solo se emiten las rutinas usadas:\n" + asm);
    }
}
