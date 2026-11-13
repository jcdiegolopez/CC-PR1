class Base {
    let id: integer = 1;
    let nombre: string = "base";

    function constructor(nombre: string) {
        this.nombre = nombre;
        this.id = this.id + 10;
    }

    function info(): string {
        return this.nombre;
    }
}

class Hija : Base {
    let extra: integer = 5;
}

class Nieta : Hija {
    let nivel: integer = 3;

    function constructor(nombre: string) {
        this.nombre = nombre + "!";
        this.nivel = this.nivel + this.extra;
    }
}

let h: Hija = new Hija("hija");
print(h.info());
print(h.id);
print(h.extra);

let n: Nieta = new Nieta("nieta");
print(n.info());
print(n.id);
print(n.extra);
print(n.nivel);
