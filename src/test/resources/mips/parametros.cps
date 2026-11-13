class Par {
    let a: integer = 0;
    let b: integer = 0;
}

function seis(a: integer, b: integer, c: integer, d: integer, e: integer, f: integer): integer {
    return a + b * 2 + c * 3 + d * 4 + e * 5 + f * 6;
}

function cambiar(n: integer): integer {
    n = n + 100;
    return n;
}

function llenar(xs: integer[], v: integer) {
    xs[0] = v;
}

function intercambiar(p: Par) {
    let t: integer = p.a;
    p.a = p.b;
    p.b = t;
}

function repetir(s: string, veces: integer): string {
    let r: string = "";
    let i: integer = 0;
    while (i < veces) {
        r = r + s;
        i = i + 1;
    }
    return r;
}

function negar(b: boolean): boolean {
    return !b;
}

print(seis(1, 2, 3, 4, 5, 6));
let n: integer = 5;
print(cambiar(n));
print(n);
let xs: integer[] = [1, 2];
llenar(xs, 42);
print(xs[0]);
let p: Par = new Par();
p.a = 1;
p.b = 2;
intercambiar(p);
print(p.a);
print(p.b);
print(repetir("ab", 3));
print(negar(false));
print(seis(n, n, n, n, n, n));
