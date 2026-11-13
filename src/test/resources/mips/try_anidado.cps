let xs: integer[] = [1, 2, 3];
let cero: integer = 0;

try {
    try {
        print(xs[5]);
    } catch (interno) {
        print("interno: " + interno);
    }
    print(10 / cero);
} catch (externo) {
    print("externo: " + externo);
}

try {
    try {
        print(1);
    } catch (e1) {
        print("no");
    }
    xs[3] = 0;
} catch (e2) {
    print("afuera: " + e2);
}

try {
    try {
        print(xs[9]);
    } catch (a1) {
        print(10 / cero);
    }
} catch (a2) {
    print("desde catch: " + a2);
}
print("fin");
