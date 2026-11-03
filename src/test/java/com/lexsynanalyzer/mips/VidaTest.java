package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VidaTest {

    @Test
    void unTemporalMuereDespuesDeSuUltimoUso() {
        Vida vida = Vida.analizar(List.of(
                Instruccion.funcion("main", 32),
                Instruccion.binaria("t0", "a", "+", "b"),   // 1
                Instruccion.binaria("t1", "t0", "*", "2"),  // 2
                Instruccion.asignar("x", "t1"),             // 3
                Instruccion.finFuncion()));

        assertTrue(vida.vivaDespues(1, "t0"));
        assertFalse(vida.vivaDespues(2, "t0"));
        assertTrue(vida.vivaAntes(2, "t0"));
        assertEquals(2, vida.proximoUso(1, "t0"));
        assertEquals(Vida.SIN_USO, vida.proximoUso(2, "t0"));
    }

    /** El patrón de {@code a && b}: el temporal sigue vivo al cruzar la etiqueta. */
    @Test
    void unTemporalPuedeEstarVivoEntreBloques() {
        Vida vida = Vida.analizar(List.of(
                Instruccion.funcion("main", 28),
                Instruccion.asignar("t0", "a"),             // 1
                Instruccion.saltoSiFalso("t0", "L1"),       // 2
                Instruccion.asignar("t0", "b"),             // 3
                Instruccion.etiqueta("L1"),                 // 4
                Instruccion.asignar("z", "t0"),             // 5
                Instruccion.finFuncion()));

        assertTrue(vida.vivaDespues(2, "t0"), "por el salto, L1 lee t0");
        assertTrue(vida.vivaDespues(3, "t0"));
        assertFalse(vida.vivaDespues(5, "t0"));
    }

    @Test
    void enUnCicloLaVariableDeControlSigueViva() {
        Vida vida = Vida.analizar(List.of(
                Instruccion.funcion("main", 28),
                Instruccion.etiqueta("L0"),                 // 1
                Instruccion.binaria("t0", "i", "<", "3"),   // 2
                Instruccion.saltoSiFalso("t0", "L1"),       // 3
                Instruccion.binaria("t0", "i", "+", "1"),   // 4
                Instruccion.asignar("i", "t0"),             // 5
                Instruccion.salto("L0"),                    // 6
                Instruccion.etiqueta("L1"),                 // 7
                Instruccion.finFuncion()));

        assertTrue(vida.vivaDespues(5, "i"), "la siguiente vuelta lee i");
        assertFalse(vida.vivaDespues(3, "t0"));
        assertFalse(vida.vivaDespues(5, "t0"));
    }

    @Test
    void losArgumentosSiguenVivosHastaElCall() {
        Vida vida = Vida.analizar(List.of(
                Instruccion.funcion("main", 28),
                Instruccion.binaria("t0", "a", "+", "1"),   // 1
                Instruccion.param("t0"),                    // 2
                Instruccion.llamar("t0", "f", 1),           // 3
                Instruccion.imprimir("t0"),                 // 4
                Instruccion.finFuncion()));

        assertTrue(vida.vivaDespues(2, "t0"), "el call carga los argumentos de memoria");
        assertTrue(vida.usaEn(3, "t0"));
        assertTrue(vida.vivaDespues(3, "t0"), "el resultado del call se imprime");
    }

    @Test
    void literalesYNombresDeCamposNoSonVariables() {
        Vida vida = Vida.analizar(List.of(
                Instruccion.funcion("main", 28),
                Instruccion.leerCampo("t0", "p", "edad"),
                Instruccion.imprimir("t0"),
                Instruccion.imprimir("\"hola\""),
                Instruccion.finFuncion()));

        assertTrue(vida.usaEn(1, "p"));
        assertFalse(vida.usaEn(1, "edad"));
        assertFalse(vida.usaEn(3, "\"hola\""));
    }
}
