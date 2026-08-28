package com.lexsynanalyzer.semantic;

import com.lexsynanalyzer.analyzer.AnalysisError;
import com.lexsynanalyzer.analyzer.TipoError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lexsynanalyzer.semantic.TiposSemanticosTest.assertSinErrores;
import static com.lexsynanalyzer.semantic.TiposSemanticosTest.semanticos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas semánticas de literales de arreglos, multidimensionales, índices y foreach. */
class ArreglosSemanticosTest {

    @Test
    @DisplayName("Declaración e inicialización de arreglos homogéneos no genera errores")
    void arregloHomogeneoValido() {
        assertSinErrores("""
                let numeros: integer[] = [1, 2, 3, 4, 5];
                let palabras: string[] = ["uno", "dos", "tres"];
                let flags: boolean[] = [true, false, true];
                """);
    }

    @Test
    @DisplayName("Arreglos con elementos heterogéneos producen error semántico")
    void rechazaArregloHeterogeneo() {
        List<AnalysisError> errores = semanticos("let lista = [1, \"dos\", 3];");

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("mismo tipo"), errores.toString());
    }

    @Test
    @DisplayName("Arreglos anidados / multidimensionales homogéneos son válidos")
    void validaArreglosMultidimensionales() {
        assertSinErrores("""
                let matriz: integer[][] = [[1, 2], [3, 4]];
                let elem: integer = matriz[0][1];
                """);
    }

    @Test
    @DisplayName("Arreglos anidados con dimensiones incompatibles producen error")
    void rechazaMatrizIncompatible() {
        List<AnalysisError> errores = semanticos("let matriz = [[1, 2], [\"a\", \"b\"]];");

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("mismo tipo"), errores.toString());
    }

    @Test
    @DisplayName("El acceso por índice devuelve el tipo elemento y requiere índice entero")
    void validaIndexacion() {
        assertSinErrores("""
                let arr: integer[] = [10, 20, 30];
                let primer: integer = arr[0];
                """);

        List<AnalysisError> errIndice = semanticos("""
                let arr: integer[] = [10, 20];
                let x = arr["cero"];
                """);
        assertEquals(1, errIndice.size(), errIndice.toString());
        assertTrue(errIndice.getFirst().descripcion().contains("debe ser de tipo 'integer'"), errIndice.toString());
    }

    @Test
    @DisplayName("Indexar una variable que no es arreglo ni cadena produce error")
    void rechazaIndexarNoArreglo() {
        List<AnalysisError> errores = semanticos("""
                let num: integer = 5;
                let x = num[0];
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("Solo se pueden indexar arreglos"), errores.toString());
    }

    @Test
    @DisplayName("La sentencia 'foreach' itera sobre arreglos y expone el elemento tipado")
    void foreachSobreArreglo() {
        assertSinErrores("""
                let numeros: integer[] = [1, 2, 3];
                foreach (n in numeros) {
                    let doble: integer = n * 2;
                    print(doble);
                }
                """);
    }

    @Test
    @DisplayName("La sentencia 'foreach' itera sobre cadenas")
    void foreachSobreCadena() {
        assertSinErrores("""
                let texto: string = "Compiscript";
                foreach (caracter in texto) {
                    print(caracter);
                }
                """);
    }

    @Test
    @DisplayName("La variable declarada en 'foreach' no existe fuera de su bloque")
    void variableDeForeachAisladaEnBloque() {
        List<AnalysisError> errores = semanticos("""
                let nums: integer[] = [1, 2];
                foreach (elem in nums) {
                    print(elem);
                }
                print(elem);
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertEquals("elem", errores.getFirst().simbolo());
        assertTrue(errores.getFirst().descripcion().contains("no está declarado"), errores.toString());
    }

    @Test
    @DisplayName("Usar 'foreach' sobre un tipo no iterable es un error semántico")
    void rechazaForeachSobreTipoNoIterable() {
        List<AnalysisError> errores = semanticos("""
                let x: integer = 100;
                foreach (item in x) {
                    print(item);
                }
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("requiere un arreglo o una cadena"), errores.toString());
    }
}
