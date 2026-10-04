class Contador {
    let valor: integer = 0;
    const PASO: integer = 1;

    function incrementar() {
        this.valor = this.valor + this.PASO;
    }

    function obtener(): integer {
        return this.valor;
    }
}

let c: Contador = new Contador();
c.incrementar();
c.valor = c.valor * 2;
let actual: integer = c.obtener();
print(actual);
