class Animal {
    let nombre: string;
    let patas: integer = 4;

    function constructor(nombre: string) {
        this.nombre = nombre;
    }

    function hablar(): string {
        return this.nombre + " hace ruido";
    }

    function describir(): string {
        return this.nombre + " tiene patas";
    }
}

class Perro : Animal {
    let raza: string = "mestizo";

    function hablar(): string {
        return this.nombre + " ladra";
    }
}

class Cachorro : Perro {
    let edad: integer = 1;
}

let p: Perro = new Perro("Toby");
print(p.hablar());
print(p.describir());
print(p.patas);
print(p.raza);
let c: Cachorro = new Cachorro("Rex");
c.edad = c.edad + c.patas;
print(c.hablar());
print(c.edad);
print(c.nombre + " " + c.raza);
let a: Animal = p;
print(a.hablar());
