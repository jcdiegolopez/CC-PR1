// ============================================================
// CATEGORIA: CLASES Y OBJETOS
// ============================================================

// --- Casos EXITOSOS ---

class Animal {
    let nombre: string;

    function constructor(nombre: string) {
        this.nombre = nombre;
    }

    function hablar(): string {
        return this.nombre + " hace ruido.";
    }
}

class Perro : Animal {
    function hablar(): string {
        return this.nombre + " ladra.";
    }
}

let miPerro: Perro = new Perro("Toby"); // constructor heredado, llamada correcta
print(miPerro.hablar());                // acceso correcto a método heredado
print(miPerro.nombre);                  // acceso correcto a atributo heredado

// --- Casos FALLIDOS ---

// 1) Acceso a atributo/método inexistente -> ERROR
print(miPerro.edad);

// 2) Llamada al constructor con número incorrecto de argumentos -> ERROR
let otroPerro: Perro = new Perro("Toby", "extra");

// 3) 'this' usado fuera del cuerpo de una clase -> ERROR
this.nombre = "Firulais";

// 4) Herencia de una clase no declarada -> ERROR
class Gato : NoExiste {
    function maullar(): string {
        return "miau";
    }
}

// 5) Herencia cíclica -> ERROR
class A : B {
}
class B : A {
}
