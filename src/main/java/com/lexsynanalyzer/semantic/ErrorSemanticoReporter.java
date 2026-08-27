package com.lexsynanalyzer.semantic;

import com.lexsynanalyzer.analyzer.AnalysisError;
import com.lexsynanalyzer.analyzer.TipoError;

import java.util.List;
import java.util.Objects;

/** Punto único para reportar errores semánticos desde el Visitor. */
public final class ErrorSemanticoReporter {

    private final List<AnalysisError> errores;

    public ErrorSemanticoReporter(List<AnalysisError> errores) {
        this.errores = Objects.requireNonNull(errores);
    }

    public void reportar(int linea, int columna, String simbolo, String descripcion) {
        errores.add(new AnalysisError(
                TipoError.SEMANTICO, linea, columna, simbolo, Objects.requireNonNull(descripcion))
        );
    }

    public int cantidad() {
        return (int) errores.stream()
                .filter(error -> error.tipo() == TipoError.SEMANTICO)
                .count();
    }
}
