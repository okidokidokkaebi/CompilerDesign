package edu.kit.kastel.vads.compiler.backend.regalloc;

public class SpecialRegister implements Register {

    private final SPECIAL_REGISTERS register;

    public SpecialRegister(SPECIAL_REGISTERS register) {
        this.register = register;
    }

    public SPECIAL_REGISTERS getSpecialRegister() {
        return register;
    }

    @Override
    public int getRegisterNo() {
        return this.register.ordinal();
    }

    public static String toString(SPECIAL_REGISTERS register) {
        switch (register) {
            case RAX -> {
                return "%rax";
            }
            case RBX -> {
                return "%rbx";
            }
            case RCX -> {
                return "%rcx";
            }
            case RDI -> {
                return "%rdi";
            }
            case RDX -> {
                return "%rdx";
            }
            case RSI -> {
                return "%rsi";
            }
            default -> throw new IllegalArgumentException("Unknown special register: " + register);
        }
    }
}
