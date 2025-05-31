package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.*;
import edu.kit.kastel.vads.compiler.backend.statements.ConcreteStatement;
import edu.kit.kastel.vads.compiler.backend.statements.Statement;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.*;

import java.util.*;
import java.util.function.Predicate;

import static edu.kit.kastel.vads.compiler.backend.regalloc.StatementRegisterAllocator.allocateRegisters;
import static edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode.LEFT;
import static edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode.RIGHT;

public class CodeGenerator {

    public final static int INDENT = 2;

    public final static String[] registers_64 = new String[]{
            "%eax", "%ebx", "%ecx", "%edx", "%esi", "%edi", "%esp", "%ebp",
            "%r8d",  "%r9d",  "%r10d", "%r11d", "%r12d", "%r13d", "%r14d", "%r15d"
    };

    private void dfs(Set<Node> visited, List<Node> stack, CountingRegisterAllocator allocator, List<Statement> statements) {
        while (!stack.isEmpty()) {
            //System.out.println("\nStack: " + stack);
            //System.out.println("Visited: " + visited);
            Node active = stack.removeLast();
            for (Node predecessor : active.predecessors()) {
                if (visited.add(predecessor) || predecessor instanceof ConstIntNode) {
                    stack.add(predecessor);
                    dfs(visited, stack, allocator, statements);
                }
            }
            //System.out.println("IS on active node: " + active + " with predecessors: " + active.predecessors());

            switch (active) {
                case ConstIntNode c -> {
                    if (!c.getConstructed()) {
                        c.setResultRegister(new VirtualRegister(allocator.getNew()));
                        c.flagConstructed();
                        statements.add(new ConcreteStatement("mov", Optional.of(new ConstValue(c.value())), Optional.of(c.resultRegister())));
                    }
                }
                case AddNode add -> {
                    Register left = add.predecessor(LEFT).resultRegister();
                    Register right = add.predecessor(RIGHT).resultRegister();

                    Register result = new VirtualRegister(allocator.getNew());
                    statements.add(new ConcreteStatement("mov", Optional.of(right), Optional.of(result)));

                    add.setResultRegister(result);
                    statements.add(new ConcreteStatement("add", Optional.of(left), Optional.of(add.resultRegister())));
                }
                case SubNode sub -> {
                    Register left = sub.predecessor(LEFT).resultRegister();
                    Register right = sub.predecessor(RIGHT).resultRegister();

                    Register result = new VirtualRegister(allocator.getNew());
                    sub.setResultRegister(result);

                    statements.add(new ConcreteStatement("mov", Optional.of(left), Optional.of(result)));
                    statements.add(new ConcreteStatement("sub", Optional.of(right), Optional.of(result)));
                }
                case MulNode mul -> {
                    Register left = mul.predecessor(LEFT).resultRegister();
                    Register right = mul.predecessor(RIGHT).resultRegister();

                    Register result = new VirtualRegister(allocator.getNew());
                    mul.setResultRegister(result);

                    statements.add(new ConcreteStatement("mov", Optional.of(right), Optional.of(result)));
                    statements.add(new ConcreteStatement("imul", Optional.of(left), Optional.of(result)));
                }
                case DivNode div -> {
                    divModOperation(div, statements, allocator);
                }
                case ModNode mod -> {
                    divModOperation(mod, statements, allocator);
                    statements.add(new ConcreteStatement("mov", Optional.of(new SpecialRegister(SPECIAL_REGISTERS.RDX)), Optional.of(mod.resultRegister())));
                }
                case ReturnNode ret -> {

                    for (Node predecessor : ret.predecessors()) {
                        if (predecessor instanceof ProjNode projNode) {
                            if (projNode.projectionInfo() == ProjNode.SimpleProjectionInfo.SIDE_EFFECT) {
                                continue;
                            }
                        }
                        if (isValidOpNode.test(predecessor)) {
                            Register result_register = predecessor.resultRegister();
                            if (result_register.getRegisterNo() < 8) {
                                statements.add(new ConcreteStatement("mov", new SpecialRegister(result_register.getRegisterNo()), new SpecialRegister(SPECIAL_REGISTERS.RAX)));
                            } else {
                                statements.add(new ConcreteStatement("mov", Optional.of(result_register), new SpecialRegister(SPECIAL_REGISTERS.RAX)));
                            }
                            statements.add(new ConcreteStatement("ret", Optional.empty(), Optional.empty()));

                        } else {
                            throw new IllegalStateException("ReturnNode has an unexpected predecessor: " + predecessor.getClass().getName());
                        }
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

    Predicate<Node> isValidOpNode = predecessor -> (predecessor instanceof ProjNode node && node.projectionInfo() == ProjNode.SimpleProjectionInfo.RESULT) || predecessor instanceof BinaryOperationNode || predecessor instanceof ConstIntNode;

    private static void divModOperation(Node opNode, List<Statement> statements, CountingRegisterAllocator allocator) {
        // %rax = %rax </> %rcx <- nothing to do after op
        // %rdx = %rax <%> %rcx <- move necessary (handle in caller)
        Register left = opNode.predecessor(LEFT).resultRegister();
        Register right = opNode.predecessor(RIGHT).resultRegister();

        statements.add(new ConcreteStatement("mov", Optional.of(new ConstValue(0)), new SpecialRegister(SPECIAL_REGISTERS.RDX)));
        statements.add(new ConcreteStatement("mov", Optional.of(left), new SpecialRegister(SPECIAL_REGISTERS.RAX)));
        statements.add(new ConcreteStatement("cdq", Optional.empty(), Optional.empty()));
        statements.add(new ConcreteStatement("idiv", Optional.empty(), Optional.of(right)));

        Register result = new VirtualRegister(allocator.getNew());
        // We set the result register for this node to the same register, for modulo operations
        opNode.setResultRegister(result);

        // set Proj RESULT resultRegister
        opNode.graph().successors(opNode).forEach(suc -> {
            if (suc instanceof ProjNode projNode) {
                projNode.setResultRegister(result);
            } else {
                throw new IllegalStateException("ProjNode has an unexpected predecessor: " + suc.getClass().getName());
            }
        });

        statements.add(new ConcreteStatement("mov", Optional.of(new SpecialRegister(SPECIAL_REGISTERS.RAX)), Optional.of(result)));
    }

    private static void handleProjNode(Node node, SPECIAL_REGISTERS reg) {
        if (node instanceof ProjNode projNode) {
            if (projNode.projectionInfo() == ProjNode.SimpleProjectionInfo.RESULT) {
                if (reg.ordinal() < 8) {
                    projNode.setResultRegister(new SpecialRegister(reg.ordinal()));
                } else {
                    projNode.setResultRegister(new VirtualRegister(reg.ordinal()));
                }
            }
        }
    }

    public String generateCode(List<IrGraph> program) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                .global main
                .global _main
                .global _send_sigfpe
                .text
                
                main:
                call _main
                movq %rax, %rdi
                movq $0x3C, %rax
                syscall
                
                _send_sigfpe:
                    movq 62, %rax
                    movq 0, %rdi
                    movq 8, %rsi
                    syscall
                
                """);


        for (IrGraph graph : program) {
            builder.append("_").append(graph.name()).append(":\n");

            // Second to last Node is *always* return statement
            ReturnNode returnNode = (ReturnNode) graph.endBlock().predecessor(0);
            Set<Node> visited = new HashSet<>();
            List<Node> stack = new ArrayList<>();
            visited.add(returnNode);
            stack.add(returnNode);

            List<Statement> statements = new ArrayList<>();

            dfs(visited, stack, new CountingRegisterAllocator(), statements);

            allocateRegisters(statements, builder);
        }
        return builder.toString();
    }


}
