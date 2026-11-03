package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BloquesBasicosTest {

    /**
     * <pre>
     * 0 function main, 28     ┐ B0
     * 1 i = 0                 ┘
     * 2 L0:                   ┐ B1
     * 3 t0 = i &lt; 3            │
     * 4 ifFalse t0 goto L1    ┘
     * 5 i = i + 1             ┐ B2
     * 6 goto L0               ┘
     * 7 L1:                   ┐ B3
     * 8 print i               │
     * 9 endfunc               ┘
     * </pre>
     */
    private static final List<Instruccion> CICLO = List.of(
            Instruccion.funcion("main", 28),
            Instruccion.asignar("i", "0"),
            Instruccion.etiqueta("L0"),
            Instruccion.binaria("t0", "i", "<", "3"),
            Instruccion.saltoSiFalso("t0", "L1"),
            Instruccion.binaria("i", "i", "+", "1"),
            Instruccion.salto("L0"),
            Instruccion.etiqueta("L1"),
            Instruccion.imprimir("i"),
            Instruccion.finFuncion());

    @Test
    void lideresSonInicioEtiquetasYLoQueSigueAUnSalto() {
        BloquesBasicos bloques = BloquesBasicos.de(CICLO);

        assertEquals(List.of(
                        new BloquesBasicos.Bloque(0, 0, 2, List.of(1)),
                        new BloquesBasicos.Bloque(1, 2, 5, List.of(3, 2)),
                        new BloquesBasicos.Bloque(2, 5, 7, List.of(1)),
                        new BloquesBasicos.Bloque(3, 7, 10, List.of())),
                bloques.bloques());
        assertEquals(1, bloques.bloqueDe(4).numero());
    }

    @Test
    void callYReturnTerminanBloque() {
        BloquesBasicos bloques = BloquesBasicos.de(List.of(
                Instruccion.funcion("f", 24),
                Instruccion.llamar("t0", "g", 0),
                Instruccion.retorno("t0"),
                Instruccion.finFuncion()));

        assertEquals(3, bloques.bloques().size());
        assertEquals(List.of(), bloques.bloques().get(1).sucesores(), "return sale de la función");
    }

    @Test
    void saltoAEtiquetaInexistenteEsUnError() {
        assertThrows(IllegalArgumentException.class, () -> BloquesBasicos.de(List.of(
                Instruccion.funcion("main", 24), Instruccion.salto("L9"), Instruccion.finFuncion())));
    }
}
