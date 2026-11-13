package com.lexsynanalyzer.mips.mirilla;

import com.lexsynanalyzer.mips.InstruccionMips;

import java.util.List;
import java.util.Map;

/**
 * Regla 9 (extra): {@code beqz $r, L1} + {@code j L2} + {@code L1:} → {@code bnez $r, L2} + {@code L1:}.
 * Es el patrón de un {@code if} cuyo bloque es solo un {@code break}/{@code continue}: con la condición
 * invertida sobra un salto. La etiqueta {@code L1} se deja; si nadie más la usa la quita otra regla.
 */
public final class ReglaInversionRama implements Regla {

    private static final Map<String, String> INVERSA = Map.ofEntries(
            Map.entry("beqz", "bnez"), Map.entry("bnez", "beqz"),
            Map.entry("beq", "bne"), Map.entry("bne", "beq"),
            Map.entry("bltz", "bgez"), Map.entry("bgez", "bltz"),
            Map.entry("blez", "bgtz"), Map.entry("bgtz", "blez"),
            Map.entry("blt", "bge"), Map.entry("bge", "blt"),
            Map.entry("ble", "bgt"), Map.entry("bgt", "ble"));

    @Override
    public String nombre() {
        return "inversion-de-rama";
    }

    @Override
    public int aplicar(List<InstruccionMips> codigo) {
        int veces = 0;
        for (int k = 0; k < codigo.size(); k++) {
            InstruccionMips rama = codigo.get(k);
            String inversa = rama.esSaltoCondicional() ? INVERSA.get(rama.op()) : null;
            if (inversa == null) {
                continue;
            }
            int s = Ventana.siguiente(codigo, k);
            if (s < 0 || !Ventana.esSaltoAEtiqueta(codigo.get(s)) || !etiquetaAntesDeInstruccion(codigo, s, rama.destinoSalto())) {
                continue;
            }
            InstruccionMips invertida = new InstruccionMips(InstruccionMips.Tipo.INSTRUCCION, inversa,
                    rama.operandos(), rama.comentario());
            codigo.set(k, Ventana.conDestino(invertida, codigo.get(s).destinoSalto()));
            codigo.remove(s);
            veces++;
        }
        return veces;
    }

    private static boolean etiquetaAntesDeInstruccion(List<InstruccionMips> codigo, int salto, String etiqueta) {
        for (int k = salto + 1; k < codigo.size() && !codigo.get(k).esInstruccion(); k++) {
            if (codigo.get(k).esEtiqueta() && etiqueta.equals(codigo.get(k).op())) {
                return true;
            }
        }
        return false;
    }
}
