function dividir(a: integer, b: integer): integer {
    return a / b;
}

function seguro(a: integer, b: integer): integer {
    try {
        return dividir(a, b);
    } catch (e) {
        print("seguro: " + e);
        return 0;
    }
}

function profundo(n: integer): integer {
    if (n == 0) {
        return dividir(1, 0);
    }
    return profundo(n - 1) + 1;
}

print(seguro(10, 2));
print(seguro(1, 0));
try {
    print(profundo(5));
} catch (e) {
    print("main: " + e);
}
let x: integer = 7;
print(x + seguro(8, 4));
