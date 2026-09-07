package com.lexsynanalyzer.semantic;

import com.lexsynanalyzer.analyzer.AnalysisError;
import com.lexsynanalyzer.analyzer.TipoError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lexsynanalyzer.semantic.TiposSemanticosTest.assertSinErrores;
import static com.lexsynanalyzer.semantic.TiposSemanticosTest.semanticos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas semánticas de clases, herencia, atributos, métodos, 'this', 'new' y constructores. */
class ClasesSemanticasTest {

    @Test
    @DisplayName("Declaración básica de clases y miembros no genera errores")
    void declaraClaseConMetodosYAtributos() {
        assertSinErrores("""
                class Calculadora {
                    var factor: integer;
                    function sumar(a: integer, b: integer): integer {
                        return a + b;
                    }
                }
                let calc: Calculadora = new Calculadora();
                let resultado: integer = calc.sumar(2, 3);
                """);
    }

    @Test
    @DisplayName("'this' solo es válido dentro del cuerpo de una clase")
    void validaUsoDeThis() {
        assertSinErrores("""
                class Persona {
                    var nombre: string;
                    function saludar(): string {
                        return "Hola " + this.nombre;
                    }
                }
                """);

        List<AnalysisError> errores = semanticos("let x = this;");
        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("'this' solo puede usarse dentro"), errores.toString());
    }

    @Test
    @DisplayName("Acceso a atributos y métodos existentes es válido")
    void accesoValidoAMiembros() {
        assertSinErrores("""
                class Punto {
                    var x: integer;
                    var y: integer;
                    function distancia(): integer {
                        return this.x + this.y;
                    }
                }
                let p: Punto = new Punto();
                p.x = 10;
                let d: integer = p.distancia();
                """);
    }

    @Test
    @DisplayName("Acceso a atributo o método inexistente produce error semántico")
    void rechazaAccesoAMiembroInexistente() {
        List<AnalysisError> errores = semanticos("""
                class Usuario {
                    var id: integer;
                }
                let u: Usuario = new Usuario();
                print(u.correo);
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("no contiene ningún atributo"), errores.toString());
    }

    @Test
    @DisplayName("Instanciación 'new' con clase inexistente produce error semántico")
    void rechazaNewDeClaseInexistente() {
        List<AnalysisError> errores = semanticos("let x = new Inexistente();");

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("no está declarada"), errores.toString());
    }

    @Test
    @DisplayName("Instanciación 'new' sobre una variable que no es clase produce error")
    void rechazaNewDeSimboloQueNoEsClase() {
        List<AnalysisError> errores = semanticos("""
                let miVar: integer = 10;
                let x = new miVar();
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("no se puede instanciar con 'new'"), errores.toString());
    }

    @Test
    @DisplayName("Constructor explícito valida cantidad y tipos de argumentos")
    void validaConstructorExplicito() {
        assertSinErrores("""
                class Empleado {
                    var nombre: string;
                    var salario: integer;
                    function constructor(n: string, s: integer) {
                        this.nombre = n;
                        this.salario = s;
                    }
                }
                let emp: Empleado = new Empleado("Ana", 5000);
                """);

        List<AnalysisError> errCantidad = semanticos("""
                class Empleado {
                    function constructor(n: string, s: integer) {}
                }
                let emp = new Empleado("Ana");
                """);
        assertEquals(1, errCantidad.size(), errCantidad.toString());
        assertTrue(errCantidad.getFirst().descripcion().contains("espera 2 argumento(s)"), errCantidad.toString());

        List<AnalysisError> errTipo = semanticos("""
                class Empleado {
                    function constructor(n: string, s: integer) {}
                }
                let emp = new Empleado("Ana", "cinco mil");
                """);
        assertEquals(1, errTipo.size(), errTipo.toString());
        assertTrue(errTipo.getFirst().descripcion().contains("integer"), errTipo.toString());
    }

    @Test
    @DisplayName("Clase sin constructor explícito rechaza argumentos en 'new'")
    void rechazaArgumentosEnConstructorPorDefecto() {
        List<AnalysisError> errores = semanticos("""
                class Simple {}
                let s = new Simple(1, 2);
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("espera 0 argumentos"), errores.toString());
    }

    @Test
    @DisplayName("Herencia permite acceder a atributos y métodos de la clase base")
    void herenciaAccedeAMiembrosBase() {
        assertSinErrores("""
                class Animal {
                    var nombre: string;
                    function hacerSonido(): string {
                        return "sonido";
                    }
                }
                class Perro : Animal {
                    function ladrar(): string {
                        return this.hacerSonido() + " guau";
                    }
                }
                let p: Perro = new Perro();
                p.nombre = "Firulais";
                let sonido: string = p.ladrar();
                let base: Animal = p;
                """);
    }

    @Test
    @DisplayName("Herencia de clase inexistente es un error semántico")
    void rechazaHerenciaDeClaseInexistente() {
        List<AnalysisError> errores = semanticos("""
                class SubClase : ClaseNoExiste {}
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("La clase padre 'ClaseNoExiste' no está declarada"), errores.toString());
    }

    @Test
    @DisplayName("Herencia cíclica es detectada y reportada")
    void detectaHerenciaCiclica() {
        List<AnalysisError> errores = semanticos("""
                class A : B {}
                class B : A {}
                """);

        assertFalse(errores.isEmpty(), "se esperaba error de herencia cíclica");
        assertTrue(errores.stream().anyMatch(e -> e.descripcion().contains("Herencia cíclica detectada")), errores.toString());
    }

    @Test
    @DisplayName("Asignación a propiedad valida compatibilidad de tipos y constantes")
    void validaAsignacionAPropiedades() {
        assertSinErrores("""
                class Cuenta {
                    var saldo: integer;
                    const CODIGO: integer = 123;
                }
                let c: Cuenta = new Cuenta();
                c.saldo = 500;
                """);

        List<AnalysisError> errTipo = semanticos("""
                class Cuenta {
                    var saldo: integer;
                }
                let c: Cuenta = new Cuenta();
                c.saldo = "quinientos";
                """);
        assertEquals(1, errTipo.size(), errTipo.toString());
        assertTrue(errTipo.getFirst().descripcion().contains("saldo"), errTipo.toString());

        List<AnalysisError> errConst = semanticos("""
                class Cuenta {
                    const CODIGO: integer = 123;
                }
                let c: Cuenta = new Cuenta();
                c.CODIGO = 456;
                """);
        assertEquals(1, errConst.size(), errConst.toString());
        assertTrue(errConst.getFirst().descripcion().contains("constante"), errConst.toString());
    }

    @Test
    @DisplayName("Invocar un atributo como si fuera método es un error")
    void rechazaInvocarAtributoComoMetodo() {
        List<AnalysisError> errores = semanticos("""
                class Auto {
                    var velocidad: integer;
                }
                let a: Auto = new Auto();
                a.velocidad();
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("es un atributo"), errores.toString());
    }

    @Test
    @DisplayName("El nombre de una clase no puede usarse como un valor")
    void rechazaClaseComoValor() {
        List<AnalysisError> errores = semanticos("""
                class Motor {
                    var potencia: integer;
                }
                let x: integer = Motor + 1;
                """);

        assertEquals(1, errores.size(), errores.toString());
        assertTrue(errores.getFirst().descripcion().contains("no puede utilizarse como un valor"), errores.toString());
        assertEquals("Motor", errores.getFirst().simbolo(), errores.toString());
    }

    @Test
    @DisplayName("Una herencia cíclica se reporta sin colgar la búsqueda de miembros")
    void herenciaCiclicaNoProvocaRecursionInfinita() {
        // 'new A()' busca un constructor que no existe: sin límite de niveles, la búsqueda subiría
        // por A -> B -> A ... indefinidamente y reventaría el analizador con StackOverflowError.
        List<AnalysisError> errores = semanticos("""
                class A : B {
                    var x: integer = 1;
                }
                class B : A {
                    var y: integer = 2;
                }
                let a: A = new A();
                let n: integer = a.noExiste;
                """);

        assertEquals(3, errores.size(), errores.toString());
        assertTrue(errores.stream().allMatch(error -> error.tipo() == TipoError.SEMANTICO), errores.toString());
        assertEquals(2, errores.stream().filter(e -> e.descripcion().contains("Herencia cíclica")).count(),
                errores.toString());
        assertTrue(errores.getLast().descripcion().contains("noExiste"), errores.toString());
    }
}
