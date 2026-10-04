function factorial(n: integer): integer {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}
let f: integer = factorial(5);
print(f);
