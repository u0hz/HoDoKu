/*
 * Copyright (C) 2008/09/10  Bernhard Hobiger
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

/*
 * Sudoku-Grid mit Indices für Debugging:
 *
 *      1  2  3    4  5  6    7  8  9
 *   +----------+----------+----------+
 * 1 | 00 01 02 | 03 04 05 | 06 07 08 | 1
 * 2 | 09 10 11 | 12 13 14 | 15 16 17 | 2
 * 3 | 18 19 20 | 21 22 23 | 24 25 26 | 3
 *   +----------+----------+----------+
 * 4 | 27 28 29 | 30 31 32 | 33 34 35 | 4
 * 5 | 36 37 38 | 39 40 41 | 42 43 44 | 5
 * 6 | 45 46 47 | 48 49 50 | 51 52 53 | 6
 *   +----------+----------+----------+
 * 7 | 54 55 56 | 57 58 59 | 60 61 62 | 7
 * 8 | 63 64 65 | 66 67 68 | 69 70 71 | 8
 * 9 | 72 73 74 | 75 76 77 | 78 79 80 | 9
 *   +----------+----------+----------+
 *      1  2  3    4  5  6    7  8  9
 */

package sudoku;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 *
 * @author zhobigbe
 */
public abstract class AbstractSolver {
    protected static int[][] allUnits = null;
    protected static int[][] lineUnits = null;
    protected static int[][] colUnits = null;
    protected static int[][] lineBlockUnits = null;
    protected static int[][] colBlockUnits = null;
    
    //protected static boolean[] cellsSeen = null;
    
    // solvers should not be created as standalone objects, they should always be part of a SudokuSolver;
    // that way solvers belonging to the same SudokuSolver can communicate with each other
    // (e.g. for Kraken Fish search)
    protected SudokuSolver solver;  // the solver instance to which this particular instance belongs
    protected Sudoku sudoku;
    protected int candType;
    
    // Templates basics: je ein Template pro Kandidat mit allen Positionen, die sofort gesetzt
    // werden können, und allen, die gelöscht werden können (muss hier sein, weil Templates
    // in anderen Solvern zur Optimierung eingesetzt werden)
    // auch die Listen werden hier aufgebaut, damit der Code nicht verdoppelt werden muss
    protected SudokuSet[] setValueTemplates = new SudokuSet[10];  // eine 1 für jede Position, in der sofort gesetzt werden kann
    protected SudokuSet[] delCandTemplates = new SudokuSet[10];   // eine 1 für jede Position, in der der Kandidat gelöscht werden kann
    protected List<SudokuSetBase>[] candTemplates;
    
    private SudokuSetBase tmpSet = new SudokuSetBase();
    
    static {
        // Indices für alle Einheiten anlegen, dabei Kombinationen für die verschiedenen
        // Fischtypen berücksichtigen (Franken-Fish, Kraken-Fish)
        allUnits = new int[27][];
        lineUnits = Sudoku.LINES;
        colUnits = Sudoku.COLS;
        lineBlockUnits = new int[18][];
        colBlockUnits = new int[18][];
        for (int i = 0; i < 9; i++) {
            allUnits[i] = Sudoku.LINES[i];
            allUnits[i + 9] = Sudoku.COLS[i];
            allUnits[i + 18] = Sudoku.BLOCKS[i];
            lineBlockUnits[i] = Sudoku.LINES[i];
            lineBlockUnits[i + 9] = Sudoku.BLOCKS[i];
            colBlockUnits[i] = Sudoku.COLS[i];
            colBlockUnits[i + 9] = Sudoku.BLOCKS[i];
        }
        
        // für alle Kombinationen aus zwei Zellen feststellen, ob sie einander sehen
//        cellsSeen = new boolean[8081];
//        for (int i = 0; i < 81; i++) {
//            for (int j = 0; j < 81; j++) {
//                if (Sudoku.getLine(i) == Sudoku.getLine(j) ||
//                        Sudoku.getCol(i) == Sudoku.getCol(j) ||
//                        Sudoku.getBlock(i) == Sudoku.getBlock(j)) {
//                    cellsSeen[i * 100 + j] = true;
//                } else {
//                    cellsSeen[i * 100 + j] = false;
//                }
//            }
//        }
    }
    
    /** Creates a new instance of AbstractSolver */
    public AbstractSolver(SudokuSolver solver) {
        this.solver = solver;
        // Templates erzeugen
        candTemplates = new List[10];
        for (int i = 0; i < setValueTemplates.length; i++) {
            setValueTemplates[i] = new SudokuSet();
            delCandTemplates[i] = new SudokuSet();
            candTemplates[i] = new LinkedList<SudokuSetBase>();
        }
    }
    
    public SudokuSolver getSolver() {
        return solver;
    }
    
    /**
     * Verboten sind alle Templates, die keine 1 an einer der bereits gesetzten Positionen haben:
     *    (positions & template) != positions
     * Verboten sind alle Templates, die eine 1 an einer nicht mehr erlaubten Position haben:
     *    (~(positions | allowedPositions) & template) != 0
     * Verboten sind alle Templates, die eine 1 an einer Position eines Templates haben, das aus
     *    allen verundeten Templates eines anderen Kandidaten gebildet wurde
     * Verboten sind alle Templates, die keine einzige überlappungsfreie Kombination mit wenigstens
     *    einem Template einer anderen Ziffer haben
     *
     * Wenn die Templates bekannt sind:
     *    alle Templates OR: Alle Kandidaten, die nicht enthalten sind, können gelöscht werden
     *    alle Templates AND: Alle Positionen, die übrig bleiben, können gesetzt werden
     *    alle gültigen Kombinationen aus Templates zweier Ziffern bilden (OR), alle Ergebnisse
     *           AND: An allen verbliebenen Positionen können alle Kandidaten, die nicht zu einer dieser
     *           Ziffern gehören, eliminiert werden.
     *
     * Verfeinern:
     *    Verboten sind alle Templates, die eine 1 an einer Position eines Templates haben, das aus
     *       allen verundeten Templates eines anderen Kandidaten gebildet wurde
     *    Verboten sind alle Templates, die keine einzige überlappungsfreie Kombination mit wenigstens
     *       einem Template einer anderen Ziffer haben
     *
     */
    public void initCandTemplates() {
        initCandTemplates(false);
    }
    
    public void initCandTemplates(boolean initLists) {
        if (! Options.getInstance().checkTemplates) {
            return;
        }
        Sudoku s = getSudoku();
        SudokuSetBase[] allowedPositions = s.getAllowedPositions();
        SudokuSet[] positions = s.getPositions();
        SudokuSetBase[] templates = Sudoku.templates;
        SudokuSetBase[] forbiddenPositions = new SudokuSetBase[10]; // eine 1 an jeder Position, an der Wert nicht mehr sein darf
        
//        SudokuSetBase setMask = new SudokuSetBase();
//        SudokuSetBase delMask = new SudokuSetBase();
//        SudokuSetBase temp = new SudokuSetBase();
        for (int i = 1; i <= 9; i++) {
            setValueTemplates[i].setAll();
            delCandTemplates[i].clear();
            candTemplates[i].clear();
            
            // eine 1 an jeder verbotenen Position ~(positions | allowedPositions)
            forbiddenPositions[i] = new SudokuSetBase();
            forbiddenPositions[i].set(positions[i]);
            forbiddenPositions[i].or(allowedPositions[i]);
            forbiddenPositions[i].not();
        }
        for (int i = 0; i < templates.length; i++) {
            for (int j = 1; j <= 9; j++) {
                if (! positions[j].andEquals(templates[i])) {
                    // Template hat keine 1 an einer bereits gesetzten Position
                    continue;
                }
                if (! forbiddenPositions[j].andEmpty(templates[i])) {
                    // Template hat eine 1 an einer verbotenen Position
                    continue;
                }
                // Template ist für Kandidaten erlaubt!
                setValueTemplates[j].and(templates[i]);
                delCandTemplates[j].or(templates[i]);
                if (initLists) {
                    candTemplates[j].add(templates[i]);
                }
            }
        }
        
        // verfeinern
        if (initLists) {
            int removals = 0;
            do {
                removals = 0;
                for (int j = 1; j <= 9; j++) {
                    setValueTemplates[j].setAll();
                    delCandTemplates[j].clear();
                    ListIterator<SudokuSetBase> it = candTemplates[j].listIterator();
                    while (it.hasNext()) {
                        SudokuSetBase template = it.next();
                        boolean removed = false;
                        for (int k = 1; k <= 9; k++) {
                            if (k != j && ! template.andEmpty(setValueTemplates[k])) {
                                it.remove();
                                removed = true;
                                removals++;
                                break;
                            }
                        }
                        if (! removed) {
                            setValueTemplates[j].and(template);
                            delCandTemplates[j].or(template);
                        }
                    }
                }
            } while (removals > 0);
        }
        
        for (int i = 1; i <= 9; i++) {
            delCandTemplates[i].not();
        }
    }
    
    public Sudoku getSudoku() {
        return sudoku;
    }
    
    public void setSudoku(Sudoku sudoku) {
        this.sudoku = sudoku;
        candType = SudokuCell.PLAY;
    }
    
    public SolutionStep getStep(Sudoku sudoku, SolutionType type) {
        setSudoku(sudoku);
        return getStep(type);
    }
    
    public boolean doStep(Sudoku sudoku, SolutionStep step) {
        setSudoku(sudoku);
        return doStep(step);
    }
    
    protected abstract SolutionStep getStep(SolutionType type);
    protected abstract boolean doStep(SolutionStep step);
    
    /**
     * Prüft, ob die Zelle mit Index <code>index</code> alle Zellen in <code>cells</code> sieht
     * (muss in der selben Zeile, Spalte oder im selben Block liegen).
     *
     *
     * @param index Index der Zelle, die geprüft werden soll
     * @param cells SudokuSet mit den Indexen aller Fins
     * @return <code>true</code>, wenn der Index alle Fins sieht, sonst <code>false</code>
     */
    protected boolean seesCells(int index, SudokuSet cells) {
//        int ii = index * 100;
//        for (int i = 0; i < cells.size(); i++) {
//            if (! cellsSeen[ii + cells.get(i)]) {
//                return false;
//            }
//        }
//        return true;
        if (! cells.isEmpty()) {
            tmpSet.set(Sudoku.buddies[cells.get(0)]);
        }
        for (int i = 1; i < cells.size(); i++) {
            tmpSet.and(Sudoku.buddies[cells.get(i)]);
        }
        if (tmpSet.contains(index)) {
            return true;
        }
        return false;
    }

    /**
     * Calculates n over k
     *
     * @param n
     * @param k
     * @return
     */
    public static int combinations(int n, int k) {
        if (n <= 167) {
            double fakN = 1;
            for (int i = 2; i <= n; i++) {
                fakN *= i;
            }
            double fakNMinusK = 1;
            for (int i = 2; i <= n - k; i++) {
                fakNMinusK *= i;
            }
            double fakK = 1;
            for (int i = 2; i <= k; i++) {
                fakK *= i;
            }
            return (int) (fakN / (fakNMinusK * fakK));
        } else {
            BigInteger fakN = BigInteger.ONE;
            for (int i = 2; i <= n; i++) {
                fakN = fakN.multiply(new BigInteger(i + ""));
            }
            BigInteger fakNMinusK = BigInteger.ONE;
            for (int i = 2; i <= n - k; i++) {
                fakNMinusK = fakNMinusK.multiply(new BigInteger(i + ""));
            }
            BigInteger fakK = BigInteger.ONE;
            for (int i = 2; i <= k; i++) {
                fakK = fakK.multiply(new BigInteger(i + ""));
            }
            fakNMinusK = fakNMinusK.multiply(fakK);
            fakN = fakN.divide(fakNMinusK);
            return fakN.intValue();
        }
    }
}
