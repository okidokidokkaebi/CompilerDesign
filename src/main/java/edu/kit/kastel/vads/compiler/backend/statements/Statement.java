package edu.kit.kastel.vads.compiler.backend.statements;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;

import java.util.List;

public interface Statement {
    String toString();
    List<Register> getUsedRegisters();
}
