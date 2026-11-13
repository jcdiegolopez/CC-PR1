package com.lexsynanalyzer.gui;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MipsPanelTest {

    private static final String PROGRAMA = """
            let saludo: string = "¿Qué tal? # no es comentario";
            print(saludo);
            print(1 + 2);
            """;

    @Test
    void muestraElAsmTalCual() {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(PROGRAMA);
        MipsPanel panel = new MipsPanel();

        panel.mostrarMips(resultado.mips(), resultado.mipsSinOptimizar(), resultado.mirilla(), "saludo");

        assertEquals(resultado.mips().toString(), panel.getTexto().replace("\r\n", "\n"));
        assertTrue(panel.getMensaje().startsWith("[OK]"), panel.getMensaje());
        assertTrue(panel.accionesHabilitadas());
    }

    @Test
    void guardaElAsmEnUtf8() throws Exception {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(PROGRAMA);
        MipsPanel panel = new MipsPanel();
        panel.mostrarMips(resultado.mips(), resultado.mipsSinOptimizar(), resultado.mirilla(), "saludo");

        Path archivo = directorio.resolve("saludo.asm");
        panel.guardarEn(archivo);

        assertEquals(resultado.mips().toString(), Files.readString(archivo, StandardCharsets.UTF_8));
    }

    @TempDir
    Path directorio;

    @Test
    void conErrores_muestraElMensajeYNoDejaGuardar() {
        MipsPanel panel = new MipsPanel();

        panel.mostrarSinMips();

        assertEquals(MipsPanel.MENSAJE_SIN_MIPS, panel.getMensaje());
        assertEquals("", panel.getTexto());
        assertFalse(panel.accionesHabilitadas());
    }

    @Test
    void verSinOptimizar_alternaEntreLasDosVersiones() {
        AnalysisResult a = LexSynAnalyzer.analizarTexto("print(1);");
        AnalysisResult b = LexSynAnalyzer.analizarTexto("print(1); print(2);");
        MipsPanel panel = new MipsPanel();
        panel.mostrarMips(a.mips(), b.mips(), Map.of("prueba", 1), "x");

        panel.setMostrarSinOptimizar(true);
        assertEquals(b.mips().toString(), panel.getTexto().replace("\r\n", "\n"));

        panel.setMostrarSinOptimizar(false);
        assertEquals(a.mips().toString(), panel.getTexto().replace("\r\n", "\n"));
    }

    @Test
    void resumenDeLaMirilla_cuentaEliminadasYReglas() {
        Map<String, Integer> reglas = new LinkedHashMap<>();
        reglas.put("salto a la siguiente", 5);
        reglas.put("sw/lw", 12);

        assertEquals("Mirilla: 17 instrucciones eliminadas (salto a la siguiente ×5, sw/lw ×12)",
                MipsPanel.resumenMirilla(17, reglas));
        assertEquals("Mirilla: 1 instrucción eliminada (move ×1)", MipsPanel.resumenMirilla(1, Map.of("move", 1)));
        assertEquals("Mirilla: no aplicó ninguna transformación.", MipsPanel.resumenMirilla(0, Map.of()));
    }

    @Test
    void elNumeralDentroDeUnStringNoAbreComentario() {
        assertEquals(-1, MipsPanel.inicioComentario("str0: .asciiz \"a # b\""));
        assertEquals(14, MipsPanel.inicioComentario("    li $t0, 1 # x"));
    }
}
