let a: integer[] = [1, 2];
let pasos: integer = 0;

function leer(xs: integer[], i: integer): integer {
    pasos = pasos + 1;
    return xs[i];
}

try {
    print(leer(a, 1));
    print(leer(a, 5));
    print(99);
} catch (err) {
    print(err);
}
try {
    a[-1] = 3;
} catch (e) {
    print(e);
}
print(pasos);
