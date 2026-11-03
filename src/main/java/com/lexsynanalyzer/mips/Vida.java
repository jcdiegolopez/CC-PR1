package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Variables vivas y próximo uso de cada nombre en el cuerpo TAC de una función (Dragon Book §8.5 y
 * §9.2.5). Un nombre está <b>vivo</b> en un punto si algún camino desde ahí lo lee antes de volver
 * a escribirlo. {@link GetReg} lo usa para no guardar temporales muertos y para elegir qué registro
 * desalojar.
 *
 * <p>Primero se calcula qué está vivo al salir de cada bloque básico, iterando hasta punto fijo
 * ({@code vivasSalida(B) = ∪ vivasEntrada(sucesor)}, {@code vivasEntrada(B) = usos(B) ∪
 * (vivasSalida(B) − definiciones(B))}). Después se recorre cada bloque de abajo hacia arriba.
 *
 * <p>Los argumentos de una llamada se cuentan como leídos en el {@code call} (además de en su
 * {@code param}), porque el traductor de llamadas los carga de memoria al llegar al {@code call}.
 */
public final class Vida {

    /** Próximo uso de un nombre que no se vuelve a leer en el bloque. */
    public static final int SIN_USO = Integer.MAX_VALUE;

    private final List<Set<String>> vivasAntes = new ArrayList<>();
    private final List<Set<String>> vivasDespues = new ArrayList<>();
    private final List<Map<String, Integer>> proximoUso = new ArrayList<>();
    private final List<Set<String>> usosPorInstruccion = new ArrayList<>();

    private Vida(int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            vivasAntes.add(Set.of());
            vivasDespues.add(Set.of());
            proximoUso.add(Map.of());
        }
    }

    public static Vida analizar(List<Instruccion> cuerpo) {
        BloquesBasicos bloques = BloquesBasicos.de(cuerpo);
        List<Set<String>> usos = new ArrayList<>();
        List<Set<String>> definiciones = new ArrayList<>();
        for (int i = 0; i < cuerpo.size(); i++) {
            usos.add(usos(cuerpo, i));
            definiciones.add(definiciones(cuerpo.get(i)));
        }
        Vida vida = new Vida(cuerpo.size());
        vida.usosPorInstruccion.addAll(usos);
        List<Set<String>> salida = vivasAlSalir(bloques, usos, definiciones);
        for (BloquesBasicos.Bloque bloque : bloques.bloques()) {
            Set<String> vivas = new HashSet<>(salida.get(bloque.numero()));
            Map<String, Integer> proximo = new HashMap<>();
            for (int i = bloque.fin() - 1; i >= bloque.inicio(); i--) {
                vida.vivasDespues.set(i, Set.copyOf(vivas));
                vida.proximoUso.set(i, Map.copyOf(proximo));
                vivas.removeAll(definiciones.get(i));
                definiciones.get(i).forEach(proximo::remove);
                vivas.addAll(usos.get(i));
                for (String usado : usos.get(i)) {
                    proximo.put(usado, i);
                }
                vida.vivasAntes.set(i, Set.copyOf(vivas));
            }
        }
        return vida;
    }

    private static List<Set<String>> vivasAlSalir(BloquesBasicos bloques, List<Set<String>> usos,
                                                  List<Set<String>> definiciones) {
        int cantidad = bloques.bloques().size();
        List<Set<String>> entrada = new ArrayList<>();
        List<Set<String>> salida = new ArrayList<>();
        for (int b = 0; b < cantidad; b++) {
            entrada.add(new HashSet<>());
            salida.add(new HashSet<>());
        }
        boolean cambio = true;
        while (cambio) {
            cambio = false;
            for (int b = cantidad - 1; b >= 0; b--) {
                BloquesBasicos.Bloque bloque = bloques.bloques().get(b);
                Set<String> nuevaSalida = new HashSet<>();
                bloque.sucesores().forEach(s -> nuevaSalida.addAll(entrada.get(s)));
                Set<String> nuevaEntrada = new HashSet<>(nuevaSalida);
                for (int i = bloque.fin() - 1; i >= bloque.inicio(); i--) {
                    nuevaEntrada.removeAll(definiciones.get(i));
                    nuevaEntrada.addAll(usos.get(i));
                }
                if (!nuevaSalida.equals(salida.get(b)) || !nuevaEntrada.equals(entrada.get(b))) {
                    salida.set(b, nuevaSalida);
                    entrada.set(b, nuevaEntrada);
                    cambio = true;
                }
            }
        }
        return salida;
    }

    /** Nombres (no literales) que lee la instrucción {@code i}. */
    static Set<String> usos(List<Instruccion> cuerpo, int i) {
        Instruccion ins = cuerpo.get(i);
        Set<String> usos = new HashSet<>();
        switch (ins.op()) {
            case Instruccion.OP_ETIQUETA, Instruccion.OP_SALTO, Instruccion.OP_FUNCION,
                 Instruccion.OP_FIN_FUNCION, Instruccion.OP_TRY, Instruccion.OP_FIN_TRY,
                 Instruccion.OP_CATCH, Instruccion.OP_NUEVO_OBJETO -> {
                // No leen variables (new lleva el nombre de la clase y un tamaño).
            }
            case Instruccion.OP_SI, Instruccion.OP_SI_NO, Instruccion.OP_ASIGNAR, Instruccion.OP_NEGAR,
                 Instruccion.OP_NO, Instruccion.OP_PRINT, Instruccion.OP_PRINT_CADENA,
                 Instruccion.OP_PRINT_BOOLEANO, Instruccion.OP_PARAM, Instruccion.OP_RETORNO,
                 Instruccion.OP_NUEVO_ARREGLO, Instruccion.OP_LONGITUD, Instruccion.OP_LEER_CAMPO ->
                    agregarNombre(usos, ins.a1());
            case Instruccion.OP_ESCRIBIR_CAMPO -> {
                agregarNombre(usos, ins.a1());
                agregarNombre(usos, ins.res());
            }
            case Instruccion.OP_LLAMAR -> {
                for (int j = i - 1; j >= 0 && cuerpo.get(j).op().equals(Instruccion.OP_PARAM); j--) {
                    agregarNombre(usos, cuerpo.get(j).a1());
                }
            }
            default -> {
                // Binarias, strings, arreglos y cualquier instrucción nueva: se asume que lee todo.
                agregarNombre(usos, ins.a1());
                agregarNombre(usos, ins.a2());
                if (!escribeResultado(ins)) {
                    agregarNombre(usos, ins.res());
                }
            }
        }
        return usos;
    }

    /** Nombre que escribe la instrucción, si escribe alguno. */
    static Set<String> definiciones(Instruccion ins) {
        if (ins.op().equals(Instruccion.OP_CATCH)) {
            return nombre(ins.a1());
        }
        return escribeResultado(ins) ? nombre(ins.res()) : Set.of();
    }

    private static boolean escribeResultado(Instruccion ins) {
        return ins.res() != null && switch (ins.op()) {
            case Instruccion.OP_ASIGNAR, Instruccion.OP_NEGAR, Instruccion.OP_NO, Instruccion.OP_LLAMAR,
                 Instruccion.OP_NUEVO_ARREGLO, Instruccion.OP_LEER_INDICE, Instruccion.OP_LONGITUD,
                 Instruccion.OP_NUEVO_OBJETO, Instruccion.OP_LEER_CAMPO, Instruccion.OP_CONCAT,
                 Instruccion.OP_STREQ, Instruccion.OP_STRNE -> true;
            default -> Instruccion.OPERADORES_BINARIOS.contains(ins.op());
        };
    }

    private static Set<String> nombre(String operando) {
        return esNombre(operando) ? Set.of(operando) : Set.of();
    }

    private static void agregarNombre(Set<String> nombres, String operando) {
        if (esNombre(operando)) {
            nombres.add(operando);
        }
    }

    private static boolean esNombre(String operando) {
        return operando != null && !Operandos.esLiteral(operando);
    }

    /** ¿La instrucción {@code i} lee {@code nombre}? */
    public boolean usaEn(int i, String nombre) {
        return usosPorInstruccion.get(i).contains(nombre);
    }

    /** ¿{@code nombre} se lee en la instrucción {@code i} o después, antes de reescribirse? */
    public boolean vivaAntes(int i, String nombre) {
        return vivasAntes.get(i).contains(nombre);
    }

    /** ¿{@code nombre} se vuelve a leer después de la instrucción {@code i}? */
    public boolean vivaDespues(int i, String nombre) {
        return vivasDespues.get(i).contains(nombre);
    }

    public Set<String> vivasDespues(int i) {
        return Collections.unmodifiableSet(vivasDespues.get(i));
    }

    /**
     * Índice de la próxima instrucción del mismo bloque, después de {@code i}, que lee
     * {@code nombre}; {@link #SIN_USO} si no hay.
     */
    public int proximoUso(int i, String nombre) {
        return proximoUso.get(i).getOrDefault(nombre, SIN_USO);
    }
}
