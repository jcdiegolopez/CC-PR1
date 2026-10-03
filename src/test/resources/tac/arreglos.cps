let v: integer[] = [1, 2, 3];
let m: integer[][] = [[1, 2], [3, 4]];
let i: integer = 1;
v[0] = 10;
v[i + 1] = v[i] * 2;
m[1][0] = m[0][1];
let e: integer[] = [];
print(m[1][1]);
