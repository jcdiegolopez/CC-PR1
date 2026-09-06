// ============================================================
// CATEGORIA: MANEJO DE AMBITO
// ============================================================

// --- Casos EXITOSOS ---
let global1: integer = 1;

function usaGlobal(): integer {
    return global1 + 1; // resolución correcta de variable global
}

{
    let local1: integer = 2;
    print(local1); // acceso correcto dentro del mismo bloque
}

function ambitoLocal(): integer {
    let interno: integer = 42; // nuevo entorno de función
    if (interno > 0) {
        let dentroDeIf: integer = interno * 2; // nuevo entorno de bloque
        print(dentroDeIf);
    }
    return interno;
}

// Sombreado (shadowing) permitido en ámbitos anidados
let sombra: integer = 100;
{
    let sombra: integer = 200;
    print(sombra);
}

// --- Casos FALLIDOS ---

// 1) Uso de variable no declarada -> ERROR
print(noExiste);

// 2) Redeclaración en el mismo ámbito -> ERROR
let repetida: integer = 1;
let repetida: integer = 2;

// 3) Variable declarada dentro de un bloque no es visible fuera de él -> ERROR
{
    let soloAqui: integer = 5;
}
print(soloAqui);
