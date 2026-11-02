package com.lexsynanalyzer.mips;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Una línea de la sección {@code .text}: una instrucción MIPS, una etiqueta o un comentario suelto.
 *
 * <p>Se guarda estructurada (no como texto) para que la optimización por mirilla pueda preguntar
 * por el mnemónico y los operandos sin volver a parsear. En una instrucción, {@code comentario} es
 * opcional y se imprime al final de la línea; en una etiqueta {@code op} es el nombre de la
 * etiqueta y en un comentario suelto {@code op} queda en {@code null}.
 */
public record InstruccionMips(Tipo tipo, String op, List<String> operandos, String comentario) {

    public enum Tipo {
        INSTRUCCION,
        ETIQUETA,
        COMENTARIO
    }

    /** Saltos incondicionales: la instrucción siguiente solo se alcanza por una etiqueta. */
    public static final Set<String> SALTOS_INCONDICIONALES = Set.of("j", "jr", "b");

    /** Saltos condicionales; su destino es el último operando. */
    public static final Set<String> SALTOS_CONDICIONALES =
            Set.of("beq", "bne", "beqz", "bnez", "bltz", "blez", "bgtz", "bgez", "blt", "ble", "bgt", "bge");

    private static final String SANGRIA = "    ";

    public InstruccionMips {
        Objects.requireNonNull(tipo);
        operandos = List.copyOf(operandos);
        if (tipo != Tipo.COMENTARIO) {
            Objects.requireNonNull(op);
        }
        if (tipo == Tipo.COMENTARIO) {
            Objects.requireNonNull(comentario);
        }
    }

    // --- Fábricas ---

    public static InstruccionMips de(String op, String... operandos) {
        return new InstruccionMips(Tipo.INSTRUCCION, op, List.of(operandos), null);
    }

    public static InstruccionMips etiqueta(String nombre) {
        return new InstruccionMips(Tipo.ETIQUETA, nombre, List.of(), null);
    }

    public static InstruccionMips comentarioSuelto(String texto) {
        return new InstruccionMips(Tipo.COMENTARIO, null, List.of(), texto);
    }

    /** Copia de esta instrucción con un comentario al final de la línea. */
    public InstruccionMips conComentario(String texto) {
        return new InstruccionMips(tipo, op, operandos, texto);
    }

    // --- Consultas para la mirilla ---

    public boolean esInstruccion() {
        return tipo == Tipo.INSTRUCCION;
    }

    public boolean esEtiqueta() {
        return tipo == Tipo.ETIQUETA;
    }

    public boolean esSaltoIncondicional() {
        return esInstruccion() && SALTOS_INCONDICIONALES.contains(op);
    }

    public boolean esSaltoCondicional() {
        return esInstruccion() && SALTOS_CONDICIONALES.contains(op);
    }

    /** Etiqueta a la que salta {@code j}, {@code b} o un salto condicional; {@code null} si no salta a una etiqueta. */
    public String destinoSalto() {
        if (!esInstruccion() || operandos.isEmpty() || "jr".equals(op)) {
            return null;
        }
        return esSaltoIncondicional() || esSaltoCondicional() ? operandos.get(operandos.size() - 1) : null;
    }

    /** Sintaxis de MARS: etiquetas en la columna 0, instrucciones sangradas. */
    @Override
    public String toString() {
        return switch (tipo) {
            case ETIQUETA -> op + ":" + sufijoComentario();
            case COMENTARIO -> SANGRIA + "# " + comentario;
            case INSTRUCCION -> SANGRIA + op + (operandos.isEmpty() ? "" : " " + String.join(", ", operandos))
                    + sufijoComentario();
        };
    }

    private String sufijoComentario() {
        return comentario == null ? "" : "  # " + comentario;
    }
}
