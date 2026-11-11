package com.lexsynanalyzer.mips;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulador mínimo del subconjunto de MIPS que emite el generador, <b>solo para tests</b>. Permite
 * comprobar la salida de los programas en cada {@code mvn test} sin depender de MARS. No reemplaza
 * la prueba en MARS: es una red de seguridad rápida.
 *
 * <p>Lee el texto del {@code .asm} (lo mismo que leería MARS): {@code .data} con {@code .asciiz},
 * {@code .word} y {@code .space}; {@code .text} con etiquetas e instrucciones. Memoria little-endian,
 * {@code lw}/{@code sw} deben estar alineados. Syscalls 1, 4, 9, 10, 11 y 17.
 */
final class SimuladorMips {

    record Resultado(String salida, int codigoSalida, long pasos) {
    }

    private static final int BASE_DATOS = 0x10010000;
    private static final int BASE_HEAP = 0x10040000;
    private static final int LIMITE_PASOS = 5_000_000;

    private final Map<String, Integer> registros = new HashMap<>();
    private final Map<Integer, Byte> memoria = new HashMap<>();
    private final Map<String, Integer> etiquetasDatos = new HashMap<>();
    private final Map<String, Integer> etiquetasTexto = new HashMap<>();
    private final List<String[]> texto = new ArrayList<>();
    private final ByteArrayOutputStream salida = new ByteArrayOutputStream();
    private int hi;
    private int lo;
    private int heap = BASE_HEAP;

    static Resultado ejecutar(String asm) {
        return new SimuladorMips(asm).correr();
    }

    private SimuladorMips(String asm) {
        cargar(asm);
        for (String r : List.of("$zero", "$at", "$v0", "$v1", "$a0", "$a1", "$a2", "$a3", "$t0", "$t1", "$t2",
                "$t3", "$t4", "$t5", "$t6", "$t7", "$t8", "$t9", "$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6",
                "$s7", "$k0", "$k1", "$gp", "$sp", "$fp", "$ra")) {
            registros.put(r, 0);
        }
        registros.put("$sp", 0x7fffeffc);
        registros.put("$gp", 0x10008000);
    }

    // --- Carga del .asm ---

    private void cargar(String asm) {
        boolean enDatos = false;
        int cursorDatos = BASE_DATOS;
        for (String crudo : asm.split("\n")) {
            String linea = sinComentario(crudo).strip();
            if (linea.isEmpty() || linea.startsWith(".globl")) {
                continue;
            }
            if (linea.equals(".data") || linea.equals(".text")) {
                enDatos = linea.equals(".data");
                continue;
            }
            if (enDatos) {
                cursorDatos = cargarDato(linea, cursorDatos);
            } else if (linea.endsWith(":")) {
                etiquetasTexto.put(linea.substring(0, linea.length() - 1), texto.size());
            } else {
                int espacio = linea.indexOf(' ');
                String op = espacio < 0 ? linea : linea.substring(0, espacio);
                String resto = espacio < 0 ? "" : linea.substring(espacio + 1).strip();
                List<String> partes = new ArrayList<>();
                partes.add(op);
                if (!resto.isEmpty()) {
                    for (String operando : resto.split(",")) {
                        partes.add(operando.strip());
                    }
                }
                texto.add(partes.toArray(String[]::new));
            }
        }
    }

    private int cargarDato(String linea, int cursor) {
        int dosPuntos = linea.indexOf(':');
        String etiqueta = linea.substring(0, dosPuntos).strip();
        String directiva = linea.substring(dosPuntos + 1).strip();
        if (directiva.startsWith(".asciiz")) {
            etiquetasDatos.put(etiqueta, cursor);
            for (byte b : leerCadena(directiva.substring(".asciiz".length()).strip())) {
                memoria.put(cursor++, b);
            }
            memoria.put(cursor++, (byte) 0);
            return cursor;
        }
        cursor = (cursor + 3) & ~3;
        etiquetasDatos.put(etiqueta, cursor);
        if (directiva.startsWith(".word")) {
            for (String valor : directiva.substring(".word".length()).split(",")) {
                escribirPalabra(cursor, Integer.decode(valor.strip()));
                cursor += 4;
            }
            return cursor;
        }
        if (directiva.startsWith(".space")) {
            return cursor + Integer.parseInt(directiva.substring(".space".length()).strip());
        }
        throw new IllegalArgumentException("Directiva no soportada: " + linea);
    }

    private static byte[] leerCadena(String literal) {
        if (!literal.startsWith("\"") || !literal.endsWith("\"")) {
            throw new IllegalArgumentException("Literal .asciiz mal formado: " + literal);
        }
        StringBuilder texto = new StringBuilder();
        for (int i = 1; i < literal.length() - 1; i++) {
            char c = literal.charAt(i);
            if (c == '\\') {
                char siguiente = literal.charAt(++i);
                texto.append(switch (siguiente) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case '0' -> '\0';
                    default -> siguiente;
                });
            } else if (c == '"') {
                throw new IllegalArgumentException("Comilla sin escapar en .asciiz: " + literal);
            } else {
                texto.append(c);
            }
        }
        return texto.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String sinComentario(String linea) {
        boolean enCadena = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '\\' && enCadena) {
                i++;
            } else if (c == '"') {
                enCadena = !enCadena;
            } else if (c == '#' && !enCadena) {
                return linea.substring(0, i);
            }
        }
        return linea;
    }

    // --- Ejecución ---

    private Resultado correr() {
        Integer inicio = etiquetasTexto.get("main");
        if (inicio == null) {
            throw new IllegalStateException("No hay etiqueta main");
        }
        int pc = inicio;
        long pasos = 0;
        while (true) {
            if (pc < 0 || pc >= texto.size()) {
                throw new IllegalStateException("La ejecución se salió de .text (pc=" + pc + ")");
            }
            if (++pasos > LIMITE_PASOS) {
                throw new IllegalStateException("Límite de pasos: ¿ciclo infinito?");
            }
            String[] ins = texto.get(pc);
            int siguiente = pc + 1;
            switch (ins[0]) {
                case "li" -> poner(ins[1], Integer.decode(ins[2]));
                case "la" -> poner(ins[1], direccionEtiqueta(ins[2]));
                case "move" -> poner(ins[1], valor(ins[2]));
                case "lw" -> poner(ins[1], leerPalabra(direccion(ins[2])));
                case "sw" -> escribirPalabra(direccion(ins[2]), valor(ins[1]));
                case "lb" -> poner(ins[1], leerByte(direccion(ins[2])));
                case "sb" -> memoria.put(direccion(ins[2]), (byte) valor(ins[1]));
                case "addu", "add", "addiu", "addi" -> poner(ins[1], valor(ins[2]) + valor(ins[3]));
                case "subu", "sub" -> poner(ins[1], valor(ins[2]) - valor(ins[3]));
                case "mul" -> poner(ins[1], valor(ins[2]) * valor(ins[3]));
                case "neg" -> poner(ins[1], -valor(ins[2]));
                case "div" -> dividir(ins);
                case "mflo" -> poner(ins[1], lo);
                case "mfhi" -> poner(ins[1], hi);
                case "slt", "slti" -> poner(ins[1], valor(ins[2]) < valor(ins[3]) ? 1 : 0);
                case "sle" -> poner(ins[1], valor(ins[2]) <= valor(ins[3]) ? 1 : 0);
                case "sgt" -> poner(ins[1], valor(ins[2]) > valor(ins[3]) ? 1 : 0);
                case "sge" -> poner(ins[1], valor(ins[2]) >= valor(ins[3]) ? 1 : 0);
                case "seq" -> poner(ins[1], valor(ins[2]) == valor(ins[3]) ? 1 : 0);
                case "sne" -> poner(ins[1], valor(ins[2]) != valor(ins[3]) ? 1 : 0);
                case "xori", "xor" -> poner(ins[1], valor(ins[2]) ^ valor(ins[3]));
                case "andi", "and" -> poner(ins[1], valor(ins[2]) & valor(ins[3]));
                case "ori", "or" -> poner(ins[1], valor(ins[2]) | valor(ins[3]));
                case "sll" -> poner(ins[1], valor(ins[2]) << valor(ins[3]));
                case "srl" -> poner(ins[1], valor(ins[2]) >>> valor(ins[3]));
                case "sra" -> poner(ins[1], valor(ins[2]) >> valor(ins[3]));
                case "j" -> siguiente = destino(ins[1]);
                case "jal" -> {
                    poner("$ra", pc + 1);
                    siguiente = destino(ins[1]);
                }
                case "jr" -> siguiente = valor(ins[1]);
                case "beq" -> siguiente = valor(ins[1]) == valor(ins[2]) ? destino(ins[3]) : siguiente;
                case "bne" -> siguiente = valor(ins[1]) != valor(ins[2]) ? destino(ins[3]) : siguiente;
                case "blt" -> siguiente = valor(ins[1]) < valor(ins[2]) ? destino(ins[3]) : siguiente;
                case "ble" -> siguiente = valor(ins[1]) <= valor(ins[2]) ? destino(ins[3]) : siguiente;
                case "bgt" -> siguiente = valor(ins[1]) > valor(ins[2]) ? destino(ins[3]) : siguiente;
                case "bge" -> siguiente = valor(ins[1]) >= valor(ins[2]) ? destino(ins[3]) : siguiente;
                case "beqz" -> siguiente = valor(ins[1]) == 0 ? destino(ins[2]) : siguiente;
                case "bnez" -> siguiente = valor(ins[1]) != 0 ? destino(ins[2]) : siguiente;
                case "bltz" -> siguiente = valor(ins[1]) < 0 ? destino(ins[2]) : siguiente;
                case "bgez" -> siguiente = valor(ins[1]) >= 0 ? destino(ins[2]) : siguiente;
                case "nop" -> {
                }
                case "syscall" -> {
                    Integer codigo = syscall();
                    if (codigo != null) {
                        return new Resultado(salida.toString(StandardCharsets.UTF_8), codigo, pasos);
                    }
                }
                default -> throw new IllegalStateException("Instrucción no soportada: " + String.join(" ", ins));
            }
            pc = siguiente;
        }
    }

    private void dividir(String[] ins) {
        if (ins.length == 3) {
            int divisor = valor(ins[2]);
            if (divisor == 0) {
                throw new IllegalStateException("div entre cero sin chequeo previo");
            }
            lo = valor(ins[1]) / divisor;
            hi = valor(ins[1]) % divisor;
        } else {
            poner(ins[1], valor(ins[2]) / valor(ins[3]));
        }
    }

    /** Devuelve el código de salida si el programa terminó. */
    private Integer syscall() {
        int a0 = registros.get("$a0");
        switch (registros.get("$v0")) {
            case 1 -> escribir(String.valueOf(a0));
            case 4 -> {
                for (int d = a0; leerByte(d) != 0; d++) {
                    salida.write(leerByte(d));
                }
            }
            case 9 -> {
                registros.put("$v0", heap);
                heap += (a0 + 3) & ~3;
            }
            case 10 -> {
                return 0;
            }
            case 11 -> salida.write(a0 & 0xFF);
            case 17 -> {
                return a0;
            }
            default -> throw new IllegalStateException("Syscall no soportada: " + registros.get("$v0"));
        }
        return null;
    }

    private void escribir(String texto) {
        salida.writeBytes(texto.getBytes(StandardCharsets.UTF_8));
    }

    private void poner(String registro, int valor) {
        if (!registros.containsKey(registro)) {
            throw new IllegalStateException("Registro desconocido: " + registro);
        }
        if (!registro.equals("$zero")) {
            registros.put(registro, valor);
        }
    }

    private int valor(String operando) {
        if (operando.startsWith("$")) {
            Integer valor = registros.get(operando);
            if (valor == null) {
                throw new IllegalStateException("Registro desconocido: " + operando);
            }
            return valor;
        }
        return Integer.decode(operando);
    }

    private int destino(String etiqueta) {
        Integer destino = etiquetasTexto.get(etiqueta);
        if (destino == null) {
            throw new IllegalStateException("Etiqueta de texto inexistente: " + etiqueta);
        }
        return destino;
    }

    private int direccionDato(String etiqueta) {
        Integer direccion = etiquetasDatos.get(etiqueta);
        if (direccion == null) {
            throw new IllegalStateException("Etiqueta de datos inexistente: " + etiqueta);
        }
        return direccion;
    }

    private int direccionEtiqueta(String etiqueta) {
        Integer direccion = etiquetasDatos.get(etiqueta);
        if (direccion != null) {
            return direccion;
        }
        Integer instruccion = etiquetasTexto.get(etiqueta);
        if (instruccion != null) {
            return instruccion;
        }
        throw new IllegalStateException("Etiqueta inexistente: " + etiqueta);
    }

    /** {@code off($r)}, {@code ($r)} o una etiqueta de datos. */
    private int direccion(String operando) {
        int parentesis = operando.indexOf('(');
        if (parentesis < 0) {
            return direccionDato(operando);
        }
        String desplazamiento = operando.substring(0, parentesis);
        String registro = operando.substring(parentesis + 1, operando.length() - 1);
        return (desplazamiento.isEmpty() ? 0 : Integer.decode(desplazamiento)) + valor(registro);
    }

    private int leerPalabra(int direccion) {
        alineada(direccion);
        int valor = 0;
        for (int i = 3; i >= 0; i--) {
            valor = (valor << 8) | (memoria.getOrDefault(direccion + i, (byte) 0) & 0xFF);
        }
        return valor;
    }

    private void escribirPalabra(int direccion, int valor) {
        alineada(direccion);
        for (int i = 0; i < 4; i++) {
            memoria.put(direccion + i, (byte) (valor >>> (8 * i)));
        }
    }

    private int leerByte(int direccion) {
        if (direccion == 0) {
            throw new IllegalStateException("Acceso a la dirección 0 (null)");
        }
        return memoria.getOrDefault(direccion, (byte) 0);
    }

    private static void alineada(int direccion) {
        if (direccion % 4 != 0) {
            throw new IllegalStateException("Acceso a palabra no alineado: 0x" + Integer.toHexString(direccion));
        }
    }
}
