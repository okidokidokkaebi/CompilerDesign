package edu.kit.kastel.vads.compiler.backend.statements;

import edu.kit.kastel.vads.compiler.backend.aasm.VirtualRegister;
import edu.kit.kastel.vads.compiler.backend.regalloc.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static edu.kit.kastel.vads.compiler.backend.aasm.CodeGenerator.mapRegistersToAasm;

public class MoveStatement implements Statement {

    private final String opcode;
    private final List<VirtualRegister> virtual_reg;

    private final Optional<ConstOrRegister> left;
    private final Optional<Register> right;

    private final int create;

    @Nullable
    private Register assignedLeft = null;
    @Nullable
    private Register assignedRight;

    public MoveStatement(String opcode, Optional<ConstOrRegister> left, Optional<Register> right) {
        this.create = 0;

        this.opcode = opcode;
        this.virtual_reg = new ArrayList<>();

        this.left = left;
        this.right = right;

        if (left.isPresent()) {
            if (left.get() instanceof Register) {
                virtual_reg.add((VirtualRegister) left.get());
            }
        }

        if (right.isPresent() && right.get() instanceof VirtualRegister) {
            virtual_reg.add((VirtualRegister) right.get());
        }
    }

    public MoveStatement(String opcode, Optional<ConstOrRegister> left, SpecialRegister right) {
        this.create = 1;

        this.opcode = opcode;
        this.virtual_reg = new ArrayList<>();

        this.left = left;
        this.right = Optional.empty();

        this.assignedLeft = null;
        this.assignedRight = right;

        if (left.get() instanceof Register) {
            if (!(left.get() instanceof SpecialRegister) && !(left.get() instanceof ConstValue)) {
                virtual_reg.add((VirtualRegister) left.get());
            }
        }

    }

    public MoveStatement(String opcode, SpecialRegister left, SpecialRegister right) {
        this.create = 2;

        this.opcode = opcode;
        this.virtual_reg = new ArrayList<>();

        this.left = Optional.empty();
        this.right = Optional.empty();

        this.assignedLeft = left;
        this.assignedRight = right;
    }

    @Override
    public List<VirtualRegister> getUsedRegisters() {
        return List.copyOf(virtual_reg);
    }

    @Override
    public void assign(VirtualRegister virtualRegister, USABLE_REGISTERS register) {
        if (this.left.isPresent()) {
            if (this.left.get().equals(virtualRegister)) {
                this.assignedLeft = new VirtualRegister(register.ordinal());
            }
        }

        if (this.right.isPresent()) {
            if (this.right.get().equals(virtualRegister)) {
                this.assignedRight = new VirtualRegister(register.ordinal());
            }
        }

    }

    @Override
    public String toString() {

        String l, r;
        l = " ";
        r = " ";

        if (this.assignedLeft != null) {
            if (assignedLeft instanceof SpecialRegister) {
                l += assignedLeft;
            } else {
                l += mapRegistersToAasm(assignedLeft);
            }
            l += ", ";

        } else if (left.isPresent()) {
            if (left.get() instanceof ConstValue) {
                l += "$" + ((ConstValue) left.get()).getValue();
            } else {
                l += mapRegistersToAasm((Register) left.get());
            }
            l += ", ";
        } else {
            l = "";
        }

        if (this.assignedRight != null) {
            if (assignedRight instanceof SpecialRegister) {
                r += assignedRight;
            } else {
                r += mapRegistersToAasm(assignedRight);
            }
        } else if (right.isPresent()) {
            r += mapRegistersToAasm(right.get());
        } else {
            r = "";
        }


        return opcode + l + r;
    }
}
