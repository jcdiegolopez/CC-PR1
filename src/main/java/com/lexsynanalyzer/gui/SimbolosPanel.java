package com.lexsynanalyzer.gui;

import com.lexsynanalyzer.semantic.Direccion;
import com.lexsynanalyzer.semantic.Layout;
import com.lexsynanalyzer.tac.Instruccion;
import com.lexsynanalyzer.tac.ProgramaTAC;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pestaña "Tabla de Símbolos / Registros de Activación": cada variable con su ámbito, nombre en el
 * TAC, categoría, tipo, offset y tamaño. Los frames muestran su tamaño final (con temporales) y las
 * clases el tamaño de una instancia.
 */
public class SimbolosPanel extends JPanel {

    private static final Color COLOR_BG = new Color(0x1E, 0x1E, 0x1E);
    private static final Color COLOR_HEADER = new Color(0x25, 0x25, 0x26);
    private static final Color COLOR_TEXT = new Color(0xD4, 0xD4, 0xD4);
    private static final Color COLOR_GRID = new Color(0x3C, 0x3C, 0x3C);
    private static final Color COLOR_SELECTION = new Color(0x04, 0x39, 0x5E);
    private static final Color COLOR_AMBITO = new Color(0x4E, 0xC9, 0xB0);

    private static final Color COLOR_GLOBAL = new Color(0xC5, 0x86, 0xC0);
    private static final Color COLOR_LOCAL = new Color(0x9C, 0xDC, 0xFE);
    private static final Color COLOR_PARAM = new Color(0xDC, 0xDC, 0xAA);
    private static final Color COLOR_CAMPO = new Color(0xCE, 0x91, 0x78);

    private static final Color BANNER_SUCCESS_BG = new Color(0x1E, 0x3A, 0x29);
    private static final Color BANNER_SUCCESS_FG = new Color(0x85, 0xE8, 0x9D);
    private static final Color BANNER_SUCCESS_BORDER = new Color(0x2E, 0x6B, 0x40);
    private static final Color BANNER_ERROR_BG = new Color(0x3A, 0x1E, 0x1E);
    private static final Color BANNER_ERROR_FG = new Color(0xF8, 0x51, 0x49);
    private static final Color BANNER_ERROR_BORDER = new Color(0x6B, 0x2E, 0x2E);

    private static final String[] COLUMNAS =
            {"Ámbito", "Nombre", "Nombre TAC", "Categoría", "Tipo", "Offset", "Tamaño"};

    private final JLabel lblStatusBanner;
    private final DefaultTableModel tableModel;
    private final JTable table;

    public SimbolosPanel() {
        setLayout(new BorderLayout(0, 8));
        setBackground(COLOR_BG);

        lblStatusBanner = new JLabel("", SwingConstants.CENTER);
        lblStatusBanner.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStatusBanner.setOpaque(true);
        add(lblStatusBanner, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(COLUMNAS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setBackground(COLOR_BG);
        table.setForeground(COLOR_TEXT);
        table.setGridColor(COLOR_GRID);
        table.setRowHeight(26);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setSelectionBackground(COLOR_SELECTION);
        table.setSelectionForeground(Color.WHITE);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(true);

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.setBackground(COLOR_HEADER);
                label.setForeground(COLOR_TEXT);
                label.setFont(new Font("Segoe UI", Font.BOLD, 12));
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setOpaque(true);
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 1, COLOR_GRID),
                        new EmptyBorder(6, 8, 6, 8)
                ));
                return label;
            }
        });
        configurarRenderizadores();

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(COLOR_BG);
        scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_GRID, 1));
        add(scrollPane, BorderLayout.CENTER);

        limpiar();
    }

    /** Llena la tabla con el layout de memoria; los tamaños de frame se leen del TAC generado. */
    public void mostrar(Layout layout, ProgramaTAC tac) {
        tableModel.setRowCount(0);
        Map<String, Integer> framesFinales = tamanosDeFrame(tac);

        int bytesGlobales = 0;
        for (Layout.Variable variable : layout.globales()) {
            bytesGlobales = Math.max(bytesGlobales, variable.direccion().offset() + variable.direccion().tamano());
        }
        agregarFilas("global (región estática, " + bytesGlobales + " B)", layout.globales());

        for (Layout.Frame frame : layout.frames().values()) {
            int tamano = framesFinales.getOrDefault(frame.etiqueta(), frame.tamanoBase());
            agregarFilas("function " + frame.etiqueta() + " (frame " + tamano + " B)", frame.variables());
        }
        for (Layout.ClaseLayout clase : layout.clases().values()) {
            String herencia = clase.padre() == null ? "" : " : " + clase.padre();
            agregarFilas("class " + clase.nombre() + herencia + " (instancia " + clase.tamanoInstancia() + " B)",
                    clase.campos());
        }

        banner(String.format("[OK] %d global(es) · %d registro(s) de activación · %d clase(s)",
                layout.globales().size(), layout.frames().size(), layout.clases().size()),
                BANNER_SUCCESS_BG, BANNER_SUCCESS_FG, BANNER_SUCCESS_BORDER);
        lblStatusBanner.setToolTipText("Registro de activación: dirección de retorno (offset 0), enlace de control (8), "
                + "enlace de acceso (16), parámetros y locales desde el 24, temporales al final.");
    }

    public void mostrarSinDatos() {
        tableModel.setRowCount(0);
        banner(TacPanel.MENSAJE_SIN_TAC.replace("código intermedio", "la tabla de memoria"),
                BANNER_ERROR_BG, BANNER_ERROR_FG, BANNER_ERROR_BORDER);
    }

    public void limpiar() {
        tableModel.setRowCount(0);
        banner("Analice un archivo .cps sin errores para ver direcciones, frames y clases.",
                COLOR_HEADER, COLOR_TEXT, COLOR_GRID);
    }

    public JTable getTable() {
        return table;
    }

    private void agregarFilas(String ambito, List<Layout.Variable> variables) {
        if (variables.isEmpty()) {
            tableModel.addRow(new Object[]{ambito, "—", "—", "—", "—", "—", "—"});
            return;
        }
        for (Layout.Variable variable : variables) {
            Direccion direccion = variable.direccion();
            tableModel.addRow(new Object[]{
                    ambito,
                    variable.nombre(),
                    direccion.nombreTac(),
                    direccion.categoria().name(),
                    direccion.tipo().toString(),
                    direccion.offset(),
                    direccion.tamano()
            });
        }
    }

    /** {@code function f, N}: el tamaño final del frame incluye los temporales. */
    private static Map<String, Integer> tamanosDeFrame(ProgramaTAC tac) {
        Map<String, Integer> tamanos = new HashMap<>();
        for (Instruccion instruccion : tac.instrucciones()) {
            if (Instruccion.OP_FUNCION.equals(instruccion.op())) {
                tamanos.put(instruccion.a1(), Integer.parseInt(instruccion.a2()));
            }
        }
        return tamanos;
    }

    private void configurarRenderizadores() {
        DefaultTableCellRenderer ambitoRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                boolean repetido = row > 0 && value != null && value.equals(table.getValueAt(row - 1, column));
                setText(repetido ? "" : String.valueOf(value));
                if (!isSelected) {
                    c.setBackground(COLOR_BG);
                    c.setForeground(COLOR_AMBITO);
                }
                setFont(new Font("Consolas", Font.BOLD, 12));
                return c;
            }
        };

        DefaultTableCellRenderer monoRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(COLOR_BG);
                    c.setForeground(COLOR_TEXT);
                }
                setFont(new Font("Consolas", Font.PLAIN, 12));
                setHorizontalAlignment(column >= 5 ? SwingConstants.CENTER : SwingConstants.LEFT);
                return c;
            }
        };

        DefaultTableCellRenderer categoriaRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(COLOR_BG);
                    c.setForeground(switch (String.valueOf(value)) {
                        case "GLOBAL" -> COLOR_GLOBAL;
                        case "LOCAL" -> COLOR_LOCAL;
                        case "PARAM" -> COLOR_PARAM;
                        case "CAMPO" -> COLOR_CAMPO;
                        default -> COLOR_TEXT;
                    });
                }
                setFont(getFont().deriveFont(Font.BOLD));
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        };

        int[] anchos = {300, 120, 120, 90, 120, 70, 70};
        for (int i = 0; i < anchos.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
            table.getColumnModel().getColumn(i).setCellRenderer(monoRenderer);
        }
        table.getColumnModel().getColumn(0).setCellRenderer(ambitoRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(categoriaRenderer);
    }

    private void banner(String texto, Color fondo, Color frente, Color borde) {
        lblStatusBanner.setText(texto);
        lblStatusBanner.setBackground(fondo);
        lblStatusBanner.setForeground(frente);
        lblStatusBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borde, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));
    }
}
