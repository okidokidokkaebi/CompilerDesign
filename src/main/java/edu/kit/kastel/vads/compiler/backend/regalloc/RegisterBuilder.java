package edu.kit.kastel.vads.compiler.backend.regalloc;

import edu.kit.kastel.vads.compiler.backend.aasm.VirtualRegister;

public class RegisterBuilder {

    public static Register buildRegister(int register) {
        if (register < 8) {
            return new SpecialRegister(register);
        } else {
            return new VirtualRegister(register);
        }
    }
}
