let x: integer = 1;
let total: integer = 0;
{
    let x: integer = 10;
    print(x);
    {
        let x: integer = 100;
        x = x + 1;
        print(x);
    }
    print(x);
    x = x + 5;
    total = total + x;
}
print(x);
let i: integer = 0;
while (i < 3) {
    let cuadrado: integer = i * i;
    total = total + cuadrado;
    i = i + 1;
}
print(total);
if (x == 1) {
    let x: string = "sombra";
    print(x);
}
print(x);
function usaGlobal(): integer {
    let x: integer = 50;
    return x + total;
}
print(usaGlobal());
print(x);
