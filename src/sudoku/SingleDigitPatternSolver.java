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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Empty Rectangles:
 * 
 * Every box can hold nine different empty rectangles ('X' means 'candidate not 
 * present', digits below are lines/cols):
 * 
 * + - - - +   + - - - +   + - - - +
 * | X X . |   | X . X |   | . X X |
 * | X X . |   | X . X |   | . X X |
 * | . . . |   | . . . |   | . . . |
 * + - - - +   + - - - +   + - - - +
 *    2 2         2 1         2 0
 * + - - - +   + - - - +   + - - - +
 * | X X . |   | X . X |   | . X X |
 * | . . . |   | . . . |   | . . . |
 * | X X . |   | X . X |   | . X X |
 * + - - - +   + - - - +   + - - - +
 *    1 2         1 1         1 0
 * + - - - +   + - - - +   + - - - +
 * | . . . |   | . . . |   | . . . |
 * | X X . |   | X . X |   | . X X |
 * | X X . |   | X . X |   | . X X |
 * + - - - +   + - - - +   + - - - +
 *    0 2         0 1         0 0
 * 
 * The '.' cells must contain at least three candidates, at least one exclusively
 * within the row/col (with two candidates the basic ER move degenerates into an 
 * X-Chain, with all three candidates only in a row/col it doesn't work at all).
 * 
 * For easy comparison SudokuSets with all posiible combinations of empty cells
 * for all blocks are created at startup.
 *
 * @author Bernhard Hobiger
 */
public class SingleDigitPatternSolver extends AbstractSolver {

    /** empty rectangles: all possible empty cells relative to cell 0 */
    private static final int[][] erOffsets = new int[][]{
        {0, 1, 9, 10},
        {0, 2, 9, 11},
        {1, 2, 10, 11},
        {0, 1, 18, 19},
        {0, 2, 18, 20},
        {1, 2, 19, 20},
        {9, 10, 18, 19},
        {9, 11, 18, 20},
        {10, 11, 19, 20}
    };
    /** empty rectangles: all possible ER lines relative to line 0, synchronized with {@link #erOffsets} */
    private static final int[] erLineOffsets = new int[]{2, 2, 2, 1, 1, 1, 0, 0, 0};
    /** empty rectangles: all possible ER cols relative to col 0, synchronized with {@link #erOffsets} */
    private static final int[] erColOffsets = new int[]{2, 1, 0, 2, 1, 0, 2, 1, 0};
    /** Bitmaps for all possible ERs for all blocks (all cells set except those that
     * have to be empty; if anded with the availble candidates in a block the result has to
     * be empty too) */
    private static final SudokuSet[][] erSets = new SudokuSet[9][9];
    /** All possible ER lines for all blocks, synchronized with {@link #erSets} */
    private static final int[][] erLines = new int[9][9];
    /** All possible ER cols for all blocks, synchronized with {@link #erSets} */
    private static final int[][] erCols = new int[9][9];
    // für Skyscraper: 9 Kandidaten, je 9 Entities, in jeder Entity ein Eintrag für die Anzahl und die ersten beiden Indexe
    private int[][][] tmpArr = new int[9][9][3];
    private int[][][] tmpArr2 = new int[9][9][3];
    // for empty rectangle
    private SudokuSet blockCands = new SudokuSet();
    private SudokuSet tmpSet = new SudokuSet();
    private List<SolutionStep> steps = new ArrayList<SolutionStep>();
    private SolutionStep globalStep = new SolutionStep();

    /** Creates a new instance of SimpleSolver */
    public SingleDigitPatternSolver() {
    }
    

    static {
        // initialize erSets, erLines, erCols
        int indexOffset = 0;
        int lineOffset = 0;
        int colOffset = 0;
        for (int i = 0; i < Sudoku.BLOCKS.length; i++) {
            for (int j = 0; j < erOffsets.length; j++) {
                erSets[i][j] = new SudokuSet();
                for (int k = 0; k < erOffsets[j].length; k++) {
                    erSets[i][j].add(erOffsets[j][k] + indexOffset);
                }
            }
            erLines[i] = new int[9];
            erCols[i] = new int[9];
            for (int j = 0; j < erLineOffsets.length; j++) {
                erLines[i][j] = erLineOffsets[j] + lineOffset;
                erCols[i][j] = erColOffsets[j] + colOffset;
            }
            // on to the next block
            indexOffset += 3;
            colOffset += 3;
            if ((i % 3) == 2) {
                indexOffset += 18;
                lineOffset += 3;
                colOffset = 0;
            }
            
        }
    }

    @Override
    protected SolutionStep getStep(SolutionType type) {
        SolutionStep result = null;
        switch (type) {
            case SKYSCRAPER:
                result = findSkyScraper();
                break;
            case TWO_STRING_KITE:
                result = findTwoStringKite();
                break;
            case EMPTY_RECTANGLE:
                result = findEmptyRectangle();
                break;
        }
        return result;
    }

    @Override
    protected boolean doStep(SolutionStep step) {
        boolean handled = true;
        switch (step.getType()) {
            case SKYSCRAPER:
            case TWO_STRING_KITE:
            case EMPTY_RECTANGLE:
                for (Candidate cand : step.getCandidatesToDelete()) {
                    sudoku.delCandidate(cand.getIndex(), candType, cand.getValue());
//                    SudokuCell cell = sudoku.getCell(cand.index);
//                    cell.delCandidate(candType, cand.value);
                }
                break;
            default:
                handled = false;
        }
        return handled;
    }

    public List<SolutionStep> findAllEmptyRectangles(Sudoku sudoku) {
        Sudoku oldSudoku = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldList = steps;
        List<SolutionStep> newList = new ArrayList<SolutionStep>();
        steps = newList;
        long ticks = System.currentTimeMillis();
        findEmptyRectangles();
        ticks = System.currentTimeMillis() - ticks;
        Logger.getLogger(getClass().getName()).log(Level.FINE, "end of findAllEmptyRectangles() (" + ticks + "ms)");
        Collections.sort(steps);
        steps = oldList;
        if (oldSudoku != null) {
            setSudoku(oldSudoku);
        }
        return newList;
    }

    protected SolutionStep findEmptyRectangle() {
        steps.clear();
        findEmptyRectangles();
        if (steps.size() > 0) {
            return steps.get(0);
        }
        return null;
    }

    /**
     * Finds all empty rectangles that provide eliminations (only simple case
     * with one cinjugate pair). The search is actually delegated to 
     * {@link #findEmptyRectanglesForCandidate(int)}.
     */
    private void findEmptyRectangles() {
        for (int i = 1; i <= 9; i++) {
            findEmptyRectanglesForCandidate(i);
        }
    }

    /**
     * Try all blocks: for every block check whether all the cells in erSets[block][i]
     * don't have the candidate in question. If this is true neither the ER line nor
     * the ER col may be empty (without crossing point!) and at least one of them
     * has to hold at least two candidates.
     * 
     * For any ER try to find a conjugate pair with one candidate in the row/col
     * of the ER, and one single candidate in ther intersection of the second
     * ca didate of the conjugate pair and the col/row of the ER.
     * 
     * @param cand candidate for which the grid is searched
     */
    private void findEmptyRectanglesForCandidate(int cand) {
        //if (cand != 3) return;
        // scan all blocks
        for (int i = 0; i < Sudoku.blockTemplates.length; i++) {
            // get all occurrencies for cand in block i
            blockCands.set(sudoku.getAllowedPositions()[cand]);
            blockCands.and(Sudoku.blockTemplates[i]);
            // check all possible ERs for that block
            for (int j = 0; j < erSets[i].length; j++) {
                int erLine = 0;
                int erCol = 0;
                boolean notEnoughCandidates = true;
                // are the correct cells empty?
                tmpSet.set(blockCands);
                if (!tmpSet.andEmpty(erSets[i][j])) {
                    // definitely not an ER
                    continue;
                }
                // now check the candidates in the lines
                tmpSet.set(blockCands);
                tmpSet.and(Sudoku.lineTemplates[erLines[i][j]]);
                if (tmpSet.size() >= 2) {
                    notEnoughCandidates = false;
                }
                tmpSet.andNot(Sudoku.colTemplates[erCols[i][j]]);
                if (tmpSet.isEmpty()) {
                    // not valid!
                    continue;
                }
                erLine = erLines[i][j];
                // and the candidates in the cols
                tmpSet.set(blockCands);
                tmpSet.and(Sudoku.colTemplates[erCols[i][j]]);
                if (tmpSet.size() >= 2) {
                    notEnoughCandidates = false;
                }
                tmpSet.andNot(Sudoku.lineTemplates[erLines[i][j]]);
                if (tmpSet.isEmpty()) {
                    // not valid!
                    continue;
                }
                erCol = erCols[i][j];
                if (notEnoughCandidates) {
                    // both row and colhave only one candidate -> invalid
                    continue;
                }
                // empty rectangle found: erLine and erCol hold the lineNumbers
                // try all cells in indices erLine; if a cell that is not part of the ER holds
                // a candidate, check whether it forms a conjugate pair in the respective col
                checkEmptyRectangle(cand, i, blockCands, Sudoku.LINES[erLine], Sudoku.lineTemplates,
                        Sudoku.colTemplates, erCol, false);
                checkEmptyRectangle(cand, i, blockCands, Sudoku.COLS[erCol], Sudoku.colTemplates,
                        Sudoku.lineTemplates, erLine, true);
            }
        }
    }

    /**
     * Checks possible eliminations for a given ER. The names of the parameters
     * are chosen for a conjugate pair search in the columns, but it works for
     * the columns too, if all indices/col parameters are exchanged in the
     * method call.
     * 
     * The method tries to find a conjugate pair in a column where one of the
     * candidates is in indices firstLine. If so all candidates in the indices of the
     * second cell of the conjugate pair are checked. If one of them lies in
     * column firstCol, it can be eliminated.
     * 
     * @param cand The candidate for which the check is made
     * @param block The index of the block holding the ER
     * @param blockCands All Candidates that comprise the ER
     * @param indices Indices of all cells in firstLine/firstCol
     * @param lineTemplates Sudoku.lineTemplates/Sudoku.colTemplates
     * @param colTemplates Sudoku.colTemplates/Sudoku.lineTemplates
     * @param firstCol Index of the col/indices of the ER
     * @param lineColReversed If <code>true</code>, all lines/columns are interchanged
     */
    private void checkEmptyRectangle(int cand, int block, SudokuSet blockCands,
            int[] indices, SudokuSet[] lineTemplates,
            SudokuSet[] colTemplates, int firstCol, boolean lineColReversed) {
        for (int i = 0; i < indices.length; i++) {
            int index = indices[i];
            if (sudoku.getCell(index).getValue() != 0) {
                // cell already set
                continue;
            }
            if (Sudoku.getBlock(index) == block) {
                // cell part of the ER
                continue;
            }
            if (sudoku.getCell(index).isCandidate(candType, cand)) {
                // possible conjugate pair -> check
                tmpSet.set(sudoku.getAllowedPositions()[cand]);
                int actCol = Sudoku.getCol(index);
                if (lineColReversed) {
                    actCol = Sudoku.getLine(index);
                }
                tmpSet.and(colTemplates[actCol]);
                if (tmpSet.size() == 2) {
                    // conjugate pair found
                    int index2 = tmpSet.get(0);
                    if (index2 == index) {
                        index2 = tmpSet.get(1);
                    }
                    // now check, whether a candidate in the row of index2
                    // sees the col of the ER
                    int actLine = Sudoku.getLine(index2);
                    if (lineColReversed) {
                        actLine = Sudoku.getCol(index2);
                    }
                    tmpSet.set(sudoku.getAllowedPositions()[cand]);
                    tmpSet.and(lineTemplates[actLine]);
                    for (int j = 0; j < tmpSet.size(); j++) {
                        int indexDel = tmpSet.get(j);
                        if (Sudoku.getBlock(indexDel) == block) {
                            // cannot eliminate an ER candidate
                            continue;
                        }
                        int colDel = Sudoku.getCol(indexDel);
                        if (lineColReversed) {
                            colDel = Sudoku.getLine(indexDel);
                        }
                        if (colDel == firstCol) {
                            // elimination found!
                            globalStep.reset();
                            globalStep.setType(SolutionType.EMPTY_RECTANGLE);
                            globalStep.setEntity(SudokuCell.BLOCK);
                            globalStep.setEntityNumber(block + 1);
                            globalStep.addValue(cand);
                            globalStep.addIndex(index);
                            globalStep.addIndex(index2);
                            for (int k = 0; k < blockCands.size(); k++) {
                                globalStep.addFin(blockCands.get(k), cand);
                            }
                            globalStep.addCandidateToDelete(indexDel, cand);
                            try {
                                steps.add((SolutionStep) globalStep.clone());
                            } catch (CloneNotSupportedException ex) {
                                Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                            }
                            // only one elimination per conjugate pair possible
                            break;
                        }
                    }
                }
            }
        }
    }

    public List<SolutionStep> findAllSkyScrapers(Sudoku sudoku) {
        Sudoku oldSudoku = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldList = steps;
        List<SolutionStep> newList = new ArrayList<SolutionStep>();
        steps = newList;
        findSkyScraper(Sudoku.LINES, Sudoku.COLS, Sudoku.BLOCKS);
        findSkyScraper(Sudoku.COLS, Sudoku.LINES, Sudoku.BLOCKS);
        Collections.sort(steps);
        steps = oldList;
        if (oldSudoku != null) {
            setSudoku(oldSudoku);
        }
        return newList;
    }

    protected SolutionStep findSkyScraper() {
        steps.clear();
        SolutionStep step = findSkyScraper(Sudoku.LINES, Sudoku.COLS, Sudoku.BLOCKS);
        if (step != null) {
            return step;
        }
        return findSkyScraper(Sudoku.COLS, Sudoku.LINES, Sudoku.BLOCKS);
    }

    private SolutionStep findSkyScraper(int[][] entityIndices1, int[][] entityIndices2, int[][] entityIndices3) {
        // ALle Kandidaten aufsummieren: Wie viele Kandidaten gibt es jeweils in jeder Entity1
        initTempArray(tmpArr, entityIndices1);

        // ok: wir suchen für jeden Kandidaten zwei Entities, die genau zwei Vorkommen des Kandidatens besitzen.
        // wenn jeweils einer dieser Kandidaten in der selben Entity2 vorkommt, ist es ein Skyscraper (die Kandidaten
        // in derselben Entity2 bilden die Verbindung, die anderen beiden Kandidaten werden zum Prüfen verwendet:
        // gibt es Kandidaten, die beide dieser Kandidaten sehen, können sie gelöscht werden).
        // ACHTUNG: Wenn die beiden Prüfkandidaten in derselben Entity2 liegen, ist es ein X-Wing (ignorieren)
        for (int i = 0; i < tmpArr.length; i++) {
            // 1 Iteration pro Kandidat
            for (int j = 0; j < tmpArr[i].length; j++) {
                // 1 Iteration pro Entity für den Kandidaten (i+1)
                if (tmpArr[i][j][0] == 2) {
                    // gibt es noch eine Entity mit 2 Kandidaten?
                    for (int k = j + 1; k < tmpArr[i].length; k++) {
                        if (tmpArr[i][k][0] == 2) {
                            // ok - möglicher Skyscraper
                            // liegen 2 Kandidaten in derselben Entity2?
                            for (int l = 0; l < entityIndices2.length; l++) {
                                // wenn zwei Kandidaten in derselben Entity2 liegen, werden ihre Indexe an
                                // die zweite Position geschrieben
                                boolean c11 = Arrays.binarySearch(entityIndices2[l], tmpArr[i][j][1]) >= 0;
                                boolean c12 = Arrays.binarySearch(entityIndices2[l], tmpArr[i][j][2]) >= 0;
                                boolean c21 = Arrays.binarySearch(entityIndices2[l], tmpArr[i][k][1]) >= 0;
                                boolean c22 = Arrays.binarySearch(entityIndices2[l], tmpArr[i][k][2]) >= 0;
                                if (c11 && c21) {
                                    swapIndices(i, j);
                                    swapIndices(i, k);
                                } else if (c11 && c22) {
                                    swapIndices(i, j);
                                } else if (c12 && c21) {
                                    swapIndices(i, k);
                                } else if (c12 && c22) {
                                    // passt schon
                                } else {
                                    // kein Match -> weitersuchen
                                    continue;
                                }

                                // ok, 2 Kandidaten in der selben Entity2 -> X-Wing ausschließen (die anderen beiden
                                // Kandidaten dürfen nicht in der selben Entity2 liegen
                                boolean isXWing = false;
                                for (int m = 0; m < entityIndices2.length; m++) {
                                    if (Arrays.binarySearch(entityIndices2[m], tmpArr[i][j][1]) >= 0 &&
                                            Arrays.binarySearch(entityIndices2[m], tmpArr[i][k][1]) >= 0) {
                                        isXWing = true;
                                        break;
                                    }
                                }
                                if (isXWing) {
                                    continue;
                                // ok, ist Skyscraper, SolutionStep erzeugen und zu löschende Kandidaten suchen
                                }
                                SolutionStep step = new SolutionStep(SolutionType.SKYSCRAPER);
                                step.addValue(i + 1);
                                step.addIndex(tmpArr[i][j][1]);
                                step.addIndex(tmpArr[i][k][1]);
                                step.addIndex(tmpArr[i][j][2]);
                                step.addIndex(tmpArr[i][k][2]);
                                checkCandidatesToDelete(step, i + 1, tmpArr[i][j][1], tmpArr[i][k][1], entityIndices1, entityIndices2, entityIndices3);
                                checkCandidatesToDelete(step, i + 1, tmpArr[i][k][1], tmpArr[i][j][1], entityIndices1, entityIndices2, entityIndices3);
                                if (step.getCandidatesToDelete().size() > 0) {
                                    steps.add(step);
                                //return step;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (steps.size() > 0) {
            return steps.get(0);
        }
        return null;
    }

    public List<SolutionStep> findAllTwoStringKites(Sudoku sudoku) {
        Sudoku oldSudoku = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldList = steps;
        List<SolutionStep> newList = new ArrayList<SolutionStep>();
        steps = newList;
        findTwoStringKite(Sudoku.LINES, Sudoku.COLS);
        findTwoStringKite(Sudoku.COLS, Sudoku.LINES);
        Collections.sort(steps);
        steps = oldList;
        if (oldSudoku != null) {
            setSudoku(oldSudoku);
        }
        return newList;
    }

    protected SolutionStep findTwoStringKite() {
        steps.clear();
        SolutionStep step = findTwoStringKite(Sudoku.LINES, Sudoku.COLS);
        if (step != null) {
            return step;
        }
        return findTwoStringKite(Sudoku.COLS, Sudoku.LINES);
    }

    private SolutionStep findTwoStringKite(int[][] entityIndices1, int[][] entityIndices2) {
        // ALle Kandidaten aufsummieren: Wie viele Kandidaten gibt es jeweils in jeder Entity1 bzw. Entity2
        initTempArray(tmpArr, entityIndices1);
        initTempArray(tmpArr2, entityIndices2);

        // ok: wir suchen für jeden Kandidaten zwei Entities1, die genau zwei Vorkommen des Kandidatens besitzen und
        // deren Kandidaten in zwei verschiedenen Blöcken liegen. Dasselbe machen wir für die Entites2.
        // Für jede mögliche Kombination von 2er-Entities wird geprüft, ob sich die Entites überschneiden
        // (ob jeweils ein Kandidat aus jeder Entity im selben Block liegt).
        // wenn ja, wird für die beiden anderen Kandidaten jeweils die komplementäre Entity gesucht (für
        // den Line-Kandidaten die Col und umgekehrt). Gibt es am Schnittpunkt der komplementären Entities einen
        // Kandidaten, kann er eliminiert werden.
        for (int i = 0; i < tmpArr.length; i++) {
            // 1 Iteration pro Kandidat
            for (int j = 0; j < tmpArr[i].length; j++) {
                // 1 Iteration pro Entity für den Kandidaten (i+1)
                if (tmpArr[i][j][0] == 2) {
                    // die Kandidaten müssen in verschiedenen Blöcken liegen
                    if (Sudoku.getBlock(tmpArr[i][j][1]) == Sudoku.getBlock(tmpArr[i][j][2])) {
                        // kein 2-String Kite möglich
                        continue;
                    }
                    // jetzt die Entities2 durchschauen und alle Kombinationen prüfen
                    for (int k = 0; k < tmpArr2[i].length; k++) {
                        if (tmpArr2[i][k][0] == 2) {
                            // auch hier auf verschiedenen Blöcke prüfen
                            if (Sudoku.getBlock(tmpArr2[i][k][1]) == Sudoku.getBlock(tmpArr2[i][k][2])) {
                                // kein 2-String Kite möglich
                                continue;
                            }
                            // ok - möglicher 2-String-Kite
                            // liegen 2 Kandidaten im selben Block?
                            // wenn zwei Kandidaten im selben Block liegen, werden ihre Indexe an
                            // die zweite Position geschrieben
                            int b11 = Sudoku.getBlock(tmpArr[i][j][1]);
                            int b12 = Sudoku.getBlock(tmpArr[i][j][2]);
                            int b21 = Sudoku.getBlock(tmpArr2[i][k][1]);
                            int b22 = Sudoku.getBlock(tmpArr2[i][k][2]);
                            if (b11 == b21) {
                                swapIndices(tmpArr, i, j);
                                swapIndices(tmpArr2, i, k);
                            } else if (b11 == b22) {
                                swapIndices(tmpArr, i, j);
                            } else if (b12 == b21) {
                                swapIndices(tmpArr2, i, k);
                            } else if (b12 == b22) {
                                // passt schon
                            } else {
                                // kein Match -> weitersuchen
                                continue;
                            }
                            // jetzt dürfen noch die beiden Indizes, die die Verbindung erzeugen, nicht gleich sein!
                            if (tmpArr[i][j][2] == tmpArr2[i][k][2]) {
                                // doch kein 2-Strinh Kite, sorry
                                continue;
                            }
                            // ok, zwei verbundene Conjugate Pairs -> Zeilen bzw. Spalten suchen und
                            // Schnittpunkt prüfen
                            int e1 = -1;
                            int e2 = -1;
                            for (int l = 0; l < entityIndices1.length; l++) {
                                if (e2 == -1) {
                                    if (Arrays.binarySearch(entityIndices2[l], tmpArr[i][j][1]) >= 0) {
                                        e2 = l;
                                    }
                                }
                                if (e1 == -1) {
                                    if (Arrays.binarySearch(entityIndices1[l], tmpArr2[i][k][1]) >= 0) {
                                        e1 = l;
                                    }
                                }
                            }
                            // Schnittpunkt finden
                            int schnittIndex = 0;
                            for (int l = 0; l < entityIndices1[e1].length; l++) {
                                if (Arrays.binarySearch(entityIndices2[e2], entityIndices1[e1][l]) >= 0) {
                                    schnittIndex = entityIndices1[e1][l];
                                    break;
                                }
                            }
                            // Wenn es hier einen Kandidaten (i+1) gibt, kann er gelöscht werden
                            SudokuCell cell = sudoku.getCell(schnittIndex);
                            if (cell.getValue() == 0 && cell.isCandidate(candType, i + 1)) {
                                // ok, ist 2-String Kite, SolutionStep erzeugen und zu löschende Kandidaten suchen
                                SolutionStep step = new SolutionStep(SolutionType.TWO_STRING_KITE);
                                step.addValue(i + 1);
                                step.addIndex(tmpArr[i][j][1]);
                                step.addIndex(tmpArr2[i][k][1]);
                                step.addIndex(tmpArr[i][j][2]);
                                step.addIndex(tmpArr2[i][k][2]);
                                step.addCandidateToDelete(schnittIndex, i + 1);
                                // die Schnittkandidaten werden als Fins hinzugefügt, damit sie in einer anderen
                                // Farbe gezeichnet werden
                                step.addFin(tmpArr[i][j][2], i + 1);
                                step.addFin(tmpArr2[i][k][2], i + 1);
                                steps.add(step);
                            //return step;
                            }
                        }
                    }
                }
            }
        }
        if (steps.size() > 0) {
            return steps.get(0);
        }
        return null;
    }

    private void initTempArray(int[][][] arr, int[][] entityIndices) {
        // Initialisieren
        for (int i = 0; i < arr.length; i++) {
            for (int j = 0; j < arr[i].length; j++) {
                arr[i][j][0] = 0;   // Anzahl: Wie oft kommt Kandidat (i+1) in Entity (i+1) vor

                arr[i][j][1] = -1;  // Index des ersten Kandidatens

                arr[i][j][2] = -1;  // Index des zweiten Kandidatens

            }
        }

        // Kandidaten aufsummieren
        for (int i = 0; i < entityIndices.length; i++) {
            // 1 Durchlauf pro Entity -> wird zweiter Index in arr
            for (int j = 0; j < entityIndices[i].length; j++) {
                // 1 Durchlauf pro Zelle der Entity (i+1)
                SudokuCell cell = sudoku.getCell(entityIndices[i][j]);
                if (cell.getValue() == 0) {
                    for (int k = 1; k <= 9; k++) {
                        // 1 Durchlauf pro Kandidat
                        if (cell.isCandidate(candType, k)) {
                            arr[k - 1][i][0]++;
                            if (arr[k - 1][i][1] == -1) {
                                arr[k - 1][i][1] = entityIndices[i][j];
                            } else {
                                arr[k - 1][i][2] = entityIndices[i][j];
                            }
                        }
                    }
                }
            }
        }
    }

    private void swapIndices(int i1, int i2) {
        swapIndices(tmpArr, i1, i2);
    }

    private void swapIndices(int[][][] arr, int i1, int i2) {
        int dummy = arr[i1][i2][1];
        arr[i1][i2][1] = arr[i1][i2][2];
        arr[i1][i2][2] = dummy;
    }

    private void checkCandidatesToDelete(SolutionStep step, int cand, int index1, int index2, int[][] entityIndices1,
            int[][] entityIndices2, int[][] entityIndices3) {
        // feststellen, in welcher Entity2 index1 liegt, anschließend alle anderen Kandidaten in dieser
        // Entity durchgehen und schauen, ob sie dieselbe Entity1 oder Entity3 mit index2 haben; wenn ja,
        // kann der Kandidat gelöscht werden
        int i2 = 0;
        for (i2 = 0; i2 < entityIndices2.length; i2++) {
            // es muss einen Treffer geben!
            if (Arrays.binarySearch(entityIndices2[i2], index1) >= 0) {
                break;
            }
        }

        // Alle Kandidaten in entityIndices2[i2] durchgehen
        for (int i = 0; i < entityIndices2[i2].length; i++) {
            if (entityIndices2[i2][i] == index1) {
                // Kandidat für Skyscraper -> zählt nicht mit
                continue;
            }
            int index = entityIndices2[i2][i];
            if (sudoku.getCell(index).getValue() == 0 && sudoku.getCell(index).isCandidate(candType, cand)) {
                // ok möglicher Kandidat, muss mit index2 eine Entity1 oder Entity3 gemeinsam haben
                if (isSameEntity(index, index2, entityIndices1) || isSameEntity(index, index2, entityIndices3)) {
                    step.addCandidateToDelete(index, cand);
                }
            }
        }
    }

    private boolean isSameEntity(int i1, int i2, int[][] entityIndices) {
        for (int i = 0; i < entityIndices.length; i++) {
            if (Arrays.binarySearch(entityIndices[i], i1) >= 0 && Arrays.binarySearch(entityIndices[i], i2) >= 0) {
                return true;
            }
        }
        return false;
    }
}
