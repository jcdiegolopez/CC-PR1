let total: integer = 0;
for (let i: integer = 0; i < 5; i = i + 1) {
    total = total + i * i;
}
print(total);

for (let j: integer = 3; j > 0; j = j - 1) {
    print(j);
}

let pares: integer = 0;
for (let k: integer = 0; k < 10; k = k + 1) {
    if (k % 2 == 1) {
        continue;
    }
    pares = pares + 1;
}
print(pares);

let tabla: integer = 0;
for (let a: integer = 1; a <= 3; a = a + 1) {
    for (let b: integer = 1; b <= 3; b = b + 1) {
        tabla = tabla + a * b;
    }
}
print(tabla);
