class Contador {
    let valor: integer = 0;
    let nombre: string = "c";
    const PASO: integer = 2;

    function constructor(nombre: string, inicio: integer) {
        this.nombre = nombre;
        this.valor = inicio;
    }

    function incrementar() {
        this.valor = this.valor + this.PASO;
    }

    function obtener(): integer {
        return this.valor;
    }
}

let a: Contador = new Contador("a", 10);
let b: Contador = new Contador("b", 100);
a.incrementar();
a.incrementar();
b.incrementar();
print(a.obtener());
print(b.obtener());
print(a.nombre + b.nombre);
b.valor = b.valor * 3;
print(b.valor);
let mismo: Contador = a;
mismo.incrementar();
print(a.valor);
print(mismo == a);
print(a == b);
