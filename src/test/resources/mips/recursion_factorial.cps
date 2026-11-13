function factorial(n: integer): integer {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

let i: integer = 0;
while (i <= 6) {
    print(factorial(i));
    i = i + 1;
}
print(factorial(12));
