function marca(n: integer, v: boolean): boolean {
    print(n);
    return v;
}

print(marca(1, false) && marca(2, true));
print(marca(3, true) || marca(4, true));
print(marca(5, true) && marca(6, false));
print(marca(7, false) || marca(8, true));
let r: boolean = marca(9, false) && marca(10, true) || marca(11, true);
print(r);
