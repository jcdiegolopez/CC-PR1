class Punto {
    let x: integer = 0;
    let y: integer = 0;

    function constructor(x: integer, y: integer) {
        this.x = x;
        this.y = y;
    }

    function suma(): integer {
        return this.x + this.y;
    }
}

class Caja {
    let etiqueta: string = "sin nombre";
    let usos: integer = 1;
    let esquina: Punto;

    function constructor(etiqueta: string, x: integer) {
        this.etiqueta = etiqueta;
        this.usos = this.usos + 1;
        this.esquina = new Punto(x, x * 2);
    }
}

class Vacia {
    let n: integer = 7;
}

let p: Punto = new Punto(3, 4);
print(p.x);
print(p.y);
print(p.suma());

let c: Caja = new Caja("caja", 5);
print(c.etiqueta);
print(c.usos);
print(c.esquina.suma());

let v: Vacia = new Vacia();
let w: Vacia = new Vacia();
w.n = 8;
print(v.n);
print(w.n);
print(v == w);

let puntos: Punto[] = [new Punto(1, 1), new Punto(2, 3)];
print(puntos[1].suma());
