package com.lexsynanalyzer.gui;

import com.lexsynanalyzer.analyzer.AnalysisError;
import com.lexsynanalyzer.analyzer.TipoError;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.List;

public class ResultsTablePanel extends JPanel {

    private static final Color COLOR_BG = new Color(0x1E, 0x1E, 0x1E);
    private static final Color COLOR_HEADER = new Color(0x25, 0x25, 0x26);
    private static final Color COLOR_TEXT = new Color(0xD4, 0xD4, 0xD4);
    private static final Color COLOR_GRID = new Color(0x3C, 0x3C, 0x3C);
    private static final Color COLOR_SELECTION = new Color(0x04, 0x39, 0x5E);

    private static final Color BANNER_SUCCESS_BG = new Color(0x1E, 0x3A, 0x29);
    private static final Color BANNER_SUCCESS_FG = new Color(0x85, 0xE8, 0x9D);
    private static final Color BANNER_SUCCESS_BORDER = new Color(0x2E, 0x6B, 0x40);

    private static final Color BANNER_ERROR_BG = new Color(0x3A, 0x1E, 0x1E);
    private static final Color BANNER_ERROR_FG = new Color(0xF8, 0x51, 0x49);
    private static final Color BANNER_ERROR_BORDER = new Color(0x6B, 0x2E, 0x2E);

    private static final Color COLOR_LEXICO = new Color(0xCE, 0x91, 0x78);
    private static final Color COLOR_SINTACTICO = new Color(0xF4, 0x47, 0x47);
    private static final Color COLOR_SEMANTICO = new Color(0xD7, 0xBA, 0x7D);

    private final JLabel lblStatusBanner;
    private final DefaultTableModel tableModel;
    private final JTable table;

    public ResultsTablePanel() {
        setLayout(new BorderLayout(0, 8));
        setBackground(COLOR_BG);

        // Banner de estado
        lblStatusBanner = new JLabel("Seleccione un archivo .cps y haga clic en 'Analizar'.", SwingConstants.CENTER);
        lblStatusBanner.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStatusBanner.setOpaque(true);
        lblStatusBanner.setBackground(COLOR_HEADER);
        lblStatusBanner.setForeground(COLOR_TEXT);
        lblStatusBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_GRID, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));
        add(lblStatusBanner, BorderLayout.NORTH);

        // Modelo de tabla de errores
        String[] columnas = {"Tipo", "Línea", "Columna", "Símbolo / Token", "Descripción"};
        tableModel = new DefaultTableModel(columnas, 0) {
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

        // Encabezado de la tabla con renderer personalizado oscuro
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

        // Renderizador de celdas
        configurarRenderizador();

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(COLOR_BG);
        scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_GRID, 1));
        add(scrollPane, BorderLayout.CENTER);
    }

    private void configurarRenderizador() {
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(COLOR_BG);
                    c.setForeground(COLOR_TEXT);
                }
                setHorizontalAlignment(SwingConstants.CENTER);
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
                return c;
            }
        };

        DefaultTableCellRenderer textRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(COLOR_BG);
                    c.setForeground(COLOR_TEXT);
                }
                return c;
            }
        };

        DefaultTableCellRenderer tipoRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    if ("LÉXICO".equals(value)) {
                        c.setForeground(COLOR_LEXICO);
                    } else if ("SINTÁCTICO".equals(value)) {
                        c.setForeground(COLOR_SINTACTICO);
                    } else if ("SEMÁNTICO".equals(value)) {
                        c.setForeground(COLOR_SEMANTICO);
                    } else {
                        c.setForeground(COLOR_TEXT);
                    }
                    c.setBackground(COLOR_BG);
                }
                setFont(getFont().deriveFont(Font.BOLD));
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        };

        table.getColumnModel().getColumn(0).setCellRenderer(tipoRenderer);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);

        table.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);

        table.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(2).setPreferredWidth(60);

        table.getColumnModel().getColumn(3).setCellRenderer(monoRenderer);
        table.getColumnModel().getColumn(3).setPreferredWidth(140);

        table.getColumnModel().getColumn(4).setCellRenderer(textRenderer);
        table.getColumnModel().getColumn(4).setPreferredWidth(440);
    }

    public void mostrarExito() {
        tableModel.setRowCount(0);
        lblStatusBanner.setText("[OK] Archivo analizado correctamente. No se encontraron errores léxicos, sintácticos ni semánticos.");
        lblStatusBanner.setBackground(BANNER_SUCCESS_BG);
        lblStatusBanner.setForeground(BANNER_SUCCESS_FG);
        lblStatusBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BANNER_SUCCESS_BORDER, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));
    }

    public void mostrarErrores(List<AnalysisError> errores) {
        tableModel.setRowCount(0);
        for (AnalysisError err : errores) {
            tableModel.addRow(new Object[]{
                    tipoVisible(err.tipo()),
                    err.linea(),
                    err.columna(),
                    err.simbolo() == null || err.simbolo().isEmpty() ? "<desconocido>" : err.simbolo(),
                    err.descripcion()
            });
        }

        long lexCount = errores.stream().filter(e -> e.tipo() == TipoError.LEXICO).count();
        long synCount = errores.stream().filter(e -> e.tipo() == TipoError.SINTACTICO).count();
        long semCount = errores.stream().filter(e -> e.tipo() == TipoError.SEMANTICO).count();

        lblStatusBanner.setText(String.format(
                "[ERROR] Se encontraron %d error(es) en el archivo (%d léxico(s), %d sintáctico(s), %d semántico(s)).",
                errores.size(), lexCount, synCount, semCount
        ));
        lblStatusBanner.setBackground(BANNER_ERROR_BG);
        lblStatusBanner.setForeground(BANNER_ERROR_FG);
        lblStatusBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BANNER_ERROR_BORDER, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));
    }

    public void limpiar() {
        tableModel.setRowCount(0);
        lblStatusBanner.setText("Seleccione un archivo .cps y haga clic en 'Analizar'.");
        lblStatusBanner.setBackground(COLOR_HEADER);
        lblStatusBanner.setForeground(COLOR_TEXT);
        lblStatusBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_GRID, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));
    }

    public JTable getTable() {
        return table;
    }

    private String tipoVisible(TipoError tipo) {
        return switch (tipo) {
            case LEXICO -> "LÉXICO";
            case SINTACTICO -> "SINTÁCTICO";
            case SEMANTICO -> "SEMÁNTICO";
        };
    }
}
