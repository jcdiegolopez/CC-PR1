package com.lexsynanalyzer.gui;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Enumeration;
import java.util.Objects;

/**
 * Panel visual para explorar el árbol sintáctico (ParseTree) generado por ANTLR4.
 *
 * <p>Diseñado con la estética oscura de VS Code. Renderiza reglas no terminales y tokens
 * terminales con colores diferenciados y provee controles interactivos para expandir
 * o colapsar nodos.
 */
public class SyntaxTreePanel extends JPanel {

    private static final Color COLOR_BG = new Color(0x1E, 0x1E, 0x1E);
    private static final Color COLOR_HEADER = new Color(0x25, 0x25, 0x26);
    private static final Color COLOR_GRID = new Color(0x3C, 0x3C, 0x3C);
    private static final Color COLOR_TEXT = new Color(0xD4, 0xD4, 0xD4);
    private static final Color COLOR_SELECTION = new Color(0x04, 0x39, 0x5E);
    private static final Color COLOR_ACCENT = new Color(0x1B, 0x6E, 0xC2);
    private static final Color COLOR_ACCENT_HOVER = new Color(0x15, 0x58, 0x9C);

    private static final Color COLOR_RULE = new Color(0x4E, 0xC9, 0xB0);       // Verde azulado (reglas)
    private static final Color COLOR_TERMINAL = new Color(0x9C, 0xDC, 0xFE);   // Azul claro (identificadores / tokens)
    private static final Color COLOR_KEYWORD = new Color(0x56, 0x9C, 0xD6);    // Azul oscuro (palabras clave)
    private static final Color COLOR_STRING = new Color(0xCE, 0x91, 0x78);     // Naranja (strings)
    private static final Color COLOR_NUMBER = new Color(0xB5, 0xCE, 0xA8);     // Verde claro (números)
    private static final Color COLOR_MUTED = new Color(0x80, 0x80, 0x80);      // Gris (EOF y símbolos)

    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final JLabel lblInfo;

    public SyntaxTreePanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(COLOR_BG);

        // Barra superior con título, información y botones de control
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(COLOR_HEADER);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_GRID),
                new EmptyBorder(6, 12, 6, 12)
        ));

        lblInfo = new JLabel("Árbol Sintáctico (ParseTree) — Listo");
        lblInfo.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblInfo.setForeground(COLOR_TEXT);
        headerPanel.add(lblInfo, BorderLayout.WEST);

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        controlsPanel.setBackground(COLOR_HEADER);

        JButton btnExpand = crearBoton("Expandir Todo", "Expandir todos los nodos del árbol");
        JButton btnCollapse = crearBoton("Colapsar Todo", "Colapsar todos los nodos excepto la raíz");

        btnExpand.addActionListener(e -> expandirTodo());
        btnCollapse.addActionListener(e -> colapsarTodo());

        controlsPanel.add(btnExpand);
        controlsPanel.add(btnCollapse);
        headerPanel.add(controlsPanel, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Árbol sintáctico
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new NodeData("Sin árbol disponible", NodeType.MUTED));
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setBackground(COLOR_BG);
        tree.setForeground(COLOR_TEXT);
        tree.setFont(new Font("Consolas", Font.PLAIN, 13));
        tree.setRowHeight(22);
        tree.setCellRenderer(new SyntaxTreeCellRenderer());
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.getViewport().setBackground(COLOR_BG);
        scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_GRID, 1));
        add(scrollPane, BorderLayout.CENTER);
    }

    private JButton crearBoton(String texto, String tooltip) {
        JButton btn = new JButton(texto);
        btn.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setForeground(Color.WHITE);
        btn.setBackground(COLOR_ACCENT);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setToolTipText(tooltip);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_GRID, 1),
                new EmptyBorder(3, 10, 3, 10)
        ));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(COLOR_ACCENT_HOVER);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(COLOR_ACCENT);
            }
        });
        return btn;
    }

    /** Muestra el ParseTree de ANTLR4 en el árbol visual. */
    public void mostrarArbol(ParseTree arbol, String[] nombresReglas) {
        if (arbol == null) {
            limpiar();
            return;
        }

        DefaultMutableTreeNode root = construirNodo(arbol, nombresReglas);
        treeModel.setRoot(root);
        tree.setRootVisible(true);

        int totalNodos = contarNodos(root);
        lblInfo.setText(String.format("Árbol Sintáctico (ParseTree) — %d nodos generados", totalNodos));

        // Expandir los primeros 3 niveles por comodidad visual
        expandirNiveles(new TreePath(root), 3);
    }

    public void limpiar() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(
                new NodeData("No hay árbol sintáctico disponible (analice un archivo .cps)", NodeType.MUTED));
        treeModel.setRoot(root);
        lblInfo.setText("Árbol Sintáctico (ParseTree) — Listo");
    }

    public void expandirTodo() {
        if (treeModel.getRoot() == null) return;
        TreeNode root = (TreeNode) treeModel.getRoot();
        expandirRecursivo(new TreePath(root));
    }

    private void expandirRecursivo(TreePath parent) {
        TreeNode node = (TreeNode) parent.getLastPathComponent();
        if (node.getChildCount() >= 0) {
            for (Enumeration<?> e = node.children(); e.hasMoreElements(); ) {
                TreeNode n = (TreeNode) e.nextElement();
                TreePath path = parent.pathByAddingChild(n);
                expandirRecursivo(path);
            }
        }
        tree.expandPath(parent);
    }

    public void colapsarTodo() {
        if (treeModel.getRoot() == null) return;
        TreeNode root = (TreeNode) treeModel.getRoot();
        for (Enumeration<?> e = root.children(); e.hasMoreElements(); ) {
            TreeNode child = (TreeNode) e.nextElement();
            tree.collapsePath(new TreePath(new Object[]{root, child}));
        }
    }

    private void expandirNiveles(TreePath parent, int nivel) {
        if (nivel <= 0) return;
        tree.expandPath(parent);
        TreeNode node = (TreeNode) parent.getLastPathComponent();
        for (Enumeration<?> e = node.children(); e.hasMoreElements(); ) {
            TreeNode n = (TreeNode) e.nextElement();
            expandirNiveles(parent.pathByAddingChild(n), nivel - 1);
        }
    }

    private DefaultMutableTreeNode construirNodo(ParseTree treeNode, String[] nombresReglas) {
        if (treeNode instanceof ParserRuleContext ruleCtx) {
            int ruleIndex = ruleCtx.getRuleIndex();
            String ruleName = (nombresReglas != null && ruleIndex >= 0 && ruleIndex < nombresReglas.length)
                    ? nombresReglas[ruleIndex]
                    : ruleCtx.getClass().getSimpleName().replace("Context", "");

            DefaultMutableTreeNode node = new DefaultMutableTreeNode(new NodeData(ruleName, NodeType.RULE));
            for (int i = 0; i < treeNode.getChildCount(); i++) {
                node.add(construirNodo(treeNode.getChild(i), nombresReglas));
            }
            return node;
        } else if (treeNode instanceof TerminalNode terminal) {
            String text = terminal.getText();
            NodeType type;
            if ("<EOF>".equals(text)) {
                type = NodeType.MUTED;
            } else if (text.startsWith("\"")) {
                type = NodeType.STRING;
            } else if (text.matches("\\d+")) {
                type = NodeType.NUMBER;
            } else if (text.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                type = esPalabraClave(text) ? NodeType.KEYWORD : NodeType.IDENTIFIER;
            } else {
                type = NodeType.SYMBOL;
            }

            String display = "\"" + text + "\"";
            return new DefaultMutableTreeNode(new NodeData(display, type));
        } else {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(new NodeData(treeNode.getText(), NodeType.MUTED));
            for (int i = 0; i < treeNode.getChildCount(); i++) {
                node.add(construirNodo(treeNode.getChild(i), nombresReglas));
            }
            return node;
        }
    }

    private static boolean esPalabraClave(String text) {
        return switch (text) {
            case "let", "var", "const", "function", "class", "if", "else", "while",
                 "do", "for", "foreach", "in", "break", "continue", "return", "try",
                 "catch", "switch", "case", "default", "print", "this", "new",
                 "true", "false", "null", "integer", "boolean", "string" -> true;
            default -> false;
        };
    }

    private static int contarNodos(TreeNode node) {
        int count = 1;
        for (Enumeration<?> e = node.children(); e.hasMoreElements(); ) {
            count += contarNodos((TreeNode) e.nextElement());
        }
        return count;
    }

    public JTree getTree() {
        return tree;
    }

    // ------------------------------------------------------------------
    // Modelo de datos interno y Renderizador
    // ------------------------------------------------------------------

    public enum NodeType {
        RULE,
        IDENTIFIER,
        KEYWORD,
        STRING,
        NUMBER,
        SYMBOL,
        MUTED
    }

    public record NodeData(String text, NodeType type) {
        @Override
        public String toString() {
            return text;
        }
    }

    private static class SyntaxTreeCellRenderer extends DefaultTreeCellRenderer {

        public SyntaxTreeCellRenderer() {
            setBackgroundNonSelectionColor(COLOR_BG);
            setBackgroundSelectionColor(COLOR_SELECTION);
            setTextSelectionColor(Color.WHITE);
            setBorderSelectionColor(COLOR_ACCENT);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode dmtn && dmtn.getUserObject() instanceof NodeData data) {
                setText(data.text());
                if (!sel) {
                    setForeground(switch (data.type()) {
                        case RULE -> COLOR_RULE;
                        case IDENTIFIER -> COLOR_TERMINAL;
                        case KEYWORD -> COLOR_KEYWORD;
                        case STRING -> COLOR_STRING;
                        case NUMBER -> COLOR_NUMBER;
                        case SYMBOL -> COLOR_TEXT;
                        case MUTED -> COLOR_MUTED;
                    });
                }
                if (data.type() == NodeType.RULE) {
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    setFont(new Font("Consolas", Font.PLAIN, 12));
                }
            }

            setIcon(null); // Look limpio y moderno sin iconos de carpeta/hoja de Swing clásico
            return this;
        }
    }
}
