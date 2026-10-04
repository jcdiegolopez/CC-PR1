package com.lexsynanalyzer.gui;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacYSimbolosPanelTest {

    private static final String PROGRAMA = """
            class Punto { let x: integer = 0; }
            function doble(n: integer): integer { return n * 2; }
            let p: Punto = new Punto();
            let r: integer = doble(p.x);
            """;

    @Test
    void tacPanel_muestraElCodigoGenerado() {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(PROGRAMA);
        TacPanel panel = new TacPanel();

        panel.mostrarTac(resultado.tac());

        assertEquals(resultado.tac().toString(), panel.getTexto().strip().replace("\r\n", "\n"));
    }

    @Test
    void tacPanel_conErrores_muestraElMensajeYNingunCodigo() {
        TacPanel panel = new TacPanel();

        panel.mostrarSinTac();

        assertEquals(TacPanel.MENSAJE_SIN_TAC, panel.getMensaje());
        assertEquals("", panel.getTexto());
    }

    @Test
    void simbolosPanel_listaGlobalesFramesYCamposConSusOffsets() {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(PROGRAMA);
        SimbolosPanel panel = new SimbolosPanel();

        panel.mostrar(resultado.layout(), resultado.tac());

        JTable tabla = panel.getTable();
        assertTrue(contieneFila(tabla, "p", "GLOBAL", 0));
        assertTrue(contieneFila(tabla, "r", "GLOBAL", 8));
        assertTrue(contieneFila(tabla, "n", "PARAM", 24));
        assertTrue(contieneFila(tabla, "x", "CAMPO", 0));
        assertTrue(contieneAmbito(tabla, "function doble (frame 32 B)"));
        assertTrue(contieneAmbito(tabla, "class Punto (instancia 4 B)"));
    }

    private static boolean contieneFila(JTable tabla, String nombre, String categoria, int offset) {
        for (int fila = 0; fila < tabla.getRowCount(); fila++) {
            if (nombre.equals(tabla.getValueAt(fila, 1)) && categoria.equals(tabla.getValueAt(fila, 3))
                    && Integer.valueOf(offset).equals(tabla.getValueAt(fila, 5))) {
                return true;
            }
        }
        return false;
    }

    private static boolean contieneAmbito(JTable tabla, String ambito) {
        for (int fila = 0; fila < tabla.getRowCount(); fila++) {
            if (ambito.equals(tabla.getValueAt(fila, 0))) {
                return true;
            }
        }
        return false;
    }
}
