package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.*;
import edu.kit.kastel.vads.compiler.backend.statements.MoveStatement;
import edu.kit.kastel.vads.compiler.backend.statements.Statement;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.*;

import java.util.*;
import java.util.function.Predicate;

public class CodeGenerator {

    public final static int INDENT = 2;

    public final static String[] registers_64 = new String[]{
            "%eax", "%ebx", "%ecx", "%edx", "%esi", "%edi", "%esp", "%ebp",
            "%r8d",  "%r9d",  "%r10d", "%r11d", "%r12d", "%r13d", "%r14d", "%r15d"
    };

    private void dfs(Set<Node> visited, ArrayList<Node> stack, CountingRegisterAllocator allocator, StringBuilder builder, ArrayList<Statement> statements) {
        while (!stack.isEmpty()) {
            Node active = stack.removeLast();
            for (Node predecessor : active.predecessors()) {
                if (visited.add(predecessor) || predecessor instanceof ConstIntNode) {
                    stack.add(predecessor);
                    dfs(visited, stack, allocator, builder, statements);
                }
            }

            switch (active) {
                case ConstIntNode c -> {

                    if (!c.getConstructed()) {
                        c.flagConstructed();
                        //c.setResultRegister(new VirtualRegister(allocator.getNew()));

                        IrGraph graph = c.graph();
                        List<Node> successors = graph.successors(c).stream().toList();


                        for (Node successor : successors) {
                            ConstIntNode clone = new ConstIntNode(c.block(), c.value());
                            successor.setPredecessor(successor.predecessors().indexOf(c), clone);
                            clone.setResultRegister(new VirtualRegister(allocator.getNew()));
                            clone.flagConstructed();
                            statements.add(new MoveStatement("mov", Optional.of(new ConstValue(clone.value())), Optional.of(clone.resultRegister())));

                        }
                    }

//                    c.setResultRegister(new VirtualRegister(allocator.getNew()));
//                    statements.add(new MoveStatement("mov", Optional.of(new ConstValue(c.value())), Optional.of(c.resultRegister())));
                    // appendIndentedLine(builder, "mov", c.value(), c.resultRegister());
                }
                case AddNode add -> {
                    Register left = add.predecessor(BinaryOperationNode.LEFT).resultRegister();
                    Register right = add.predecessor(BinaryOperationNode.RIGHT).resultRegister();
                    add.setResultRegister(right);
                    statements.add(new MoveStatement("add", Optional.of(left), Optional.of(right)));
                    // appendIndentedLine(builder, "add", left, right);
                }
                case SubNode sub -> {
                    Register left = sub.predecessor(BinaryOperationNode.LEFT).resultRegister();
                    Register right = sub.predecessor(BinaryOperationNode.RIGHT).resultRegister();
                    sub.setResultRegister(left);
                    statements.add(new MoveStatement("sub", Optional.of(right), Optional.of(left)));
                    // appendIndentedLine(builder, "sub", right, left);
                }
                case MulNode mul -> {
                    Register left = mul.predecessor(BinaryOperationNode.LEFT).resultRegister();
                    Register right = mul.predecessor(BinaryOperationNode.RIGHT).resultRegister();
                    mul.setResultRegister(right);
                    statements.add(new MoveStatement("imul", Optional.of(left), Optional.of(right)));
                    // appendIndentedLine(builder, "imul", left, right);
                }
                case DivNode div -> {
                    // write return register into child node (IDK why this is so complicated :/)
                    div.graph().successors(div).forEach(suc -> handleProjNode(suc, SPECIAL_REGISTERS.RAX));
                    divModOperation(div, builder, statements);
                }
                case ModNode mod -> {
                    mod.graph().successors(mod).forEach(su -> handleProjNode(su, SPECIAL_REGISTERS.RDX));
                    divModOperation(mod, builder, statements);
                    // put correct result in %rax
                    statements.add(new MoveStatement("mov", new SpecialRegister(SPECIAL_REGISTERS.RDX), new SpecialRegister(SPECIAL_REGISTERS.RAX)));
                    // appendIndentedLine(builder, "mov", "%rdx", "%rax");
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
                                statements.add(new MoveStatement("mov", new SpecialRegister(result_register.getRegisterNo()), new SpecialRegister(SPECIAL_REGISTERS.RAX)));
                            } else {
                                statements.add(new MoveStatement("mov", Optional.of(result_register), new SpecialRegister(SPECIAL_REGISTERS.RAX)));
                            }

                            statements.add(new MoveStatement("ret", Optional.empty(), Optional.empty()));

                            // appendIndentedLine(builder, "mov", predecessor.resultRegister(), raxRegister);
                            // appendIndentedLine(builder, "ret", "");
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

    Predicate<Node> isValidOpNode = predecessor ->
            (predecessor instanceof ProjNode node && node.projectionInfo() == ProjNode.SimpleProjectionInfo.RESULT)
                    || predecessor instanceof BinaryOperationNode
                    || predecessor instanceof ConstIntNode;

    private static void divModOperation(Node opNode, StringBuilder builder, ArrayList<Statement> statements) {
        // %rax = %rax </> %rcx <- nothing to do after op
        // %rdx = %rax <%> %rcx <- move necessary (handle in caller)
        Register left = opNode.predecessor(BinaryOperationNode.LEFT).resultRegister();
        Register right = opNode.predecessor(BinaryOperationNode.RIGHT).resultRegister();

        statements.add(new MoveStatement("mov", Optional.of(new ConstValue(0)), new SpecialRegister(SPECIAL_REGISTERS.RDX)));
        statements.add(new MoveStatement("mov", Optional.of(left), new SpecialRegister(SPECIAL_REGISTERS.RAX)));
        statements.add(new MoveStatement("cdq", Optional.empty(), Optional.empty()));
        statements.add(new MoveStatement("idiv", Optional.empty(), Optional.of(right)));

        // appendIndentedLine(builder, "mov", 0, "%rdx");
        // appendIndentedLine(builder, "mov", left, "%rax");
        // appendIndentedLine(builder, "div", right);
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
            builder.append("_")
                    .append(graph.name())
                    .append(":\n");

            // Second to last Node is *always* return statement
            ReturnNode returnNode = (ReturnNode) graph.endBlock().predecessor(0);
            Set<Node> visited = new HashSet<>();
            ArrayList<Node> stack = new ArrayList<>();
            visited.add(returnNode);
            stack.add(returnNode);

            ArrayList<Statement> statements = new ArrayList<>();

            dfs(visited, stack, new CountingRegisterAllocator(), builder, statements);

            allocateRegisters(statements, builder);
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

    class LiveInterval {
        private final int start;
        private int end;

        private final VirtualRegister virtual;

        public LiveInterval(int start, int end, VirtualRegister virtual) {
            this.start = start;
            this.end = end;
            this.virtual = virtual;

            if (virtual.getRegisterNo() == 0) {
                System.out.println("Entered");
            }
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public void setEnd(int end) {
            this.end = end;
        }

        public Register getVirtual() {
            return virtual;
        }
    }

    public ArrayList<LiveInterval> createSortedIntervals(List<Statement> statements) {
        HashMap<VirtualRegister, LiveInterval> intervals = new HashMap<>();

        for (int line = 0; line < statements.size(); line++) {
            for (VirtualRegister v: statements.get(line).getUsedRegisters()) {
                if (!intervals.containsKey(v)) {
                    intervals.put(v, new LiveInterval(line, line, v));
                } else {
                    LiveInterval inter = intervals.get(v);
                    inter.setEnd(line);
                }
            }
        }

        ArrayList<LiveInterval> result = new ArrayList<>(intervals.values().stream().toList());
        result.sort(Comparator.comparingInt(LiveInterval::getStart));

        return result;
    }

    public String mapToString(USABLE_REGISTERS register) {
        switch (register) {
            case RAX -> {
                return "%rax";
            }
            case RBX -> {
                return "%rbx";
            }
            case RDX -> {
                return "%rdx";
            }
            case RSI -> {
                return "%rsi";
            }
            case RBP -> {
                return "%rbp";
            }
            case RDI -> {
                return "%rdi";
            }
            case RCX -> {
                return "%rcx";
            }
            case RSP -> {
                return "%rsp";
            }
            case R8 -> {
                return "%r8";
            }
            case R9 -> {
                return "%r9";
            }
            case R10 -> {
                return "%r10";
            }
            case R11 -> {
                return "%r11";
            }
            case R12 -> {
                return "%r12";
            }
            case R13 -> {
                return "%r13";
            }
            case R14 -> {
                return "%r14";
            }
            case R15 -> {
                return "%r15";
            }
            case SPILL -> {
                return "%spill";
            }

        }
        return "";
    }

    private void allocateRegisters(ArrayList<Statement> statements, StringBuilder builder) {
        ArrayList<LiveInterval> liveIntervals = createSortedIntervals(statements);

        System.out.println(liveIntervals);

        List<LiveInterval> activeIntervals = new ArrayList<>();
        ArrayList<USABLE_REGISTERS> freeRegister = new ArrayList<>(Arrays.asList(
            USABLE_REGISTERS.R8,
            USABLE_REGISTERS.R9,
            USABLE_REGISTERS.R10,
            USABLE_REGISTERS.R11,
            USABLE_REGISTERS.R12,
            USABLE_REGISTERS.R13,
            USABLE_REGISTERS.R14,
            USABLE_REGISTERS.R15)
        );
        
        HashMap<VirtualRegister, USABLE_REGISTERS> allocations = new HashMap<>();

        for (LiveInterval interval: liveIntervals) {
            activeIntervals = new ArrayList<>(activeIntervals.stream()
                    .filter(inter -> inter.getEnd() > interval.getStart())
                    .toList());

            if (activeIntervals.size() < freeRegister.size()) {
                HashSet<USABLE_REGISTERS> usedRegisters = new HashSet<>();
                for (LiveInterval active: activeIntervals) {
                    usedRegisters.add(allocations.get(active.getVirtual()));
                }

                USABLE_REGISTERS assigned = freeRegister.stream()
                        .filter(free -> !usedRegisters.contains(free)).findFirst().get();

                allocations.put((VirtualRegister) interval.getVirtual(), assigned);
                activeIntervals.add(interval);
            } else {
                allocations.put((VirtualRegister) interval.getVirtual(), USABLE_REGISTERS.SPILL);
            }
        }

        System.out.println("Allocated registers:");
        for (VirtualRegister allocs: allocations.keySet()) {
            System.out.println("  " + allocs + ", " + mapToString(allocations.get(allocs)));
        }

        System.out.println("Program with virtual registers:");
        for (Statement statement: statements) {
            System.out.println(statement);
        }

        System.out.println("\nProgram with assigned registers:");
        for (Statement statement: statements) {
            for (VirtualRegister reg: statement.getUsedRegisters()) {
                statement.assign(reg, allocations.get(reg));
            }

            System.out.println(statement);

            builder.repeat(" ", INDENT)
                    .append(statement)
                    .append("\n");
        }

    }

    /*
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
    */

    public static String mapRegistersToAasm(Register reg) {
        int regNo = reg.getRegisterNo();
        if (regNo < 0 || regNo >= registers_64.length) {
            // TODO variable needs to be put on stack
            return "%spill";
            //throw new IllegalArgumentException("Invalid register number: " + regNo);
        }
        return registers_64[reg.getRegisterNo()];
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, String reg) {
        builder.repeat(" ", INDENT)
                .append(opcode)
                .append(" ")
                .append(reg)
                .append("\n");
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, Register reg) {
        builder.repeat(" ", INDENT)
                .append(opcode)
                .append(" ")
                .append(mapRegistersToAasm(reg))
                .append("\n");
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, int val, String reg) {
        builder.repeat(" ", INDENT)
                .append(opcode)
                .append(" $")
                .append(val)
                .append(", ")
                .append(reg)
                .append("\n");
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, int val, Register reg) {
        builder.repeat(" ", INDENT)
                .append(opcode)
                .append(" $")
                .append(val)
                .append(", ")
                .append(mapRegistersToAasm(reg))
                .append("\n");
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, String regA, String regB) {
        builder.repeat(" ", INDENT)
                .append(opcode)
                .append(" ")
                .append(regA)
                .append(", ")
                .append(regB)
                .append("\n");
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, Register regA, String regB) {
        builder.repeat(" ", INDENT)
                .append(opcode)
                .append(" ")
                .append(mapRegistersToAasm(regA))
                .append(", ")
                .append(regB)
                .append("\n");
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, String regA, Register regB) {
        builder.repeat(" ", INDENT)
                .append(opcode)
                .append(" ")
                .append(regA)
                .append(", ")
                .append(mapRegistersToAasm(regB))
                .append("\n");
    }

    private static void appendIndentedLine(StringBuilder builder, String opcode, Register regA, Register regB) {
        builder.repeat(" ", INDENT)
                .append(opcode)
                .append(" ")
                .append(mapRegistersToAasm(regA))
                .append(", ")
                .append(mapRegistersToAasm(regB))
                .append("\n");
    }
}
