package com.lexsynanalyzer.tac;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Cada instrucción debe imprimirse exactamente como la tabla del §3.1 del plan. */
class InstruccionTest {

    @Test
    void asignacionYAritmetica() {
        assertEquals("a = 5", Instruccion.asignar("a", "5").toString());
        assertEquals("t0 = a + b", Instruccion.binaria("t0", "a", "+", "b").toString());
        assertEquals("t0 = a <= b", Instruccion.binaria("t0", "a", "<=", "b").toString());
        assertEquals("t1 = !t0", Instruccion.no("t1", "t0").toString());
        assertEquals("t1 = -a", Instruccion.negar("t1", "a").toString());
    }

    @Test
    void operadorBinarioDesconocidoSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> Instruccion.binaria("t0", "a", "**", "b"));
    }

    @Test
    void etiquetasYSaltos() {
        assertEquals("L1:", Instruccion.etiqueta("L1").toString());
        assertEquals("goto L2", Instruccion.salto("L2").toString());
        assertEquals("if t0 goto L3", Instruccion.saltoSi("t0", "L3").toString());
        assertEquals("ifFalse t0 goto L1", Instruccion.saltoSiFalso("t0", "L1").toString());
    }

    @Test
    void funcionesYLlamadas() {
        assertEquals("function suma, 16", Instruccion.funcion("suma", 16).toString());
        assertEquals("endfunc", Instruccion.finFuncion().toString());
        assertEquals("param x", Instruccion.param("x").toString());
        assertEquals("t2 = call suma, 2", Instruccion.llamar("t2", "suma", 2).toString());
        assertEquals("call suma, 2", Instruccion.llamar("suma", 2).toString());
        assertEquals("return t3", Instruccion.retorno("t3").toString());
        assertEquals("return", Instruccion.retorno().toString());
    }

    @Test
    void arreglos() {
        assertEquals("t0 = newarray 3", Instruccion.nuevoArreglo("t0", "3").toString());
        assertEquals("t0 = a[i]", Instruccion.leerIndice("t0", "a", "i").toString());
        assertEquals("a[i] = b", Instruccion.escribirIndice("a", "i", "b").toString());
        assertEquals("t0 = len a", Instruccion.longitud("t0", "a").toString());
    }

    @Test
    void objetos() {
        assertEquals("t0 = new Perro, 24", Instruccion.nuevoObjeto("t0", "Perro", 24).toString());
        assertEquals("t0 = obj.campo", Instruccion.leerCampo("t0", "obj", "campo").toString());
        assertEquals("obj.campo = b", Instruccion.escribirCampo("obj", "campo", "b").toString());
    }

    @Test
    void excepcionesYSalida() {
        assertEquals("try L0", Instruccion.intentar("L0").toString());
        assertEquals("endtry", Instruccion.finIntentar().toString());
        assertEquals("catch err", Instruccion.capturar("err").toString());
        assertEquals("print t0", Instruccion.imprimir("t0").toString());
    }
}
