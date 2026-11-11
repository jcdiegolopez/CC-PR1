package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.semantic.Layout;
import com.lexsynanalyzer.tac.Instruccion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Traduce los argumentos y las llamadas a funciones Compiscript. */
public final class TraductorLlamadas {

    private final Map<String, List<String>> argumentos = new HashMap<>();

    private TraductorLlamadas() {
    }

    static void registrarEn(GeneradorMIPS generador) {
        TraductorLlamadas traductor = new TraductorLlamadas();
        generador.registrar(Instruccion.OP_PARAM, traductor::parametro);
        generador.registrar(Instruccion.OP_LLAMAR, traductor::llamar);
    }

    private void parametro(Instruccion instruccion, ContextoFuncion contexto) {
        String operando = instruccion.a1();
        if (!Operandos.esLiteral(operando)) {
            String registro = contexto.registros().leer(operando);
            contexto.programa().emit(InstruccionMips.de("sw", registro, contexto.ubicaciones().direccion(operando)));
        }
        argumentos.computeIfAbsent(contexto.funcion(), ignorado -> new ArrayList<>()).add(operando);
    }

    private void llamar(Instruccion instruccion, ContextoFuncion contexto) {
        String funcion = instruccion.a1();
        List<String> valores = argumentos.remove(contexto.funcion());
        if (valores == null) {
            valores = List.of();
        }
        int cantidad = Integer.parseInt(instruccion.a2());
        if (valores.size() != cantidad) {
            throw new IllegalStateException("La llamada a " + funcion + " declara " + cantidad
                    + " parámetros, pero recibió " + valores.size());
        }
        Layout layout = contexto.layout();
        if (layout == null) {
            throw new IllegalStateException("Se necesita Layout para traducir la llamada a " + funcion);
        }
        Integer tamanoFrame = contexto.tamanosFrames().get(funcion);
        if (tamanoFrame == null) {
            throw new IllegalStateException("No existe un frame TAC para la función " + funcion);
        }
        List<Integer> offsets = Ubicaciones.offsetsParametros(layout, funcion);
        if (offsets.size() != cantidad) {
            throw new IllegalStateException("El Layout de " + funcion + " tiene " + offsets.size()
                    + " parámetros, pero la llamada transmite " + cantidad);
        }

        contexto.registros().antesDeLlamada();
        for (int i = 0; i < cantidad; i++) {
            cargar(contexto.programa(), contexto.ubicaciones(), valores.get(i));
            contexto.programa().emit(InstruccionMips.de("sw", "$t8",
                    (offsets.get(i) - tamanoFrame) + "($sp)"));
        }
        contexto.programa().emit(InstruccionMips.de("jal", GeneradorMIPS.etiquetaFuncion(funcion)));
        if (instruccion.res() != null) {
            String resultado = contexto.registros().paraResultado(instruccion.res());
            contexto.programa().emit(InstruccionMips.de("move", resultado, "$v0"));
            contexto.registros().definido(instruccion.res(), resultado);
        }
    }

    private static void cargar(ProgramaMips programa, ResolvedorUbicaciones ubicaciones, String operando) {
        if (Operandos.esInmediato(operando)) {
            programa.emit(InstruccionMips.de("li", "$t8", Operandos.valorInmediato(operando)));
        } else if (Operandos.esCadena(operando)) {
            programa.emit(InstruccionMips.de("la", "$t8", programa.cadena(Operandos.contenidoCadena(operando))));
        } else {
            programa.emit(InstruccionMips.de("lw", "$t8", ubicaciones.direccion(operando)));
        }
    }
}
