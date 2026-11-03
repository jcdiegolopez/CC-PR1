package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;
import com.lexsynanalyzer.tac.ProgramaTAC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * {@link ResolvedorUbicaciones} y su propio {@link AsignadorRegistros}, que recibe el cuerpo
 * completo de la función. Al final se emiten las rutinas del runtime que el programa pidió.
 *
 * <p>Ya vienen registradas las expresiones ({@link TraductorExpr}) y las rutinas de strings
 * ({@link RuntimeCadenas}). {@code function}, {@code endfunc} y {@code __lanzar} son provisionales:
 * los reemplazan el marco de activación y el runtime de errores registrando los suyos.
 */
public final class GeneradorMIPS {

    public static final String MAIN = "main";

    /** Instrucciones que se explican solas en el {@code .asm}: no llevan el TAC como comentario. */
    private static final Set<String> SIN_COMENTARIO = Set.of(Instruccion.OP_FUNCION, Instruccion.OP_ETIQUETA);

    private final Function<String, ResolvedorUbicaciones> ubicaciones;
    private final FabricaRegistros fabricaRegistros;
    private final Map<String, Traductor> traductores = new HashMap<>();
    private final Map<String, Rutina> rutinas = new HashMap<>();

    /**
     * @param ubicaciones      memoria de cada función, por su etiqueta TAC
     * @param fabricaRegistros {@link FabricaRegistros#INGENUO} o {@link FabricaRegistros#GETREG}
     */
    public GeneradorMIPS(Function<String, ResolvedorUbicaciones> ubicaciones, FabricaRegistros fabricaRegistros) {
        this.ubicaciones = Objects.requireNonNull(ubicaciones);
        this.fabricaRegistros = Objects.requireNonNull(fabricaRegistros);
        registrar(Instruccion.OP_FUNCION, GeneradorMIPS::inicioFuncionProvisional);
        registrar(Instruccion.OP_FIN_FUNCION, GeneradorMIPS::finFuncionProvisional);
        registrar(Instruccion.OP_ETIQUETA, GeneradorMIPS::etiqueta);
        registrarRutina(LANZAR, GeneradorMIPS::lanzarProvisional);
        TraductorExpr.registrarEn(this);
        RuntimeCadenas.registrarEn(this);
    }

    /** Registra (o reemplaza) el traductor de {@code op}. */
    public GeneradorMIPS registrar(String op, Traductor traductor) {
        traductores.put(Objects.requireNonNull(op), Objects.requireNonNull(traductor));
        return this;
    }

    /** Registra (o reemplaza) la rutina del runtime {@code nombre}. */
    public GeneradorMIPS registrarRutina(String nombre, Rutina rutina) {
        rutinas.put(Objects.requireNonNull(nombre), Objects.requireNonNull(rutina));
        return this;
    }

    /** Etiqueta MIPS de una función TAC. El prefijo evita chocar con mnemónicos ({@code add}, {@code move}). */
    public static String etiquetaFuncion(String funcionTac) {
        return MAIN.equals(funcionTac) ? MAIN : "f_" + funcionTac;
    }

    /** Con TAC vacío (el programa tenía errores) devuelve un programa vacío. */
    public ProgramaMips generar(ProgramaTAC tac) {
        ProgramaMips programa = new ProgramaMips();
        for (List<Instruccion> cuerpo : funciones(tac.instrucciones())) {
            traducirFuncion(programa, cuerpo);
        }
        if (!programa.estaVacio()) {
            emitirRutinas(programa);
        }
        return programa;
    }

    /** Parte el TAC en funciones: cada una va de {@code function} a {@code endfunc}, inclusive. */
    private static List<List<Instruccion>> funciones(List<Instruccion> instrucciones) {
        List<List<Instruccion>> funciones = new ArrayList<>();
        List<Instruccion> actual = null;
        for (Instruccion instruccion : instrucciones) {
            if (instruccion.op().equals(Instruccion.OP_FUNCION)) {
                actual = new ArrayList<>();
                funciones.add(actual);
            }
            if (actual == null) {
                throw new IllegalStateException("Instrucción fuera de una función: " + instruccion);
            }
            actual.add(instruccion);
            if (instruccion.op().equals(Instruccion.OP_FIN_FUNCION)) {
                actual = null;
            }
        }
        return funciones;
    }

    private void traducirFuncion(ProgramaMips programa, List<Instruccion> cuerpo) {
        Instruccion funcion = cuerpo.get(0);
        String nombre = funcion.a1();
        AsignadorRegistros registros = fabricaRegistros.crear(programa, ubicaciones.apply(nombre), List.copyOf(cuerpo));
        ContextoFuncion contexto = new ContextoFuncion(programa, registros, nombre, Integer.parseInt(funcion.a2()));
        for (int i = 0; i < cuerpo.size(); i++) {
            registros.antesDeInstruccion(i);
            traducir(cuerpo.get(i), contexto);
            registros.siguienteInstruccion();
        }
    }

    private void traducir(Instruccion instruccion, ContextoFuncion contexto) {
        Traductor traductor = traductores.get(instruccion.op());
        if (traductor == null) {
            contexto.programa().emit(InstruccionMips.comentarioSuelto("PENDIENTE: " + instruccion));
            return;
        }
        if (!SIN_COMENTARIO.contains(instruccion.op())) {
            contexto.programa().emit(InstruccionMips.comentarioSuelto(instruccion.toString()));
        }
        traductor.traducir(instruccion, contexto);
    }

    /** Emite cada rutina pedida una vez; una rutina puede pedir otras mientras se emite. */
    private void emitirRutinas(ProgramaMips programa) {
        Set<String> emitidas = new HashSet<>();
        boolean hayNuevas = true;
        while (hayNuevas) {
            hayNuevas = false;
            for (String nombre : List.copyOf(programa.rutinasRequeridas())) {
                if (emitidas.add(nombre)) {
                    Rutina rutina = rutinas.get(nombre);
                    if (rutina == null) {
                        throw new IllegalStateException("Rutina del runtime sin implementar: " + nombre);
                    }
                    rutina.emitir(programa);
                    hayNuevas = true;
                }
            }
        }
    }

    /** Una etiqueta del TAC empieza un bloque básico: lo pendiente en registros se guarda antes. */
    private static void etiqueta(Instruccion instruccion, ContextoFuncion contexto) {
        contexto.registros().finDeBloque();
        contexto.programa().emit(InstruccionMips.etiqueta(instruccion.a1()));
    }

    // --- Provisionales: los reemplazan MarcoActivacion y RuntimeErrores (persona B) ---

    /** Rutina que recibe en {@code $a0} el mensaje de un error de ejecución. */
    public static final String LANZAR = "__lanzar";

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

    /** Sin manejadores de {@code catch}: imprime el error y termina con código 1. */
    private static void lanzarProvisional(ProgramaMips programa) {
        programa.emit(InstruccionMips.etiqueta(LANZAR));
        programa.emit(InstruccionMips.de("move", "$t8", "$a0"));
        programa.emit(InstruccionMips.de("la", "$a0", programa.cadena("Error en tiempo de ejecución: ")));
        programa.emit(InstruccionMips.de("li", "$v0", "4"));
        programa.emit(InstruccionMips.de("syscall"));
        programa.emit(InstruccionMips.de("move", "$a0", "$t8"));
        programa.emit(InstruccionMips.de("syscall"));
        programa.emit(InstruccionMips.de("li", "$a0", "10"));
        programa.emit(InstruccionMips.de("li", "$v0", "11"));
        programa.emit(InstruccionMips.de("syscall"));
        programa.emit(InstruccionMips.de("li", "$a0", "1"));
        programa.emit(InstruccionMips.de("li", "$v0", "17"));
        programa.emit(InstruccionMips.de("syscall"));
    }
}
