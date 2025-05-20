package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.AddNode;
import edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
import edu.kit.kastel.vads.compiler.ir.node.DivNode;
import edu.kit.kastel.vads.compiler.ir.node.ModNode;
import edu.kit.kastel.vads.compiler.ir.node.MulNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.Phi;
import edu.kit.kastel.vads.compiler.ir.node.ProjNode;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;
import edu.kit.kastel.vads.compiler.ir.node.StartNode;
import edu.kit.kastel.vads.compiler.ir.node.SubNode;

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
            case AddNode add -> binary_src_dst(builder, registers, add, "add");
            case SubNode sub -> binary_src_dst(builder, registers, sub, "sub");
            case MulNode mul -> binary_src_dst(builder, registers, mul, "imul");
            case DivNode div -> binary_div_mod(builder, registers, div, "div");
            case ModNode mod -> binary_div_mod(builder, registers, mod, "mod");
            case ReturnNode r ->
                  builder.repeat(" ", 2).append("ret %rax");

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

    private static void binary_src_dst(
            StringBuilder builder,
            Map<Node, Register> registers,
            BinaryOperationNode node,
            String opcode
    ) {

        builder.repeat(" ", 2)
                .append(opcode)
                .append(" ")
                .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT)))
                .append(", ")
                .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)))
                .append("\n");

        if (registers.get(predecessorSkipProj(node.graph().successors(node).stream().toList().getFirst(), BinaryOperationNode.LEFT)) == null) {
            builder.repeat(" ", 2)
                    .append("mov ")
                    .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)))
                    .append(", ")
                    .append("%rax");
        } else {
            builder.repeat(" ", 2)
                    .append("mov ")
                    .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)))
                    .append(", ")
                    .append(registers.get(predecessorSkipProj(node.graph().successors(node).stream().toList().getFirst(), BinaryOperationNode.LEFT)));
        }
    }

    private static void binary_div_mod(
            StringBuilder builder,
            Map<Node, Register> registers,
            BinaryOperationNode node,
            String opcode
    ) {
        builder.repeat(" ", 2)
                .append("mov %rdx, 0\n")
                .append("mov %rax, ")
                .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT)))
                .append("\n")
                .append("mov %rcx, ")
                .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)))
                .append("\n")
                .append("div %rcx\n")
        ;
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
