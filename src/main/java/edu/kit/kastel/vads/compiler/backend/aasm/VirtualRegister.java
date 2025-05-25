package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;

public record VirtualRegister(int id) implements Register {
    public VirtualRegister(int id) {
        if (id < 8) {
            throw new IllegalArgumentException("Virtual register id must be at least 8");
        }
        this.id = id;
    }

    public int getRegisterNo() {
        return id;
    }

    @Override
    public String toString() {
        return "%" + id();
    }
}
