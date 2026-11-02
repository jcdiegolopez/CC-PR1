package com.lexsynanalyzer.mips;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstruccionMipsTest {

    @Test
    void instruccionSeImprimeSangradaConOperandosSeparadosPorComa() {
        assertEquals("    addu $t0, $t1, $t2", InstruccionMips.de("addu", "$t0", "$t1", "$t2").toString());
        assertEquals("    syscall", InstruccionMips.de("syscall").toString());
    }

    @Test
    void etiquetaVaEnLaColumnaCero() {
        assertEquals("L3:", InstruccionMips.etiqueta("L3").toString());
    }

    @Test
    void comentariosAlFinalYSueltos() {
        assertEquals("    lw $t0, 28($fp)  # derrame t1",
                InstruccionMips.de("lw", "$t0", "28($fp)").conComentario("derrame t1").toString());
        assertEquals("    # t0 = a + b", InstruccionMips.comentarioSuelto("t0 = a + b").toString());
    }

    @Test
    void destinoSaltoReconoceSaltosAEtiquetas() {
        assertEquals("L1", InstruccionMips.de("j", "L1").destinoSalto());
        assertEquals("L2", InstruccionMips.de("beqz", "$t0", "L2").destinoSalto());
        assertEquals("L4", InstruccionMips.de("bne", "$t0", "$t1", "L4").destinoSalto());
        assertNull(InstruccionMips.de("jr", "$ra").destinoSalto());
        assertNull(InstruccionMips.de("addu", "$t0", "$t1", "$t2").destinoSalto());
        assertNull(InstruccionMips.etiqueta("j").destinoSalto());
    }

    @Test
    void clasificaSaltos() {
        assertTrue(InstruccionMips.de("jr", "$ra").esSaltoIncondicional());
        assertTrue(InstruccionMips.de("bnez", "$t0", "L0").esSaltoCondicional());
        assertFalse(InstruccionMips.de("jal", "f_suma").esSaltoIncondicional());
        assertFalse(InstruccionMips.etiqueta("j").esSaltoIncondicional());
    }

    @Test
    void validaCamposObligatorios() {
        assertThrows(NullPointerException.class, () -> InstruccionMips.comentarioSuelto(null));
        assertThrows(NullPointerException.class, () -> InstruccionMips.etiqueta(null));
    }
}
