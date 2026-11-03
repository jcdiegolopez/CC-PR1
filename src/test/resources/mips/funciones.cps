let contador: integer = 0;
function factorial(n: integer): integer {
    if (n <= 1) { return 1; }
    return n * factorial(n - 1);
}
function fib(n: integer): integer {
    if (n < 2) { return n; }
    return fib(n - 1) + fib(n - 2);
}
function siguiente(): integer {
    contador = contador + 1;
    return contador;
}
function suma4(a: integer, b: integer, c: integer, d: integer): integer {
    return a * 1000 + b * 100 + c * 10 + d;
}
function saludo(nombre: string): string {
    return "hola " + nombre;
}
function esPar(n: integer): boolean {
    return n % 2 == 0;
}
function mostrar(x: integer) {
    print(x);
}
print(factorial(5));
print(fib(10));
let x: integer = 7;
let y: integer = 3;
print(x * 2 + factorial(3) * (y + 1));
print(suma4(1, 2, 3, 4));
print(suma4(y, x, factorial(2), fib(5)));
contador = 10;
let antes: integer = contador;
let z: integer = siguiente() + contador;
print(z);
print(antes);
print(contador);
print(saludo("mundo"));
print(esPar(4));
print(esPar(7));
mostrar(x + y);
print(siguiente() * 100 + siguiente());
