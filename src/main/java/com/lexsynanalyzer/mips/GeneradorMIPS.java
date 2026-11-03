package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;
import com.lexsynanalyzer.tac.ProgramaTAC;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Traduce un {@link ProgramaTAC} a MIPS para MARS. Solo lee el TAC: nunca mira el árbol sintáctico,
 * así el ensamblador sale del código intermedio como pide el enunciado.
 *
 * <p>No contiene lógica de construcciones: recorre las instrucciones y delega cada una en el
 * {@link Traductor} registrado para su {@code op}. Antes de cada instrucción emite el TAC original
 * como comentario, para poder seguir qué MIPS salió de qué línea. Una instrucción sin traductor
 * queda como comentario {@code PENDIENTE}.
 *
 * <p>Cada {@code function f, N} abre un {@link ContextoFuncion} nuevo, con su propio
 * {@link ResolvedorUbicaciones} y su propio {@link AsignadorRegistros}.
 */
public final class GeneradorMIPS {

    public static final String MAIN = "main";

    /** Instrucciones que se explican solas en el {@code .asm}: no llevan el TAC como comentario. */
    private static final Set<String> SIN_COMENTARIO = Set.of(Instruccion.OP_FUNCION, Instruccion.OP_ETIQUETA);

    private final Function<String, ResolvedorUbicaciones> ubicaciones;
    private final BiFunction<ProgramaMips, ResolvedorUbicaciones, AsignadorRegistros> fabricaRegistros;
    private final Map<String, Traductor> traductores = new HashMap<>();

    /**
     * @param ubicaciones      memoria de cada función, por su etiqueta TAC
     * @param fabricaRegistros asignador a usar: {@code RegistrosIngenuos::new} o {@code GetReg::new}
     */
    public GeneradorMIPS(Function<String, ResolvedorUbicaciones> ubicaciones,
                         BiFunction<ProgramaMips, ResolvedorUbicaciones, AsignadorRegistros> fabricaRegistros) {
        this.ubicaciones = Objects.requireNonNull(ubicaciones);
        this.fabricaRegistros = Objects.requireNonNull(fabricaRegistros);
        registrar(Instruccion.OP_FUNCION, GeneradorMIPS::inicioFuncionProvisional);
        registrar(Instruccion.OP_FIN_FUNCION, GeneradorMIPS::finFuncionProvisional);
        registrar(Instruccion.OP_ETIQUETA, GeneradorMIPS::etiqueta);
    }

    /** Registra (o reemplaza) el traductor de {@code op}. */
    public GeneradorMIPS registrar(String op, Traductor traductor) {
        traductores.put(Objects.requireNonNull(op), Objects.requireNonNull(traductor));
        return this;
    }

    /** Etiqueta MIPS de una función TAC. El prefijo evita chocar con mnemónicos ({@code add}, {@code move}). */
    public static String etiquetaFuncion(String funcionTac) {
        return MAIN.equals(funcionTac) ? MAIN : "f_" + funcionTac;
    }

    /** Con TAC vacío (el programa tenía errores) devuelve un programa vacío. */
    public ProgramaMips generar(ProgramaTAC tac) {
        ProgramaMips programa = new ProgramaMips();
        ContextoFuncion contexto = null;
        for (Instruccion instruccion : tac.instrucciones()) {
            if (instruccion.op().equals(Instruccion.OP_FUNCION)) {
                contexto = abrirFuncion(programa, instruccion);
            }
            if (contexto == null) {
                throw new IllegalStateException("Instrucción fuera de una función: " + instruccion);
            }
            traducir(instruccion, contexto);
            if (instruccion.op().equals(Instruccion.OP_FIN_FUNCION)) {
                contexto = null;
            }
        }
        return programa;
    }

    private ContextoFuncion abrirFuncion(ProgramaMips programa, Instruccion funcion) {
        String nombre = funcion.a1();
        AsignadorRegistros registros = fabricaRegistros.apply(programa, ubicaciones.apply(nombre));
        return new ContextoFuncion(programa, registros, nombre, Integer.parseInt(funcion.a2()));
    }

    private void traducir(Instruccion instruccion, ContextoFuncion contexto) {
        Traductor traductor = traductores.get(instruccion.op());
        if (traductor == null) {
            contexto.programa().emit(InstruccionMips.comentarioSuelto("PENDIENTE: " + instruccion));
        } else {
            if (!SIN_COMENTARIO.contains(instruccion.op())) {
                contexto.programa().emit(InstruccionMips.comentarioSuelto(instruccion.toString()));
            }
            traductor.traducir(instruccion, contexto);
        }
        contexto.registros().siguienteInstruccion();
    }

    /** Una etiqueta del TAC empieza un bloque básico: lo pendiente en registros se guarda antes. */
    private static void etiqueta(Instruccion instruccion, ContextoFuncion contexto) {
        contexto.registros().finDeBloque();
        contexto.programa().emit(InstruccionMips.etiqueta(instruccion.a1()));
    }

    // --- Provisionales: los reemplaza MarcoActivacion (persona B) con prólogo y epílogo reales ---

    private static void inicioFuncionProvisional(Instruccion instruccion, ContextoFuncion contexto) {
        contexto.programa().emit(InstruccionMips.etiqueta(etiquetaFuncion(contexto.funcion())));
    }

    private static void finFuncionProvisional(Instruccion instruccion, ContextoFuncion contexto) {
        contexto.registros().finDeBloque();
        if (contexto.esMain()) {
            contexto.programa().emit(InstruccionMips.de("li", "$v0", "10"));
            contexto.programa().emit(InstruccionMips.de("syscall"));
        } else {
            contexto.programa().emit(InstruccionMips.de("jr", "$ra"));
        }
    }
}
