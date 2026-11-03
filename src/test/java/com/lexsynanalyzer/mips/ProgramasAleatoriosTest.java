package com.lexsynanalyzer.mips;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prueba diferencial: genera programas Compiscript aleatorios (con semilla fija, así cada falla se
 * repite), calcula en Java la salida que <b>deberían</b> dar y la compara con la del MIPS generado,
 * con los dos asignadores. El oráculo no sale del compilador, así que también encuentra errores del TAC.
 *
 * <p>Los programas mezclan enteros (con desborde de 32 bits, división y módulo de negativos),
 * booleanos con cortocircuito, strings, ternarios, if/else, while, do-while, switch con fall-through,
 * break, continue, bloques que sombrean variables, llamadas a funciones (una recursiva y otra que
 * modifica una global) y expresiones muy anidadas que obligan a derramar registros.
 */
class ProgramasAleatoriosTest {

    static LongStream semillas() {
        return LongStream.range(0, 300);
    }

    @ParameterizedTest(name = "semilla {0}")
    @MethodSource("semillas")
    void elMipsImprimeLoMismoQueLaSemanticaDeReferencia(long semilla) {
        Generador generador = new Generador(new Random(semilla));
        String fuente = generador.programa();
        String esperado = generador.ejecutar();

        for (FabricaRegistros modo : List.of(FabricaRegistros.INGENUO, FabricaRegistros.GETREG)) {
            String asm = MipsDePrueba.compilar(fuente, modo).toString();
            if (semilla < GUARDADOS && modo == FabricaRegistros.GETREG) {
                guardar(semilla, fuente, asm, esperado);
            }
            SimuladorMips.Resultado resultado = SimuladorMips.ejecutar(asm);
            assertEquals(esperado, resultado.salida(),
                    () -> (modo == FabricaRegistros.GETREG ? "GETREG" : "INGENUO") + "\n" + fuente);
            assertEquals(0, resultado.codigoSalida());
        }
    }

    /** Los primeros programas quedan en {@code target/asm/aleatorios/} para correrlos también en MARS. */
    private static final int GUARDADOS = 300;

    private static void guardar(long semilla, String fuente, String asm, String esperado) {
        Path carpeta = Path.of("target", "asm", "aleatorios");
        try {
            Files.createDirectories(carpeta);
            Files.writeString(carpeta.resolve("aleatorio_" + semilla + ".cps"), fuente);
            Files.writeString(carpeta.resolve("aleatorio_" + semilla + ".asm"), asm);
            Files.writeString(carpeta.resolve("aleatorio_" + semilla + ".salida"), esperado);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // --- Mini AST con texto Compiscript y evaluación de referencia ---

    private interface Expr {
        String texto();

        Object valor(Map<String, Object> entorno);
    }

    private interface Sentencia {
        void escribir(StringBuilder sb, String sangria);

        void ejecutar(Map<String, Object> entorno, StringBuilder salida);
    }

    private static final class Romper extends RuntimeException {
        Romper() {
            super(null, null, false, false);
        }
    }

    private static final class Continuar extends RuntimeException {
        Continuar() {
            super(null, null, false, false);
        }
    }

    private static Expr expr(String texto, java.util.function.Function<Map<String, Object>, Object> valor) {
        return new Expr() {
            @Override
            public String texto() {
                return texto;
            }

            @Override
            public Object valor(Map<String, Object> entorno) {
                return valor.apply(entorno);
            }
        };
    }

    private static int entero(Expr e, Map<String, Object> entorno) {
        return (Integer) e.valor(entorno);
    }

    private static boolean booleano(Expr e, Map<String, Object> entorno) {
        return (Boolean) e.valor(entorno);
    }

    private static String cadena(Expr e, Map<String, Object> entorno) {
        return (String) e.valor(entorno);
    }

    private static final class Generador {
        private static final String[] ENTEROS = {"a", "b", "c", "d", "e"};
        private static final String[] BOOLEANOS = {"p", "q"};
        private static final String[] CADENAS = {"s", "u"};
        private static final String[] PALABRAS = {"", "x", "ab", "hola", "Z9"};
        /** Variables que puede sombrear un bloque; {@code a} no, porque {@code inc()} la modifica. */
        private static final String[] SOMBREABLES = {"b", "c", "d", "e"};
        private static final String FUNCIONES = String.join("\n",
                "function sq(x: integer): integer { return x * x; }",
                "function mx(x: integer, y: integer): integer {",
                "    if (x > y) { return x; }",
                "    return y;",
                "}",
                "function sumaHasta(n: integer): integer {",
                "    if (n <= 0) { return 0; }",
                "    return n + sumaHasta(n - 1);",
                "}",
                "function inc(): integer {",
                "    a = a + 1;",
                "    return a;",
                "}",
                "");

        private static int sumaHasta(int n) {
            return n <= 0 ? 0 : n + sumaHasta(n - 1);
        }

        private final Random azar;
        private final List<Sentencia> sentencias = new ArrayList<>();
        private final Map<String, Object> iniciales = new HashMap<>();
        private int contadores;
        private int profundidadCiclo;

        Generador(Random azar) {
            this.azar = azar;
        }

        String programa() {
            for (String v : ENTEROS) {
                iniciales.put(v, azar.nextInt(41) - 20);
            }
            for (String v : BOOLEANOS) {
                iniciales.put(v, azar.nextBoolean());
            }
            for (String v : CADENAS) {
                iniciales.put(v, PALABRAS[azar.nextInt(PALABRAS.length)]);
            }
            int cantidad = 6 + azar.nextInt(10);
            for (int i = 0; i < cantidad; i++) {
                sentencias.add(sentencia(2));
            }
            StringBuilder sb = new StringBuilder();
            for (String v : ENTEROS) {
                sb.append("let ").append(v).append(": integer = ").append(literal((Integer) iniciales.get(v))).append(";\n");
            }
            for (String v : BOOLEANOS) {
                sb.append("let ").append(v).append(": boolean = ").append(iniciales.get(v)).append(";\n");
            }
            for (String v : CADENAS) {
                sb.append("let ").append(v).append(": string = \"").append(iniciales.get(v)).append("\";\n");
            }
            for (int k = 0; k < contadores; k++) {
                sb.append("let k").append(k).append(": integer = 0;\n");
            }
            sb.append(FUNCIONES);
            sentencias.forEach(s -> s.escribir(sb, ""));
            return sb.toString();
        }

        String ejecutar() {
            Map<String, Object> entorno = new HashMap<>(iniciales);
            for (int k = 0; k < contadores; k++) {
                entorno.put("k" + k, 0);
            }
            StringBuilder salida = new StringBuilder();
            sentencias.forEach(s -> s.ejecutar(entorno, salida));
            return salida.toString();
        }

        private static String literal(int n) {
            return n < 0 ? "(-" + (-n) + ")" : String.valueOf(n);
        }

        // --- Sentencias ---

        private Sentencia sentencia(int anidamiento) {
            int opcion = azar.nextInt(anidamiento > 0 ? 13 : 7);
            return switch (opcion) {
                case 0, 1 -> asignar(ENTEROS[azar.nextInt(ENTEROS.length)], enteroExpr(3));
                case 2 -> asignar(BOOLEANOS[azar.nextInt(BOOLEANOS.length)], booleanoExpr(2));
                case 3 -> asignar(CADENAS[azar.nextInt(CADENAS.length)], cadenaExpr(2));
                case 4 -> imprimir(azar.nextInt(3) == 0 ? encadenado() : enteroExpr(3), Object::toString);
                case 5 -> imprimir(booleanoExpr(2), Object::toString);
                case 6 -> profundidadCiclo > 0 && azar.nextInt(3) == 0 ? saltoDeCiclo() : imprimir(cadenaExpr(2), o -> (String) o);
                case 7, 8 -> si(anidamiento);
                case 9 -> mientras(anidamiento, false);
                case 10 -> mientras(anidamiento, true);
                case 11 -> sombra(anidamiento);
                default -> segun(anidamiento);
            };
        }

        private Sentencia asignar(String variable, Expr valor) {
            return new Sentencia() {
                @Override
                public void escribir(StringBuilder sb, String sangria) {
                    sb.append(sangria).append(variable).append(" = ").append(valor.texto()).append(";\n");
                }

                @Override
                public void ejecutar(Map<String, Object> entorno, StringBuilder salida) {
                    entorno.put(variable, valor.valor(entorno));
                }
            };
        }

        private Sentencia imprimir(Expr valor, java.util.function.Function<Object, String> formato) {
            return new Sentencia() {
                @Override
                public void escribir(StringBuilder sb, String sangria) {
                    sb.append(sangria).append("print(").append(valor.texto()).append(");\n");
                }

                @Override
                public void ejecutar(Map<String, Object> entorno, StringBuilder salida) {
                    salida.append(formato.apply(valor.valor(entorno))).append('\n');
                }
            };
        }

        /** {@code if (c) { break; }} o {@code continue}, solo dentro de un ciclo. */
        private Sentencia saltoDeCiclo() {
            Expr condicion = booleanoExpr(1);
            boolean romper = azar.nextBoolean();
            return new Sentencia() {
                @Override
                public void escribir(StringBuilder sb, String sangria) {
                    sb.append(sangria).append("if (").append(condicion.texto()).append(") { ")
                            .append(romper ? "break" : "continue").append("; }\n");
                }

                @Override
                public void ejecutar(Map<String, Object> entorno, StringBuilder salida) {
                    if (booleano(condicion, entorno)) {
                        throw romper ? new Romper() : new Continuar();
                    }
                }
            };
        }

        private List<Sentencia> bloque(int anidamiento) {
            List<Sentencia> cuerpo = new ArrayList<>();
            int cantidad = 1 + azar.nextInt(3);
            for (int i = 0; i < cantidad; i++) {
                cuerpo.add(sentencia(anidamiento));
            }
            return cuerpo;
        }

        private static void escribirBloque(StringBuilder sb, String sangria, List<Sentencia> cuerpo) {
            cuerpo.forEach(s -> s.escribir(sb, sangria + "    "));
        }

        private static void ejecutarBloque(List<Sentencia> cuerpo, Map<String, Object> entorno, StringBuilder salida) {
            cuerpo.forEach(s -> s.ejecutar(entorno, salida));
        }

        private Sentencia si(int anidamiento) {
            Expr condicion = booleanoExpr(2);
            List<Sentencia> entonces = bloque(anidamiento - 1);
            List<Sentencia> sino = azar.nextBoolean() ? bloque(anidamiento - 1) : null;
            return new Sentencia() {
                @Override
                public void escribir(StringBuilder sb, String sangria) {
                    sb.append(sangria).append("if (").append(condicion.texto()).append(") {\n");
                    escribirBloque(sb, sangria, entonces);
                    sb.append(sangria).append("}");
                    if (sino != null) {
                        sb.append(" else {\n");
                        escribirBloque(sb, sangria, sino);
                        sb.append(sangria).append("}");
                    }
                    sb.append('\n');
                }

                @Override
                public void ejecutar(Map<String, Object> entorno, StringBuilder salida) {
                    if (booleano(condicion, entorno)) {
                        ejecutarBloque(entonces, entorno, salida);
                    } else if (sino != null) {
                        ejecutarBloque(sino, entorno, salida);
                    }
                }
            };
        }

        /**
         * {@code k = 0; while (k < n) { k = k + 1; ... }}. El contador avanza al inicio del cuerpo para
         * que {@code continue} no deje el ciclo infinito; ninguna otra sentencia lo toca.
         */
        private Sentencia mientras(int anidamiento, boolean hacerMientras) {
            String k = "k" + contadores++;
            int vueltas = 1 + azar.nextInt(4);
            profundidadCiclo++;
            List<Sentencia> cuerpo = bloque(anidamiento - 1);
            profundidadCiclo--;
            return new Sentencia() {
                @Override
                public void escribir(StringBuilder sb, String sangria) {
                    sb.append(sangria).append(k).append(" = 0;\n");
                    sb.append(sangria).append(hacerMientras ? "do {\n" : "while (" + k + " < " + vueltas + ") {\n");
                    sb.append(sangria).append("    ").append(k).append(" = ").append(k).append(" + 1;\n");
                    escribirBloque(sb, sangria, cuerpo);
                    sb.append(sangria).append(hacerMientras ? "} while (" + k + " < " + vueltas + ");\n" : "}\n");
                }

                @Override
                public void ejecutar(Map<String, Object> entorno, StringBuilder salida) {
                    entorno.put(k, 0);
                    boolean primera = true;
                    while ((hacerMientras && primera) || (Integer) entorno.get(k) < vueltas) {
                        primera = false;
                        entorno.put(k, (Integer) entorno.get(k) + 1);
                        try {
                            ejecutarBloque(cuerpo, entorno, salida);
                        } catch (Continuar c) {
                            // sigue con la condición
                        } catch (Romper r) {
                            break;
                        }
                    }
                }
            };
        }

        /** Un bloque que declara de nuevo una variable: dentro, esa variable es otra. */
        private Sentencia sombra(int anidamiento) {
            String variable = SOMBREABLES[azar.nextInt(SOMBREABLES.length)];
            int inicial = azar.nextInt(41) - 20;
            List<Sentencia> cuerpo = bloque(anidamiento - 1);
            return new Sentencia() {
                @Override
                public void escribir(StringBuilder sb, String sangria) {
                    sb.append(sangria).append("{\n");
                    sb.append(sangria).append("    let ").append(variable).append(": integer = ")
                            .append(literal(inicial)).append(";\n");
                    escribirBloque(sb, sangria, cuerpo);
                    sb.append(sangria).append("}\n");
                }

                @Override
                public void ejecutar(Map<String, Object> entorno, StringBuilder salida) {
                    Object exterior = entorno.get(variable);
                    entorno.put(variable, inicial);
                    try {
                        ejecutarBloque(cuerpo, entorno, salida);
                    } finally {
                        entorno.put(variable, exterior);
                    }
                }
            };
        }

        /** {@code switch} con casos distintos, fall-through aleatorio y {@code default} al final. */
        private Sentencia segun(int anidamiento) {
            Expr selector = enteroExpr(2);
            List<Integer> valores = new ArrayList<>();
            List<List<Sentencia>> cuerpos = new ArrayList<>();
            List<Boolean> rompe = new ArrayList<>();
            int casos = 1 + azar.nextInt(3);
            int saltoGuardado = profundidadCiclo;
            profundidadCiclo = 0; // un break dentro de un case saldría del switch, no del ciclo
            while (valores.size() < casos) {
                int v = azar.nextInt(7) - 3;
                if (!valores.contains(v)) {
                    valores.add(v);
                    cuerpos.add(bloque(0));
                    rompe.add(azar.nextBoolean());
                }
            }
            List<Sentencia> porDefecto = azar.nextBoolean() ? bloque(0) : null;
            profundidadCiclo = saltoGuardado;
            return new Sentencia() {
                @Override
                public void escribir(StringBuilder sb, String sangria) {
                    sb.append(sangria).append("switch (").append(selector.texto()).append(") {\n");
                    for (int i = 0; i < valores.size(); i++) {
                        sb.append(sangria).append("    case ").append(literal(valores.get(i))).append(":\n");
                        escribirBloque(sb, sangria + "    ", cuerpos.get(i));
                        if (rompe.get(i)) {
                            sb.append(sangria).append("        break;\n");
                        }
                    }
                    if (porDefecto != null) {
                        sb.append(sangria).append("    default:\n");
                        escribirBloque(sb, sangria + "    ", porDefecto);
                    }
                    sb.append(sangria).append("}\n");
                }

                @Override
                public void ejecutar(Map<String, Object> entorno, StringBuilder salida) {
                    int valor = entero(selector, entorno);
                    int inicio = valores.indexOf(valor);
                    if (inicio < 0) {
                        inicio = valores.size();
                    }
                    for (int i = inicio; i < valores.size(); i++) {
                        ejecutarBloque(cuerpos.get(i), entorno, salida);
                        if (rompe.get(i)) {
                            return;
                        }
                    }
                    if (porDefecto != null) {
                        ejecutarBloque(porDefecto, entorno, salida);
                    }
                }
            };
        }

        // --- Expresiones ---

        private Expr enteroExpr(int profundidad) {
            int opcion = azar.nextInt(profundidad <= 0 ? 2 : 11);
            switch (opcion) {
                case 9, 10 -> {
                    return llamada(profundidad);
                }
                case 0 -> {
                    int n = azar.nextInt(41) - 20;
                    return expr(literal(n), e -> n);
                }
                case 1 -> {
                    String v = ENTEROS[azar.nextInt(ENTEROS.length)];
                    return expr(v, e -> e.get(v));
                }
                case 2 -> {
                    Expr x = enteroExpr(profundidad - 1);
                    return expr("(-" + x.texto() + ")", e -> -entero(x, e));
                }
                case 3 -> {
                    Expr c = booleanoExpr(profundidad - 1);
                    Expr x = enteroExpr(profundidad - 1);
                    Expr y = enteroExpr(profundidad - 1);
                    return expr("(" + c.texto() + " ? " + x.texto() + " : " + y.texto() + ")",
                            e -> booleano(c, e) ? entero(x, e) : entero(y, e));
                }
                case 4 -> {
                    // Divisor literal distinto de cero: la división entre cero se prueba aparte.
                    int divisor = (1 + azar.nextInt(7)) * (azar.nextBoolean() ? 1 : -1);
                    Expr x = enteroExpr(profundidad - 1);
                    boolean modulo = azar.nextBoolean();
                    return expr("(" + x.texto() + (modulo ? " % " : " / ") + literal(divisor) + ")",
                            e -> modulo ? entero(x, e) % divisor : entero(x, e) / divisor);
                }
                default -> {
                    String op = new String[]{"+", "-", "*"}[azar.nextInt(3)];
                    Expr x = enteroExpr(profundidad - 1);
                    Expr y = enteroExpr(profundidad - 1);
                    return expr("(" + x.texto() + " " + op + " " + y.texto() + ")", e -> switch (op) {
                        case "+" -> entero(x, e) + entero(y, e);
                        case "-" -> entero(x, e) - entero(y, e);
                        default -> entero(x, e) * entero(y, e);
                    });
                }
            }
        }

        /** Llamada a una de las funciones del programa; los argumentos van de izquierda a derecha. */
        private Expr llamada(int profundidad) {
            switch (azar.nextInt(4)) {
                case 0 -> {
                    Expr x = enteroExpr(profundidad - 1);
                    return expr("sq(" + x.texto() + ")", e -> {
                        int v = entero(x, e);
                        return v * v;
                    });
                }
                case 1 -> {
                    Expr x = enteroExpr(profundidad - 1);
                    Expr y = enteroExpr(profundidad - 1);
                    return expr("mx(" + x.texto() + ", " + y.texto() + ")", e -> {
                        int primero = entero(x, e);
                        int segundo = entero(y, e);
                        return primero > segundo ? primero : segundo;
                    });
                }
                case 2 -> {
                    Expr x = enteroExpr(profundidad - 1);
                    return expr("sumaHasta((" + x.texto() + " % 9))", e -> sumaHasta(entero(x, e) % 9));
                }
                default -> {
                    return expr("inc()", e -> {
                        int nuevo = (Integer) e.get("a") + 1;
                        e.put("a", nuevo);
                        return nuevo;
                    });
                }
            }
        }

        /** {@code (x0 * y0) + ((x1 * y1) + (...))}: muchos temporales vivos a la vez, fuerza derrames. */
        private Expr encadenado() {
            Expr resultado = enteroExpr(0);
            int eslabones = 8 + azar.nextInt(5);
            for (int i = 0; i < eslabones; i++) {
                Expr x = enteroExpr(0);
                Expr y = enteroExpr(0);
                Expr resto = resultado;
                resultado = expr("((" + x.texto() + " * " + y.texto() + ") + " + resto.texto() + ")",
                        e -> entero(x, e) * entero(y, e) + entero(resto, e));
            }
            return resultado;
        }

        private Expr booleanoExpr(int profundidad) {
            int opcion = azar.nextInt(profundidad <= 0 ? 3 : 9);
            switch (opcion) {
                case 0 -> {
                    boolean b = azar.nextBoolean();
                    return expr(String.valueOf(b), e -> b);
                }
                case 1 -> {
                    String v = BOOLEANOS[azar.nextInt(BOOLEANOS.length)];
                    return expr(v, e -> e.get(v));
                }
                case 2, 3 -> {
                    String op = new String[]{"<", "<=", ">", ">=", "==", "!="}[azar.nextInt(6)];
                    Expr x = enteroExpr(Math.max(0, profundidad - 1));
                    Expr y = enteroExpr(Math.max(0, profundidad - 1));
                    return expr("(" + x.texto() + " " + op + " " + y.texto() + ")", e -> {
                        int a = entero(x, e);
                        int b = entero(y, e);
                        return switch (op) {
                            case "<" -> a < b;
                            case "<=" -> a <= b;
                            case ">" -> a > b;
                            case ">=" -> a >= b;
                            case "==" -> a == b;
                            default -> a != b;
                        };
                    });
                }
                case 4 -> {
                    Expr x = booleanoExpr(profundidad - 1);
                    return expr("(!" + x.texto() + ")", e -> !booleano(x, e));
                }
                case 5 -> {
                    Expr x = cadenaExpr(profundidad - 1);
                    Expr y = cadenaExpr(profundidad - 1);
                    boolean igual = azar.nextBoolean();
                    return expr("(" + x.texto() + (igual ? " == " : " != ") + y.texto() + ")",
                            e -> cadena(x, e).equals(cadena(y, e)) == igual);
                }
                default -> {
                    boolean y = azar.nextBoolean();
                    Expr izq = booleanoExpr(profundidad - 1);
                    Expr der = booleanoExpr(profundidad - 1);
                    return expr("(" + izq.texto() + (y ? " && " : " || ") + der.texto() + ")",
                            e -> y ? booleano(izq, e) && booleano(der, e) : booleano(izq, e) || booleano(der, e));
                }
            }
        }

        private Expr cadenaExpr(int profundidad) {
            int opcion = azar.nextInt(profundidad <= 0 ? 2 : 4);
            switch (opcion) {
                case 0 -> {
                    String p = PALABRAS[azar.nextInt(PALABRAS.length)];
                    return expr("\"" + p + "\"", e -> p);
                }
                case 1 -> {
                    String v = CADENAS[azar.nextInt(CADENAS.length)];
                    return expr(v, e -> e.get(v));
                }
                default -> {
                    Expr x = cadenaExpr(profundidad - 1);
                    Expr y = cadenaExpr(profundidad - 1);
                    return expr("(" + x.texto() + " + " + y.texto() + ")", e -> cadena(x, e) + cadena(y, e));
                }
            }
        }
    }
}
