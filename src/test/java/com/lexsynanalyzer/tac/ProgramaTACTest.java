package com.lexsynanalyzer.tac;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramaTACTest {

    @Test
    void programaNuevoEstaVacio() {
        ProgramaTAC programa = ProgramaTAC.vacio();

        assertTrue(programa.estaVacio());
        assertEquals("", programa.toString());
    }

    @Test
    void emitConservaElOrdenYImprimeUnaInstruccionPorLinea() {
        ProgramaTAC programa = new ProgramaTAC();
        programa.emit(Instruccion.binaria("t0", "a", "+", "b"));
        programa.emit(Instruccion.asignar("r", "t0"));

        assertEquals("t0 = a + b\nr = t0", programa.toString());
        assertEquals(2, programa.tamano());
    }

    @Test
    void reservarYFijarCompletaElFrameAlFinalDeLaFuncion() {
        ProgramaTAC programa = new ProgramaTAC();
        int cabecera = programa.reservar();
        programa.emit(Instruccion.retorno("1"));
        programa.emit(Instruccion.finFuncion());

        programa.fijar(cabecera, Instruccion.funcion("f", 32));

        assertEquals("function f, 32\nreturn 1\nendfunc", programa.toString());
    }

    @Test
    void reservaSinCompletarFallaAlLeerElPrograma() {
        ProgramaTAC programa = new ProgramaTAC();
        programa.reservar();

        assertThrows(IllegalStateException.class, programa::toString);
        assertThrows(IllegalStateException.class, programa::instrucciones);
    }

    @Test
    void fijarSoloAceptaReservasPendientes() {
        ProgramaTAC programa = new ProgramaTAC();
        int emitida = programa.emit(Instruccion.retorno());
        int reserva = programa.reservar();
        programa.fijar(reserva, Instruccion.finFuncion());

        assertThrows(IllegalArgumentException.class, () -> programa.fijar(emitida, Instruccion.finFuncion()));
        assertThrows(IllegalArgumentException.class, () -> programa.fijar(reserva, Instruccion.finFuncion()));
        assertThrows(IllegalArgumentException.class, () -> programa.fijar(99, Instruccion.finFuncion()));
    }

    @Test
    void instruccionesDevuelveVistaNoModificable() {
        ProgramaTAC programa = new ProgramaTAC();
        programa.emit(Instruccion.retorno());

        assertThrows(UnsupportedOperationException.class,
                () -> programa.instrucciones().add(Instruccion.retorno()));
    }
}
