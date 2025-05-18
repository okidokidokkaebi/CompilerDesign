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

import java.util.*;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class CodeGenerator {
    ArrayList<Node> node_stack;

    public String generateCode(List<IrGraph> program) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                .global main
                .global _main
                .text
                main:
                call _main
                ; move the return value into the first argument for the syscall
                movq %rax, %rdi
                ; move the exit syscall number into rax
                movq $0x3C, %rax
                syscall
                """);

        this.node_stack = new ArrayList<>();

        for (IrGraph graph : program) {
            AasmRegisterAllocator allocator = new AasmRegisterAllocator();
            Map<Node, Register> registers = allocator.allocateRegisters(graph);

            builder.append("_")
                    .append(graph.name())
                    .append(":\n");
            generateForGraph(graph, builder, registers);

            if (!(this.node_stack.removeLast() instanceof Block)) {
                throw new AssertionError("Could not Optimize Tree");
            }

            if (!(this.node_stack.removeLast() instanceof ReturnNode)) {
                throw new AssertionError("Could not Optimize Tree");
            }

            Node last = this.node_stack.removeLast();

            if (!(last instanceof ConstIntNode)) {
                throw new AssertionError("Could not Optimize Tree");
            }

            builder.append("ret ")
                    .append("$")
                    .append(((ConstIntNode) last).value())
                    .append("\n");
        }

//        for (Node node : node_stack) {
//            System.out.println(node);
//        }

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

        // ------------------
        //this.node_stack.add(node);
        switch (node) {
            case AddNode add -> try_binary_op(add);
            case SubNode sub -> try_binary_op(sub);
            case MulNode mul -> try_binary_op(mul);
            case DivNode div -> try_binary_op(div);
            case ModNode mod -> try_binary_op(mod);
            default -> {
                this.node_stack.add(node);
            }
        }
        // ------------------
//        switch (node) {
//            case AddNode add -> binary_src_dst(builder, registers, add, "add");
//            case SubNode sub -> binary_src_dst(builder, registers, sub, "sub");
//            case MulNode mul -> binary_src_dst(builder, registers, mul, "imul");
//            case DivNode div -> binary_div_mod(builder, registers, div, "div");
//            case ModNode mod -> binary_div_mod(builder, registers, mod, "mod");
//            case ReturnNode r -> builder.repeat(" ", 2).append("ret ")
//                    .append(registers.get(predecessorSkipProj(r, ReturnNode.RESULT)));
//            case ConstIntNode c -> builder.repeat(" ", 2)
//                    .append("mov $")
//                    .append(c.value())
//                    .append(", ")
//                    .append(registers.get(c));
//            case Phi _ -> throw new UnsupportedOperationException("phi");
//            case Block _, ProjNode _, StartNode _ -> {
//                // do nothing, skip line break
//                return;
//            }
//        }
//        builder.append("\n");
    }

    // ------------------
    private void try_binary_op(Node operator) {
        Node right = this.node_stack.removeLast();
        Node left = this.node_stack.removeLast();
        
        if (right instanceof ConstIntNode) {
            if (left instanceof ConstIntNode) {
                Block Block;
                switch (operator) {
                    case AddNode add -> this.node_stack.add(new ConstIntNode(add.block(),
                            ((ConstIntNode) left).value() + ((ConstIntNode) right).value()));
                    case SubNode sub -> this.node_stack.add(new ConstIntNode(sub.block(),
                            ((ConstIntNode) left).value() - ((ConstIntNode) right).value()));
                    case MulNode mul -> this.node_stack.add(new ConstIntNode(mul.block(),
                            ((ConstIntNode) left).value() * ((ConstIntNode) right).value()));
                    case DivNode div -> this.node_stack.add(new ConstIntNode(div.block(),
                            ((ConstIntNode) left).value() / ((ConstIntNode) right).value()));
                    case ModNode mod -> this.node_stack.add(new ConstIntNode(mod.block(),
                            ((ConstIntNode) left).value() % ((ConstIntNode) right).value()));
                    default -> {
                        this.node_stack.add(left);
                        this.node_stack.add(right);
                    }
                }
            }
        }
    }
    // ------------------

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
                .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)));
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
