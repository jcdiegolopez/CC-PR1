let xs: integer[] = [1, 2, 3];

function buscar(v: integer): integer {
    let i: integer = 0;
    let r: integer = -2;
    while (i < 10) {
        try {
            if (xs[i] == v) {
                return i;
            }
        } catch (e) {
            return -1;
        }
        i = i + 1;
    }
    return r;
}

print(buscar(2));
print(buscar(7));

let suma: integer = 0;
for (let k: integer = 0; k < 10; k = k + 1) {
    try {
        if (k == 1) {
            continue;
        }
        if (k == 4) {
            break;
        }
        suma = suma + xs[k];
    } catch (e) {
        suma = suma + 1000;
    }
}
print(suma);

try {
    print(xs[10]);
} catch (e) {
    print("ok: " + e);
}
print(buscar(3));
