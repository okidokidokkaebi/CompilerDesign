package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class CodeGenerator {

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
            AasmRegisterAllocator allocator = new AasmRegisterAllocator();
            Map<Node, Register> registers = allocator.allocateRegisters(graph);

            builder.append("_")
                    .append(graph.name())
                    .append(":\n");
            generateForGraph(graph, builder, registers);
        }
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
            case ReturnNode r -> builder.repeat(" ", 2).append("ret ")
                    .append(registers.get(predecessorSkipProj(r, ReturnNode.RESULT)));
            case ConstIntNode c -> builder.repeat(" ", 2)
                    .append("mov $")
                    .append(c.value())
                    .append(", ")
                    .append(registers.get(c));
            case Phi _ -> throw new UnsupportedOperationException("phi");
            case Block _, ProjNode _, StartNode _ -> {
                // do nothing, skip line break
                return;
            }
        }
        builder.append("\n");
    }

    private static void simpleBinaryOp(
            StringBuilder builder,
            Map<Node, Register> registers,
            BinaryOperationNode node,
            String opcode
    ) {
        // right = left <op> right
        // TODO map registers to aasm registers
        // TODO check best result register
        builder.repeat(" ", 2)
                .append(opcode)
                .append(" ")
                .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT)))
                .append(", ")
                .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)));
    }

    private static void binaryDivMod(
            StringBuilder builder,
            Map<Node, Register> registers,
            BinaryOperationNode node,
            String opcode
    ) {
        // %rcx = %rax <div> %rcx
        // %rdx = %rax <mod> %rcx
        builder.repeat(" ", 2)
                .append("mov $0, %rdx\n");
        Register reg;
        // TODO map registers to aasm registers
        // check if first operand is already in %rax
        if (!"%rax".equals((reg = registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT))).toString())) {
            builder.repeat(" ", 2)
                    .append("mov ")
                    .append(reg)
                    .append(", %rax\n");
        }
        // check if second operand is already in %rcx
        if (!"%rcx".equals((reg = registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT))).toString())) {
            builder.repeat(" ", 2)
                    .append("mov ")
                    .append(reg)
                    .append(", %rcx\n");
        }
        // perform operation
        builder.repeat(" ", 2).append("div %rcx\n");
        if (node instanceof ModNode) {
            builder.repeat(" ", 2).append("mov %rdx, %rax\n");
        }
    }

    private static void binary(
            StringBuilder builder,
            Map<Node, Register> registers,
            BinaryOperationNode node,
            String opcode
    ) {
        builder.repeat(" ", 2).append(registers.get(node))
                .append(" = ")
                .append(opcode)
                .append(" ")
                .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT)))
                .append(" ")
                .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)));
    }
}
