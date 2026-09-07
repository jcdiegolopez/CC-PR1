package com.lexsynanalyzer;

import com.lexsynanalyzer.analyzer.AnalysisError;
import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.analyzer.TipoError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regresión de los archivos de demostración por categoría de regla semántica.
 *
 * <p>Cada archivo de {@code casos/semanticos} contiene primero un bloque de casos válidos y luego
 * los casos inválidos numerados en sus comentarios. Estas pruebas fijan la cantidad exacta de
 * diagnósticos de cada archivo para que un cambio en el Visitor no los desalinee en silencio.
 */
class CasosSemanticosTest {

    private static final Path DIRECTORIO_CASOS =
            Paths.get("src", "test", "resources", "casos", "semanticos");

    @ParameterizedTest(name = "{0} reporta {1} error(es) semántico(s)")
    @CsvSource({
            "01_sistema_de_tipos.cps,   5",
            "02_manejo_de_ambito.cps,   3",
            "03_funciones.cps,          4",
            "04_control_de_flujo.cps,   7",
            "05_clases_y_objetos.cps,   6",
            "06_listas_y_arreglos.cps,  4",
            "07_generales.cps,          5"
    })
    @DisplayName("Cada archivo de demostración reporta solo los errores semánticos esperados")
    void reportaLosErroresSemanticosEsperados(String nombre, int esperados) throws IOException {
        List<AnalysisError> errores = analizar(nombre);

        // El análisis semántico solo corre sobre un árbol completo: estos archivos deben estar
        // libres de errores léxicos y sintácticos o no se validaría ninguna regla.
        assertEquals(0, contar(errores, TipoError.LEXICO),
                "No se esperaban errores léxicos en " + nombre + ": " + errores);
        assertEquals(0, contar(errores, TipoError.SINTACTICO),
                "No se esperaban errores sintácticos en " + nombre + ": " + errores);
        assertEquals(esperados, contar(errores, TipoError.SEMANTICO),
                "Cantidad inesperada de errores semánticos en " + nombre + ": " + errores);
    }

    @ParameterizedTest(name = "{0} ubica cada error con línea, columna, símbolo y descripción")
    @CsvSource({
            "01_sistema_de_tipos.cps",
            "02_manejo_de_ambito.cps",
            "03_funciones.cps",
            "04_control_de_flujo.cps",
            "05_clases_y_objetos.cps",
            "06_listas_y_arreglos.cps",
            "07_generales.cps"
    })
    @DisplayName("Todo diagnóstico semántico llega ubicado y descrito en español")
    void cadaErrorLlegaUbicadoYDescrito(String nombre) throws IOException {
        List<AnalysisError> errores = analizar(nombre);
        assertFalse(errores.isEmpty(), "Se esperaban errores semánticos en " + nombre);

        for (AnalysisError error : errores) {
            assertTrue(error.linea() > 0, "Línea inválida en " + nombre + ": " + error);
            assertTrue(error.columna() > 0, "Columna inválida en " + nombre + ": " + error);
            assertFalse(error.simbolo() == null || error.simbolo().isBlank(),
                    "Símbolo vacío en " + nombre + ": " + error);
            assertFalse(error.descripcion().isBlank(), "Descripción vacía en " + nombre + ": " + error);
        }
    }

    private static List<AnalysisError> analizar(String nombre) throws IOException {
        File archivo = DIRECTORIO_CASOS.resolve(nombre.trim()).toFile();
        assertTrue(archivo.exists(), "El archivo de prueba debe existir: " + archivo.getAbsolutePath());

        AnalysisResult resultado = LexSynAnalyzer.analyze(archivo);
        return resultado.errores();
    }

    private static long contar(List<AnalysisError> errores, TipoError tipo) {
        return errores.stream().filter(error -> error.tipo() == tipo).count();
    }
}
