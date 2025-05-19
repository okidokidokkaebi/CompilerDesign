package edu.kit.kastel.vads.compiler;

import edu.kit.kastel.vads.compiler.backend.aasm.CodeGenerator;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.SsaTranslation;
import edu.kit.kastel.vads.compiler.ir.optimize.LocalValueNumbering;
import edu.kit.kastel.vads.compiler.ir.util.YCompPrinter;
import edu.kit.kastel.vads.compiler.lexer.Lexer;
import edu.kit.kastel.vads.compiler.parser.ParseException;
import edu.kit.kastel.vads.compiler.parser.Parser;
import edu.kit.kastel.vads.compiler.parser.TokenSource;
import edu.kit.kastel.vads.compiler.parser.ast.FunctionTree;
import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.semantic.SemanticAnalysis;
import edu.kit.kastel.vads.compiler.semantic.SemanticException;
import util.LiveRange;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Invalid arguments: Expected one input file and one output file");
            System.exit(3);
        }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        ProgramTree program = lexAndParse(input);
        try {
            new SemanticAnalysis(program).analyze();
        } catch (SemanticException e) {
            e.printStackTrace();
            System.exit(7);
            return;
        }
        List<IrGraph> graphs = new ArrayList<>();
        for (FunctionTree function : program.topLevelTrees()) {
            SsaTranslation translation = new SsaTranslation(function, new LocalValueNumbering());
            graphs.add(translation.translate());
        }

        if ("vcg".equals(System.getenv("DUMP_GRAPHS")) || "vcg".equals(System.getProperty("dumpGraphs"))) {
            Path tmp = output.toAbsolutePath().resolveSibling("graphs");
            Files.createDirectory(tmp);
            for (IrGraph graph : graphs) {
                dumpGraph(graph, tmp, "before-codegen");
            }
        }

        // TODO: generate assembly and invoke gcc instead of generating abstract assembly
        String s = new CodeGenerator().generateCode(graphs);

        // - - - - Linear Scan - - - -
        HashMap<Integer, LiveRange> virtualRegisters = new HashMap<>();

        BufferedReader bufReader = new BufferedReader(new StringReader(s));
        String line = null;

        boolean entered = false;
        int lineNumber = 0;

        while((line = bufReader.readLine()) != null) {

            if (entered) {
                String[] split = line.stripLeading().stripTrailing().split(" ");
                List<String> clean = Arrays.stream(split).filter(str -> !str.contentEquals("")).toList();

                for (String str : clean) {
                    String sanitized_line = str.replace(",", "");

                    if (sanitized_line.contains("%")) {
                        sanitized_line = sanitized_line.replace("%", "");
                        if (sanitized_line.contentEquals("rax")){ continue; }
                        int parsed = Integer.parseInt(sanitized_line);

                        if (virtualRegisters.containsKey(parsed)) {
                            virtualRegisters.get(parsed).lastUsedLine = lineNumber;
                        } else {

                            virtualRegisters.put(parsed, new LiveRange(lineNumber, lineNumber));
                        }

                    }
                }

            }

            if (line.contentEquals("_main:")) { entered = true; }
            lineNumber++;
        }

        ArrayList<Map.Entry<Integer, LiveRange>> virtReg = new ArrayList<>(virtualRegisters.entrySet());
        virtReg.sort(Comparator.comparingInt(left -> left.getValue().definedLine));
        System.out.println(virtualRegisters);

        String[] registers = {
            "%rbx",
            "%rcx",
            "%rdx",
            "%rsi",
            "%rdi",
            "%rsp",
            "%rbp",
            "%r8",
            "%r9",
            "%r10",
            "%r11",
            "%r12",
            "%r13",
            "%r14",
            "%r15",
        };

        List<Map.Entry<String, Integer>> active = new ArrayList<>();
        Map<Integer, String> regAllocation = new HashMap<>();

        for (Map.Entry<Integer, LiveRange> vreg : virtualRegisters.entrySet()) {
            active = new ArrayList<>(active.stream().filter(entry -> {
                if (entry.getValue() >= vreg.getValue().definedLine) {
                    return true;
                } else {
                    return false;
                }
            }).toList()
            );

            if (active.size() < registers.length) {
                String reg = registers[active.size()];
                regAllocation.put(vreg.getKey(), reg);
                active.add(Map.entry(reg, vreg.getValue().lastUsedLine));
            } else {
                regAllocation.put(vreg.getKey(), "spilling...");
            }
        }

        for (Map.Entry<Integer, String> reg : regAllocation.entrySet()) {
            s = s.replace("%" + reg.getKey(), reg.getValue());
        }

        System.out.println(s);

        // - - - - - - - - - - -

        Path generated_assembly = Path.of(args[1] + ".s");
        Files.writeString(generated_assembly, s);
        Process cmdProc = Runtime.getRuntime().exec("gcc " + generated_assembly + " -o " + output);
    }

    private static ProgramTree lexAndParse(Path input) throws IOException {
        try {
            Lexer lexer = Lexer.forString(Files.readString(input));
            TokenSource tokenSource = new TokenSource(lexer);
            Parser parser = new Parser(tokenSource);
            return parser.parseProgram();
        } catch (ParseException e) {
            e.printStackTrace();
            System.exit(42);
            throw new AssertionError("unreachable");
        }
    }

    private static void dumpGraph(IrGraph graph, Path path, String key) throws IOException {
        Files.writeString(
                path.resolve(graph.name() + "-" + key + ".vcg"),
                YCompPrinter.print(graph)
        );
    }
}
