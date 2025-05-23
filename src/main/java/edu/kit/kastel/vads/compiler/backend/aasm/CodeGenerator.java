package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.CountingRegisterAllocator;
import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.backend.regalloc.RegisterAllocator;
import edu.kit.kastel.vads.compiler.backend.regalloc.SPECIAL_REGISTERS;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.*;

import java.util.*;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class CodeGenerator {

    public final static int indent = 2;

    public final static String[] registers_64 = new String[]{
            "%rax", "%rbx", "%rcx", "%rdx", "%rsi", "%rdi", "%r8", "%r9",
            "%r10", "%r11", "%r12", "%r13", "%r14", "%r15"
    };

    private void dfs(Set<Node> visited, Stack<Node> stack, CountingRegisterAllocator allocator, StringBuilder builder) {
        while (!stack.isEmpty()) {
            Node active = stack.pop();
            for (Node predecessor : active.predecessors()) {
                if (visited.add(predecessor)) {
                    stack.add(predecessor);
                    dfs(visited, stack, allocator, builder);
                }
            }

            switch (active) {
                case ConstIntNode c -> {
                    c.setResultRegister(new VirtualRegister(allocator.getNew()));
                    appendIndentedLine(builder, "mov", c.value(), c.resultRegister());
                }
                case AddNode add -> {
                    Register left = add.predecessor(BinaryOperationNode.LEFT).resultRegister();
                    Register right = add.predecessor(BinaryOperationNode.RIGHT).resultRegister();
                    add.setResultRegister(right);
                    appendIndentedLine(builder, "add", left, right);
                }
                case SubNode sub -> {
                    Register left = sub.predecessor(BinaryOperationNode.LEFT).resultRegister();
                    Register right = sub.predecessor(BinaryOperationNode.RIGHT).resultRegister();
                    sub.setResultRegister(left);
                    appendIndentedLine(builder, "sub", right, left);
                }
                case MulNode mul -> {
                    Register left = mul.predecessor(BinaryOperationNode.LEFT).resultRegister();
                    Register right = mul.predecessor(BinaryOperationNode.RIGHT).resultRegister();
                    mul.setResultRegister(right);
                    appendIndentedLine(builder, "imul", left, right);
                }
                case DivNode div -> {
                    Register left = div.predecessor(BinaryOperationNode.LEFT).resultRegister();
                    Register right = div.predecessor(BinaryOperationNode.RIGHT).resultRegister();
                    div.setResultRegister(new VirtualRegister(SPECIAL_REGISTERS.RAX.ordinal()));
                    // TODO
                }
                case ModNode mod -> {
                    Register left = mod.predecessor(BinaryOperationNode.LEFT).resultRegister();
                    Register right = mod.predecessor(BinaryOperationNode.RIGHT).resultRegister();
                    mod.setResultRegister(new VirtualRegister(SPECIAL_REGISTERS.RCX.ordinal()));
                    // TODO
                }
                case ReturnNode ret -> {
                    Register left = ret.predecessor(BinaryOperationNode.LEFT).resultRegister();
                    Register right = ret.predecessor(BinaryOperationNode.RIGHT).resultRegister();
                    ret.setResultRegister(new VirtualRegister(SPECIAL_REGISTERS.RAX.ordinal()));

                    if (left == null) {
                        // right is return value
                        appendIndentedLine(builder, "mov", right, ret.resultRegister());
                        appendIndentedLine(builder, "ret", "");
                    } else if (right == null) {
                        // left is return value
                        appendIndentedLine(builder, "mov", left, ret.resultRegister());
                        appendIndentedLine(builder, "ret", "");
                    } else {
                        throw new AssertionError("ReturnNode has two null registers as predecessors. Maybe check for more predecessors?");
                    }
                }
                case Phi _ -> throw new UnsupportedOperationException("phi");
                case Block _, ProjNode _, StartNode _ -> {
                    // do nothing
                    return;
                }
            }
        }
    }

    public String generateCode(List<IrGraph> program) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                .global main
                .global _main
                .text
                
                main:
                call _main
                movq %rax, %rdi
                movq $0x3C, %rax
                syscall
                
                """);


        for (IrGraph graph : program) {
            builder.append("_")
                .append(graph.name())
                .append(":\n");

            // Second to last Node is *always* return statement
            ReturnNode returnNode = (ReturnNode) graph.endBlock().predecessor(0);
            Set<Node> visited = new HashSet<>();
            Stack<Node> stack = new Stack<>();
            visited.add(returnNode);
            stack.add(returnNode);
            dfs(visited, stack, new CountingRegisterAllocator(), builder);
        }
/*
        for (IrGraph graph : program) {
            AasmRegisterAllocator allocator = new AasmRegisterAllocator();
            Map<Node, Register> registers = allocator.allocateRegisters(graph);

            builder.append("_")
                    .append(graph.name())
                    .append(":\n");
            generateForGraph(graph, builder, registers);
        }*/
        return builder.toString();
    }

    private void generateForGraph(IrGraph graph, StringBuilder builder, Map<Node, Register> registers) {
        Set<Node> visited = new HashSet<>();
        scan(graph.endBlock(), visited, builder, registers);
    }

    private void scan(Node node, Set<Node> visited, StringBuilder builder, Map<Node, Register> registers) {
        for (Node predecessor : node.predecessors()) {
            if (visited.add(predecessor)) {
                scan(predecessor, visited, builder, registers);
            }
        }

        switch (node) {
            case AddNode add -> simpleBinaryOp(builder, registers, add, "add");
            case SubNode sub -> simpleBinaryOp(builder, registers, sub, "sub");
            case MulNode mul -> simpleBinaryOp(builder, registers, mul, "imul");
            case DivNode div -> binaryDivMod(builder, registers, div, "div");
            case ModNode mod -> binaryDivMod(builder, registers, mod, "div");
            case ReturnNode r -> appendIndentedLine(builder, "ret", "");
            case ConstIntNode c -> appendIndentedLine(builder, "mov", c.value(), registers.get(c));
            case Phi _ -> throw new UnsupportedOperationException("phi");
            case Block _, ProjNode _, StartNode _ -> {
                // do nothing, skip line break
                return;
            }
        }
        //builder.append("\n");
    }

    private static void simpleBinaryOp(
            StringBuilder builder,
            Map<Node, Register> registers,
            BinaryOperationNode node,
            String opcode
    ) {
        // right = left <op> right
        // TODO check best result register
        appendIndentedLine(builder, opcode, registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT)),
                registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)));
        // TODO sub andersrum!
    }

    private static void binaryDivMod(
            StringBuilder builder,
            Map<Node, Register> registers,
            BinaryOperationNode node,
            String opcode
    ) {
        // %rax = %rax </> %rcx <- nothing to do after op
        // %rdx = %rax <%> %rcx
        appendIndentedLine(builder, "mov", 0, "%rdx");
        Register reg;
        // check if first operand is already in %rax
        if (!"%0".equals((reg = registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT))).toString())) {
            appendIndentedLine(builder, "mov", reg, "%rax");
        }
        // check if second operand is already in %rcx
        if (!"%2".equals((reg = registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT))).toString())) {
            appendIndentedLine(builder, "mov", reg, "%rcx");
        }
        // perform operation
        appendIndentedLine(builder, opcode, "%rcx");
        // put correct result in %rax
        if (node instanceof ModNode) {
            appendIndentedLine(builder, "mov", "%rdx", "%rax");
        }
    }

    private static String mapRegistersToAasm(Register reg) {
        int regNo = reg.getRegisterNo();
        if (regNo < 0 || regNo >= registers_64.length) {
            // TODO variable needs to be put on stack
            throw new IllegalArgumentException("Invalid register number: " + regNo);
        }
        return registers_64[reg.getRegisterNo()];
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, String reg) {
        builder.repeat(" ", indent)
                .append(opcode)
                .append(" ")
                .append(reg)
                .append("\n");
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, Register reg) {
        builder.repeat(" ", indent)
                .append(opcode)
                .append(" ")
                .append(mapRegistersToAasm(reg))
                .append("\n");
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, int val, String reg) {
        builder.repeat(" ", indent)
                .append(opcode)
                .append(" $")
                .append(val)
                .append(", ")
                .append(reg)
                .append("\n");
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, int val, Register reg) {
        builder.repeat(" ", indent)
                .append(opcode)
                .append(" $")
                .append(val)
                .append(", ")
                .append(mapRegistersToAasm(reg))
                .append("\n");
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, String regA, String regB) {
        builder.repeat(" ", indent)
                .append(opcode)
                .append(" ")
                .append(regA)
                .append(", ")
                .append(regB)
                .append("\n");
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, Register regA, String regB) {
        builder.repeat(" ", indent)
                .append(opcode)
                .append(" ")
                .append(mapRegistersToAasm(regA))
                .append(", ")
                .append(regB)
                .append("\n");
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, String regA, Register regB) {
        builder.repeat(" ", indent)
                .append(opcode)
                .append(" ")
                .append(regA)
                .append(", ")
                .append(mapRegistersToAasm(regB))
                .append("\n");
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, Register regA, Register regB) {
        builder.repeat(" ", indent)
                .append(opcode)
                .append(" ")
                .append(mapRegistersToAasm(regA))
                .append(", ")
                .append(mapRegistersToAasm(regB))
                .append("\n");
    }
}
