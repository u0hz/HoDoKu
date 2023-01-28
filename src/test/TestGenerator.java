/*
 * TestGenerator.java
 *
 * Created on 19. Dezember 2007, 09:30
 *
 * Versucht Sudokus zu finden, auf die bestimmte Muster passen (derzeit
 * nur für Fische). Ein Muster ist ein Array of char, das für jede Zelle
 * eines der folgenden Zeichen enthält:
 *    'X': Kandidat muss gesetzt sein
 *    '/': Kandidat darf nicht gesetzt sein
 *    '*': Kandidat kann gelöscht werden
 *    '.': Beliebige Kandidaten
 *
 * Vorgangsweise:
 *
 * Es werden gültige Sudokus erzeugt, für jedes Sudoku wird für jeden Kandidaten
 * probiert, ob das Muster passt. Die Ergebnisse werden in Maps gespeichert.
 * Das Ganze läuft in einem eigenen Thread, damit jederzeit gut abgebrochen werden kann.
 */

package test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.TreeMap;
import sudoku.DifficultyType;
import sudoku.Options;
import sudoku.Sudoku;
import sudoku.SudokuCell;
import sudoku.SudokuCreator;

/**
 *
 * @author zhobigbe
 */
public class TestGenerator extends Thread {
    private static final String t1 = "0300";  // X-Wing
    private static final String p1 =
            ".*.....*." +
            "/X/////X/" +
            ".*.....*." +
            ".*.....*." +
            ".*.....*." +
            ".*.....*." +
            ".*.....*." +
            "/X/////X/" +
            ".*.....*.";
    private static final String t2 = "0300";  // X-Wing
    private static final String p2 =
            "/X//X////" +
            ".*..*...." +
            ".*..*...." +
            ".*..*...." +
            ".*..*...." +
            ".*..*...." +
            ".*..*...." +
            "/X//X////" +
            ".*..*....";
    private static final String t3 = "0301";  // Complete Swordfish
    private static final String p3 =
            "/X//X//X/" +
            "/X//X//X/" +
            ".*..*..*." +
            ".*..*..*." +
            ".*..*..*." +
            ".*..*..*." +
            ".*..*..*." +
            "/X//X//X/" +
            ".*..*..*.";
    private static final String t4 = "0301";  // Incomplete Swordfish
    private static final String p4 =
            "/X//X////" +
            "/X/////X/" +
            ".*..*..*." +
            ".*..*..*." +
            ".*..*..*." +
            ".*..*..*." +
            ".*..*..*." +
            "////X//X/" +
            ".*..*..*.";
    
    private String type;
    private char[] pattern;
    private Sudoku orgSudoku;
    private Sudoku sudoku;
    private Sudoku solvedSudoku;
    private Map<Integer,String> ergOhneKandDel = new TreeMap<Integer,String>();
    private Map<Integer,String> ergMitKandDel = new TreeMap<Integer,String>();
    private int count;
    private int anzResults = 0;
    
    /** Creates a new instance of TestGenerator */
    public TestGenerator(String type, String pattern, int anzResults) {
        this.type = type;
        this.pattern = pattern.toCharArray();
        this.anzResults = anzResults;
        start();
    }
    
    public void run() {
        SudokuCreator creator = new SudokuCreator();
        int sudokuCounter = 0;
        while (! isInterrupted() && (ergMitKandDel.size() < anzResults || ergOhneKandDel.size() < anzResults)) {
//            orgSudoku = new Sudoku(true, true);
//            orgSudoku.setSudoku(":0300:3:5.92..8.727.485....86.7.52...5....7....7.3.5...75.26...5..2.7..6.81.7295792.5.431:618|939|841|142|342|442|944|946|849|152|452|674|874|676|876:362:r18 c25");
//            System.out.println(orgSudoku.getSudoku());
//            creator.findSolution(solvedSudoku);
//            System.out.println(solvedSudoku.getSudoku());
            orgSudoku = creator.generateSudoku(Options.getInstance().difficultyLevels[DifficultyType.EXTREME.ordinal()], false);
            //System.out.println(orgSudoku.getSudoku());
            solvedSudoku = orgSudoku.clone();
            creator.findSolution(solvedSudoku);
            //System.out.println(solvedSudoku.getSudoku());
            for (int cand = 1; cand <= 9; cand++) {
                sudoku = orgSudoku.clone();
                checkCand(cand);
                if (isInterrupted()) {
                    break;
                }
            }
            sudokuCounter++;
            if ((sudokuCounter % 100) == 0) {
                System.out.println(sudokuCounter + " Versuche");
            }
            //break;
        }
    }
    
    private void checkCand(int cand) {
        int anzCandDel = 0;
        int cType = SudokuCell.PLAY;
        boolean mustDelCands = false;
        boolean first = true;
        StringBuffer candDelStr = new StringBuffer();
        SudokuCell[] cells = sudoku.getCells();
        for (int i = 0; i < cells.length; i++) {
            // drei Spezialfälle: 'X', '/' und '*'
            if (pattern[i] == 'X' && (cells[i].getValue() != 0 || ! cells[i].isCandidate(cType, cand))) {
                // Kein Kandidat an dieser Stelle
                return;
            }
            if (pattern[i] == '/') {
                if (cells[i].getValue() == cand) {
                    // Zelle hat falschen Wert
                    return;
                }
                if (cells[i].getValue() == 0 && cells[i].isCandidate(cType, cand)) {
                    // Wenn die Lösung an dieser Stelle einen anderen Wert als cand hat, kann der Kandidat gelöscht werden
                    if (solvedSudoku.getCell(i).getValue() == cand) {
                        // kann nicht gelöscht werden
                        return;
                    }
                    cells[i].delCandidate(cType, cand);
                    mustDelCands = true;
                }
            }
            if (pattern[i] == '*' && cells[i].getValue() == 0 && cells[i].isCandidate(cType, cand)) {
                // Kandidat, der gelöscht werden kann
                anzCandDel++;
                if (first) {
                    first = false;
                } else {
                    candDelStr.append("|");
                }
                candDelStr.append(Integer.toString(cand) + Integer.toString((i / 9) + 1) + Integer.toString((i % 9) + 1));
            }
        }
        // Wenn wir hier ankommen, ist das Sudoku gültig
        StringBuffer libStr = new StringBuffer(sudoku.getSudoku());
        libStr.replace(1, 5, type);
        libStr.replace(6, 7, Integer.toString(cand));
        libStr.replace(libStr.length() - 1, libStr.length(), candDelStr + ":");
        int key = anzCandDel * 100 + count++;
        System.out.println("Ergebnis: " + mustDelCands + "/" + libStr.toString());
        if (mustDelCands) {
            ergMitKandDel.put(key, libStr.toString());
        } else {
            ergOhneKandDel.put(key, libStr.toString());
        }
    }
    
    private void printResult() {
        System.out.println("Ergebnisse ohne zu löschende Kandidaten:");
        for (int key : ergOhneKandDel.keySet()) {
            System.out.println("  " + key + ": <" + ergOhneKandDel.get(key) + ">");
        }
        System.out.println("Ergebnisse mit zu löschenden Kandidaten:");
        for (int key : ergMitKandDel.keySet()) {
            System.out.println("  " + key + ": <" + ergMitKandDel.get(key) + ">");
        }
    }
    
    public static void main(String[] args) throws IOException, InterruptedException {
        TestGenerator generator = new TestGenerator(t4, p4, 10);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        in.readLine();
        generator.interrupt();
        generator.join();
        generator.printResult();
    }
}
