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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Bernhard Hobiger
 */
public class UniquenessSolver extends AbstractSolver {

    private SudokuSet emptyCells = new SudokuSet();   // enthält eine Liste mit allen Zellen, die noch keinen Wert haben
    private boolean[] biValueCells = new boolean[81]; // enthält für alle Zellen in EmptyCells true, wenn die Zelle nur zwei Kandidaten hat
    private int[][] rectangles = new int[40][4];      // bisher gefundene unique Rectangles zur Optimierung; gibt es mehr als 40 Rects, wird einfach nicht optimiert
    private int[] tmpRect = new int[4];               // Puffer zum Prüfen der Rectangles
    private int rectAnz = 0;                          // aktueller Index in rectangles
    // Eigenschaften für die Tests auf löschbare Kandidaten
    private SudokuSet twoCandidates = new SudokuSet();        // Indexe aller Zellen des Rectangles, die nur zwei Kandidaten haben
    private SudokuSet additionalCandidates = new SudokuSet(); // Indexe aller Zellen des Rectangles mit mehr als zwei Kandidaten
    // Optimierung
    private SolutionStep globalStep = new SolutionStep(SolutionType.FULL_HOUSE);
    private int[] indexe;              // die Indexe der Zellen, die das unique rectangle bilden
    private int cand1;                 // der erste der Kandidaten des Rectangles
    private int cand2;                 // der zweite der Kandidaten des Rectangles
    private List<SolutionStep> steps;  // die bisher gefundenen Steps
    private SudokuSet tmpSet = new SudokuSet(); // for various checks

    /** Creates a new instance of SimpleSolver */
    public UniquenessSolver() {
    }

    @Override
    protected SolutionStep getStep(SolutionType type) {
        SolutionStep result = null;
        switch (type) {
            case UNIQUENESS_1:
            case UNIQUENESS_2:
            case UNIQUENESS_3:
            case UNIQUENESS_4:
            case UNIQUENESS_5:
            case UNIQUENESS_6:
            case HIDDEN_RECTANGLE:
                result = getUniqueness(type);
                break;
            case AVOIDABLE_RECTANGLE_1:
            case AVOIDABLE_RECTANGLE_2:
                result = getAvoidableRectangle(type);
                break;
            case BUG_PLUS_1:
                result = getBugPlus1();
                break;
        }
        return result;
    }

    @Override
    protected boolean doStep(SolutionStep step) {
        boolean handled = true;
        switch (step.getType()) {
            case UNIQUENESS_1:
            case UNIQUENESS_2:
            case UNIQUENESS_3:
            case UNIQUENESS_4:
            case UNIQUENESS_5:
            case UNIQUENESS_6:
            case HIDDEN_RECTANGLE:
            case AVOIDABLE_RECTANGLE_1:
            case AVOIDABLE_RECTANGLE_2:
            case BUG_PLUS_1:
                for (Candidate cand : step.getCandidatesToDelete()) {
                    sudoku.delCandidate(cand.getIndex(), candType, cand.getValue());
                }
                break;
            default:
                handled = false;
        }
        return handled;
    }

    /**
     * Zuerst wird eine Liste mit allen Zellen erstellt, die noch keinen Wert
     * gesetzt haben. In einer synchronisierten Liste werden alle Zellen gekennzeichnet,
     * die nur noch zwei Kandidaten haben.
     *
     * Jede Zelle mit nur zwei Kandidaten ist ein Ausgangspunkt für eine Suche nach einem
     * Unique Rectangle:
     *   - finde in der gleichen Zeile oder Spalte eine Zelle mit denselben zwei Kandidaten
     *     (plus beliebig viele andere), die im selben Block liegt
     *   - Gibt es eine solche Zelle, suche in der anderen Einheit (Zelle in selber Spalte,
     *     Suche in selber Zeile) eine weitere Zelle mit denselben zwei Kandidaten und
     *     prüfe das vierte Eck
     *   - wenn ein Rechteck gefunden wurde, prüfe die Bedingungen
     *   - wurde keine Rechteck gefunden, starte die Suche von der nächsten Ausgangszelle
     *
     */
    private SolutionStep getUniqueness(SolutionType type) {
        steps = getAllUniquenessInternal(new ArrayList<SolutionStep>());
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getType() == type) {
                return steps.get(i);
            }
        }
        return null;
    }

    /**
     * More or less equal to getUniqueness().
     */
    private SolutionStep getAvoidableRectangle(SolutionType type) {
        steps = getAllAvoidableRectangles(new ArrayList<SolutionStep>());
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getType() == type) {
                return steps.get(i);
            }
        }
        return null;
    }

    /**
     * More or less equal to getUniqueness().
     */
    private List<SolutionStep> getAllAvoidableRectangles(List<SolutionStep> tmpSteps) {
        // initialisieren
        this.steps = tmpSteps;
        initSets();

        // checkfor solved cells that are not givens
        for (int i = 0; i < sudoku.getCells().length; i++) {
            SudokuCell cell = sudoku.getCell(i);
            if (cell.getValue() == 0 || cell.isFixed()) {
                // cell is either not solved or a given
                continue;
            }
            cand1 = cell.getValue();
            findUniquenessForStartCell(i, Sudoku.LINES, Sudoku.COLS, true);
            findUniquenessForStartCell(i, Sudoku.COLS, Sudoku.LINES, true);
        }
        return tmpSteps;
    }

    public List<SolutionStep> getAllUniqueness(Sudoku sudoku) {
        Sudoku save = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> tmpSteps = new ArrayList<SolutionStep>();
        tmpSteps = getAllUniqueness(tmpSteps);
        if (save != null) {
            setSudoku(save);
        }
        return tmpSteps;
    }

    public List<SolutionStep> getAllUniqueness(List<SolutionStep> steps) {
        steps = getAllUniquenessInternal(steps);
        steps = getAllAvoidableRectangles(steps);
        return steps;
    }

    private List<SolutionStep> getAllUniquenessInternal(List<SolutionStep> steps) {
        // initialisieren
        this.steps = steps;
        initSets();

        // normal uniqueness
        for (int i = 0; i < emptyCells.size(); i++) {
            int index = emptyCells.get(i);
            if (biValueCells[index] == false) {
                continue;
            }
            short[] cands = sudoku.getCell(index).getAllCandidates(candType);
            cand1 = cands[0];
            cand2 = cands[1];
            findUniquenessForStartCell(index, Sudoku.LINES, Sudoku.COLS, false);
            findUniquenessForStartCell(index, Sudoku.COLS, Sudoku.LINES, false);
        }
        return steps;
    }

    private SolutionStep getBugPlus1() {
        initSets();

        // alle BiValue-Cells durchgehen: es darf nur eine Zelle mit genau einem Zusatzkandidaten geben
        // ist das erfüllt, werden alle Zeilen, Spalten und Blöcke, die die 3er Zelle beinhalten abgesucht.
        // der Kandidat, der in mindestens einem Haus mehr als 2 mal vorkommt, muss gesetzt werden.
        // 20081024: Der Kandidat muss in der 3er-Zelle selbst auch vorkommen, sonst ist es kein BUG+1
        int count = 0;
        int index3 = 0;
        for (int i = 0; i < emptyCells.size(); i++) {
            int index = emptyCells.get(i);
            if (sudoku.getCell(index).getValue() == 0 && biValueCells[index] == false) {
                count++;
                index3 = index;
            }
        }
        if (count == 1) {
            // Möglicherweise BUG+1 -> auf BUG-Pattern prüfen (in jedem Haus kommt jeder Kandidat nur genau 2 mal vor)
            short[] candArr = sudoku.getCell(index3).getAllCandidates(candType);
            for (int i = 0; i < candArr.length; i++) {
                if (isBugPlus1(candArr[i])) {
                    indexe = null;
                    initStep(SolutionType.BUG_PLUS_1);
                    for (int j = 0; j < candArr.length; j++) {
                        if (j != i && candArr[j] != 0) {
                            globalStep.addCandidateToDelete(index3, candArr[j]);
                        }
                    }
                    try {
                        return (SolutionStep) globalStep.clone();
                    } catch (CloneNotSupportedException ex) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                    }
                }
            }
        }

        return null;
    }

    private boolean isBugPlus1(int cand) {
        if (!isBugPlus1Unit(cand, Sudoku.LINES)) {
            return false;
        }
        if (!isBugPlus1Unit(cand, Sudoku.COLS)) {
            return false;
        }
        if (!isBugPlus1Unit(cand, Sudoku.BLOCKS)) {
            return false;
        }
        return true;
    }

    /**
     * In jedem Haus in units dürfen alle Kandidaten außer cand nur 2 mal vorkommen.
     * cand darf nur 3 mal vorkommen.
     */
    private boolean isBugPlus1Unit(int cand, int[][] units) {
        for (int i = 0; i < units.length; i++) {
            for (int k = 1; k <= 9; k++) {
                int count = 0;
                for (int j = 0; j < units[i].length; j++) {
                    if (sudoku.getCell(units[i][j]).getValue() == 0 && sudoku.getCell(units[i][j]).isCandidate(candType, k)) {
                        count++;
                    }
                    if ((k == cand && count > 3) || (k != cand && count > 2)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * - Feststellen, in welcher Unit1 sich die Zelle befindet
     * - alle anderen Zellen dieser Unit1 anschauen, die im selben Block sind
     * - wenn eine Zelle die selben zwei Kandidaten (plus beliebig viele andere) hat, für beide Zellen
     *   die Unit2 finden
     * - Alle Zellen dieser Unit2(s) durchgehen, die nicht im selben Block wie die Unit1(s) sind
     * - Wenn beide Zellen die selben beiden Kandidaten haben -> mögliches Unique Rectangle, SolutionStep prüfen
     * 
     * when checking for avoidable rectangles some rules change: index11/index12 have
     * to be solved cells, they designate the candidates; the other side of the
     * avoidable rectangle has to have at least on not solved cell. All solved cells
     * must not be givens.
     */
    private void findUniquenessForStartCell(int index11, int[][] units1, int[][] units2, boolean avoidable) {
        // richtige Unit1 finden
        int ui11 = getUnitIndex(index11, units1);
        int block1 = Sudoku.getBlock(index11);

        // alle Zellen dieser Unit1 durchgehen
        for (int i = 0; i < units1[ui11].length; i++) {
            int index12 = units1[ui11][i];
            if (Sudoku.getBlock(index12) != block1 || index12 == index11) {
                // zweite Zelle muss im selben Block sein, darf nicht gleich der Startzelle sein!
                continue;
            }
            SudokuCell cell12 = sudoku.getCell(index12);
            if ((!avoidable && (cell12.getValue() == 0 && cell12.isCandidate(candType, cand1) && cell12.isCandidate(candType, cand2))) ||
                    (avoidable && (cell12.getValue() != 0 && ! cell12.isFixed()))) {
                if (avoidable) {
                    cand2 = cell12.getValue();
                }
                // möglicher zweiter Eckpunkt: die beiden betroffenen Unit2 feststellen
                int ui21 = getUnitIndex(index11, units2);
                int ui22 = getUnitIndex(index12, units2);
                // jetzt beide units2 durchgehen
                for (int j = 0; j < units2[ui21].length; j++) {
                    int index21 = units2[ui21][j];
                    int index22 = units2[ui22][j];
                    if (Sudoku.getBlock(index21) == block1) {
                        // die zweite Seite des Rechtecks muss in einem anderen Block sein
                        continue;
                    }
                    SudokuCell cell21 = sudoku.getCell(index21);
                    SudokuCell cell22 = sudoku.getCell(index22);
                    if ((!avoidable && (cell21.getValue() == 0 && cell22.getValue() == 0 &&
                            cell21.isCandidate(candType, cand1) && cell21.isCandidate(candType, cand2) &&
                            cell22.isCandidate(candType, cand1) && cell22.isCandidate(candType, cand2))) ||
                            (avoidable && ((cell21.getValue() == cand2 && !cell21.isFixed() && cell22.getValue() == 0 && cell22.isCandidate(candType, cand1) && cell22.getAnzCandidates(candType) == 2) ||
                            (cell22.getValue() == cand1 && !cell22.isFixed() && cell21.getValue() == 0 && cell21.isCandidate(candType, cand2) && cell21.getAnzCandidates(candType) == 2) ||
                            (cell21.getValue() == 0 && cell21.isCandidate(candType, cand2) && cell21.getAnzCandidates(candType) == 2 &&
                            cell22.getValue() == 0 && cell22.isCandidate(candType, cand1) && cell22.getAnzCandidates(candType) == 2)))) {
                        // ok, mögliches Unique Rectangle; prüfen, ob wir das schon hatten
                        if (checkRect(index11, index12, index21, index22)) {
                            // Typ prüfen und feststellen, ob Kandidaten gelöscht werden können
                            indexe = new int[]{index11, index12, index21, index22};
                            if (avoidable) {
                                checkAvoidableRectangle(index21, index22, cell21, cell22);
                            } else {
                                checkCandidatesToDelete(new SudokuCell[]{sudoku.getCell(index11), cell12, cell21, cell22});
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkCandidatesToDelete(SudokuCell[] cells) {
        // feststellen, wie viele Zellen nur zwei Kandidaten haben und welche mehr
        initCheck(indexe, cells);

        // Uniqueness Test 1: 3 Zellen haben nur 2 Kandidaten -> in der vierten Zelle können
        // cand1 und cand2 gelöscht werden
        if (twoCandidates.size() == 3) {
            // ja, ist Uniqueness Type 1
            initStep(SolutionType.UNIQUENESS_1);
            globalStep.addCandidateToDelete(additionalCandidates.get(0), cand1);
            globalStep.addCandidateToDelete(additionalCandidates.get(0), cand2);
            try {
                steps.add((SolutionStep) globalStep.clone());
            } catch (CloneNotSupportedException ex) {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
            }
        }

        // Uniqueness Test 2/5/5A: eine oder zwei Zellen haben 2 Kandidaten, die anderen Zellen 
        // haben genau 1 zusätzlichen Kandidaten, der in allen Zellen gleich ist: Dieser 
        // Kandidat kann in allen Zellen gelöscht werden, die alle anderen Zellen sehen (außer 
        // in den Zellen des Rechtangles)
        // Gibt es 2 andere Zellen und sind sie in einer Zeile oder Spalte, ist es Test2, sonst Test 5
        if (twoCandidates.size() == 2 || twoCandidates.size() == 1) {
            boolean isType25 = true;
            int addCand = -1;
            for (int i = 0; i < additionalCandidates.size(); i++) {
                SudokuCell cell3 = sudoku.getCell(additionalCandidates.get(i));
                if (cell3.getAnzCandidates(candType) != 3) {
                    isType25 = false;
                    break;
                }
                if (addCand == -1) {
                    addCand = getThirdCandidate(cell3, cand1, cand2);
                } else {
                    if (getThirdCandidate(cell3, cand1, cand2) != addCand) {
                        isType25 = false;
                        break;
                    }
                }
            }
            if (isType25) {
                // ok, ist gültiger Test Typ 2 oder 5
                // feststellen, ob es Zellen gibt, die alle Zellen mit Zusatzkandidaten sehen können -> Zusatzkandidat kann gelöscht werden
                SolutionType type = SolutionType.UNIQUENESS_2;
                int i1 = additionalCandidates.get(0);
                int i2 = additionalCandidates.get(1);
                if (additionalCandidates.size() == 3 || (Sudoku.getLine(i1) != Sudoku.getLine(i2) && Sudoku.getCol(i1) != Sudoku.getCol(i2))) {
                    type = SolutionType.UNIQUENESS_5;
                }
                initStep(type);
                for (int i = 0; i < sudoku.getCells().length; i++) {
                    SudokuCell cell = sudoku.getCell(i);
                    if (cell.getValue() == 0 && cell.isCandidate(candType, addCand) && !additionalCandidates.contains(i) &&
                            !twoCandidates.contains(i) && seesCells(i, additionalCandidates)) {
                        globalStep.addCandidateToDelete(i, addCand);
                    }
                }
                if (globalStep.getCandidatesToDelete().size() > 0) {
                    try {
                        steps.add((SolutionStep) globalStep.clone());
                    } catch (CloneNotSupportedException ex) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                    }
                }
            }
        }

        // Uniqueness Test 3: Wenn zwei Zellen des Rectangles zusätzliche Kandidaten haben, finde im Haus dieser beiden Zellen
        // weitere (k - 1) Zellen, sodass in allen Zellen genau k Kandidaten sind (die Kandidaten des Rectangles zählen nicht
        // mit und dürfen in den extra Zellen nicht vorkommen). Wenn das erfüllt ist können diese k Kandidaten aus allen anderen
        // Zellen des Hauses gelöscht werden
        if (twoCandidates.size() == 2) {
            SudokuSet u3Cands = new SudokuSet();
            // alle zusätzlichen Kandidaten sammeln
            for (int i = 0; i < additionalCandidates.size(); i++) {
                SudokuCell cell = sudoku.getCell(additionalCandidates.get(i));
                for (int j = 1; j <= 9; j++) {
                    if (j != cand1 && j != cand2 && cell.isCandidate(candType, j)) {
                        u3Cands.add(j);
                    }
                }
            }
            // das Haus feststellen und alle Zellen sammeln, die nicht besetzt sind und nicht cand1 oder cand2 als Kandidaten haben
            int i1 = additionalCandidates.get(0);
            int i2 = additionalCandidates.get(1);
            if (Sudoku.getLine(i1) == Sudoku.getLine(i2)) {
                checkUniqueness3(SudokuCell.LINE, Sudoku.LINES[Sudoku.getLine(i1)], u3Cands);
            }
            if (Sudoku.getCol(i1) == Sudoku.getCol(i2)) {
                checkUniqueness3(SudokuCell.COL, Sudoku.COLS[Sudoku.getCol(i1)], u3Cands);
            }
            if (Sudoku.getBlock(i1) == Sudoku.getBlock(i2)) {
                checkUniqueness3(SudokuCell.USER, Sudoku.BLOCKS[Sudoku.getBlock(i1)], u3Cands);
            }
        }

        // Uniqueness Test 4: Wenn es 2 Zellen mit Zusatzkandidaten gibt und beide in der selben Zeile oder Spalte sind und keine
        // der Zellen, die beide Zellen sehen können, einen der beiden Kandidaten enthalten, kann der andere Kandidat
        // aus den Zellen mit zusätzlichen Kandidaten gelöscht werden.
        if (twoCandidates.size() == 2) {
            int i1 = additionalCandidates.get(0);
            int i2 = additionalCandidates.get(1);
            if ((Sudoku.getLine(i1) == Sudoku.getLine(i2)) || (Sudoku.getCol(i1) == Sudoku.getCol(i2))) {
                boolean noCand1 = true;
                boolean noCand2 = true;
                for (int i = 0; i < sudoku.getCells().length; i++) {
                    SudokuCell cell = sudoku.getCell(i);
                    if (cell.getValue() == 0 && !additionalCandidates.contains(i) &&
                            !twoCandidates.contains(i) && seesCells(i, additionalCandidates)) {
                        if (cell.isCandidate(candType, cand1)) {
                            noCand1 = false;
                        }
                        if (cell.isCandidate(candType, cand2)) {
                            noCand2 = false;
                        }
                    }
                }
                if (noCand1 || noCand2) {
                    initStep(SolutionType.UNIQUENESS_4);
                    int candToDelete = noCand1 ? cand2 : cand1;
                    globalStep.addCandidateToDelete(additionalCandidates.get(0), candToDelete);
                    globalStep.addCandidateToDelete(additionalCandidates.get(1), candToDelete);
                    try {
                        steps.add((SolutionStep) globalStep.clone());
                    } catch (CloneNotSupportedException ex) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                    }
                }
            }
        }

        // Uniqueness Test 6: Wenn es 2 Zellen mit Zusatzkandidaten gibt und sie diagonal ausgerichtet sind und es in beiden
        // Spalten und Zeilen keine Zellen gibt, die einen der beiden Kandidaten besitzen, kann der andere gelöscht werden.
        if (twoCandidates.size() == 2) {
            int i1 = additionalCandidates.get(0);
            int i2 = additionalCandidates.get(1);
            if ((Sudoku.getLine(i1) != Sudoku.getLine(i2)) && (Sudoku.getCol(i1) != Sudoku.getCol(i2))) {
                int[][] units = new int[4][];
                units[0] = Sudoku.LINES[Sudoku.getLine(i1)];
                units[1] = Sudoku.COLS[Sudoku.getCol(i1)];
                units[2] = Sudoku.LINES[Sudoku.getLine(i2)];
                units[3] = Sudoku.COLS[Sudoku.getCol(i2)];
                boolean noCand1 = true;
                boolean noCand2 = true;
                for (int uIndex = 0; uIndex < units.length; uIndex++) {
                    for (int i = 0; i < units[uIndex].length; i++) {
                        int tmp = units[uIndex][i];
                        SudokuCell cell = sudoku.getCell(tmp);
                        if (cell.getValue() == 0 && !additionalCandidates.contains(tmp) && !twoCandidates.contains(tmp)) {
                            if (cell.isCandidate(candType, cand1)) {
                                noCand1 = false;
                            }
                            if (cell.isCandidate(candType, cand2)) {
                                noCand2 = false;
                            }
                        }
                    }
                }
                if (noCand1 || noCand2) {
                    initStep(SolutionType.UNIQUENESS_6);
                    int candToDelete = noCand1 ? cand1 : cand2;
                    globalStep.addCandidateToDelete(additionalCandidates.get(0), candToDelete);
                    globalStep.addCandidateToDelete(additionalCandidates.get(1), candToDelete);
                    try {
                        steps.add((SolutionStep) globalStep.clone());
                    } catch (CloneNotSupportedException ex) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                    }
                }
            }
        }

        // Hidden Rectangle: If one or two cells contain only two candidates (if two they
        // have to be aligned diagonally) one of the cells with only two cands is the corner; if
        // the line and the col through the other three cells contain only two occurences
        // of one of the uniqueness candidates each, the other uniqueness candidate
        // can be deleted
        if (twoCandidates.size() == 2 || twoCandidates.size() == 1) {
            boolean doCheck = true;
            if (twoCandidates.size() == 2) {
                // must be aligned diagonally -> must not be in the same row or column
                int i1 = twoCandidates.get(0);
                int i2 = twoCandidates.get(1);
                if (Sudoku.getLine(i1) == Sudoku.getLine(i2) ||
                        Sudoku.getCol(i1) == Sudoku.getCol(i2)) {
                    doCheck = false;
                }
            }
            if (doCheck) {
                checkHiddenRectangle(twoCandidates.get(0));
                if (twoCandidates.size() == 2) {
                    checkHiddenRectangle(twoCandidates.get(1));
                }
            }
        }
    }

    /**
     * Avoidable Rectangle: If only one of cell21/cell22 is not set,
     * cand1/cand2 can be deleted from that cell. If both cell21 and cell22
     * are not set, cell21 must contain cand2, cell22 must contain cand1
     * and both cells have to have the same additional candidate. This
     * candidate can be deleted from all cells that see both cell21 and cell22.
     */
    private void checkAvoidableRectangle(int index21, int index22, SudokuCell cell21, SudokuCell cell22) {
        if (cell21.getValue() != 0 || cell22.getValue() != 0) {
            // first type: cand1/cand2 can be deleted
            initStep(SolutionType.AVOIDABLE_RECTANGLE_1);
            if (cell21.getValue() != 0) {
                globalStep.addCandidateToDelete(index22, cand1);
            } else {
                globalStep.addCandidateToDelete(index21, cand2);
            }
            try {
                steps.add((SolutionStep) globalStep.clone());
            } catch (CloneNotSupportedException ex) {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
            }
        } else {
            // the additional candidate in index21 and index22 has to be the same
            short[] cands = cell21.getAllCandidates(candType);
            int additionalCand = cands[0];
            if (additionalCand == cand2) {
                additionalCand = cands[1];
            }
            if (! cell22.isCandidate(candType, additionalCand)) {
                // wrong candidate -> do nothing
                return;
            }
            // check for deletions
            tmpSet.set(Sudoku.buddies[index21]);
            tmpSet.and(Sudoku.buddies[index22]);
            tmpSet.and(sudoku.getAllowedPositions()[additionalCand]);
            if (tmpSet.isEmpty()) {
                // no eliminations possible -> do nothing
                return;
            }
            initStep(SolutionType.AVOIDABLE_RECTANGLE_2);
            for (int i = 0; i < tmpSet.size(); i++) {
                globalStep.addCandidateToDelete(tmpSet.get(i), additionalCand);
                globalStep.addEndoFin(index21, additionalCand);
                globalStep.addEndoFin(index22, additionalCand);
            }
            try {
                steps.add((SolutionStep) globalStep.clone());
            } catch (CloneNotSupportedException ex) {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
            }
        }
    }

    /**
     *  Hidden Rectangle: If one or two cells contain only two candidates (if two they
     *  have to be aligned diagonally) one of the cells with only two cands is the corner; if
     *  the line and the col through the other three cells contain only two occurences
     *  of one of the uniqueness candidates each, the other uniqueness candidate
     *  can be deleted
     *
     * @param cornerIndex
     */
    private void checkHiddenRectangle(int cornerIndex) {
        // whether there is only one cell or two cells with two candidates,
        // checking two additional cells is enough to get the line and col 
        // that have to be checked
        int lineC = Sudoku.getLine(cornerIndex);
        int colC = Sudoku.getCol(cornerIndex);
        int i1 = additionalCandidates.get(0);
        int i2 = additionalCandidates.get(1);
        int line1 = Sudoku.getLine(i1);
        if (line1 == lineC) {
            line1 = Sudoku.getLine(i2);
        }
        int col1 = Sudoku.getCol(i1);
        if (col1 == colC) {
            col1 = Sudoku.getCol(i2);
        }

        checkCandForHiddenRectangle(line1, col1, cand1, cand2);
        checkCandForHiddenRectangle(line1, col1, cand2, cand1);
    }

    /**
     * In line <code>line</code> and col <code>col</code> the candidate
     * <code>cand1</code> may appear only twice. If that is the case
     * candidate <code>cand2</code> may be deleted from the cell at the
     * intersection of <code>line</code> and <code>col</code>.
     * 
     * @param line
     * @param col
     * @param cand1
     * @param cand2
     */
    private void checkCandForHiddenRectangle(int line, int col, int cand1, int cand2) {
        tmpSet.set(sudoku.getAllowedPositions()[cand1]);
        tmpSet.and(Sudoku.lineTemplates[line]);
        if (tmpSet.size() != 2) {
            return;
        }
        tmpSet.set(sudoku.getAllowedPositions()[cand1]);
        tmpSet.and(Sudoku.colTemplates[col]);
        if (tmpSet.size() != 2) {
            return;
        }
        // ok ->hidden rectangle; delete cand2 from cell at the intersection
        int delIndex = Sudoku.getIndex(line, col);

        initStep(SolutionType.HIDDEN_RECTANGLE);
        globalStep.addCandidateToDelete(delIndex, cand2);
        try {
            steps.add((SolutionStep) globalStep.clone());
        } catch (CloneNotSupportedException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
        }
    }

    private void checkUniqueness3(int type, int[] unit, SudokuSet u3Cands) {
        SudokuSet u3Indices = new SudokuSet();
        for (int i = 0; i < unit.length; i++) {
            SudokuCell cell = sudoku.getCell(unit[i]);
            // eine Zelle muss untersucht werden, wenn sie noch nicht gesetzt ist, wenn sie weder cand1
            // noch cand2 enthält und wenn sie nicht zum Rectangle gehört
            if (cell.getValue() == 0 && !cell.isCandidate(candType, cand1) &&
                    !cell.isCandidate(candType, cand2) && !twoCandidates.contains(unit[i])) {
                u3Indices.add(unit[i]);
            }
        }
        // ok, alle Zellen beisammen -> alle Kombinationen prüfen
        if (u3Indices.size() > 0) {
            checkUniqueness3Recursive(type, unit, u3Indices, u3Cands, new SudokuSet(), 0);
        }
    }

    private void checkUniqueness3Recursive(int type, int[] unit, SudokuSet u3Indices, SudokuSet candsIncluded, SudokuSet indicesIncluded, int startIndex) {
        for (int i = startIndex; i < u3Indices.size(); i++) {
            SudokuSet aktCands = candsIncluded.clone();
            SudokuSet aktIndices = indicesIncluded.clone();
            aktIndices.add(u3Indices.get(i));
            SudokuCell cell = sudoku.getCell(u3Indices.get(i));
            // alle zusätzlichen Kandidaten sammeln
            for (int j = 1; j <= 9; j++) {
                if (cell.isCandidate(candType, j)) {
                    aktCands.add(j);
                }
            }
            // Wenn Blöcke überprüft werden, wird nur geschaut, wenn die Zellen, die das Naked Subset bilden, nicht
            // in einer Zeile oder Spalte liegen (wurde bereits behandelt)
            if (type != SudokuCell.USER || !isSameLineOrCol(aktIndices)) {
                // wenn die Anzahl der zusätzlichen Kandidaten um 1 größer ist als die Anzahl der
                // beteiligten Zellen, ist es ein Uniqueness Test 3 -> prüfen, ob Kandidaten gelöscht werden können
                if (aktCands.size() == (aktIndices.size() + 1)) {
                    // gelöscht werden können Kandidaten in derselben unit, die weder zum Rectangle noch zu
                    // aktIndices gehören, wenn die Kandidaten zu aktCands gehören (und wenn die Zelle noch nicht gesetzt ist natürlich)
                    // die Kandidaten, die das Naked Xle bilden, werden als fins eingeschrieben (zur Darstellung)
                    initStep(SolutionType.UNIQUENESS_3);
                    for (int k = 0; k < aktCands.size(); k++) {
                        int cTmp = aktCands.get(k);
                        for (int l = 0; l < aktIndices.size(); l++) {
                            if (sudoku.getCell(aktIndices.get(l)).isCandidate(candType, cTmp)) {
                                globalStep.addFin(aktIndices.get(l), cTmp);
                            }
                        }
                        for (int l = 0; l < additionalCandidates.size(); l++) {
                            if (sudoku.getCell(additionalCandidates.get(l)).isCandidate(candType, cTmp)) {
                                globalStep.addFin(additionalCandidates.get(l), cTmp);
                            }
                        }
                    }
                    for (int j = 0; j < unit.length; j++) {
                        SudokuCell cell1 = sudoku.getCell(unit[j]);
                        if (cell1.getValue() == 0 && !twoCandidates.contains(unit[j]) &&
                                !additionalCandidates.contains(unit[j]) && !aktIndices.contains(unit[j])) {
                            for (int k = 1; k <= 9; k++) {
                                if (cell1.isCandidate(candType, k) && aktCands.contains(k)) {
                                    globalStep.addCandidateToDelete(unit[j], k);
                                }
                            }
                        }
                    }
                    if (type == SudokuCell.LINE || type == SudokuCell.COL) {
                        int block = getBlockForCheck3(aktIndices, additionalCandidates);
                        if (block != -1) {
                            int[] unit1 = Sudoku.BLOCKS[block];
                            for (int j = 0; j < unit1.length; j++) {
                                SudokuCell cell1 = sudoku.getCell(unit1[j]);
                                if (cell1.getValue() == 0 && !twoCandidates.contains(unit1[j]) &&
                                        !additionalCandidates.contains(unit1[j]) && !aktIndices.contains(unit1[j])) {
                                    for (int k = 1; k <= 9; k++) {
                                        if (cell1.isCandidate(candType, k) && aktCands.contains(k)) {
                                            globalStep.addCandidateToDelete(unit1[j], k);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (globalStep.getCandidatesToDelete().size() > 0) {
                        try {
                            steps.add((SolutionStep) globalStep.clone());
                        } catch (CloneNotSupportedException ex) {
                            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                        }
                    }
                }
            }
            // weiter geht die wilde Hatz
            checkUniqueness3Recursive(type, unit, u3Indices, aktCands, aktIndices, i + 1);
        }
    }

    private int getBlockForCheck3(SudokuSet aktIndices, SudokuSet additionalCandidates) {
        if (aktIndices.size() < 1) {
            return -1;
        }
        int block = Sudoku.getBlock(aktIndices.get(0));
        for (int i = 1; i < aktIndices.size(); i++) {
            if (Sudoku.getBlock(aktIndices.get(i)) != block) {
                return -1;
            }
        }
        for (int i = 0; i < additionalCandidates.size(); i++) {
            if (Sudoku.getBlock(additionalCandidates.get(i)) != block) {
                return -1;
            }
        }
        return block;
    }

    private boolean isSameLineOrCol(SudokuSet aktIndices) {
        if (aktIndices.size() < 1) {
            return false;
        }
        int line = Sudoku.getLine(aktIndices.get(0));
        int col = Sudoku.getCol(aktIndices.get(0));
        for (int i = 1; i < aktIndices.size(); i++) {
            if (Sudoku.getLine(aktIndices.get(i)) != line) {
                return false;
            }
            if (Sudoku.getCol(aktIndices.get(i)) != col) {
                return false;
            }
        }
        return true;
    }

    private void initStep(SolutionType type) {
        globalStep.reset();
        globalStep.setType(type);
        if (indexe != null) {
            globalStep.addValue(cand1);
            globalStep.addValue(cand2);
            globalStep.addIndex(indexe[0]);
            globalStep.addIndex(indexe[1]);
            globalStep.addIndex(indexe[2]);
            globalStep.addIndex(indexe[3]);
        }
    }

    private int getThirdCandidate(SudokuCell cell, int cand1, int cand2) {
        if (cell.getAnzCandidates(candType) != 3) {
            return -1;
        }
        short[] cands = cell.getAllCandidates(candType);
        for (int i = 0; i < cands.length; i++) {
            if (cands[i] != 0) {
                if (cands[i] != cand1 && cands[i] != cand2) {
                    return cands[i];
                }
            }
        }
        return -1;
    }

    private void initCheck(int[] indices, SudokuCell[] cells) {
        twoCandidates.clear();
        additionalCandidates.clear();
        for (int i = 0; i < indices.length; i++) {
            if (cells[i].getAnzCandidates(candType) == 2) {
                twoCandidates.add(indices[i]);
            } else {
                additionalCandidates.add(indices[i]);
            }
        }
    }

    /**
     * Prüft, ob das gefundene Rechteck schon gepuffert ist; wenn, wird
     * false zurückgegeben; wenn nein, wird es gepuffert und es wird
     * true zurückgegeben. Ist der Puffer bereits voll, wird immer true zurückgegeben.
     */
    private boolean checkRect(int i11, int i12, int i21, int i22) {
        tmpRect[0] = i11;
        tmpRect[1] = i12;
        tmpRect[2] = i21;
        tmpRect[3] = i22;
        // für den Vergleich sortieren; bei nur 4 Elementen reicht BubbleSort
        for (int i = tmpRect.length; i > 1; i--) {
            boolean changed = false;
            for (int j = 1; j < i; j++) {
                if (tmpRect[j - 1] > tmpRect[j]) {
                    int tmp = tmpRect[j - 1];
                    tmpRect[j - 1] = tmpRect[j];
                    tmpRect[j] = tmp;
                    changed = true;
                }
            }
            if (changed == false) {
                break;
            }
        }
        // Rechtangle in rechtangles suchen
        for (int i = 0; i < rectAnz; i++) {
            int[] r = rectangles[i];
            if (r[0] == tmpRect[0] && r[1] == tmpRect[1] && r[2] == tmpRect[2] && r[3] == tmpRect[3]) {
                // schon enthalten -> nicht mehr prüfen
                return false;
            }
        }
        // nicht enthalten -> wenn noch Platz ist einschreiben
        if (rectAnz < rectangles.length) {
            rectangles[rectAnz][0] = tmpRect[0];
            rectangles[rectAnz][1] = tmpRect[1];
            rectangles[rectAnz][2] = tmpRect[2];
            rectangles[rectAnz][3] = tmpRect[3];
            rectAnz++;
        } else {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Find Uniqueness: Kein Platz mehr in rectangles!");
        }
        // In jedem Fall prüfen
        return true;
    }

    private int getUnitIndex(int index, int[][] units) {
        for (int i = 0; i < units.length; i++) {
            if (Arrays.binarySearch(units[i], index) >= 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * befüllt emptyCells und biValueCells
     */
    private void initSets() {
        // bestehende Sets löschen
        emptyCells.clear();
        for (int i = 0; i < biValueCells.length; i++) {
            biValueCells[i] = false;
        }
        // bestehende gepufferte rectangles löschen
        rectAnz = 0;

        // alle Zellen durchgehen
        SudokuCell[] cells = sudoku.getCells();
        for (int i = 0; i < cells.length; i++) {
            if (cells[i].getValue() == 0) {
                emptyCells.add(i);
                if (cells[i].getAnzCandidates(candType) == 2) {
                    biValueCells[i] = true;
                }
            }
        }
    }

    public static void main(String[] args) {
        UniquenessSolver ts = new UniquenessSolver();
        Sudoku sudoku = new Sudoku();
        //sudoku.setSudoku(":0000:x:2.7.86.5.8169..2.79.572.8.6..4..7.....2.3.56....5..7..6.3.19475.....43924.9.7.6..:141 342 844 348 149 949 854 362 368 892 894 896::");
        //sudoku.setSudoku(":0000:x:837159246.426837.96.9742.38.2..169..37..95.62.96.27...9..2683..28357469176.93182.:443 449 453 469::");
        //sudoku.setSudoku(":0000:x:.78.....6.19.5.4.8.53...29.....76...761.4.9253..59.....2....7838.7.3.1..13.....4.:241 441 344 844 866 471 671 694 695 596::");
        sudoku.setSudoku(":0000:x:..513.9.2..1..97..89..2.4..45.29.136962351847.1...6..9.349...78...8..39..89.736..:625 271 571 485::");
        sudoku.setSudoku(":0000:x:+41+67+8.5...35..4+8+178+7.+13+5.+46..4+5716..+1.3+4..7.+5+6+5789+34+2+15+4..1+7.+683.+82+5.17+4+7.1.+48.5.:942 948 252 297 399::");

        ts.setSudoku(sudoku);
        List<SolutionStep> steps = null;
        long ticks = System.currentTimeMillis();
        for (int i = 0; i < 1; i++) {
            steps = ts.getAllUniqueness(sudoku);
            //steps = ts.getAllAvoidableRectangles(new ArrayList<SolutionStep>());
        }
        ticks = System.currentTimeMillis() - ticks;
        System.out.println("getAllUniqeness(): " + ticks + "ms");
        System.out.println("Anzahl Steps: " + steps.size());
        for (int i = 0; i < steps.size(); i++) {
            System.out.println(steps.get(i).getCandidateString());
            System.out.println(steps.get(i).toString(2));
        }
    }
}
