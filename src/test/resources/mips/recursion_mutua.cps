function esPar(n: integer): boolean {
    if (n == 0) {
        return true;
    }
    return esImpar(n - 1);
}

function esImpar(n: integer): boolean {
    if (n == 0) {
        return false;
    }
    return esPar(n - 1);
}

// Secuencias femenina y masculina de Hofstadter: F(n) = n - M(F(n-1)), M(n) = n - F(M(n-1)).
function femenina(n: integer): integer {
    if (n == 0) {
        return 1;
    }
    return n - masculina(femenina(n - 1));
}

function masculina(n: integer): integer {
    if (n == 0) {
        return 0;
    }
    return n - femenina(masculina(n - 1));
}

print(esPar(10));
print(esPar(7));
print(esImpar(7));
for (let i: integer = 0; i < 10; i = i + 1) {
    print(femenina(i));
}
print(masculina(9));
