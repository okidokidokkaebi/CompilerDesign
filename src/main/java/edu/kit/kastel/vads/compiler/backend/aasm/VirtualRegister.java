package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;

public record VirtualRegister(int id) implements Register {
    public int getRegisterNo() {
        return id;
    }

    @Override
    public String toString() {
        return "%" + id();
    }
}
