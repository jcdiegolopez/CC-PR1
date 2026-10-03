package com.lexsynanalyzer.tac;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GeneradorEtiquetasTest {

    @Test
    void generaEtiquetasConsecutivasDesdeL0() {
        GeneradorEtiquetas etiquetas = new GeneradorEtiquetas();

        assertEquals("L0", etiquetas.nueva());
        assertEquals("L1", etiquetas.nueva());
        assertEquals("L2", etiquetas.nueva());
    }

    @Test
    void etiquetasDeGeneradoresDistintosSeReinician() {
        assertNotEquals(null, new GeneradorEtiquetas().nueva());
        assertEquals("L0", new GeneradorEtiquetas().nueva());
    }
}
