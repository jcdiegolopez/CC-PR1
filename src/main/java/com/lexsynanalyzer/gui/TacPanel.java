package com.lexsynanalyzer.gui;

import com.lexsynanalyzer.tac.Instruccion;
import com.lexsynanalyzer.tac.ProgramaTAC;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;

/**
 * Pestaña "Código Intermedio (TAC)": muestra el programa de tres direcciones con colores por tipo de
 * línea (cabeceras de función, etiquetas y saltos). Si el programa tiene errores, lo indica.
 */
public class TacPanel extends JPanel {

    public static final String MENSAJE_SIN_TAC = "No se generó código intermedio porque el programa tiene errores.";

    private static final Color COLOR_BG = new Color(0x1E, 0x1E, 0x1E);
    private static final Color COLOR_HEADER = new Color(0x25, 0x25, 0x26);
    private static final Color COLOR_TEXT = new Color(0xD4, 0xD4, 0xD4);
    private static final Color COLOR_GRID = new Color(0x3C, 0x3C, 0x3C);
    private static final Color COLOR_LINE_NUMBERS = new Color(0x85, 0x85, 0x85);

    private static final Color COLOR_FUNCION = new Color(0x56, 0x9C, 0xD6);
    private static final Color COLOR_ETIQUETA = new Color(0xDC, 0xDC, 0xAA);
    private static final Color COLOR_SALTO = new Color(0xC5, 0x86, 0xC0);

    private static final Color BANNER_SUCCESS_BG = new Color(0x1E, 0x3A, 0x29);
    private static final Color BANNER_SUCCESS_FG = new Color(0x85, 0xE8, 0x9D);
    private static final Color BANNER_SUCCESS_BORDER = new Color(0x2E, 0x6B, 0x40);
    private static final Color BANNER_ERROR_BG = new Color(0x3A, 0x1E, 0x1E);
    private static final Color BANNER_ERROR_FG = new Color(0xF8, 0x51, 0x49);
    private static final Color BANNER_ERROR_BORDER = new Color(0x6B, 0x2E, 0x2E);

    private static final Font FUENTE_CODIGO = new Font("Consolas", Font.PLAIN, 14);

    private final JLabel lblStatusBanner;
    private final JTextPane codigo;
    private final JTextArea numerosDeLinea;

    public TacPanel() {
        setLayout(new BorderLayout(0, 8));
        setBackground(COLOR_BG);

        lblStatusBanner = new JLabel("", SwingConstants.CENTER);
        lblStatusBanner.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStatusBanner.setOpaque(true);
        add(lblStatusBanner, BorderLayout.NORTH);

        codigo = new JTextPane();
        codigo.setEditable(false);
        codigo.setBackground(COLOR_BG);
        codigo.setForeground(COLOR_TEXT);
        codigo.setCaretColor(Color.WHITE);
        codigo.setFont(FUENTE_CODIGO);
        codigo.setMargin(new Insets(6, 8, 6, 8));

        numerosDeLinea = new JTextArea();
        numerosDeLinea.setBackground(COLOR_HEADER);
        numerosDeLinea.setForeground(COLOR_LINE_NUMBERS);
        numerosDeLinea.setFont(FUENTE_CODIGO);
        numerosDeLinea.setEditable(false);
        numerosDeLinea.setFocusable(false);
        numerosDeLinea.setMargin(new Insets(6, 8, 6, 8));

        JScrollPane scrollPane = new JScrollPane(codigo);
        scrollPane.setRowHeaderView(numerosDeLinea);
        scrollPane.getViewport().setBackground(COLOR_BG);
        scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_GRID, 1));
        add(scrollPane, BorderLayout.CENTER);

        limpiar();
    }

    public void mostrarTac(ProgramaTAC tac) {
        long funciones = tac.instrucciones().stream()
                .filter(i -> Instruccion.OP_FUNCION.equals(i.op()))
                .count();
        banner(String.format("[OK] Código intermedio generado: %d instrucciones en %d función(es).",
                tac.tamano(), funciones), BANNER_SUCCESS_BG, BANNER_SUCCESS_FG, BANNER_SUCCESS_BORDER);

        codigo.setText("");
        StyledDocument documento = codigo.getStyledDocument();
        StringBuilder numeros = new StringBuilder();
        int linea = 1;
        for (Instruccion instruccion : tac.instrucciones()) {
            agregar(documento, instruccion.toString() + "\n", colorDe(instruccion));
            numeros.append(linea++).append('\n');
        }
        numerosDeLinea.setText(numeros.toString());
        codigo.setCaretPosition(0);
    }

    /** El programa tiene errores: no hay TAC que mostrar. */
    public void mostrarSinTac() {
        banner(MENSAJE_SIN_TAC, BANNER_ERROR_BG, BANNER_ERROR_FG, BANNER_ERROR_BORDER);
        codigo.setText("");
        numerosDeLinea.setText("");
    }

    public void limpiar() {
        banner("Analice un archivo .cps sin errores para ver su código de tres direcciones.",
                COLOR_HEADER, COLOR_TEXT, COLOR_GRID);
        codigo.setText("");
        numerosDeLinea.setText("");
    }

    /** Texto mostrado, una instrucción por línea. */
    public String getTexto() {
        return codigo.getText();
    }

    public String getMensaje() {
        return lblStatusBanner.getText();
    }

    private static Color colorDe(Instruccion instruccion) {
        return switch (instruccion.op()) {
            case Instruccion.OP_FUNCION, Instruccion.OP_FIN_FUNCION -> COLOR_FUNCION;
            case Instruccion.OP_ETIQUETA -> COLOR_ETIQUETA;
            case Instruccion.OP_SALTO, Instruccion.OP_SI, Instruccion.OP_SI_NO,
                 Instruccion.OP_TRY, Instruccion.OP_FIN_TRY, Instruccion.OP_CATCH -> COLOR_SALTO;
            default -> COLOR_TEXT;
        };
    }

    private static void agregar(StyledDocument documento, String texto, Color color) {
        SimpleAttributeSet estilo = new SimpleAttributeSet();
        StyleConstants.setForeground(estilo, color);
        StyleConstants.setBold(estilo, color == COLOR_FUNCION);
        try {
            documento.insertString(documento.getLength(), texto, estilo);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
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
