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

import java.util.Arrays;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author zhobigbe
 */
public class SudokuCreator {
    private static final int TYPE = SudokuCell.ALL;
    
    private Sudoku sudoku;
    private Sudoku solvedSudoku;
    private Random rand = new Random();
    
    private int anzCalls = 0;
    private int recDepth = 0;
    private int maxRecDepth = 0;
    
    /** Creates a new instance of SudokuCreator */
    public SudokuCreator() {
    }
    
    public Sudoku generateSudoku(DifficultyLevel level, boolean symmetric) {
        sudoku = new Sudoku(false);
        generateFullSudoku(0);
        generateInitPos(level, symmetric);
        // all cells are givens!
        for (int i = 0; i < sudoku.getCells().length; i++) {
            SudokuCell cell = sudoku.getCell(i);
            if (cell.getValue() != 0) {
                cell.setIsFixed(true);
            }
        }
        return sudoku;
    }
    
    /**
     * Die Zellen werden der Reihe nach mit möglichen gültigen Werten gefüllt (rekursiv).
     * Wenn sich ein Regelverstoß ergibt, wird ein anderer Wert probiert.
     */
    private boolean generateFullSudoku(int index) {
        if (index == 9 * 9)
            return true;
        SudokuCell cell = sudoku.getCell(index);
        short[] possibleValues = cell.getAllCandidates();
        int pLength = possibleValues.length;
        if (pLength == 0)
            return false;
        
        while (pLength > 0) {
            int cIndex = rand.nextInt(possibleValues.length);
            while (possibleValues[cIndex] == 0) {
                cIndex++;
                if (cIndex == possibleValues.length)
                    cIndex = 0;
            }
            pLength--;
            sudoku.setCell(index, possibleValues[cIndex]);
            if (generateFullSudoku(index + 1)) {
                return true;
            }
            possibleValues[cIndex] = 0;
            sudoku.setCell(index, 0);
        }
        return false;
    }
    
    /**
     * Erzeugt ein neues Spiel, indem Positionen weggelassen werden.
     * Die Anzahl der Startpositionen wird festgelegt, es werden
     * so lange zufällig Zahlen weggelassen, bis die Anzahl
     * Startpositionen erreicht ist. Dabei wird immer geprüft, ob
     * die Lösung noch eindeutig ist.
     */
    private void generateInitPos(DifficultyLevel level, boolean isSymmetric) {
        int maxPosToFill = 20;
        boolean[] used = new boolean[81];
        int usedCount = used.length;
        Sudoku solvedBoard = sudoku.clone();
        
        Arrays.fill(used, false);
        while (sudoku.anzFilledCells() > maxPosToFill && usedCount > 1) {
            int i = rand.nextInt(81);
            do {
                if( i < 80 ) i++;
                else i = 0;
            } while (used[i]);
            used[i] = true;
            usedCount--;
            
            if (sudoku.getCell(i).getValue() == 0) {
                continue;
            }
            if (isSymmetric && (i/9 != 4 || i%9 != 4 ) && sudoku.getCell(9 * (8 - i / 9) + (8 - i % 9)).getValue() == 0) {
                continue;
            }
            // betreffende Zelle löschen
            sudoku.setCell(i, 0);
            int symm = 0;
            if (isSymmetric && (i/9 != 4 || i%9 != 4 )) {
                symm = 9 * (8 - i / 9) + (8 - i % 9);
                sudoku.setCell(symm, 0);
                used[symm] = true;
                usedCount--;
            }
            BacktrackingSolver bs = BacktrackingSolver.getInstance();
            bs.solve(sudoku.getSudoku(ClipboardMode.VALUES_ONLY));
            int solutions = bs.getSolutionCount();
            if (solutions > 1) {
                sudoku.setCell(i, solvedBoard.getCell(i).getValue());
                if (isSymmetric && (i/9 != 4 || i%9 != 4 )) {
                    sudoku.setCell(symm, solvedBoard.getCell(symm).getValue());
                }
            }
        }
    }
    
    public boolean validSolution(Sudoku sudoku) {
        long ticks = System.currentTimeMillis();
        BacktrackingSolver bs = BacktrackingSolver.getInstance();
        bs.solve(sudoku.getSudoku(ClipboardMode.VALUES_ONLY));
        boolean unique = bs.getSolutionCount() == 1;
        if (unique) {
            solvedSudoku = new Sudoku();
            solvedSudoku.setSudoku(bs.getSolution());
        }
        ticks = System.currentTimeMillis() - ticks;
        Logger.getLogger(getClass().getName()).log(Level.FINE, "validSolution() " + ticks + "ms");
        return unique;
    }
    
    public int findSolution(Sudoku sudoku) {
        long ticks = System.currentTimeMillis();
        Sudoku oldSudoku = this.getSolvedSudoku();
        int anzSolutions = findAllSolutions(-1, false, false, sudoku);
        solvedSudoku = oldSudoku;
        ticks = System.currentTimeMillis() - ticks;
        Logger.getLogger(getClass().getName()).log(Level.FINE, "findSolution() " + ticks + "ms");
        return anzSolutions;
    }
    
    /**
     * Findet alle möglichen Lösungen; wenn checkUnique gesetzt ist, wird abgebrochen,
     * sobald eine zweite Lösung gefunden wurde.
     *
     * Sucht die Zelle mit den wenigsten Kandidaten und probiert alle Möglichkeiten rekursiv durch.
     */
    private int findAllSolutions(int level, boolean checkUnique, boolean solutionOnly, Sudoku sudoku) {
        anzCalls++;
        recDepth++;
        if (recDepth > maxRecDepth) {
            maxRecDepth = recDepth;
        }
        // Zuerst die Zelle mit den wenigsten Kandidaten suchen
        int index = -1;
        int indexAnz = 10;
        SudokuCell[] cells = sudoku.getCells();
        for (int i = 0; i < cells.length; i++) {
            // bereits gesetzte Zellen ignorieren
            if (cells[i].getValue() != 0)
                continue;
            // Zelle ist nicht gesetzt -> Anzahl Kandidaten prüfen
            int cAnz = cells[i].getAnzCandidates(TYPE);
            if (cAnz == 0) {
                // nicht gesetzte Zelle ohne Kandidaten -> keine Lösung möglich
                recDepth--;
                return 0;
            }
            if (cAnz < indexAnz) {
                index = i;
                indexAnz = cAnz;
            }
        }
        if (index == -1) {
            // Kann nur vorkommen, wenn es keine freien Zellen mehr gibt -> Lösung gefunden
            if (solutionOnly) {
                solvedSudoku = sudoku.clone();
            }
            recDepth--;
            return 1;
        }
        
        int solutions = 0; // number of solutions
        // Alle möglichen Kandidaten durchprobieren
        for (int num = 1; num <= 9; num++) {
            if (! cells[index].isCandidateValid(TYPE, num))
                continue;
            sudoku.setCell(index, num, false, false);
            solutions += findAllSolutions(level - 1, checkUnique, solutionOnly, sudoku);
            sudoku.setCell(index, 0, false, false);
            if (solutionOnly && solutions == 1) {
                // Lösung gefunden -> aufhören
                break;
            }
            if (checkUnique && solutions > 1) {
                // es soll Eindeutigkeit geprüft werden, aber es gibt 2 Lösungen -> fertig
                break;
            }
        }
        recDepth--;
        return solutions;
    }
    
    public Sudoku getSolvedSudoku() {
        return solvedSudoku;
    }
    
    public static void main(String[] args) {
        SudokuCreator cr = new SudokuCreator();
        
        cr.sudoku = new Sudoku();
        //  45 Easter Monster 
        //  35 cr.sudoku.setSudoku("1.......2.9.4...5...6...7...5.9.3.......7.......85..4.7.....6...3...9.8...2.....1");
        // 211 cr.sudoku.setSudoku("..............3.85..1.2.......5.7.....4...1...9.......5......73..2.1........4...9");
        //1189 cr.sudoku.setSudoku(".1.....2....8..6.......3........43....2.1....8......9.4...7.5.3...2...........4..");
        // 151 cr.sudoku.setSudoku("...87..3.52.......4..........3.9..7......54...8.......2.....5.....3....9...1.....");
        //2198 cr.sudoku.setSudoku("..15............32...............2.9.5...3......7..8..27.....4.3...9.......6..5..");
        //   9 cr.sudoku.setSudoku("7.....4...2..7..8...3..8..9...5..3...6..2..9...1..7..6...3..9...3..4..6...9..1..5");
        //  17 cr.sudoku.setSudoku("...7..8......4..3......9..16..5......1..3..4...5..1..75..2..6...3..8..9...7.....2");
        // 176 cr.sudoku.setSudoku("96...5......3...1.8.......2.....46..75.........1.........21..........57.....3....");
        //System.out.println(cr.sudoku.getSudoku(ClipboardMode.PM_GRID));
        long ticks = System.currentTimeMillis();
        int i = 0;
        for (i = 0; i < 10; i++) {
            cr.findAllSolutions(-1, true, false, cr.sudoku);
        }
        ticks = System.currentTimeMillis() - ticks;
        System.out.println("findAllSolutions(): " + (ticks / i) + "ms");
        System.out.println("(anzCalls=" + (cr.anzCalls / 10) + ", maxRecDepth=" + cr.maxRecDepth + "/" + cr.recDepth + ")");
    }
}
