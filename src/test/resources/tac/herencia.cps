class Animal {
    let nombre: string;
    let patas: integer = 4;

    function constructor(nombre: string) {
        this.nombre = nombre;
    }

    function hablar(): string {
        return this.nombre + " hace ruido.";
    }

    function describir(): string {
        return this.hablar();
    }
}

class Perro : Animal {
    let raza: string = "mestizo";

    function hablar(): string {
        return this.nombre + " ladra.";
    }
}

let p: Perro = new Perro("Toby");
print(p.hablar());
print(p.describir());
let a: Animal = p;
print(a.hablar());
