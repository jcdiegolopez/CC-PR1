package com.lexsynanalyzer.tac;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempPoolTest {

    /** Simula {@code (a + b) * (c - d)} como lo hará el generador: liberar operandos y luego pedir. */
    @Test
    void reciclaje_deLaExpresionDelEnunciado_usaDosTemporales() {
        TempPool pool = new TempPool();

        String t0 = pool.nuevo();                  // t0 = a + b
        String t1 = pool.nuevo();                  // t1 = c - d
        pool.liberar(t0);
        pool.liberar(t1);
        String resultado = pool.nuevo();           // t0 = t0 * t1

        assertEquals("t0", t0);
        assertEquals("t1", t1);
        assertEquals("t0", resultado);
        assertEquals(2, pool.totalCreados());
        assertEquals(2, pool.maximoSimultaneos());
    }

    @Test
    void reutilizaElTemporalLibreDeMenorIndice() {
        TempPool pool = new TempPool();
        String t0 = pool.nuevo();
        String t1 = pool.nuevo();
        String t2 = pool.nuevo();

        pool.liberar(t2);
        pool.liberar(t0);

        assertEquals("t0", pool.nuevo());
        assertEquals("t2", pool.nuevo());
        assertEquals(3, pool.totalCreados());
        assertTrue(TempPool.esTemporal(t1));
    }

    @Test
    void liberarIgnoraLiteralesVariablesYNulos() {
        TempPool pool = new TempPool();
        pool.nuevo();

        pool.liberar("5");
        pool.liberar("\"t0\"");
        pool.liberar("x");
        pool.liberar("tmp1");
        pool.liberar(null);

        assertEquals("t1", pool.nuevo());
    }

    @Test
    void liberarDosVecesNoEntregaElMismoTemporalDosVeces() {
        TempPool pool = new TempPool();
        String t0 = pool.nuevo();

        pool.liberar(t0);
        pool.liberar(t0);

        assertEquals("t0", pool.nuevo());
        assertEquals("t1", pool.nuevo());
    }

    @Test
    void liberarUnTemporalQueNuncaSeCreoNoHaceNada() {
        TempPool pool = new TempPool();

        pool.liberar("t7");

        assertEquals("t0", pool.nuevo());
    }

    @Test
    void reiniciarEmpiezaDeCeroParaCadaFuncion() {
        TempPool pool = new TempPool();
        pool.nuevo();
        pool.nuevo();

        pool.reiniciar();

        assertEquals("t0", pool.nuevo());
        assertEquals(1, pool.totalCreados());
        assertEquals(1, pool.maximoSimultaneos());
    }

    @Test
    void esTemporalReconoceSoloElFormatoTn() {
        assertTrue(TempPool.esTemporal("t0"));
        assertTrue(TempPool.esTemporal("t12"));
        assertFalse(TempPool.esTemporal("t"));
        assertFalse(TempPool.esTemporal("t1a"));
        assertFalse(TempPool.esTemporal("T1"));
        assertFalse(TempPool.esTemporal(null));
    }
}
