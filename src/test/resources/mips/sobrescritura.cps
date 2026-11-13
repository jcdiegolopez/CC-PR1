class Figura {
    let lado: integer = 0;

    function constructor(lado: integer) {
        this.lado = lado;
    }

    function nombre(): string {
        return "figura";
    }

    function area(): integer {
        return 0;
    }

    function lados(): integer {
        return this.lado;
    }
}

class Cuadrado : Figura {
    function nombre(): string {
        return "cuadrado";
    }

    function area(): integer {
        return this.lado * this.lado;
    }
}

class CuadradoGrande : Cuadrado {
    function area(): integer {
        return this.lado * this.lado * 100;
    }
}

let f: Figura = new Figura(3);
let c: Cuadrado = new Cuadrado(3);
let g: CuadradoGrande = new CuadradoGrande(2);
print(f.nombre());
print(f.area());
print(c.nombre());
print(c.area());
print(g.nombre());
print(g.area());
print(g.lados());
