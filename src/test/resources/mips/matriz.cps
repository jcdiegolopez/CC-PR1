let m: integer[][] = [[1, 2, 3], [4, 5, 6]];
m[1][0] = m[0][2] * 10;
foreach (fila in m) {
    let total: integer = 0;
    foreach (x in fila) {
        total = total + x;
    }
    print(total);
}
let i: integer = 0;
while (i < 2) {
    print(m[i][i]);
    i = i + 1;
}
print(m[1][0]);
