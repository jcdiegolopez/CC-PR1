let xs: integer[] = [3, 1, 4, 1, 5];
let suma: integer = 0;
foreach (x in xs) {
    suma = suma + x;
}
print(suma);

foreach (x in xs) {
    if (x == 1) {
        continue;
    }
    if (x == 5) {
        break;
    }
    print(x);
}

let nombres: string[] = ["ana", "beto"];
foreach (n in nombres) {
    print(n + "!");
}

let m: integer[][] = [[1, 2], [3]];
let cuenta: integer = 0;
foreach (fila in m) {
    foreach (v in fila) {
        cuenta = cuenta + v;
    }
}
print(cuenta);
