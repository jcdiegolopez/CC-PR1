package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Divide el cuerpo TAC de una función en bloques básicos: secuencias que se ejecutan completas, de
 * la primera a la última instrucción, sin saltos hacia adentro ni hacia afuera a mitad del bloque.
 *
 * <p>Líderes (primera instrucción de un bloque), como en el Dragon Book §8.4:
 * <ol>
 *   <li>la primera instrucción;</li>
 *   <li>toda etiqueta (alguien puede saltar ahí);</li>
 *   <li>toda instrucción que sigue a un salto ({@code goto}, {@code if}, {@code ifFalse}), a un
 *       {@code call} o a un {@code return}.</li>
 * </ol>
 */
public final class BloquesBasicos {

    /** Instrucciones {@code [inicio, fin)} del cuerpo y los bloques a los que puede pasar el control. */
    public record Bloque(int numero, int inicio, int fin, List<Integer> sucesores) {
        public Bloque {
            sucesores = List.copyOf(sucesores);
        }
    }

    private static final Set<String> TERMINAN_BLOQUE = Set.of(Instruccion.OP_SALTO, Instruccion.OP_SI,
            Instruccion.OP_SI_NO, Instruccion.OP_LLAMAR, Instruccion.OP_RETORNO);

    private final List<Bloque> bloques;
    private final int[] bloqueDeInstruccion;

    private BloquesBasicos(List<Bloque> bloques, int cantidadInstrucciones) {
        this.bloques = List.copyOf(bloques);
        this.bloqueDeInstruccion = new int[cantidadInstrucciones];
        for (Bloque bloque : bloques) {
            for (int i = bloque.inicio(); i < bloque.fin(); i++) {
                bloqueDeInstruccion[i] = bloque.numero();
            }
        }
    }

    public static BloquesBasicos de(List<Instruccion> cuerpo) {
        List<Integer> lideres = lideres(cuerpo);
        Map<String, Integer> bloqueDeEtiqueta = new HashMap<>();
        for (int b = 0; b < lideres.size(); b++) {
            Instruccion primera = cuerpo.get(lideres.get(b));
            if (primera.esEtiqueta()) {
                bloqueDeEtiqueta.put(primera.a1(), b);
            }
        }
        List<Bloque> bloques = new ArrayList<>();
        for (int b = 0; b < lideres.size(); b++) {
            int inicio = lideres.get(b);
            int fin = b + 1 < lideres.size() ? lideres.get(b + 1) : cuerpo.size();
            Instruccion ultima = cuerpo.get(fin - 1);
            bloques.add(new Bloque(b, inicio, fin, sucesores(ultima, b, lideres.size(), bloqueDeEtiqueta)));
        }
        return new BloquesBasicos(bloques, cuerpo.size());
    }

    private static List<Integer> lideres(List<Instruccion> cuerpo) {
        List<Integer> lideres = new ArrayList<>();
        for (int i = 0; i < cuerpo.size(); i++) {
            boolean despuesDeSalto = i > 0 && TERMINAN_BLOQUE.contains(cuerpo.get(i - 1).op());
            if (i == 0 || cuerpo.get(i).esEtiqueta() || despuesDeSalto) {
                lideres.add(i);
            }
        }
        return lideres;
    }

    private static List<Integer> sucesores(Instruccion ultima, int bloque, int cantidad,
                                           Map<String, Integer> bloqueDeEtiqueta) {
        List<Integer> sucesores = new ArrayList<>();
        switch (ultima.op()) {
            case Instruccion.OP_SALTO -> sucesores.add(destino(ultima.a1(), bloqueDeEtiqueta));
            case Instruccion.OP_SI, Instruccion.OP_SI_NO -> {
                sucesores.add(destino(ultima.a2(), bloqueDeEtiqueta));
                agregarSiguiente(sucesores, bloque, cantidad);
            }
            case Instruccion.OP_RETORNO, Instruccion.OP_FIN_FUNCION -> {
                // Sale de la función: no hay sucesores dentro de ella.
            }
            default -> agregarSiguiente(sucesores, bloque, cantidad);
        }
        return sucesores;
    }

    private static void agregarSiguiente(List<Integer> sucesores, int bloque, int cantidad) {
        if (bloque + 1 < cantidad && !sucesores.contains(bloque + 1)) {
            sucesores.add(bloque + 1);
        }
    }

    private static int destino(String etiqueta, Map<String, Integer> bloqueDeEtiqueta) {
        Integer destino = bloqueDeEtiqueta.get(etiqueta);
        if (destino == null) {
            throw new IllegalArgumentException("Salto a una etiqueta que no está en la función: " + etiqueta);
        }
        return destino;
    }

    public List<Bloque> bloques() {
        return bloques;
    }

    public Bloque bloqueDe(int instruccion) {
        return bloques.get(bloqueDeInstruccion[instruccion]);
    }
}
