function suma(xs: integer[]): integer {
    let total: integer = 0;
    foreach (x in xs) {
        total = total + x;
    }
    return total;
}
let v: integer[] = [1, 2, 3];
let i: integer = 1;
v[0] = 10;
v[i + 1] = v[i] * 7;
print(v[0]);
print(v[1]);
print(v[2]);
let vacio: integer[] = [];
print(suma(vacio));

print(suma(v));

let nombres: string[] = ["ana", "beto"];
nombres[1] = nombres[0] + "lia";
print(nombres[1]);
