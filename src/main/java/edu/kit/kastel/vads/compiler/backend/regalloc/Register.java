package edu.kit.kastel.vads.compiler.backend.regalloc;

import edu.kit.kastel.vads.compiler.backend.statements.ConstOrRegister;

public interface Register extends ConstOrRegister {
    public int getRegisterNo();
}
