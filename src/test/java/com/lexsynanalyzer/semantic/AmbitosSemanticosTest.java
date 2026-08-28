package com.lexsynanalyzer.semantic;

import com.lexsynanalyzer.analyzer.AnalysisError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lexsynanalyzer.semantic.TiposSemanticosTest.assertSinErrores;
import static com.lexsynanalyzer.semantic.TiposSemanticosTest.semanticos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas de ámbito: visibilidad, sombreado y redeclaración. */
class AmbitosSemanticosTest {

    @Test
    @DisplayName("Un bloque puede sombrear una variable del ámbito exterior")
    void permiteSombreadoEnUnBloque() {
        assertSinErrores("""
                let x: integer = 1;
                {
                    let x: string = "interior";
                    print(x);
                }
                print(x);
                """);
    }

    @Test
    @DisplayName("Redeclarar un nombre en el mismo ámbito es un error semántico")
    void rechazaRedeclaracionEnElMismoAmbito() {
        List<AnalysisError> errores = semanticos("""
                let x: integer = 1;
                let x: string = "otra";
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertEquals(2, errores.getFirst().linea());
        assertTrue(errores.getFirst().descripcion().contains("ya está declarada"), errores.toString());
    }

    @Test
    @DisplayName("Una variable declarada dentro de un bloque no es visible fuera de él")
    void ocultaLasVariablesLocalesFueraDelBloque() {
        List<AnalysisError> errores = semanticos("""
                {
                    let interna: integer = 1;
                }
                print(interna);
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertEquals("interna", errores.getFirst().simbolo());
    }

    @Test
    @DisplayName("Un bloque anidado ve las variables de sus ámbitos ancestros")
    void resuelveNombresHaciaLosAmbitosPadres() {
        assertSinErrores("""
                let global: integer = 1;
                {
                    {
                        let suma: integer = global + 1;
                        print(suma);
                    }
                }
                """);
    }

    @Test
    @DisplayName("Cada for abre su propio ámbito para la variable de control")
    void aislaLaVariableDeControlDeCadaFor() {
        assertSinErrores("""
                for (let i: integer = 0; i < 3; i = i + 1) {
                    print(i);
                }
                for (let i: integer = 0; i < 5; i = i + 1) {
                    print(i);
                }
                """);

        List<AnalysisError> errores = semanticos("""
                for (let i: integer = 0; i < 3; i = i + 1) {
                    print(i);
                }
                print(i);
                """);
        assertEquals(1, errores.size(), errores.toString());
        assertEquals("i", errores.getFirst().simbolo());
    }

    @Test
    @DisplayName("El foreach expone su variable solo dentro del bloque y con el tipo del elemento")
    void exponeLaVariableDelForeachSoloEnSuBloque() {
        assertSinErrores("""
                let numeros: integer[] = [10, 20, 30];
                foreach (n in numeros) {
                    let doble: integer = n * 2;
                    print(doble);
                }
                """);

        List<AnalysisError> fuera = semanticos("""
                let numeros: integer[] = [10, 20, 30];
                foreach (n in numeros) {
                    print(n);
                }
                print(n);
                """);
        assertEquals(1, fuera.size(), fuera.toString());
        assertEquals("n", fuera.getFirst().simbolo());

        List<AnalysisError> tipoElemento = semanticos("""
                let numeros: integer[] = [10, 20, 30];
                foreach (n in numeros) {
                    let texto: string = n;
                }
                """);
        assertEquals(1, tipoElemento.size(), tipoElemento.toString());
        assertTrue(tipoElemento.getFirst().descripcion().contains("integer"), tipoElemento.toString());
    }

    @Test
    @DisplayName("El identificador del catch existe solo dentro de su bloque")
    void exponeElIdentificadorDelCatchSoloEnSuBloque() {
        assertSinErrores("""
                try {
                    print("intento");
                } catch (err) {
                    print(err);
                }
                """);

        List<AnalysisError> errores = semanticos("""
                try {
                    print("intento");
                } catch (err) {
                    print("fallo");
                }
                print(err);
                """);
        assertEquals(1, errores.size(), errores.toString());
        assertEquals("err", errores.getFirst().simbolo());
    }

    @Test
    @DisplayName("El sombreado no altera el tipo de la variable exterior al salir del bloque")
    void restauraElTipoExteriorAlSalirDelBloque() {
        List<AnalysisError> errores = semanticos("""
                let x: string = "exterior";
                {
                    let x: integer = 1;
                }
                x = 5;
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertEquals(5, errores.getFirst().linea());
    }
}
