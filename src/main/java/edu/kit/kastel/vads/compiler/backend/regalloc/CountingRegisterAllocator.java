package edu.kit.kastel.vads.compiler.backend.regalloc;

import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.Node;

import java.util.Map;

public class CountingRegisterAllocator implements RegisterAllocator {

    private int count = 0;

    @Override
    public Map<Node, Register> allocateRegisters(IrGraph graph) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public int getNew() {
        return count++;
    }
}
