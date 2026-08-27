package com.lexsynanalyzer.semantic;

import com.lexsynanalyzer.analyzer.AnalysisError;
import com.lexsynanalyzer.analyzer.TipoError;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ErrorSemanticoReporterTest {

    @Test
    void reportaErrorSemanticoConSuUbicacion() {
        var errores = new ArrayList<AnalysisError>();
        var reporter = new ErrorSemanticoReporter(errores);

        reporter.reportar(4, 9, "edad", "La variable edad no puede recibir un string");

        assertEquals(1, reporter.cantidad());
        AnalysisError error = errores.getFirst();
        assertEquals(TipoError.SEMANTICO, error.tipo());
        assertEquals(4, error.linea());
        assertEquals(9, error.columna());
        assertEquals("edad", error.simbolo());
    }

    @Test
    void conservaErroresDeOtrosTiposAlContarSemanticos() {
        var errores = new ArrayList<AnalysisError>();
        errores.add(new AnalysisError(TipoError.LEXICO, 1, 1, "@", "Símbolo inválido"));
        var reporter = new ErrorSemanticoReporter(errores);

        reporter.reportar(2, 1, "x", "Variable no declarada");

        assertEquals(2, errores.size());
        assertEquals(1, reporter.cantidad());
    }

    @Test
    void exigeDescripcion() {
        var reporter = new ErrorSemanticoReporter(new ArrayList<AnalysisError>());

        assertThrows(NullPointerException.class,
                () -> reporter.reportar(1, 1, "x", null));
    }
}
