package com.lexsynanalyzer.analyzer;

import com.lexsynanalyzer.parser.LexSynAnalyzerLexer;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser;
import com.lexsynanalyzer.semantic.AnalizadorSemantico;
import com.lexsynanalyzer.semantic.AsignadorMemoria;
import com.lexsynanalyzer.semantic.ErrorSemanticoReporter;
import com.lexsynanalyzer.semantic.Layout;
import com.lexsynanalyzer.tac.GeneradorTAC;
import com.lexsynanalyzer.tac.ProgramaTAC;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LexSynAnalyzer {

    public static AnalysisResult analyze(File archivo) throws IOException {
        return analizar(CharStreams.fromPath(archivo.toPath()));
    }

    /** Analiza código fuente en memoria; útil para pruebas y para el editor de la GUI. */
    public static AnalysisResult analizarTexto(String codigoFuente) {
        return analizar(CharStreams.fromString(codigoFuente));
    }

    private static AnalysisResult analizar(CharStream input) {
        List<AnalysisError> errores = new ArrayList<>();

        LexSynAnalyzerLexer lexer = new LexSynAnalyzerLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new CapturingErrorListener(TipoError.LEXICO, errores, MensajesEspanol::traducirLexico));

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LexSynAnalyzerParser parser = new LexSynAnalyzerParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new CapturingErrorListener(
                TipoError.SINTACTICO, errores, MensajesEspanol::traducirSintactico));

        LexSynAnalyzerParser.ProgramContext arbol = parser.program();

        // El análisis semántico solo tiene sentido sobre un árbol completo: si el archivo tiene
        // errores léxicos o sintácticos, el ParseTree está incompleto y produciría falsos errores.
        AnalizadorSemantico semantico = null;
        if (errores.isEmpty()) {
            semantico = new AnalizadorSemantico(new ErrorSemanticoReporter(errores));
            semantico.analizar(arbol);
        }

        // Con cualquier error léxico, sintáctico o semántico no se genera código intermedio.
        ProgramaTAC tac = ProgramaTAC.vacio();
        Layout layout = null;
        if (errores.isEmpty()) {
            layout = AsignadorMemoria.asignar(semantico.resultado());
            tac = new GeneradorTAC(layout).generar(arbol);
        }

        return new AnalysisResult(sinDuplicadosYOrdenados(errores), arbol, parser.getRuleNames(), tac, layout);
    }

    private static List<AnalysisError> sinDuplicadosYOrdenados(List<AnalysisError> errores) {
        Map<String, AnalysisError> unicos = new LinkedHashMap<>();
        for (AnalysisError error : errores) {
            unicos.putIfAbsent(claveDeDuplicado(error), error);
        }

        return unicos.values().stream()
                .sorted(Comparator.comparingInt(AnalysisError::linea)
                        .thenComparingInt(AnalysisError::columna)
                        .thenComparing(AnalysisError::tipo))
                .toList();
    }

    /**
     * Los errores léxicos y sintácticos se agrupan por posición porque ANTLR suele emitir varios
     * mensajes derivados del mismo fallo. Un error semántico, en cambio, es un diagnóstico único:
     * dos reglas distintas pueden fallar sobre el mismo token y ambas deben mostrarse.
     */
    private static String claveDeDuplicado(AnalysisError error) {
        String posicion = error.tipo() + ":" + error.linea() + ":" + error.columna();
        return error.tipo() == TipoError.SEMANTICO
                ? posicion + ":" + error.descripcion()
                : posicion;
    }

    private LexSynAnalyzer() {
    }
}
