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
import java.util.List;
import java.util.Locale;
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
    public void searchForType(SolutionType type) {
        SearchForTypeThread thread = new SearchForTypeThread(this, type);
        thread.start();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        try {
            while (in.readLine().compareTo("q") != 0) {
                ;
            }
        } catch (IOException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error reading from console", ex);
        }
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Interrupted waiting for search thread", ex);
        }
        Logger.getLogger(getClass().getName()).log(Level.INFO, "Gesamt: " + thread.getAnz() + " Sudoku erzeugt (" + thread.getAnzFound() + " Treffer)");
    }

    public void batchSolve(String fileName) {
        BatchSolveThread thread = new BatchSolveThread(fileName);
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
        System.out.println(thread.getBruteForceAnz() + " puzzles require guessing!");
        System.out.println(thread.getTemplateAnz() + " puzzles require templates!");
        System.out.println(thread.getUnsolvedAnz() + " puzzles unsolved!");
        System.out.println();
        for (int i = 1; i < thread.getResultLength(); i++) {
            System.out.println("   " + Options.DEFAULT_DIFFICULTY_LEVELS[i].getName() + ": " + thread.getResult(i));
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        // Logging: Standardmäßig auf die Console, ins Logfile nur Exceptions
        Handler fh = new FileHandler("%t/hodoku.log", false);
        fh.setFormatter(new SimpleFormatter());
        fh.setLevel(Level.SEVERE);
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(fh);
        rootLogger.setLevel(Level.CONFIG);
        Handler[] handlers = rootLogger.getHandlers();
        for (Handler handler : handlers) {
            if (handler instanceof ConsoleHandler) {
                handler.setLevel(Level.ALL);
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
        if (! Options.getInstance().language.equals("")) {
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
        if (! found && ! Options.getInstance().laf.equals("")) {
            Options.getInstance().laf = "";
        }
        try {
            if (! Options.getInstance().laf.equals("")) {
                UIManager.setLookAndFeel(Options.getInstance().laf);
            } else {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
            Logger.getLogger(Main.class.getName()).log(Level.CONFIG, "laf=" + UIManager.getLookAndFeel().getName());
        } catch (Exception ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Error changing LaF", ex);
        }
        
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("/u5")) {
                new Main().searchForType(SolutionType.UNIQUENESS_5);
                return;
            }
            if (args[0].equalsIgnoreCase("/ar")) {
                new Main().searchForType(SolutionType.AVOIDABLE_RECTANGLE_2);
                return;
            }
            if (args[0].equalsIgnoreCase("/bs")) {
                new Main().batchSolve(args[1]);
                return;
            }
        }
//            Sudoku sudoku = new Sudoku();
//            //sudoku.setSudoku(".1.....2....8..6.......3........43....2.1....8......9.4...7.5.3...2...........4..");
//            //sudoku.setSudoku("000000490020100000000000500800400300609000000000200000000069070040050000000000001");
//            //sudoku.setSudoku("...87..3.52.......4..........3.9..7......54...8.......2.....5.....3....9...1.....");
//            SudokuSolver solver = SudokuSolver.getInstance();
//            int anzRuns = 100;
//            long ticks = System.currentTimeMillis();
//            for (int i = 0; i < anzRuns; i++) {
//                Sudoku tmpSudoku = (Sudoku) sudoku.clone();
//                solver.setSudoku(tmpSudoku);
//                solver.solve();
//            }
//            ticks = System.currentTimeMillis() - ticks;
//            System.out.println("Time: " + (ticks / anzRuns) + "ms");

//        UIDefaults uiDefaults = UIManager.getDefaults();
//        Enumeration enumDef = uiDefaults.keys();
//        SortedMap<String,Object> sm = new TreeMap<String,Object>();
//        while (enumDef.hasMoreElements()) {
//            String key = enumDef.nextElement().toString();
//            Object val = uiDefaults.get(key);
//            sm.put(key, val);
//        }
//        for (Object key : sm.keySet()) {
//            Object val = sm.get(key);
//            System.out.println("[" + key.toString() + "]:[" +
//                    (null != val ? val.toString() : "(null)") +
//                    "]");
//        }
        
        java.awt.EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                new MainFrame().setVisible(true);
            }
        });
    }
}

class SearchForTypeThread extends Thread {

    private Main m;
    private SolutionType type;
    private int anz = 0;
    private int anzFound = 0;

    public SearchForTypeThread(Main m, SolutionType type) {
        this.m = m;
        this.type = type;
    }

    @Override
    public void run() {
        String path = m.getSrcDir() + "ar.txt";
        anz = 0;
        anzFound = 0;
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(path, true));
            SudokuCreator creator = new SudokuCreator();
            SudokuSolver solver = SudokuSolver.getInstance();
            // einmal ein leeres Sudoku erzeugen, damit alles richtig initialisiert wird
            new Sudoku();

            while (!isInterrupted()) {
                Sudoku newSudoku = creator.generateSudoku(Options.getInstance().getDifficultyLevels()[DifficultyType.EXTREME.ordinal()], false);
                solver.setSudoku(newSudoku.clone());
                solver.solve();
                List<SolutionStep> steps = solver.getSteps();
                for (int i = 0; i < steps.size(); i++) {
                    if (steps.get(i).getType() == type) {
                        // found a suitable sudoku
                        String txt = newSudoku.getSudoku(ClipboardMode.CLUES_ONLY);
                        out.write(txt);
                        out.newLine();
                        out.flush();
                        Logger.getLogger(getClass().getName()).log(Level.INFO, txt);
                        anzFound++;
                    }
                }
                anz++;
                if ((getAnz() % 10) == 0) {
                    System.out.println(".");
                }
            }
            out.close();
        } catch (IOException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error writing sudoku file", ex);
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
    private int[] results;
    private int bruteForceAnz;
    private int templateAnz;
    private int unsolvedAnz = 0;
    private int count;
    private long ticks;

    BatchSolveThread(String fn) {
        fileName = fn;
    }

    @Override
    public void run() {
        Logger.getLogger(getClass().getName()).log(Level.INFO, "Starting batch solve...");
        results = new int[Options.DEFAULT_DIFFICULTY_LEVELS.length];
        bruteForceAnz = 0;
        templateAnz = 0;
        unsolvedAnz = 0;
        BufferedReader inFile = null;
        BufferedWriter outFile = null;
        ticks = System.currentTimeMillis();
        count = 0;
        try {
            inFile = new BufferedReader(new FileReader(fileName));
            outFile = new BufferedWriter(new FileWriter(fileName + ".out.txt"));
            String line = null;
            SudokuSolver solver = SudokuSolver.getInstance();
            Sudoku sudoku = new Sudoku(true);
            while (!isInterrupted() && (line = inFile.readLine()) != null) {
                line = line.trim();
                sudoku.setSudoku(line);
                solver.setSudoku(sudoku);
                solver.solve();
                count++;
                boolean needsGuessing = false;
                boolean needsTemplates = false;
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
                }
                if (unsolved) {
                    unsolvedAnz++;
                }
                String out = line + " #" + count + " " + solver.getLevel().getName() + " (" + solver.getScore() + ")" +
                        (needsGuessing ? " G" : "") + (needsTemplates ? " T" : "");
                results[solver.getLevel().getOrdinal()]++;
                outFile.write(out);
                outFile.newLine();

                if ((count % 100) == 0) {
                    long ticks2 = System.currentTimeMillis() - getTicks();
                    System.out.println(count + ": " + (ticks2 / count));
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error in batch solve:", ex);
        } finally {
            try {
                if (inFile != null) {
                    inFile.close();
                }
                if (outFile != null) {
                    outFile.close();
                }
            } catch (Exception ex) {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error closing files:", ex);
            }
        }
        if (isInterrupted()) {
            Logger.getLogger(getClass().getName()).log(Level.INFO, "Interrupted, shutting down...");
        } else {
            Logger.getLogger(getClass().getName()).log(Level.INFO, "Done!");
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