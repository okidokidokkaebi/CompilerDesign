package edu.kit.kastel.vads.compiler.backend.regalloc;

import edu.kit.kastel.vads.compiler.backend.aasm.VirtualRegister;
import edu.kit.kastel.vads.compiler.backend.statements.Statement;

import java.util.*;

import static edu.kit.kastel.vads.compiler.backend.aasm.CodeGenerator.INDENT;
import static edu.kit.kastel.vads.compiler.backend.aasm.CodeGenerator.registers_64;

public class StatementRegisterAllocator {

    static class LiveInterval {
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

    private static ArrayList<LiveInterval> createSortedIntervals(List<Statement> statements) {
        HashMap<VirtualRegister, LiveInterval> intervals = new HashMap<>();

        for (int line = 0; line < statements.size(); line++) {
            for (VirtualRegister v : statements.get(line).getUsedRegisters()) {
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

    public static String mapToString(USABLE_REGISTERS register) {
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

    static public void allocateRegisters(List<Statement> statements, StringBuilder builder) {
        ArrayList<LiveInterval> liveIntervals = createSortedIntervals(statements);

        System.out.println(liveIntervals);

        List<LiveInterval> activeIntervals = new ArrayList<>();
        ArrayList<USABLE_REGISTERS> freeRegister = new ArrayList<>(Arrays.asList(USABLE_REGISTERS.R8, USABLE_REGISTERS.R9, USABLE_REGISTERS.R10, USABLE_REGISTERS.R11, USABLE_REGISTERS.R12, USABLE_REGISTERS.R13, USABLE_REGISTERS.R14, USABLE_REGISTERS.R15));

        HashMap<VirtualRegister, USABLE_REGISTERS> allocations = new HashMap<>();

        for (LiveInterval interval : liveIntervals) {
            activeIntervals = new ArrayList<>(activeIntervals.stream().filter(inter -> inter.getEnd() > interval.getStart()).toList());

            if (activeIntervals.size() < freeRegister.size()) {
                HashSet<USABLE_REGISTERS> usedRegisters = new HashSet<>();
                for (LiveInterval active : activeIntervals) {
                    usedRegisters.add(allocations.get(active.getVirtual()));
                }

                USABLE_REGISTERS assigned = freeRegister.stream().filter(free -> !usedRegisters.contains(free)).findFirst().get();

                allocations.put((VirtualRegister) interval.getVirtual(), assigned);
                activeIntervals.add(interval);
            } else {
                allocations.put((VirtualRegister) interval.getVirtual(), USABLE_REGISTERS.SPILL);
            }
        }

        System.out.println("Allocated registers:");
        for (VirtualRegister allocs : allocations.keySet()) {
            System.out.println("  " + allocs + ", " + mapToString(allocations.get(allocs)));
        }

        System.out.println("Program with virtual registers:");
        for (Statement statement : statements) {
            System.out.println(statement);
        }

        System.out.println("\nProgram with assigned registers:");
        for (Statement statement : statements) {
            for (VirtualRegister reg : statement.getUsedRegisters()) {
                statement.assign(reg, allocations.get(reg));
            }

            System.out.println(statement);

            builder.repeat(" ", INDENT).append(statement).append("\n");
        }

    }

    public static String mapRegistersToAasm(Register reg) {
        int regNo = reg.getRegisterNo();
        if (regNo < 0 || regNo >= registers_64.length) {
            // TODO variable needs to be put on stack
            return "%spill";
        }
        return registers_64[reg.getRegisterNo()];
    }
}
