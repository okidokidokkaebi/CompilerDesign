package edu.kit.kastel.vads.compiler.backend.statements;

import edu.kit.kastel.vads.compiler.backend.aasm.VirtualRegister;
import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.backend.regalloc.USABLE_REGISTERS;

import java.util.List;

public interface Statement {
    String toString();
    List<VirtualRegister> getUsedRegisters();
    void assign(VirtualRegister virtualRegister, USABLE_REGISTERS register);
    boolean isSourceRegister(VirtualRegister register);
    boolean isDestinationRegister(VirtualRegister register);
}
