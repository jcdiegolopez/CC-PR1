let i: integer = 0;
let suma: integer = 0;
while (i < 5) {
    suma = suma + i;
    i = i + 1;
}
print(suma);
let j: integer = 10;
do {
    j = j - 3;
} while (j > 0);
print(j);
let total: integer = 0;
for (let k: integer = 1; k <= 10; k = k + 1) {
    if (k % 2 == 0) { continue; }
    if (k > 7) { break; }
    total = total + k;
}
print(total);
let n: integer = 0;
while (true) {
    n = n + 1;
    if (n == 3) { break; }
}
print(n);
let c: integer = 0;
for (let p: integer = 0; p < 3; p = p + 1) {
    for (let q: integer = 0; q < 3; q = q + 1) {
        if (q == 2) { break; }
        c = c + 1;
    }
}
print(c);
