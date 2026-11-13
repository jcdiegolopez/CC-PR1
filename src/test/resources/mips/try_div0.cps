let a: integer = 10;
let divisores: integer[] = [0, 5];

try {
    print(a / divisores[1]);
    print(a / divisores[0]);
    print(-1);
} catch (e) {
    print(e);
}

let cero: integer = divisores[0];
try {
    print(a % cero);
} catch (e) {
    print("modulo: " + e);
}
print(a);
