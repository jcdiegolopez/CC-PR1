package com.lexsynanalyzer.mips;

import java.util.Objects;

/**
 * Asignador sin memoria entre instrucciones: cada operando se carga de nuevo y cada resultado se
 * guarda de inmediato. Genera más {@code lw}/{@code sw} de los necesarios, pero memoria siempre
 * tiene el valor correcto, así que no puede equivocarse en saltos, llamadas ni {@code catch}.
 *
 * <p>Sirve como referencia: todo programa debe dar la misma salida con este asignador y con
 * {@link GetReg}. Dentro de una instrucción TAC reparte {@code $t0}, {@code $t1}, … en orden.
 */
public final class RegistrosIngenuos implements AsignadorRegistros {

    private static final int REGISTROS_DISPONIBLES = 8;

    private final ProgramaMips programa;
    private final ResolvedorUbicaciones ubicaciones;
    private int siguiente;

    public RegistrosIngenuos(ProgramaMips programa, ResolvedorUbicaciones ubicaciones) {
        this.programa = Objects.requireNonNull(programa);
        this.ubicaciones = Objects.requireNonNull(ubicaciones);
    }

    @Override
    public String leer(String operando) {
        String registro = tomarRegistro();
        if (Operandos.esInmediato(operando)) {
            programa.emit(InstruccionMips.de("li", registro, Operandos.valorInmediato(operando)));
        } else if (Operandos.esCadena(operando)) {
            String etiqueta = programa.cadena(Operandos.contenidoCadena(operando));
            programa.emit(InstruccionMips.de("la", registro, etiqueta));
        } else {
            programa.emit(InstruccionMips.de("lw", registro, ubicaciones.direccion(operando)));
        }
        return registro;
    }

    @Override
    public String paraResultado(String destino) {
        return tomarRegistro();
    }

    @Override
    public void definido(String destino, String registro) {
        programa.emit(InstruccionMips.de("sw", registro, ubicaciones.direccion(destino)));
    }

    @Override
    public void copia(String destino, String origen) {
        definido(destino, leer(origen));
    }

    @Override
    public void siguienteInstruccion() {
        siguiente = 0;
    }

    /** No hace nada: todo resultado ya se guardó en {@link #definido}. */
    @Override
    public void finDeBloque() {
    }

    /** No hace nada: ningún valor vive en un registro entre instrucciones. */
    @Override
    public void antesDeLlamada() {
    }

    /** No hace nada: memoria siempre está al día. */
    @Override
    public void antesDePosibleExcepcion() {
    }

    private String tomarRegistro() {
        if (siguiente == REGISTROS_DISPONIBLES) {
            throw new IllegalStateException("Una instrucción TAC pidió más de " + REGISTROS_DISPONIBLES
                    + " registros; falta llamar a siguienteInstruccion()");
        }
        return "$t" + siguiente++;
    }
}
