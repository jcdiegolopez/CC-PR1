package com.lexsynanalyzer.gui;

import com.lexsynanalyzer.mips.ProgramaMips;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Pestaña "Código Ensamblador (MIPS)": muestra el {@code .asm} que se abre en MARS, permite compararlo con
 * la versión anterior a la mirilla, copiarlo y guardarlo. Con errores no hay ensamblador que mostrar.
 */
public class MipsPanel extends JPanel {

    public static final String MENSAJE_SIN_MIPS =
            "No se generó código ensamblador porque el programa tiene errores.";

    private static final Color COLOR_BG = new Color(0x1E, 0x1E, 0x1E);
    private static final Color COLOR_HEADER = new Color(0x25, 0x25, 0x26);
    private static final Color COLOR_TEXT = new Color(0xD4, 0xD4, 0xD4);
    private static final Color COLOR_TEXT_SECUNDARIO = new Color(0xA8, 0xA8, 0xA8);
    private static final Color COLOR_GRID = new Color(0x3C, 0x3C, 0x3C);
    private static final Color COLOR_LINE_NUMBERS = new Color(0x85, 0x85, 0x85);

    private static final Color COLOR_DIRECTIVA = new Color(0x56, 0x9C, 0xD6);
    private static final Color COLOR_ETIQUETA = new Color(0xDC, 0xDC, 0xAA);
    private static final Color COLOR_SALTO = new Color(0xC5, 0x86, 0xC0);
    private static final Color COLOR_COMENTARIO = new Color(0x6A, 0x99, 0x55);
    private static final Color COLOR_CADENA = new Color(0xCE, 0x91, 0x78);

    private static final Color COLOR_ACCENT = new Color(0x1B, 0x6E, 0xC2);
    private static final Color COLOR_ACCENT_HOVER = new Color(0x15, 0x58, 0x9C);
    private static final Color COLOR_ACCENT_BORDER = new Color(0x0F, 0x46, 0x80);
    private static final Color COLOR_SECUNDARIO = new Color(0x33, 0x33, 0x33);
    private static final Color COLOR_SECUNDARIO_HOVER = new Color(0x3E, 0x3E, 0x3E);
    private static final Color COLOR_DISABLED_BUTTON = new Color(0x2D, 0x2D, 0x2D);
    private static final Color COLOR_DISABLED_TEXT = new Color(0x9E, 0x9E, 0x9E);

    private static final Color BANNER_SUCCESS_BG = new Color(0x1E, 0x3A, 0x29);
    private static final Color BANNER_SUCCESS_FG = new Color(0x85, 0xE8, 0x9D);
    private static final Color BANNER_SUCCESS_BORDER = new Color(0x2E, 0x6B, 0x40);
    private static final Color BANNER_ERROR_BG = new Color(0x3A, 0x1E, 0x1E);
    private static final Color BANNER_ERROR_FG = new Color(0xF8, 0x51, 0x49);
    private static final Color BANNER_ERROR_BORDER = new Color(0x6B, 0x2E, 0x2E);

    private static final Font FUENTE_CODIGO = new Font("Consolas", Font.PLAIN, 14);
    private static final Font FUENTE_UI = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FUENTE_BOTON = new Font("Segoe UI", Font.BOLD, 12);

    private static final String TEXTO_VER_SIN_OPTIMIZAR = "Ver sin optimizar";
    private static final String TEXTO_VER_OPTIMIZADO = "Ver optimizado";

    private final JLabel lblStatusBanner;
    private final JLabel lblMirilla;
    private final JLabel lblAccion;
    private final JToggleButton btnSinOptimizar;
    private final JButton btnCopiar;
    private final JButton btnGuardar;
    private final JTextPane codigo;
    private final JTextArea numerosDeLinea;

    private String asmOptimizado = "";
    private String asmSinOptimizar = "";
    private String nombreSugerido = "programa";

    public MipsPanel() {
        setLayout(new BorderLayout(0, 8));
        setBackground(COLOR_BG);

        lblStatusBanner = new JLabel("", SwingConstants.CENTER);
        lblStatusBanner.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStatusBanner.setOpaque(true);

        lblMirilla = new JLabel(" ");
        lblMirilla.setFont(FUENTE_UI);
        lblMirilla.setForeground(COLOR_TEXT);

        lblAccion = new JLabel(" ");
        lblAccion.setFont(FUENTE_UI);
        lblAccion.setForeground(COLOR_TEXT_SECUNDARIO);

        btnSinOptimizar = new JToggleButton(TEXTO_VER_SIN_OPTIMIZAR);
        estilizar(btnSinOptimizar, COLOR_SECUNDARIO, COLOR_SECUNDARIO_HOVER, COLOR_GRID);
        btnSinOptimizar.setToolTipText("Alterna entre el ensamblador final y el que salió antes de la mirilla");
        btnSinOptimizar.addActionListener(e -> {
            btnSinOptimizar.setText(btnSinOptimizar.isSelected() ? TEXTO_VER_OPTIMIZADO : TEXTO_VER_SIN_OPTIMIZAR);
            btnSinOptimizar.setBackground(btnSinOptimizar.isSelected() ? COLOR_ACCENT : COLOR_SECUNDARIO);
            lblAccion.setText(" ");
            pintarCodigo();
        });

        btnCopiar = new JButton("Copiar");
        estilizar(btnCopiar, COLOR_SECUNDARIO, COLOR_SECUNDARIO_HOVER, COLOR_GRID);
        btnCopiar.setToolTipText("Copia al portapapeles el ensamblador que se está viendo");
        btnCopiar.addActionListener(e -> copiar());

        btnGuardar = new JButton("Guardar .asm");
        estilizar(btnGuardar, COLOR_ACCENT, COLOR_ACCENT_HOVER, COLOR_ACCENT_BORDER);
        btnGuardar.setToolTipText("Guarda el ensamblador que se está viendo para abrirlo en MARS");
        btnGuardar.addActionListener(e -> elegirArchivoYGuardar());

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        acciones.setOpaque(false);
        acciones.add(lblAccion);
        acciones.add(btnSinOptimizar);
        acciones.add(btnCopiar);
        acciones.add(btnGuardar);

        JPanel barra = new JPanel(new BorderLayout(12, 0));
        barra.setBackground(COLOR_HEADER);
        barra.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_GRID, 1),
                new EmptyBorder(6, 12, 6, 6)));
        barra.add(lblMirilla, BorderLayout.CENTER);
        barra.add(acciones, BorderLayout.EAST);

        JPanel cabecera = new JPanel(new BorderLayout(0, 8));
        cabecera.setOpaque(false);
        cabecera.add(lblStatusBanner, BorderLayout.NORTH);
        cabecera.add(barra, BorderLayout.SOUTH);
        add(cabecera, BorderLayout.NORTH);

        codigo = new JTextPane();
        codigo.setEditable(false);
        codigo.setBackground(COLOR_BG);
        codigo.setForeground(COLOR_TEXT);
        codigo.setCaretColor(Color.WHITE);
        codigo.setSelectionColor(new Color(0x26, 0x4F, 0x78));
        codigo.setSelectedTextColor(COLOR_TEXT);
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

    /**
     * @param optimizado     ensamblador final (después de la mirilla)
     * @param sinOptimizar   el mismo programa antes de la mirilla
     * @param mirilla        veces que se aplicó cada regla
     * @param nombreSugerido nombre base para el archivo, normalmente el del {@code .cps}
     */
    public void mostrarMips(ProgramaMips optimizado, ProgramaMips sinOptimizar, Map<String, Integer> mirilla,
                            String nombreSugerido) {
        asmOptimizado = optimizado.toString();
        asmSinOptimizar = sinOptimizar.toString();
        this.nombreSugerido = nombreSugerido == null || nombreSugerido.isBlank() ? "programa" : nombreSugerido;

        banner(String.format("[OK] Código ensamblador MIPS generado: %d instrucciones. Listo para abrir en MARS.",
                optimizado.cantidadInstrucciones()), BANNER_SUCCESS_BG, BANNER_SUCCESS_FG, BANNER_SUCCESS_BORDER);
        lblMirilla.setText(resumenMirilla(sinOptimizar.cantidadInstrucciones() - optimizado.cantidadInstrucciones(),
                mirilla));
        lblMirilla.setToolTipText(lblMirilla.getText());
        lblAccion.setText(" ");

        btnSinOptimizar.setSelected(false);
        btnSinOptimizar.setText(TEXTO_VER_SIN_OPTIMIZAR);
        habilitarAcciones(true);
        pintarCodigo();
    }

    /** El programa tiene errores: no hay ensamblador que mostrar. */
    public void mostrarSinMips() {
        vaciar();
        banner(MENSAJE_SIN_MIPS, BANNER_ERROR_BG, BANNER_ERROR_FG, BANNER_ERROR_BORDER);
        lblMirilla.setText("Corrija los errores de la pestaña de diagnósticos y vuelva a analizar.");
    }

    /** El programa es válido pero el backend no pudo traducirlo (por ejemplo, un closure). */
    public void mostrarFallo(String causa) {
        vaciar();
        banner("No se pudo generar código ensamblador para este programa.",
                BANNER_ERROR_BG, BANNER_ERROR_FG, BANNER_ERROR_BORDER);
        lblMirilla.setText("Causa: " + (causa == null ? "error interno del generador MIPS" : causa)
                + ". El TAC sí se generó.");
        lblMirilla.setToolTipText(lblMirilla.getText());
    }

    public void limpiar() {
        vaciar();
        banner("Analice un archivo .cps sin errores para ver su código ensamblador MIPS.",
                COLOR_HEADER, COLOR_TEXT, COLOR_GRID);
        lblMirilla.setText("El ensamblador se puede guardar como .asm y correr en MARS 4.5.");
    }

    /** Muestra la versión anterior a la mirilla ({@code true}) o la final ({@code false}). */
    public void setMostrarSinOptimizar(boolean sinOptimizar) {
        if (btnSinOptimizar.isSelected() != sinOptimizar) {
            btnSinOptimizar.doClick();
        }
    }

    /** Escribe en {@code archivo} el ensamblador que se está viendo, en UTF-8 (MARS lo lee así con las tildes). */
    public void guardarEn(Path archivo) throws IOException {
        Files.writeString(archivo, asmVisible(), StandardCharsets.UTF_8);
    }

    /** Texto mostrado, igual al {@code .asm}. */
    public String getTexto() {
        return codigo.getText();
    }

    public String getMensaje() {
        return lblStatusBanner.getText();
    }

    public String getResumenMirilla() {
        return lblMirilla.getText();
    }

    public boolean accionesHabilitadas() {
        return btnGuardar.isEnabled();
    }

    static String resumenMirilla(int eliminadas, Map<String, Integer> reglas) {
        if (reglas.isEmpty()) {
            return "Mirilla: no aplicó ninguna transformación.";
        }
        StringJoiner detalle = new StringJoiner(", ", " (", ")");
        reglas.forEach((regla, veces) -> detalle.add(regla + " ×" + veces));
        return String.format("Mirilla: %d instrucci%s eliminada%s%s", eliminadas,
                eliminadas == 1 ? "ón" : "ones", eliminadas == 1 ? "" : "s", detalle);
    }

    private String asmVisible() {
        return btnSinOptimizar.isSelected() ? asmSinOptimizar : asmOptimizado;
    }

    private void vaciar() {
        asmOptimizado = "";
        asmSinOptimizar = "";
        btnSinOptimizar.setSelected(false);
        btnSinOptimizar.setText(TEXTO_VER_SIN_OPTIMIZAR);
        btnSinOptimizar.setBackground(COLOR_SECUNDARIO);
        habilitarAcciones(false);
        lblAccion.setText(" ");
        lblMirilla.setToolTipText(null);
        codigo.setText("");
        numerosDeLinea.setText("");
    }

    private void habilitarAcciones(boolean habilitadas) {
        for (AbstractButton boton : new AbstractButton[]{btnSinOptimizar, btnCopiar, btnGuardar}) {
            boton.setEnabled(habilitadas);
            boton.setCursor(habilitadas ? new Cursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        }
        btnGuardar.setBackground(habilitadas ? COLOR_ACCENT : COLOR_DISABLED_BUTTON);
        btnGuardar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(habilitadas ? COLOR_ACCENT_BORDER : COLOR_GRID, 1),
                new EmptyBorder(5, 14, 5, 14)));
        btnCopiar.setBackground(habilitadas ? COLOR_SECUNDARIO : COLOR_DISABLED_BUTTON);
        btnSinOptimizar.setBackground(habilitadas && btnSinOptimizar.isSelected() ? COLOR_ACCENT
                : habilitadas ? COLOR_SECUNDARIO : COLOR_DISABLED_BUTTON);
    }

    private void copiar() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(asmVisible()), null);
        lblAccion.setText("Copiado al portapapeles");
    }

    private void elegirArchivoYGuardar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar código ensamblador MIPS");
        chooser.setFileFilter(new FileNameExtensionFilter("Ensamblador MIPS (*.asm)", "asm"));
        String sufijo = btnSinOptimizar.isSelected() ? "_sin_optimizar" : "";
        chooser.setSelectedFile(new File(nombreSugerido + sufijo + ".asm"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File archivo = chooser.getSelectedFile();
        if (!archivo.getName().toLowerCase().endsWith(".asm")) {
            archivo = new File(archivo.getParentFile(), archivo.getName() + ".asm");
        }
        if (archivo.exists() && JOptionPane.showConfirmDialog(this,
                "El archivo " + archivo.getName() + " ya existe. ¿Desea reemplazarlo?",
                "Reemplazar archivo", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            guardarEn(archivo.toPath());
            lblAccion.setText("Guardado: " + archivo.getName());
            lblAccion.setToolTipText(archivo.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No fue posible guardar el archivo:\n" + ex.getMessage()
                            + "\nRevise que la carpeta exista y tenga permiso de escritura.",
                    "Error al guardar", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------
    // Resaltado
    // ------------------------------------------------------------------

    private static final Set<String> SALTOS = Set.of("j", "jal", "jr", "jalr", "beq", "bne", "beqz", "bnez",
            "bltz", "bgez", "bgtz", "blez", "blt", "ble", "bgt", "bge", "syscall");

    private void pintarCodigo() {
        String asm = asmVisible();
        codigo.setText("");
        StyledDocument documento = codigo.getStyledDocument();
        StringBuilder numeros = new StringBuilder();
        String sinUltimoSalto = asm.endsWith("\n") ? asm.substring(0, asm.length() - 1) : asm;
        if (!sinUltimoSalto.isEmpty()) {
            int numero = 1;
            for (String linea : sinUltimoSalto.split("\n", -1)) {
                pintarLinea(documento, linea);
                agregar(documento, "\n", COLOR_TEXT, false);
                numeros.append(numero++).append('\n');
            }
        }
        numerosDeLinea.setText(numeros.toString());
        codigo.setCaretPosition(0);
    }

    /** Separa etiqueta, mnemónico u directiva, operandos (con strings) y comentario. */
    private static void pintarLinea(StyledDocument documento, String linea) {
        int comentario = inicioComentario(linea);
        String instruccion = comentario < 0 ? linea : linea.substring(0, comentario);

        int dosPuntos = finEtiqueta(instruccion);
        if (dosPuntos >= 0) {
            agregar(documento, instruccion.substring(0, dosPuntos + 1), COLOR_ETIQUETA, true);
            instruccion = instruccion.substring(dosPuntos + 1);
        }

        int inicio = 0;
        while (inicio < instruccion.length() && Character.isWhitespace(instruccion.charAt(inicio))) {
            inicio++;
        }
        int fin = inicio;
        while (fin < instruccion.length() && !Character.isWhitespace(instruccion.charAt(fin))) {
            fin++;
        }
        agregar(documento, instruccion.substring(0, inicio), COLOR_TEXT, false);
        String palabra = instruccion.substring(inicio, fin);
        Color colorPalabra = palabra.startsWith(".") ? COLOR_DIRECTIVA
                : SALTOS.contains(palabra) ? COLOR_SALTO : COLOR_TEXT;
        agregar(documento, palabra, colorPalabra, false);
        pintarOperandos(documento, instruccion.substring(fin));

        if (comentario >= 0) {
            agregar(documento, linea.substring(comentario), COLOR_COMENTARIO, false);
        }
    }

    private static void pintarOperandos(StyledDocument documento, String operandos) {
        int comilla = operandos.indexOf('"');
        if (comilla < 0) {
            agregar(documento, operandos, COLOR_TEXT, false);
            return;
        }
        agregar(documento, operandos.substring(0, comilla), COLOR_TEXT, false);
        agregar(documento, operandos.substring(comilla), COLOR_CADENA, false);
    }

    /** Posición del {@code #} que abre un comentario, ignorando los que están dentro de un string. */
    static int inicioComentario(String linea) {
        boolean enCadena = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '\\' && enCadena) {
                i++;
            } else if (c == '"') {
                enCadena = !enCadena;
            } else if (c == '#' && !enCadena) {
                return i;
            }
        }
        return -1;
    }

    /** Fin de una etiqueta al inicio de la línea ({@code main:}, {@code str0:}); -1 si no hay. */
    private static int finEtiqueta(String instruccion) {
        int i = 0;
        while (i < instruccion.length()) {
            char c = instruccion.charAt(i);
            if (c == ':') {
                return i > 0 ? i : -1;
            }
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '$')) {
                return -1;
            }
            i++;
        }
        return -1;
    }

    private static void agregar(StyledDocument documento, String texto, Color color, boolean negrita) {
        if (texto.isEmpty()) {
            return;
        }
        SimpleAttributeSet estilo = new SimpleAttributeSet();
        StyleConstants.setForeground(estilo, color);
        StyleConstants.setBold(estilo, negrita);
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
                new EmptyBorder(8, 12, 8, 12)));
    }

    // ------------------------------------------------------------------
    // Estilo de botones (mismo lenguaje que la barra del IDE)
    // ------------------------------------------------------------------

    /**
     * {@code BasicButtonUI} pinta el texto deshabilitado en relieve oscuro, ilegible sobre el tema oscuro;
     * aquí se pinta plano con un gris que mantiene contraste 4.5:1 sobre el fondo deshabilitado.
     */
    private static final class BotonUI extends javax.swing.plaf.basic.BasicButtonUI {
        @Override
        protected void paintText(Graphics g, AbstractButton boton, Rectangle zona, String texto) {
            if (boton.isEnabled()) {
                super.paintText(g, boton, zona, texto);
                return;
            }
            FontMetrics metricas = g.getFontMetrics(boton.getFont());
            g.setColor(COLOR_DISABLED_TEXT);
            g.drawString(texto, zona.x, zona.y + metricas.getAscent());
        }
    }

    private static void estilizar(AbstractButton boton, Color fondo, Color fondoHover, Color borde) {
        boton.setUI(new BotonUI());
        boton.setFont(FUENTE_BOTON);
        boton.setForeground(Color.WHITE);
        boton.setBackground(fondo);
        boton.setOpaque(true);
        boton.setFocusPainted(false);
        boton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borde, 1),
                new EmptyBorder(5, 14, 5, 14)));
        boton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                if (boton.isEnabled() && !boton.isSelected()) {
                    boton.setBackground(fondoHover);
                }
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                if (boton.isEnabled() && !boton.isSelected()) {
                    boton.setBackground(fondo);
                }
            }
        });
    }
}
