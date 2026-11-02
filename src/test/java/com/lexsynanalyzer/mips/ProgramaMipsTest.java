package com.lexsynanalyzer.mips;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramaMipsTest {

    @Test
    void programaNuevoEstaVacioYNoImprimeNada() {
        ProgramaMips programa = ProgramaMips.vacio();

        assertTrue(programa.estaVacio());
        assertEquals("", programa.toString());
    }

    @Test
    void sinDatosSoloImprimeLaSeccionText() {
        ProgramaMips programa = new ProgramaMips();
        programa.emit(InstruccionMips.etiqueta("main"));
        programa.emit(InstruccionMips.de("li", "$v0", "10"));
        programa.emit(InstruccionMips.de("syscall"));

        assertEquals("""
                .text
                .globl main
                main:
                    li $v0, 10
                    syscall
                """, programa.toString());
    }

    @Test
    void cadenasSeDeduplicanYVanAntesQueLosDemasDatos() {
        ProgramaMips programa = new ProgramaMips();
        programa.dato("g_x", ".word 0");
        String hola = programa.cadena("hola ");
        String otra = programa.cadena("adios");
        String repetida = programa.cadena("hola ");
        programa.emit(InstruccionMips.etiqueta("main"));

        assertEquals("str0", hola);
        assertEquals("str1", otra);
        assertEquals(hola, repetida);
        assertEquals("""
                .data
                str0: .asciiz "hola "
                str1: .asciiz "adios"
                g_x: .word 0

                .text
                .globl main
                main:
                """, programa.toString());
    }

    @Test
    void escapaBarraInvertidaYComillasParaMars() {
        assertEquals("c:\\\\dir", ProgramaMips.escapar("c:\\dir"));
        assertEquals("di \\\"hola\\\"", ProgramaMips.escapar("di \"hola\""));
    }

    @Test
    void datoRepetidoIgualSeIgnoraYDistintoFalla() {
        ProgramaMips programa = new ProgramaMips();
        programa.dato("g_x", ".word 0");
        programa.dato("g_x", ".word 0");

        assertThrows(IllegalArgumentException.class, () -> programa.dato("g_x", ".space 8"));
    }

    @Test
    void reemplazarTextoYContarInstrucciones() {
        ProgramaMips programa = new ProgramaMips();
        programa.emit(InstruccionMips.etiqueta("main"));
        programa.emit(InstruccionMips.de("move", "$t0", "$t0"));
        programa.emit(InstruccionMips.comentarioSuelto("fin"));
        assertEquals(1, programa.cantidadInstrucciones());

        programa.reemplazarTexto(List.of(InstruccionMips.etiqueta("main")));

        assertEquals(0, programa.cantidadInstrucciones());
        assertEquals(1, programa.texto().size());
        assertThrows(UnsupportedOperationException.class,
                () -> programa.texto().add(InstruccionMips.de("nop")));
    }
}
