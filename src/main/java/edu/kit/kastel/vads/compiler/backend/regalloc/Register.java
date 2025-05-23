package edu.kit.kastel.vads.compiler.backend.regalloc;

enum SPECIAL_REGISTERS {
    RAX,
    RBX,
    RCX,
    RDX,
    RSI,
    RDI,
}

public interface Register {
    public int getRegisterNo();
}
