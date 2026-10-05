let i: integer = 0;
let suma: integer = 0;
do {
    i = i + 1;
    if (i % 2 == 0) {
        continue;
    }
    suma = suma + i;
} while (i < 10);
for (let j: integer = 0; j < 5; j = j + 1) {
    if (j == 3) {
        continue;
    }
    print(j);
}
print(suma);
