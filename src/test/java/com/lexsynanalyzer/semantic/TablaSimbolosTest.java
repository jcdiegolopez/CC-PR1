package com.lexsynanalyzer.semantic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TablaSimbolosTest {

    @Test
    void declaraYBuscaEnElEntornoGlobal() {
        TablaSimbolos tabla = new TablaSimbolos();
        Simbolo simbolo = Simbolo.variable("edad", TipoDato.INTEGER, 1, 5, false, true);

        assertTrue(tabla.declarar(simbolo));
        assertEquals(simbolo, tabla.buscar("edad").orElseThrow());
        assertEquals(simbolo, tabla.buscarLocalmente("edad").orElseThrow());
    }

    @Test
    void rechazaRedeclaracionEnElMismoEntorno() {
        TablaSimbolos tabla = new TablaSimbolos();
        assertTrue(tabla.declarar(Simbolo.variable("x", TipoDato.INTEGER, 1, 1, false, true)));
        assertFalse(tabla.declarar(Simbolo.variable("x", TipoDato.STRING, 2, 1, false, true)));
    }

    @Test
    void encuentraSimboloEnUnEntornoPadre() {
        TablaSimbolos tabla = new TablaSimbolos();
        Simbolo global = Simbolo.variable("x", TipoDato.INTEGER, 1, 1, false, true);
        tabla.declarar(global);
        tabla.entrarEntorno("bloque");

        assertEquals(global, tabla.buscar("x").orElseThrow());
        assertTrue(tabla.buscarLocalmente("x").isEmpty());
    }

    @Test
    void permiteSombreadoEnEntornoHijo() {
        TablaSimbolos tabla = new TablaSimbolos();
        Simbolo exterior = Simbolo.variable("x", TipoDato.INTEGER, 1, 1, false, true);
        Simbolo interior = Simbolo.variable("x", TipoDato.STRING, 2, 1, false, true);
        tabla.declarar(exterior);
        tabla.entrarEntorno("bloque");
        tabla.declarar(interior);

        assertEquals(TipoDato.STRING, tabla.buscar("x").orElseThrow().tipo());
        tabla.salirEntorno();
        assertEquals(TipoDato.INTEGER, tabla.buscar("x").orElseThrow().tipo());
    }

    @Test
    void actualizaElSimboloEncontradoEnUnEntornoPadre() {
        TablaSimbolos tabla = new TablaSimbolos();
        tabla.declarar(Simbolo.variable("x", TipoDato.INTEGER, 1, 1, false, false));
        tabla.entrarEntorno("bloque");
        Simbolo actualizado = Simbolo.variable("x", TipoDato.INTEGER, 1, 1, false, true);

        assertTrue(tabla.actualizar(actualizado));
        tabla.salirEntorno();
        assertTrue(tabla.buscar("x").orElseThrow().inicializado());
    }
}
