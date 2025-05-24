package edu.kit.kastel.vads.compiler.backend.regalloc;

import edu.kit.kastel.vads.compiler.backend.statements.ConstOrRegister;

public class ConstValue implements ConstOrRegister {
    private final int value;

    public ConstValue(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
