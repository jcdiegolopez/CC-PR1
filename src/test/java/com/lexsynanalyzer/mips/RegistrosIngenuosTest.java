package com.lexsynanalyzer.mips;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrosIngenuosTest {

    /** Ubicaciones falsas mientras no exista la de la persona B. */
    private static final Map<String, String> MEMORIA = Map.of(
            "a", "24($fp)", "b", "28($fp)", "t0", "32($fp)", "x", "g_x");

    private ProgramaMips programa;
    private RegistrosIngenuos regs;

    @BeforeEach
    void preparar() {
        programa = new ProgramaMips();
        regs = new RegistrosIngenuos(programa, nombre -> {
            String direccion = MEMORIA.get(nombre);
            if (direccion == null) {
                throw new IllegalArgumentException("Sin dirección: " + nombre);
            }
            return direccion;
        });
    }

    private String texto() {
        return programa.texto().stream().map(InstruccionMips::toString).map(String::strip)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void sumaCargaAmbosOperandosYGuardaElResultado() {
        String ra = regs.leer("a");
        String rb = regs.leer("b");
        String rt = regs.paraResultado("t0");
        programa.emit(InstruccionMips.de("addu", rt, ra, rb));
        regs.definido("t0", rt);
        regs.siguienteInstruccion();

        assertEquals("""
                lw $t0, 24($fp)
                lw $t1, 28($fp)
                addu $t2, $t0, $t1
                sw $t2, 32($fp)""", texto());
    }

    @Test
    void literalesNoTocanMemoria() {
        regs.leer("7");
        regs.leer("true");
        regs.leer("null");
        regs.leer("\"hola\"");

        assertEquals("""
                li $t0, 7
                li $t1, 1
                li $t2, 0
                la $t3, str0""", texto());
        assertTrue(programa.toString().contains("str0: .asciiz \"hola\""));
    }

    @Test
    void globalesSeLeenPorEtiqueta() {
        String r = regs.leer("x");
        regs.definido("x", r);

        assertEquals("lw $t0, g_x\nsw $t0, g_x", texto());
    }

    @Test
    void siguienteInstruccionReiniciaLosRegistros() {
        String primero = regs.leer("a");
        assertNotEquals(primero, regs.leer("b"));

        regs.siguienteInstruccion();

        assertEquals(primero, regs.leer("b"));
    }

    @Test
    void finDeBloqueYAntesDeLlamadaNoEmitenNada() {
        regs.finDeBloque();
        regs.antesDeLlamada();

        assertTrue(programa.estaVacio());
    }

    @Test
    void olvidarSiguienteInstruccionSeDetecta() {
        for (int i = 0; i < 8; i++) {
            regs.leer("a");
        }

        assertThrows(IllegalStateException.class, () -> regs.leer("a"));
    }

    @Test
    void nombreSinDireccionFalla() {
        assertThrows(IllegalArgumentException.class, () -> regs.leer("desconocida"));
    }
}
