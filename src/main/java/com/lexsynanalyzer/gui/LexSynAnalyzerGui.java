package com.lexsynanalyzer.gui;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

public class LexSynAnalyzerGui extends JFrame {

    private static final String PLACEHOLDER_TEXT = "// Seleccione o abra un archivo .cps para comenzar el análisis...";

    private static final Color COLOR_BG = new Color(0x1E, 0x1E, 0x1E);
    private static final Color COLOR_PANEL = new Color(0x25, 0x25, 0x26);
    private static final Color COLOR_TOOLBAR = new Color(0x2D, 0x2D, 0x2D);
    private static final Color COLOR_BORDER = new Color(0x3C, 0x3C, 0x3C);
    private static final Color COLOR_TEXT = new Color(0xD4, 0xD4, 0xD4);
    private static final Color COLOR_ACCENT = new Color(0x1B, 0x6E, 0xC2);
    private static final Color COLOR_ACCENT_HOVER = new Color(0x15, 0x58, 0x9C);
    private static final Color COLOR_ACCENT_BORDER = new Color(0x0F, 0x46, 0x80);
    private static final Color COLOR_DISABLED_BUTTON = new Color(0x4A, 0x4A, 0x4A);
    private static final Color COLOR_LINE_NUMBERS = new Color(0x85, 0x85, 0x85);

    private final JTextArea editorArea;
    private final JTextArea lineNumbersArea;
    private final ResultsTablePanel resultsPanel;
    private final SyntaxTreePanel syntaxTreePanel;
    private final TacPanel tacPanel;
    private final SimbolosPanel simbolosPanel;
    private final JTabbedPane tabbedPane;
    private final JButton btnAbrir;
    private final JButton btnGuardar;
    private final JButton btnAnalizar;
    private final JButton btnLimpiar;
    private final JLabel lblArchivoPath;
    private final JLabel lblStatusBar;

    private File archivoActual;
    private boolean hasUnsavedChanges;

    public LexSynAnalyzerGui() {
        super("Compiscript — Analizador y Generador de Código Intermedio (ANTLR4)");
        UIManager.put("Button.disabledText", Color.WHITE);
        UIManager.put("TabbedPane.selected", COLOR_PANEL);
        UIManager.put("TabbedPane.background", COLOR_TOOLBAR);
        UIManager.put("TabbedPane.foreground", COLOR_TEXT);
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));

        lblArchivoPath = new JLabel("[ Ningún archivo cargado ]");
        lblArchivoPath.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblArchivoPath.setForeground(COLOR_LINE_NUMBERS);
        lblArchivoPath.setBorder(new EmptyBorder(0, 12, 0, 12));

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1150, 750);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null);
        getContentPane().setBackground(COLOR_BG);

        // Estructura principal
        JPanel rootPanel = new JPanel(new BorderLayout(0, 0));
        rootPanel.setBackground(COLOR_BG);

        // 1. Barra superior (Header + Toolbar)
        JPanel topPanel = crearTopPanel();
        rootPanel.add(topPanel, BorderLayout.NORTH);

        // 2. Editor de Código (Con Numeración de Líneas y Placeholder)
        editorArea = new JTextArea(PLACEHOLDER_TEXT);
        editorArea.setBackground(COLOR_BG);
        editorArea.setForeground(COLOR_TEXT);
        editorArea.setCaretColor(Color.WHITE);
        editorArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        editorArea.setTabSize(4);
        editorArea.setMargin(new Insets(6, 8, 6, 8));

        lineNumbersArea = new JTextArea("1 ");
        lineNumbersArea.setBackground(COLOR_PANEL);
        lineNumbersArea.setForeground(COLOR_LINE_NUMBERS);
        lineNumbersArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        lineNumbersArea.setEditable(false);
        lineNumbersArea.setFocusable(false);
        lineNumbersArea.setMargin(new Insets(6, 8, 6, 8));

        JScrollPane editorScrollPane = new JScrollPane(editorArea);
        editorScrollPane.setRowHeaderView(lineNumbersArea);
        editorScrollPane.setBorder(BorderFactory.createLineBorder(COLOR_BORDER, 1));
        editorScrollPane.getViewport().setBackground(COLOR_BG);

        JPanel editorContainer = new JPanel(new BorderLayout(0, 0));
        editorContainer.setBackground(COLOR_BG);

        JLabel editorHeader = new JLabel("Visor de Código Fuente (.cps)");
        editorHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));
        editorHeader.setForeground(COLOR_TEXT);
        editorHeader.setBackground(COLOR_PANEL);
        editorHeader.setOpaque(true);
        editorHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 0, 1, COLOR_BORDER),
                new EmptyBorder(6, 10, 6, 10)
        ));

        editorContainer.add(editorHeader, BorderLayout.NORTH);
        editorContainer.add(editorScrollPane, BorderLayout.CENTER);

        // Actualizar números de línea al escribir o cargar texto
        editorArea.addCaretListener(e -> actualizarNumerosDeLinea());
        editorArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                marcarCambiosSinGuardar();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                marcarCambiosSinGuardar();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                marcarCambiosSinGuardar();
            }
        });

        // 3. Panel de Resultados y Panel del Árbol Sintáctico en JTabbedPane
        resultsPanel = new ResultsTablePanel();
        resultsPanel.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                resaltarLineaSeleccionada();
            }
        });

        syntaxTreePanel = new SyntaxTreePanel();
        tacPanel = new TacPanel();
        simbolosPanel = new SimbolosPanel();

        tabbedPane = new JTabbedPane();
        tabbedPane.setUI(new DarkTabbedPaneUI());
        tabbedPane.setBackground(COLOR_PANEL);
        tabbedPane.setForeground(COLOR_TEXT);
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabbedPane.setBorder(BorderFactory.createLineBorder(COLOR_BORDER, 1));

        tabbedPane.addTab("Tabla de Errores y Diagnósticos", resultsPanel);
        tabbedPane.addTab("Árbol Sintáctico (ParseTree)", syntaxTreePanel);
        tabbedPane.addTab("Código Intermedio (TAC)", tacPanel);
        tabbedPane.addTab("Tabla de Símbolos / Registros de Activación", simbolosPanel);

        // 4. SplitPane Redimensionable (Vertical: Editor arriba, Pestañas abajo)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorContainer, tabbedPane);
        splitPane.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void setBorder(javax.swing.border.Border b) {
                        // Eliminar borde 3D resaltado blanco nativo
                    }

                    @Override
                    public void paint(Graphics g) {
                        g.setColor(COLOR_BG);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        splitPane.setDividerLocation(280);
        splitPane.setResizeWeight(0.4);
        splitPane.setBackground(COLOR_BG);
        splitPane.setBorder(new EmptyBorder(8, 8, 8, 8));
        splitPane.setDividerSize(6);

        rootPanel.add(splitPane, BorderLayout.CENTER);

        // 5. Barra de Estado Inferior
        lblStatusBar = new JLabel(" Listo — Esperando selección de archivo .cps");
        lblStatusBar.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatusBar.setForeground(COLOR_TEXT);
        lblStatusBar.setBackground(COLOR_PANEL);
        lblStatusBar.setOpaque(true);
        lblStatusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDER),
                new EmptyBorder(6, 10, 6, 10)
        ));
        rootPanel.add(lblStatusBar, BorderLayout.SOUTH);

        setContentPane(rootPanel);

        // Asignar Eventos a Botones
        btnAbrir = encontrarBoton("Abrir");
        btnGuardar = encontrarBoton("Guardar");
        btnAnalizar = encontrarBoton("Analizar");
        btnLimpiar = encontrarBoton("Limpiar");

        actualizarEstadoBotonAnalizar(false);
        btnGuardar.setEnabled(false);

        btnAbrir.addActionListener(e -> abrirArchivo());
        btnGuardar.addActionListener(e -> guardarArchivo());
        btnAnalizar.addActionListener(e -> ejecutarAnalisis());
        btnLimpiar.addActionListener(e -> limpiarVista());

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "saveFile");
        getRootPane().getActionMap().put("saveFile", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                guardarArchivo();
            }
        });

        actualizarNumerosDeLinea();
    }

    private JPanel crearTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COLOR_TOOLBAR);
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER));

        // Título de la aplicación
        JLabel lblTitle = new JLabel("  COMPISCRIPT IDE  |  Análisis y Código Intermedio");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(COLOR_TEXT);

        // Toolbar con 4 botones con estilo consistente y tooltips
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        toolbar.setBackground(COLOR_TOOLBAR);

        JButton btnOpen = crearBoton("Abrir Archivo (.cps)", COLOR_ACCENT, COLOR_ACCENT_HOVER,
                "Abrir un archivo de código fuente Compiscript (.cps)");
        JButton btnSave = crearBoton("Guardar", COLOR_ACCENT, COLOR_ACCENT_HOVER,
                "Guardar los cambios en el archivo actual (Ctrl+S)");
        JButton btnRun = crearBoton("Analizar", COLOR_ACCENT, COLOR_ACCENT_HOVER,
                "Ejecutar el análisis y generar el código intermedio del archivo cargado");
        JButton btnClear = crearBoton("Limpiar", COLOR_ACCENT, COLOR_ACCENT_HOVER,
                "Limpiar el editor de código, la tabla de resultados y el árbol sintáctico");

        btnOpen.setName("Abrir");
        btnSave.setName("Guardar");
        btnRun.setName("Analizar");
        btnClear.setName("Limpiar");

        toolbar.add(lblArchivoPath);
        toolbar.add(btnOpen);
        toolbar.add(btnSave);
        toolbar.add(btnRun);
        toolbar.add(btnClear);

        panel.add(lblTitle, BorderLayout.WEST);
        panel.add(toolbar, BorderLayout.EAST);

        return panel;
    }

    private JButton crearBoton(String texto, Color bg, Color bgHover, String tooltip) {
        JButton btn = new JButton(texto);
        btn.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setToolTipText(tooltip);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_ACCENT_BORDER, 1),
                new EmptyBorder(6, 16, 6, 16)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(bgHover);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(bg);
            }
        });
        return btn;
    }

    private void actualizarEstadoBotonAnalizar(boolean habilitado) {
        btnAnalizar.setEnabled(habilitado);
        btnAnalizar.setBackground(habilitado ? COLOR_ACCENT : COLOR_DISABLED_BUTTON);
        btnAnalizar.setForeground(Color.WHITE);
    }

    private JButton encontrarBoton(String name) {
        for (Component c : getAllComponents(this)) {
            if (c instanceof JButton b && name.equals(b.getName())) {
                return b;
            }
        }
        throw new IllegalStateException("Botón no encontrado: " + name);
    }

    private java.util.List<Component> getAllComponents(Container c) {
        java.util.List<Component> compList = new java.util.ArrayList<>();
        for (Component comp : c.getComponents()) {
            compList.add(comp);
            if (comp instanceof Container cont) {
                compList.addAll(getAllComponents(cont));
            }
        }
        return compList;
    }

    public ResultsTablePanel getResultsPanel() {
        return resultsPanel;
    }

    public SyntaxTreePanel getSyntaxTreePanel() {
        return syntaxTreePanel;
    }

    public TacPanel getTacPanel() {
        return tacPanel;
    }

    public SimbolosPanel getSimbolosPanel() {
        return simbolosPanel;
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    private void abrirArchivo() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccionar archivo Compiscript (.cps)");
        chooser.setFileFilter(new FileNameExtensionFilter("Archivos Compiscript (*.cps)", "cps"));

        Path defaultDir = Paths.get("src", "test", "resources", "casos");
        if (Files.exists(defaultDir)) {
            chooser.setCurrentDirectory(defaultDir.toFile());
        }

        int opcion = chooser.showOpenDialog(this);
        if (opcion == JFileChooser.APPROVE_OPTION) {
            archivoActual = chooser.getSelectedFile();
            try {
                String contenido = Files.readString(archivoActual.toPath());
                editorArea.setText(contenido);
                editorArea.setCaretPosition(0);
                hasUnsavedChanges = false;
                actualizarNumerosDeLinea();
                actualizarEstadoBotonAnalizar(true);
                btnGuardar.setEnabled(true);
                lblArchivoPath.setText(archivoActual.getName());
                lblStatusBar.setText(" Archivo cargado: " + archivoActual.getAbsolutePath());
                resultsPanel.limpiar();
                syntaxTreePanel.limpiar();
                tacPanel.limpiar();
                simbolosPanel.limpiar();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error al leer el archivo:\n" + ex.getMessage(),
                        "Error de Lectura", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void ejecutarAnalisis() {
        if (archivoActual == null || !archivoActual.exists()) {
            JOptionPane.showMessageDialog(this, "Por favor seleccione un archivo válido antes de analizar.",
                    "Archivo no seleccionado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!guardarArchivo()) {
            return;
        }

        actualizarEstadoBotonAnalizar(false);
        btnAbrir.setEnabled(false);
        lblStatusBar.setText(" [BUSY] Ejecutando análisis y generación de código intermedio...");

        SwingWorker<AnalysisResult, Void> worker = new SwingWorker<>() {
            @Override
            protected AnalysisResult doInBackground() throws Exception {
                return LexSynAnalyzer.analyze(archivoActual);
            }

            @Override
            protected void done() {
                try {
                    AnalysisResult result = get();
                    if (result.exitoso()) {
                        resultsPanel.mostrarExito();
                        lblStatusBar.setText(" [OK] Análisis completado: 0 errores encontrados.");
                        tabbedPane.setTitleAt(0, "Diagnósticos (0)");
                    } else {
                        resultsPanel.mostrarErrores(result.errores());
                        lblStatusBar.setText(String.format(" [ERROR] Análisis completado: %d errores detectados.", result.errores().size()));
                        tabbedPane.setTitleAt(0, String.format("Errores (%d)", result.errores().size()));
                    }

                    if (result.arbol() != null) {
                        syntaxTreePanel.mostrarArbol(result.arbol(), result.nombresReglas());
                        tabbedPane.setTitleAt(1, "Árbol Sintáctico (ParseTree)");
                    } else {
                        syntaxTreePanel.limpiar();
                    }

                    if (result.tieneTac()) {
                        tacPanel.mostrarTac(result.tac());
                        simbolosPanel.mostrar(result.layout(), result.tac());
                    } else {
                        tacPanel.mostrarSinTac();
                        simbolosPanel.mostrarSinDatos();
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(LexSynAnalyzerGui.this,
                            "Error crítico durante el análisis:\n" + ex.getCause().getMessage(),
                            "Error de Análisis", JOptionPane.ERROR_MESSAGE);
                    lblStatusBar.setText(" [ERROR] Fallo crítico durante el análisis.");
                } finally {
                    actualizarEstadoBotonAnalizar(true);
                    btnAbrir.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private boolean guardarArchivo() {
        if (archivoActual == null) {
            JOptionPane.showMessageDialog(this, "Abra un archivo .cps antes de guardar.",
                    "Archivo no seleccionado", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        try {
            Files.writeString(archivoActual.toPath(), editorArea.getText());
            hasUnsavedChanges = false;
            lblStatusBar.setText(" [OK] Cambios guardados: " + archivoActual.getAbsolutePath());
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No fue posible guardar el archivo:\n" + ex.getMessage(),
                    "Error al guardar", JOptionPane.ERROR_MESSAGE);
            lblStatusBar.setText(" [ERROR] No se pudieron guardar los cambios.");
            return false;
        }
    }

    private void marcarCambiosSinGuardar() {
        if (archivoActual != null && !hasUnsavedChanges) {
            hasUnsavedChanges = true;
            lblStatusBar.setText(" [EDITADO] Hay cambios sin guardar. Presione Ctrl+S o Guardar.");
        }
    }

    private void limpiarVista() {
        archivoActual = null;
        hasUnsavedChanges = false;
        editorArea.setText(PLACEHOLDER_TEXT);
        actualizarNumerosDeLinea();
        resultsPanel.limpiar();
        syntaxTreePanel.limpiar();
        tacPanel.limpiar();
        simbolosPanel.limpiar();
        tabbedPane.setTitleAt(0, "Tabla de Errores y Diagnósticos");
        tabbedPane.setTitleAt(1, "Árbol Sintáctico (ParseTree)");
        actualizarEstadoBotonAnalizar(false);
        btnGuardar.setEnabled(false);
        lblArchivoPath.setText("[ Ningún archivo cargado ]");
        lblStatusBar.setText(" Vista limpiada. Seleccione un archivo .cps.");
    }

    private void actualizarNumerosDeLinea() {
        int lineas = editorArea.getLineCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lineas; i++) {
            sb.append(i).append("\n");
        }
        lineNumbersArea.setText(sb.toString());
    }

    private void resaltarLineaSeleccionada() {
        int row = resultsPanel.getTable().getSelectedRow();
        if (row < 0) return;

        Object valLinea = resultsPanel.getTable().getValueAt(row, 1);
        if (valLinea instanceof Integer numLinea) {
            try {
                int lineIndex = numLinea - 1; // 1-indexed to 0-indexed
                int startOffset = editorArea.getLineStartOffset(lineIndex);
                int endOffset = editorArea.getLineEndOffset(lineIndex);

                editorArea.requestFocusInWindow();
                editorArea.setCaretPosition(startOffset);
                editorArea.moveCaretPosition(endOffset);
            } catch (BadLocationException ignored) {
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            LexSynAnalyzerGui gui = new LexSynAnalyzerGui();
            gui.setVisible(true);
        });
    }

    private static class DarkTabbedPaneUI extends javax.swing.plaf.basic.BasicTabbedPaneUI {
        private static final Color TAB_BG = new Color(0x2D, 0x2D, 0x2D);
        private static final Color TAB_SELECTED_BG = new Color(0x1E, 0x1E, 0x1E);
        private static final Color TAB_SELECTED_BORDER = new Color(0x1B, 0x6E, 0xC2);
        private static final Color TAB_BORDER = new Color(0x3C, 0x3C, 0x3C);
        private static final Color TEXT_COLOR = new Color(0x96, 0x96, 0x96);
        private static final Color TEXT_SELECTED_COLOR = Color.WHITE;

        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabAreaInsets = new Insets(4, 6, 0, 6);
            contentBorderInsets = new Insets(0, 0, 0, 0);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            tabInsets = new Insets(6, 16, 6, 16);
        }

        @Override
        protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {
            g.setColor(new Color(0x25, 0x25, 0x26));
            g.fillRect(0, 0, tabPane.getWidth(), tabPane.getHeight());
            super.paintTabArea(g, tabPlacement, selectedIndex);
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            if (isSelected) {
                g2.setColor(TAB_SELECTED_BG);
                g2.fillRect(x, y, w, h);
                // Indicador azul superior estilo VS Code
                g2.setColor(TAB_SELECTED_BORDER);
                g2.fillRect(x, y, w, 3);
            } else {
                g2.setColor(TAB_BG);
                g2.fillRect(x, y, w, h);
            }
            g2.dispose();
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
            g.setColor(TAB_BORDER);
            g.drawRect(x, y, w - 1, h - 1);
        }

        @Override
        protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex, String title, Rectangle textRect, boolean isSelected) {
            g.setFont(font);
            g.setColor(isSelected ? TEXT_SELECTED_COLOR : TEXT_COLOR);
            g.drawString(title, textRect.x, textRect.y + metrics.getAscent());
        }

        @Override
        protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
            // Sin bordes blancos en el contenido
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) {
            // Sin recuadro de foco clásico
        }
    }
}
