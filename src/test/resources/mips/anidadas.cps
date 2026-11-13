let base: integer = 10;

function externa(x: integer): integer {
    function doble(y: integer): integer {
        return y * 2;
    }
    function masBase(y: integer): integer {
        return y + base;
    }
    return masBase(doble(x));
}

function contar(n: integer): integer {
    function paso(k: integer): integer {
        if (k == 0) {
            return 0;
        }
        return 1 + paso(k - 1);
    }
    return paso(n);
}

print(externa(5));
print(externa(-5));
base = 1;
print(externa(3));
print(contar(4));
