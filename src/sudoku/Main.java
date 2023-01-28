/*
 * Copyright (C) 2008  Bernhard Hobiger
 *
 * This file is part of HoDoKu.
 *
 * HoDoKu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * HoDoKu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with HoDoKu. If not, see <http://www.gnu.org/licenses/>.
 */
package sudoku;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

/**
 *
 * @author Bernhard Hobiger
 */
public class Main {

    /** Creates a new instance of Main */
    public Main() {
    }

    public String getSrcDir() {
        String path = getClass().getClassLoader().getResource("sudoku").toExternalForm().toLowerCase();
        if (path.startsWith("jar")) {
            path = path.substring(10, path.indexOf("hodoku.jar"));
        } else {
            path = path.substring(6, path.indexOf("build"));
        }
        return path;
    }

    @SuppressWarnings("empty-statement")
    public void searchForType(SortedMap<SolutionType, Integer> typeMap) {
        //Logger.getLogger(getClass().getName()).log(Level.INFO, "Starting search for " + type.getStepName());
        System.out.println("Starting search for:");
        for (SolutionType tmpType : typeMap.keySet()) {
            System.out.println("   " + tmpType.getStepName() + " (" + typeMap.get(tmpType) + ")");
        }
        SearchForTypeThread thread = new SearchForTypeThread(this, typeMap);
        thread.start();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        try {
            while (in.readLine().compareTo("q") != 0) {
                ;
            }
        } catch (IOException ex) {
            System.out.println("Error reading from console");
        }
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException ex) {
            System.out.println("Interrupted waiting for search thread");
        }
        System.out.println("Gesamt: " + thread.getAnz() + " Sudoku erzeugt (" + thread.getAnzFound() + " Treffer)");
    }

    public void batchSolve(String fileName, String puzzleString, boolean printSolution, boolean printSolutionPath,
            ClipboardMode cMode, Set<SolutionType> types) {
        BatchSolveThread thread = new BatchSolveThread(fileName, puzzleString, printSolution, printSolutionPath,
                cMode, types);
        thread.start();
        Runtime.getRuntime().addShutdownHook(new ShutDownThread(thread));
        try {
            thread.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "join interrupted...", ex);
        }
        int min = (int) (thread.getTicks() / 60000);
        int sec = (int) (thread.getTicks() % 60000);
        int ms = sec % 1000;
        sec /= 1000;
        int hours = min / 60;
        min -= (hours * 60);
        System.out.println(thread.getCount() + " puzzles in " + thread.getTicks() + "ms (" + hours + ":" + min + ":" + sec + "." + ms + ")");
        System.out.println((thread.getTicks() / thread.getCount()) + "ms per puzzle");
        System.out.println(thread.getBruteForceAnz() + " puzzles require guessing!");
        System.out.println(thread.getTemplateAnz() + " puzzles require templates!");
        System.out.println(thread.getGivenUpAnz() + " puzzles unsolved!");
        System.out.println(thread.getUnsolvedAnz() + " puzzles not solved logically!");
        System.out.println();
        for (int i = 1; i < thread.getResultLength(); i++) {
            System.out.println("   " + Options.DEFAULT_DIFFICULTY_LEVELS[i].getName() + ": " + thread.getResult(i));
        }
    }

    public void sortPuzzleFile(String fileName) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            BufferedWriter out = new BufferedWriter(new FileWriter(fileName + ".out.txt"));
            List<String> puzzleList = new ArrayList<String>();
            String line = null;
            while ((line = in.readLine()) != null) {
                if (line.contains("#")) {
                    puzzleList.add(line);
                }
            }
            in.close();
            Collections.sort(puzzleList, new Comparator<String>() {

                @Override
                public int compare(String s1, String s2) {
                    String ss1 = s1.substring(s1.indexOf('#'));
                    String ss2 = s2.substring(s2.indexOf('#'));
                    return ss1.compareTo(ss2);
                }
            });
            for (String key : puzzleList) {
                out.write(key);
                out.newLine();
            }
            out.close();
        } catch (Exception ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Error sorting puzzle file", ex);
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        // Logging: Standardm‰ﬂig auf die Console, ins Logfile nur Exceptions
        Handler fh = new FileHandler("%t/hodoku.log", false);
        fh.setFormatter(new SimpleFormatter());
        fh.setLevel(Level.SEVERE);
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(fh);
        rootLogger.setLevel(Level.CONFIG);
        Handler[] handlers = rootLogger.getHandlers();
        for (Handler handler : handlers) {
            if (handler instanceof ConsoleHandler) {
                //handler.setLevel(Level.ALL);
                //handler.setLevel(Level.CONFIG);
                handler.setLevel(Level.SEVERE);
            }
        }
        //Logger.getLogger(Sudoku.class.getName()).setLevel(Level.FINER);
        //Logger.getLogger(FishSolver.class.getName()).setLevel(Level.FINER);
        //Logger.getLogger(TablingSolver.class.getName()).setLevel(Level.FINER);

        Logger.getLogger(Main.class.getName()).log(Level.CONFIG, "java.io.tmpdir=" + System.getProperty("java.io.tmpdir"));
        Logger.getLogger(Main.class.getName()).log(Level.CONFIG, "user.dir=" + System.getProperty("user.dir"));
        Logger.getLogger(Main.class.getName()).log(Level.CONFIG, "user.home=" + System.getProperty("user.home"));

        //System.setProperty("awt.useSystemAAFontSettings", "on");
        //System.setProperty("swing.aatext", "true");

//        System.out.println("Properties:");
//        Properties props = System.getProperties();
//        Enumeration propEnum = props.propertyNames();
//        while (propEnum.hasMoreElements()) {
//            Object element = propEnum.nextElement();
//            String key = element.toString();
//            String value = props.getProperty(key);
//            System.out.println("  " + key + ": " + value);
//        }

//        boolean prematureExit = true;
//        TablingSolver.main(null);
//        if (prematureExit) {
//            return;
//        }

        // Optionen lesen (macht getInstance())
        Options.getInstance();

        // set locale
        if (!Options.getInstance().language.equals("")) {
            Locale.setDefault(new Locale(Options.getInstance().language));
        }
        // adjust names of difficulty levels
        Options.getInstance().resetDifficultyLevelStrings();

        // set laf; if a laf is set in options, check if it exists
        LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
        boolean found = false;
        for (int i = 0; i < lafs.length; i++) {
            if (lafs[i].getClassName().equals(Options.getInstance().laf)) {
                found = true;
                break;
            }
        }
        if (!found && !Options.getInstance().laf.equals("")) {
            Options.getInstance().laf = "";
        }
        try {
            if (!Options.getInstance().laf.equals("")) {
                UIManager.setLookAndFeel(Options.getInstance().laf);
            } else {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
            Logger.getLogger(Main.class.getName()).log(Level.CONFIG, "laf=" + UIManager.getLookAndFeel().getName());
        } catch (Exception ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Error changing LaF", ex);
        }

        // handle command line arguments
        if (args.length > 0) {
//            for (int i = 0; i < args.length; i++) {
//                System.out.println("args[" + i + "]: <" + args[i] + ">");
//            }
            // open a fake console when HoDoKu is started as exe file
            if (System.console() == null) {
                //JOptionPane.showMessageDialog(null, "Program has no console!", "Console error", JOptionPane.ERROR_MESSAGE);
                //System.out.println("no console!");
                new SudokuConsoleFrame().setVisible(true);
            }
            // copyright notice
            System.out.println(MainFrame.VERSION);
            System.out.println("Copyright (C) 2009  Bernhard Hobiger\r\n" +
                    "\r\n" +
                    "HoDoKu is free software: you can redistribute it and/or modify\r\n" +
                    "it under the terms of the GNU General Public License as published by\r\n" +
                    "the Free Software Foundation, either version 3 of the License, or\r\n" +
                    "(at your option) any later version.\r\n\r\n");
            // store all args in a map (except puzzle string)
            String puzzleString = null;
            Map<String, String> argMap = new TreeMap<String, String>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i].trim().toLowerCase();
                if (arg.equals("/bs") || arg.equals("/vg") || arg.equals("/s") ||
                        arg.equals("/so") || arg.equals("/c")) {
                    // args with parameters (only one parameter per arg permitted)
                    argMap.put(arg, args[i + 1]);
                    i++;
                } else {
                    // args without parameters (could be puzzle)
                    if (Character.isDigit(arg.charAt(0)) || arg.charAt(0) == '.') {
                        // has to be puzzle
                        puzzleString = arg;
                        if (puzzleString.length() != 81) {
                            System.out.println("Puzzle string is not 81 characters long - ignored!");
                            puzzleString = null;
                        } else {
                            for (int j = 0; j < puzzleString.length(); j++) {
                                if (!Character.isDigit(arg.charAt(j)) && arg.charAt(j) != '.') {
                                    System.out.println("Invalid character in puzzle string (" +
                                            puzzleString.charAt(j) + ") - puzzle ignored!");
                                    puzzleString = null;
                                }
                            }
                        }
                    } else {
                        argMap.put(arg, null);
                    }
                }
            }
            // now check for args
            String helpArg = null;
            if (argMap.containsKey("/h")) {
                helpArg = "/h";
            }
            if (argMap.containsKey("-h")) {
                helpArg = "-h";
            }
            if (argMap.containsKey("/?")) {
                helpArg = "/?";
            }
            if (argMap.containsKey("-?")) {
                helpArg = "-?";
            }
            if (helpArg != null) {
                System.out.println("Usage: java -Xmx512m -jar hodoku_x.x.x.jar [options]\r\n" +
                        "\r\n" +
                        "Options:\r\n" +
                        "  /h, /?: print this help screen\r\n" +
                        "  /c <hcfg file>: use <file> for this console run\r\n" +
                        "  /lt: list internal names of techniques\r\n" +
                        "      (current config of GUI program is not changed)\r\n" +
                        "  /so <file>: sort puzzle file created with /s\r\n" +
                        "  /s <step>[:0|1|2|3][,<step>[:0|1|2|3]]...]: create puzzles with <step>\r\n" +
                        "      in their solution and write them to <step>[_<step>...].txt.\r\n" +
                        "      <step> is an internal name according to /lt or \"all\" (all steps except\r\n" +
                        "          singles) or \"nssts\" (all steps except SSTS: singles, h2, h3, h4, n2,\r\n" +
                        "          n3, n4, l2, l3, lc1, lc2, bf2, bf3, bf4, xy, sc, mc)\r\n" +
                        "      0: x <step> x (default)\r\n" +
                        "      1: ssts <step> ssts\r\n" +
                        "      2: ssts <step> s\r\n" +
                        "      3: s <step> s\r\n" +
                        "      with: 'x' - arbitrary steps, 'ssts' - SSTS, 's' - singles\r\n" +
                        "  /bs <file>: batch solve puzzles in file (output written to <file>.out.txt)\r\n" +
                        "  /vs: print solution in output file (only valid with /bs)\r\n" +
                        "  /vp: print complete solution for each puzzle (only valid with /bs)\r\n" +
                        "  /vg [l|c|s:]<step>[,<step>...]: print pm before every <step> in the solution\r\n" +
                        "      (only valid with /bs and /vp)\r\n" +
                        "      l: print library format\r\n" +
                        "      c: print candidate grid\r\n" +
                        "      s: print candidate grid with step highlighted\r\n");
                printIgnoredOptions(helpArg, argMap);
                return;
            }
            if (argMap.containsKey("/lt")) {
                printIgnoredOptions("/lt", argMap);
                SortedMap<String, String> tmpMap = new TreeMap<String, String>();
                for (SolutionType tmpType : SolutionType.values()) {
                    tmpMap.put(tmpType.getStepName(), tmpType.getArgName());
                }
                System.out.println("List of Techniques:");
                for (String stepName : tmpMap.keySet()) {
                    System.out.printf("%6s:%s\r\n", tmpMap.get(stepName), stepName);
                }
                System.out.println("Done!");
                return;
            }
            if (argMap.containsKey("/c")) {
                String fileName = argMap.get("/c");
                Options.readOptions(fileName);
                argMap.remove("/c");
            }
            if (argMap.containsKey("/so")) {
                printIgnoredOptions("/so", argMap);
                new Main().sortPuzzleFile(argMap.get("/so"));
                return;
            }
            if (argMap.containsKey("/s")) {
                printIgnoredOptions("/s", argMap);
                SortedMap<SolutionType, Integer> typeMap = new TreeMap<SolutionType, Integer>();
                String[] types = argMap.get("/s").toLowerCase().split(",");
                for (int i = 0; i < types.length; i++) {
                    String typeStr = types[i].toLowerCase();
                    int typeMode = 0;
                    if (typeStr.contains(":")) {
                        int index = typeStr.indexOf(':');
                        if (typeStr.length() < (index + 2)) {
                            System.out.println("Puzzle type missing (assume '0')!");
                        } else {
                            char typeModeChar = typeStr.charAt(index + 1);
                            switch (typeModeChar) {
                                case '0':
                                    typeMode = 0; // step must be in puzzle, nothing else required

                                    break;
                                case '1':
                                    typeMode = 1; // SSTS + step + SSTS

                                    break;
                                case '2':
                                    typeMode = 2; // SSTS + step + Singles

                                    break;
                                case '3':
                                    typeMode = 3; // singles + step + singles

                                    break;
                                default:
                                    System.out.println("Invalid puzzle type: " + typeModeChar + " (assume '0')");
                                    break;
                            }
                        }
                        typeStr = typeStr.substring(0, index);
                    }
                    if (typeStr.equals("all")) {
                        for (SolutionType tmpType : SolutionType.values()) {
                            if (!tmpType.isSingle()) {
                                typeMap.put(tmpType, typeMode);
                            }
                        }
                        break;
                    } else if (typeStr.equals("nssts")) {
                        for (SolutionType tmpType : SolutionType.values()) {
                            if (!tmpType.isSingle() && !tmpType.isSSTS()) {
                                typeMap.put(tmpType, typeMode);
                            }
                        }
                        break;
                    }
                    SolutionType type = null;
                    SolutionType[] values = SolutionType.values();
                    for (int j = 0; j < values.length; j++) {
                        if (values[j].getArgName().equals(typeStr)) {
                            type = values[j];
                        }
                    }
                    if (type == null) {
                        System.out.println("Invalid step name: " + typeStr + " (ignored!)");
                        continue;
                    }
                    typeMap.put(type, typeMode);
                }
                if (typeMap.size() == 0) {
                    System.out.println("No step name given!");
                    return;
                }
                new Main().searchForType(typeMap);
                return;
            }
            boolean printSolution = false;
            if (argMap.containsKey("/vs")) {
                printSolution = true;
                argMap.remove("/vs");
            }
            boolean printSolutionPath = false;
            if (argMap.containsKey("/vp")) {
                printSolutionPath = true;
                argMap.remove("/vp");
            }
            ClipboardMode clipboardMode = null;
            Set<SolutionType> outTypes = null;
            if (argMap.containsKey("/vg") && printSolutionPath) {
                // [l|c|s]:<type>[,<type>...]
                String types = argMap.get("/vg").toLowerCase();
                if (types.charAt(1) == ':') {
                    switch (types.charAt(0)) {
                        case 'l':
                            clipboardMode = ClipboardMode.LIBRARY;
                            break;
                        case 'c':
                            clipboardMode = ClipboardMode.PM_GRID;
                            break;
                        case 's':
                            clipboardMode = ClipboardMode.PM_GRID_WITH_STEP;
                            break;
                        default:
                            System.out.println("Invalid argument ('" + types.charAt(1) + "'): 'c' used instead!");
                            clipboardMode = ClipboardMode.PM_GRID;
                            break;
                    }
                    types = types.substring(2);
                } else {
                    System.out.println("No output mode set for '/vg': 'c' used as default!");
                    clipboardMode = ClipboardMode.PM_GRID;
                }
                String[] typesArr = types.split(",");
                for (int i = 0; i < typesArr.length; i++) {
                    StepConfig[] steps = Options.getInstance().solverSteps;
                    boolean typeFound = false;
                    for (int j = 0; j < steps.length; j++) {
                        if (steps[j].getType().getArgName().equals(typesArr[i])) {
                            if (outTypes == null) {
                                outTypes = new TreeSet<SolutionType>();
                            }
                            outTypes.add(steps[j].getType());
                            typeFound = true;
                            break;
                        }
                    }
                    if (!typeFound) {
                        System.out.println("Invalid solution type set for '/vg' (" + typesArr[i] + "): ignored!");
                    }
                }
                if (outTypes == null || outTypes.size() == 0) {
                    System.out.println("No solution type set for '/vg': option ignored!");
                    clipboardMode = null;
                    outTypes = null;
                }
                argMap.remove("/vg");
            }
            if (argMap.containsKey("/bs")) {
                printIgnoredOptions("/bs", argMap);
                String fileName = argMap.get("/bs");
                new Main().batchSolve(fileName, null, printSolution, printSolutionPath,
                        clipboardMode, outTypes);
                return;
            }
            if (puzzleString != null) {
                printIgnoredOptions("", argMap);
                new Main().batchSolve(null, puzzleString, printSolution, printSolutionPath,
                        clipboardMode, outTypes);
                return;
            }
            printIgnoredOptions("", argMap);
            System.out.println("Don't know what to do...");
            return;
        }

        // ok - no console operation, start GUI
        java.awt.EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                new MainFrame().setVisible(true);
            }
        });
    }

    /**
     * Prints all remaining (unused) options in argMap except "option"
     * @param Option option that is currently worked on
     * @param argMap All options from the command line
     */
    private static void printIgnoredOptions(String option, Map<String, String> argMap) {
        StringBuffer tmp = new StringBuffer();
        boolean found = false;
        for (String key : argMap.keySet()) {
            if (!key.equals(option)) {
                found = true;
                tmp.append(key);
                tmp.append(" ");
            }
        }
        if (found) {
            System.out.println("The following options were ignored: " + tmp.toString().trim());
        }
    }
}

class SearchForTypeThread extends Thread {

    private class PuzzleType {

        SolutionType type;
        boolean typeSeen = false;
        boolean immediatelyFollowed = false; // set, when type is seen
        int shouldBePuzzleMode = 0;
        int isPuzzleMode1 = -1;
        int isPuzzleMode2 = -1;
        String puzzleString = "";

        PuzzleType(SolutionType type, int mode) {
            reset();
            this.type = type;
            shouldBePuzzleMode = mode;
        }

        void reset() {
            typeSeen = false;
            immediatelyFollowed = false;
            isPuzzleMode1 = -1;
            isPuzzleMode2 = -1;
            puzzleString = "";
        }
    }
    private Main m;
    private SortedMap<SolutionType, Integer> typeMap;
    private int anz = 0;
    private int anzFound = 0;

    public SearchForTypeThread(Main m, SortedMap<SolutionType, Integer> typeMap) {
        this.m = m;
        this.typeMap = typeMap;
    }

    private void appendPuzzleString(PuzzleType pType, boolean mode1) {
        int mode = mode1 ? pType.isPuzzleMode1 : pType.isPuzzleMode2;
        switch (mode) {
            case -1:
                // first step is type!
                break;
            case 3:
                pType.puzzleString += " s";
                break;
            case 2:
            case 1:
                pType.puzzleString += " ssts";
                break;
            case 0:
                pType.puzzleString += " x";
                break;
        }
    }

    @Override
    public void run() {
        //String path = m.getSrcDir() + "ar.txt";
        PuzzleType[] puzzleTypes = new PuzzleType[typeMap.size()];
        StringBuffer pathBuffer = new StringBuffer();
        int index = 0;
        for (SolutionType tmpType : typeMap.keySet()) {
            pathBuffer.append(tmpType.getArgName() + "_");
            puzzleTypes[index] = new PuzzleType(tmpType, typeMap.get(tmpType));
            index++;
        }
        pathBuffer.deleteCharAt(pathBuffer.length() - 1);
        if (puzzleTypes.length == SolutionType.getNonSinglesAnz()) {
            pathBuffer.delete(0, pathBuffer.length());
            pathBuffer.append("all");
        } else if (puzzleTypes.length == SolutionType.getNonSSTSAnz()) {
            pathBuffer.delete(0, pathBuffer.length());
            pathBuffer.append("nssts");
        }
        pathBuffer.append(".txt");
        anz = 0;
        anzFound = 0;
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(pathBuffer.toString(), true));
            SudokuCreator creator = new SudokuCreator();
            SudokuSolver solver = SudokuSolver.getInstance();
            // einmal ein leeres Sudoku erzeugen, damit alles richtig initialisiert wird
            new Sudoku();

            while (!isInterrupted()) {
                Sudoku newSudoku = creator.generateSudoku(Options.getInstance().getDifficultyLevels()[DifficultyType.EXTREME.ordinal()], false);
                solver.setSudoku(newSudoku.clone());
                solver.solve();
                for (int i = 0; i < puzzleTypes.length; i++) {
                    puzzleTypes[i].reset();
                }
                List<SolutionStep> steps = solver.getSteps();
                for (int i = 0; i < steps.size(); i++) {
                    SolutionType type = steps.get(i).getType();
                    for (int j = 0; j < puzzleTypes.length; j++) {
                        if (type.equals(puzzleTypes[j].type)) {
                            if (puzzleTypes[j].immediatelyFollowed) {
                                // nothing between two occurences of type
                                puzzleTypes[j].puzzleString += " " + type.getArgName();
                            } else {
                                puzzleTypes[j].typeSeen = true;
                                puzzleTypes[j].immediatelyFollowed = true;
                                // what was before type?
                                appendPuzzleString(puzzleTypes[j], true);
                                // now the type itself
                                puzzleTypes[j].puzzleString += " " + type.getArgName();
                            }
                        } else {
                            puzzleTypes[j].immediatelyFollowed = false;
                            if (type.isSingle()) {
                                // best case
                                if (puzzleTypes[j].typeSeen) {
                                    if (puzzleTypes[j].isPuzzleMode2 == -1) {
                                        puzzleTypes[j].isPuzzleMode2 = 3;
                                    }
                                }
                                // has to be done in both cases
                                if (puzzleTypes[j].isPuzzleMode1 == -1) {
                                    puzzleTypes[j].isPuzzleMode1 = 3;
                                }
                            } else if (type.isSSTS()) {
                                // step is SSTS -> can only be 2 or 1
                                if (puzzleTypes[j].typeSeen) {
                                    if (puzzleTypes[j].isPuzzleMode2 > 2) {
                                        puzzleTypes[j].isPuzzleMode2 = 1;
                                    }
                                    if (puzzleTypes[j].isPuzzleMode1 > 1) {
                                        puzzleTypes[j].isPuzzleMode1 = 1;
                                    }
                                } else {
                                    if (puzzleTypes[j].isPuzzleMode1 == 3) {
                                        puzzleTypes[j].isPuzzleMode1 = 2;
                                    }
                                }
                            } else {
                                // worst case -> 'X'
                                if (puzzleTypes[j].typeSeen) {
                                    puzzleTypes[j].isPuzzleMode2 = 0;
                                }
                                puzzleTypes[j].isPuzzleMode1 = 0;
                            }
                        }
                    }
                }
                // now check, whether the puzzle fits the specification
                for (int i = 0; i < puzzleTypes.length; i++) {
                    String txt = null;
                    if (puzzleTypes[i].typeSeen && puzzleTypes[i].isPuzzleMode1 >= puzzleTypes[i].shouldBePuzzleMode) {
                        // found a suitable sudoku
                        appendPuzzleString(puzzleTypes[i], false);
                        if (txt == null) {
                            txt = newSudoku.getSudoku(ClipboardMode.CLUES_ONLY);
                        }
                        out.write(txt + " #" + puzzleTypes[i].puzzleString);
                        out.newLine();
                        out.flush();
                        System.out.println(txt + " #" + puzzleTypes[i].puzzleString);
                        anzFound++;
                    }
                }
                anz++;
//                if ((getAnz() % 10) == 0) {
//                    System.out.println(".");
//                }
            }
            out.close();
        } catch (IOException ex) {
            System.out.println("Error writing sudoku file");
            ex.printStackTrace();
        }
    }

    public int getAnz() {
        return anz;
    }

    public int getAnzFound() {
        return anzFound;
    }
}

class BatchSolveThread extends Thread {

    private String fileName;
    private String puzzleString;
    private boolean printSolution;
    private boolean printSolutionPath;
    private int[] results;
    private int bruteForceAnz;
    private int templateAnz;
    private int unsolvedAnz = 0;
    private int givenUpAnz = 0;
    private int count;
    private long ticks;
    private SudokuCreator creator = new SudokuCreator();
    private ClipboardMode clipboardMode;
    private Set<SolutionType> types;
    private boolean outputGrid = false;

    BatchSolveThread(String fn, String pStr, boolean ps, boolean pp, ClipboardMode cm, Set<SolutionType> t) {
        fileName = fn;
        puzzleString = pStr;
        printSolution = ps;
        printSolutionPath = pp;
        clipboardMode = cm;
        types = t;
        if (clipboardMode != null && types != null) {
            outputGrid = true;
        }
    }

    @Override
    public void run() {
        System.out.println("Starting batch solve...");
        results = new int[Options.DEFAULT_DIFFICULTY_LEVELS.length];
        bruteForceAnz = 0;
        templateAnz = 0;
        unsolvedAnz = 0;
        givenUpAnz = 0;
        BufferedReader inFile = null;
        BufferedWriter outFile = null;
        ticks = System.currentTimeMillis();
        count = 0;
        try {
            if (fileName != null) {
                inFile = new BufferedReader(new FileReader(fileName));
                outFile = new BufferedWriter(new FileWriter(fileName + ".out.txt"));
            }
            String line = null;
            SudokuSolver solver = SudokuSolver.getInstance();
            Sudoku sudoku = new Sudoku(true);
            Sudoku tmpSudoku = null;
            while (!isInterrupted() && 
                    (inFile != null && (line = inFile.readLine()) != null) ||
                    (puzzleString != null)) {
                if (puzzleString != null) {
                    line = puzzleString;
                    puzzleString = null;
                }
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }
                sudoku.setSudoku(line);
                if (outputGrid) {
                    tmpSudoku = sudoku.clone();
                }
                solver.setSudoku(sudoku);
                solver.solve();
                count++;
                boolean needsGuessing = false;
                boolean needsTemplates = false;
                boolean givenUp = false;
                boolean unsolved = false;
                List<SolutionStep> steps = solver.getSteps();
                for (int i = 0; i < steps.size(); i++) {
                    if (steps.get(i).getType() == SolutionType.BRUTE_FORCE && !needsGuessing) {
                        needsGuessing = true;
                        unsolved = true;
                        bruteForceAnz++;
                    }
                    if ((steps.get(i).getType() == SolutionType.TEMPLATE_DEL ||
                            steps.get(i).getType() == SolutionType.TEMPLATE_SET) && !needsTemplates) {
                        needsTemplates = true;
                        unsolved = true;
                        templateAnz++;
                    }
                    if (steps.get(i).getType() == SolutionType.GIVE_UP && !givenUp) {
                        givenUp = true;
                        unsolved = true;
                        givenUpAnz++;
                    }
                }
                if (unsolved) {
                    unsolvedAnz++;
                }
                String guess = needsGuessing ? " " + SolutionType.BRUTE_FORCE.getArgName() : "";
                String template = needsTemplates ? " " + SolutionType.TEMPLATE_DEL.getArgName() : "";
                String giveUp = givenUp ? " " + SolutionType.GIVE_UP.getArgName() : "";
                if (printSolution) {
                    if (sudoku.isSolved()) {
                        line = sudoku.getSudoku(ClipboardMode.VALUES_ONLY);
                    } else {
                        //System.out.println("Sudoku: " + sudoku.getSudoku(ClipboardMode.PM_GRID));
                        Sudoku solvedSudoku = sudoku.clone();
                        //System.out.println("SolvedSudoku: " + solvedSudoku.getSudoku(ClipboardMode.PM_GRID));
                        creator.validSolution(solvedSudoku);
                        solvedSudoku = creator.getSolvedSudoku();
                        //System.out.println("SolvedSudoku2: " + solvedSudoku.getSudoku(ClipboardMode.PM_GRID));
                        line = solvedSudoku.getSudoku(ClipboardMode.VALUES_ONLY);
                    //System.out.println("line: " + line);
                    }
                }
                String out = line + " #" + count + " " + solver.getLevel().getName() + " (" + solver.getScore() + ")" +
                        guess + template + giveUp;
                results[solver.getLevel().getOrdinal()]++;
                if (outFile != null) {
                    outFile.write(out);
                    outFile.newLine();
                } else {
                    System.out.println(out);
                }

                if (printSolutionPath) {
                    for (int i = 0; i < steps.size(); i++) {
                        if (outputGrid) {
                            if (types != null && clipboardMode != null && types.contains(steps.get(i).getType())) {
                                String grid = tmpSudoku.getSudoku(clipboardMode, steps.get(i));
                                String[] gridLines = grid.split("\r\n");
                                int end = clipboardMode == clipboardMode.PM_GRID_WITH_STEP ? gridLines.length - 2 : gridLines.length;
                                for (int j = 0; j < end; j++) {
                                    if (outFile != null) {
                                        outFile.write("   " + gridLines[j]);
                                        outFile.newLine();
                                    } else {
                                        System.out.println("   " + gridLines[j]);
                                    }
                                }
                            }
                            solver.doStep(tmpSudoku, steps.get(i));
                        }
                        if (outFile != null) {
                            outFile.write("   " + steps.get(i).toString(2));
                            outFile.newLine();
                        } else {
                            System.out.println("   " + steps.get(i).toString(2));
                        }
                    }
                }

                if ((count % 100) == 0) {
                    long ticks2 = System.currentTimeMillis() - getTicks();
                    System.out.println(count + " (" + (ticks2 / count) + "ms per puzzle)");
                }
            }
        } catch (Exception ex) {
            System.out.println("Error in batch solve:");
            ex.printStackTrace();
        } finally {
            try {
                if (inFile != null) {
                    inFile.close();
                }
                if (outFile != null) {
                    outFile.close();
                }
            } catch (Exception ex) {
                System.out.println("Error closing files:");
                ex.printStackTrace();
            }
        }
        if (isInterrupted()) {
            System.out.println("Interrupted, shutting down...");
        } else {
            System.out.println("Done!");
        }
        ticks = System.currentTimeMillis() - getTicks();
    }

    public int getBruteForceAnz() {
        return bruteForceAnz;
    }

    public int getTemplateAnz() {
        return templateAnz;
    }

    public int getUnsolvedAnz() {
        return unsolvedAnz;
    }

    public int getGivenUpAnz() {
        return givenUpAnz;
    }

    public long getTicks() {
        return ticks;
    }

    public int getResult(int index) {
        return results[index];
    }

    public int getResultLength() {
        return results.length;
    }

    public int getCount() {
        return count;
    }
}

class ShutDownThread extends Thread {

    private Thread thread;

    ShutDownThread(Thread thread) {
        this.thread = thread;
    }

    @Override
    public void run() {
        thread.interrupt();
    }
}