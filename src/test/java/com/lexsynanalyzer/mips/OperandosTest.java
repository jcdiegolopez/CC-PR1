package com.lexsynanalyzer.mips;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperandosTest {

    @Test
    void enterosBooleanosYNullSonInmediatos() {
        assertEquals("42", Operandos.valorInmediato("42"));
        assertEquals("-3", Operandos.valorInmediato("-3"));
        assertEquals("1", Operandos.valorInmediato("true"));
        assertEquals("0", Operandos.valorInmediato("false"));
        assertEquals("0", Operandos.valorInmediato("null"));
    }

    @Test
    void cadenasSeReconocenPorLasComillas() {
        assertTrue(Operandos.esCadena("\"hola \""));
        assertTrue(Operandos.esCadena("\"\""));
        assertEquals("hola ", Operandos.contenidoCadena("\"hola \""));
        assertEquals("", Operandos.contenidoCadena("\"\""));
        assertFalse(Operandos.esCadena("\""));
    }

    @Test
    void variablesYTemporalesNoSonLiterales() {
        for (String nombre : new String[]{"a", "t0", "t0_v", "x_1", "this", "trueValor"}) {
            assertFalse(Operandos.esLiteral(nombre), nombre);
        }
    }

    @Test
    void valorInmediatoRechazaNombres() {
        assertThrows(IllegalArgumentException.class, () -> Operandos.valorInmediato("a"));
        assertThrows(IllegalArgumentException.class, () -> Operandos.contenidoCadena("a"));
    }
}
