package com.lexsynanalyzer.semantic;

import com.lexsynanalyzer.analyzer.AnalysisError;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.analyzer.TipoError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas de tipos sobre declaraciones, asignaciones y operadores. */
class TiposSemanticosTest {

    @Test
    @DisplayName("Una inicialización con un tipo distinto al declarado es un error semántico")
    void rechazaInicializadorDeTipoIncorrecto() {
        List<AnalysisError> errores = semanticos("let x: integer = \"hola\";");

        assertEquals(1, errores.size(), "se esperaba exactamente un error: " + errores);
        assertEquals(1, errores.getFirst().linea());
        assertTrue(errores.getFirst().descripcion().contains("integer"), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("string"), errores.toString());
    }

    @Test
    @DisplayName("Una inicialización con el tipo declarado no genera errores")
    void aceptaInicializadorDelTipoDeclarado() {
        assertSinErrores("let x: integer = 2 + 3;");
    }

    @Test
    @DisplayName("El tipo se infiere del inicializador cuando no hay anotación")
    void infiereElTipoDelInicializador() {
        List<AnalysisError> errores = semanticos("""
                let x = 5;
                let y: string = x;
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertEquals(2, errores.getFirst().linea());
    }

    @Test
    @DisplayName("Los operadores aritméticos exigen operandos integer")
    void rechazaAritmeticaConBooleanos() {
        List<AnalysisError> errores = semanticos("let x = true + false;");

        assertFalse(errores.isEmpty());
        assertTrue(errores.getFirst().descripcion().contains("'+'"), errores.toString());
        assertEquals("+", errores.getFirst().simbolo());
    }

    @Test
    @DisplayName("La concatenación de dos cadenas es válida; mezclar cadena y entero no lo es")
    void concatenaCadenasPeroNoTiposMixtos() {
        assertSinErrores("let saludo: string = \"Hola \" + \"mundo\";");

        List<AnalysisError> errores = semanticos("let mezcla = \"Hola \" + 5;");
        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("string"), errores.toString());
    }

    @Test
    @DisplayName("Los operadores lógicos exigen operandos boolean")
    void validaOperadoresLogicos() {
        assertSinErrores("let x: boolean = true && false;");

        List<AnalysisError> errores = semanticos("let x: boolean = 1 && true;");
        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("boolean"), errores.toString());
    }

    @Test
    @DisplayName("Los operadores relacionales exigen integer y producen boolean")
    void validaOperadoresRelacionales() {
        assertSinErrores("let mayor: boolean = 3 > 2;");

        List<AnalysisError> errores = semanticos("let mayor: boolean = \"a\" > 2;");
        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("integer"), errores.toString());
    }

    @Test
    @DisplayName("La igualdad rechaza tipos incompatibles")
    void rechazaIgualdadEntreTiposIncompatibles() {
        assertSinErrores("let iguales: boolean = 1 == 2;");

        List<AnalysisError> errores = semanticos("let iguales: boolean = 1 == \"uno\";");
        assertEquals(1, errores.size(), errores.toString());
        assertEquals("==", errores.getFirst().simbolo());
    }

    @Test
    @DisplayName("Los operadores unarios validan el tipo de su operando")
    void validaOperadoresUnarios() {
        assertSinErrores("""
                let negado: boolean = !true;
                let opuesto: integer = -4;
                """);

        List<AnalysisError> errores = semanticos("let negado: boolean = !5;");
        assertEquals(1, errores.size(), errores.toString());
        assertEquals("!", errores.getFirst().simbolo());
    }

    @Test
    @DisplayName("El ternario exige condición boolean y ramas compatibles")
    void validaOperadorTernario() {
        assertSinErrores("let x: integer = true ? 1 : 2;");

        List<AnalysisError> condicion = semanticos("let x: integer = 5 ? 1 : 2;");
        assertEquals(1, condicion.size(), condicion.toString());
        assertTrue(condicion.getFirst().descripcion().contains("boolean"), condicion.toString());

        List<AnalysisError> ramas = semanticos("let x = true ? 1 : \"dos\";");
        assertEquals(1, ramas.size(), ramas.toString());
        assertTrue(ramas.getFirst().descripcion().contains("ternario"), ramas.toString());
    }

    @Test
    @DisplayName("Usar una variable no declarada es un error semántico")
    void rechazaVariableNoDeclarada() {
        List<AnalysisError> errores = semanticos("let x: integer = y + 1;");

        assertEquals(1, errores.size(), errores.toString());
        assertEquals("y", errores.getFirst().simbolo());
        assertTrue(errores.getFirst().descripcion().contains("no está declarado"), errores.toString());
    }

    @Test
    @DisplayName("Una constante no puede recibir un nuevo valor")
    void rechazaAsignacionAConstante() {
        List<AnalysisError> errores = semanticos("""
                const LIMITE: integer = 10;
                LIMITE = 20;
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertEquals(2, errores.getFirst().linea());
        assertEquals("LIMITE", errores.getFirst().simbolo());
    }

    @Test
    @DisplayName("Una asignación debe respetar el tipo de la variable destino")
    void rechazaAsignacionDeTipoIncorrecto() {
        List<AnalysisError> errores = semanticos("""
                let x: integer = 1;
                x = "hola";
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertEquals(2, errores.getFirst().linea());
    }

    @Test
    @DisplayName("Una variable declarada sin valor no puede leerse antes de asignarla")
    void rechazaLecturaDeVariableSinInicializar() {
        List<AnalysisError> errores = semanticos("""
                let nombre: string;
                print(nombre);
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("antes de asignarle"), errores.toString());

        assertSinErrores("""
                let nombre: string;
                nombre = "Compiscript";
                print(nombre);
                """);
    }

    @Test
    @DisplayName("Una expresión ya inválida no vuelve a reportarse en cascada")
    void noEncadenaErroresDerivados() {
        List<AnalysisError> errores = semanticos("let x: integer = (true + false) * 2;");

        assertEquals(1, errores.size(), "el error de '+' no debe repetirse en '*' ni en la asignación: " + errores);
    }

    @Test
    @DisplayName("Los errores semánticos se reportan como SEMANTICO con línea y columna")
    void reportaTipoLineaYColumna() {
        List<AnalysisError> errores = semanticos("""
                let a: integer = 1;
                let b: boolean = a;
                """);

        AnalysisError error = errores.getFirst();
        assertEquals(TipoError.SEMANTICO, error.tipo());
        assertEquals(2, error.linea());
        assertEquals(18, error.columna());
    }

    static List<AnalysisError> semanticos(String codigoFuente) {
        return LexSynAnalyzer.analizarTexto(codigoFuente).errores().stream()
                .filter(error -> error.tipo() == TipoError.SEMANTICO)
                .toList();
    }

    static void assertSinErrores(String codigoFuente) {
        List<AnalysisError> errores = LexSynAnalyzer.analizarTexto(codigoFuente).errores();
        assertTrue(errores.isEmpty(), "no se esperaban errores, pero se obtuvieron: " + errores);
    }
}
