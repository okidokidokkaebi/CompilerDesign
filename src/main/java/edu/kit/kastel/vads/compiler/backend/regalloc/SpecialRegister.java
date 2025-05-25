package edu.kit.kastel.vads.compiler.backend.regalloc;

public class SpecialRegister implements Register {

    private final SPECIAL_REGISTERS register;

    public SpecialRegister(SPECIAL_REGISTERS register) {
        this.register = register;
    }

    public SpecialRegister(int register) {
        switch (register) {
            case 0 -> this.register = SPECIAL_REGISTERS.RAX;
            case 1 -> this.register = SPECIAL_REGISTERS.RBX;
            case 2 -> this.register = SPECIAL_REGISTERS.RCX;
            case 3 -> this.register = SPECIAL_REGISTERS.RDX;
            case 4 -> this.register = SPECIAL_REGISTERS.RSI;
            case 5 -> this.register = SPECIAL_REGISTERS.RDI;
            case 6 -> this.register = SPECIAL_REGISTERS.RSP;
            case 7 -> this.register = SPECIAL_REGISTERS.RBP;
            default -> throw new IllegalArgumentException("Unknown special register: " + register);
        }

    }


    public SPECIAL_REGISTERS getSpecialRegister() {
        return register;
    }

    @Override
    public int getRegisterNo() {
        return this.register.ordinal();
    }

    public String toString() {
        switch (register) {
            case RAX -> {
                return "%eax";
            }
            case RBX -> {
                return "%ebx";
            }
            case RCX -> {
                return "%ecx";
            }
            case RDX -> {
                return "%edx";
            }
            case RSI -> {
                return "%esi";
            }
            case RDI -> {
                return "%edi";
            }
            case RSP -> {
                return "%esp";
            }
            case RBP -> {
                return "%ebp";
            }
            default -> throw new IllegalArgumentException("Unknown special register: " + register);
        }
    }
}
