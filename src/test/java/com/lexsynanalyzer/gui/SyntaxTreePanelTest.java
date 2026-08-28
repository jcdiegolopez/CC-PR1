package com.lexsynanalyzer.gui;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntaxTreePanelTest {

    @Test
    @DisplayName("El AnalysisResult transporta el ParseTree y nombres de reglas del parser")
    void analysisResultTransportaArbol() {
        String codigo = """
                class Persona {
                    var nombre: string;
                }
                let p: Persona = new Persona();
                """;

        AnalysisResult result = LexSynAnalyzer.analizarTexto(codigo);
        assertTrue(result.exitoso());
        assertNotNull(result.arbol(), "El ParseTree no debe ser nulo");
        assertNotNull(result.nombresReglas(), "Los nombres de reglas no deben ser nulos");
        assertTrue(result.nombresReglas().length > 0);
    }

    @Test
    @DisplayName("SyntaxTreePanel genera y renderiza el árbol sintáctico correctamente")
    void syntaxTreePanelGeneraArbol() {
        String codigo = """
                function sumar(a: integer, b: integer): integer {
                    return a + b;
                }
                let res: integer = sumar(10, 20);
                """;

        AnalysisResult result = LexSynAnalyzer.analizarTexto(codigo);
        SyntaxTreePanel panel = new SyntaxTreePanel();

        panel.mostrarArbol(result.arbol(), result.nombresReglas());

        assertNotNull(panel.getTree().getModel());
        DefaultTreeModel model = (DefaultTreeModel) panel.getTree().getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertNotNull(root);
        assertEquals("program", root.getUserObject().toString());
        assertTrue(root.getChildCount() > 0, "La raíz 'program' debe tener hijos");

        panel.expandirTodo();
        panel.colapsarTodo();

        panel.limpiar();
        DefaultMutableTreeNode cleanRoot = (DefaultMutableTreeNode) panel.getTree().getModel().getRoot();
        assertTrue(cleanRoot.getUserObject().toString().contains("No hay árbol"));
    }
}
