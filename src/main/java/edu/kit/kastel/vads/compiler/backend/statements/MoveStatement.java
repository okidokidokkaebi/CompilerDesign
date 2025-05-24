package edu.kit.kastel.vads.compiler.backend.statements;

import edu.kit.kastel.vads.compiler.backend.regalloc.ConstValue;
import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.backend.regalloc.SpecialRegister;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static edu.kit.kastel.vads.compiler.backend.aasm.CodeGenerator.mapRegistersToAasm;

public class MoveStatement implements Statement {

    private final String opcode;
    private final List<Register> virtual_reg;

    private final Optional<ConstOrRegister> left;
    private final Optional<Register> right;

    @Nullable
    private Register assignedLeft = null;
    @Nullable
    private Register assignedRight;

    public MoveStatement(String opcode, Optional<ConstOrRegister> left, Optional<Register> right) {
        this.opcode = opcode;
        this.virtual_reg = new ArrayList<>();

        this.left = left;
        this.right = right;

        if (left.isPresent()) {
            if (left.get() instanceof Register) {
                virtual_reg.add((Register) left.get());
            }
        }

        right.ifPresent(virtual_reg::add);
    }

    public MoveStatement(String opcode, Optional<ConstOrRegister> left, SpecialRegister right) {
        this.opcode = opcode;
        this.virtual_reg = new ArrayList<>();

        this.left = left;
        this.right = Optional.empty();

        this.assignedLeft = null;
        this.assignedRight = right;
    }

    public MoveStatement(String opcode, SpecialRegister left, SpecialRegister right) {
        this.opcode = opcode;
        this.virtual_reg = new ArrayList<>();

        this.left = Optional.empty();
        this.right = Optional.empty();

        this.assignedLeft = left;
        this.assignedRight = right;
    }

    @Override
    public List<Register> getUsedRegisters() {
        return List.copyOf(virtual_reg);
    }

    @Override
    public String toString() {

        String l, r;
        l = " ";
        r = " ";

        if (this.assignedLeft != null) {
            if (assignedLeft instanceof SpecialRegister) {
                l += SpecialRegister.toString(((SpecialRegister) assignedLeft).getSpecialRegister());
            } else {
                l += assignedLeft.toString();
            }

        } else if (left.isPresent()) {
            if (left.get() instanceof ConstValue) {
                l += "$" + ((ConstValue) left.get()).getValue();
            } else {
                l += mapRegistersToAasm((Register) left.get());
            }
        } else {
            l = "";
        }

        if (this.assignedRight != null) {
            if (assignedRight instanceof SpecialRegister) {
                r += SpecialRegister.toString(((SpecialRegister) assignedRight).getSpecialRegister());
            } else {
                r += assignedRight.toString();
            }
        } else if (right.isPresent()) {
            r += mapRegistersToAasm(right.get());
        } else {
            r = "";
        }


        return opcode + l + r;
    }
}
