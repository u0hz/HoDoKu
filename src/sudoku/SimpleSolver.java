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

/**
 *
 * @author zhobigbe
 */
public class SimpleSolver extends AbstractSolver {
    
    private int[] tmpArr1 = new int[9];
    // temporäre Arrays für Zellen-Indexe
    private int[][] tmpArrI = new int[9][4];
    // temporäre Arrays für KandidatenWerte
    private int[][] tmpArrC = new int[9][4];
    
    private int[] tmpIndexArr = new int[16];
    private int[] tmpCandArr = new int[16];
    
    private List<SolutionStep> steps;
    
    /** Creates a new instance of SimpleSolver */
    public SimpleSolver() {
        setSteps(new ArrayList<SolutionStep>());
    }
    
    @Override
    protected SolutionStep getStep(SolutionType type) {
        SolutionStep result = null;
        switch (type) {
            case FULL_HOUSE: result = findFullHouse(); break;
            case HIDDEN_SINGLE: result = findHiddenXle(1); break;
            case HIDDEN_PAIR: result = findHiddenXle(2); break;
            case HIDDEN_TRIPLE: result = findHiddenXle(3); break;
            case HIDDEN_QUADRUPLE: result = findHiddenXle(4); break;
            case NAKED_SINGLE: result = findNakedXle(1, false); break;
            case LOCKED_PAIR: result = findNakedXle(2, true); break;
            case NAKED_PAIR: result = findNakedXle(2, false); break;
            case LOCKED_TRIPLE: result = findNakedXle(3, true); break;
            case NAKED_TRIPLE: result = findNakedXle(3, false); break;
            case NAKED_QUADRUPLE: result = findNakedXle(4, false); break;
            case LOCKED_CANDIDATES:
            case LOCKED_CANDIDATES_1:
            case LOCKED_CANDIDATES_2: result = findLockedCandidates(); break;
        }
        return result;
    }
    
    @Override
    protected boolean doStep(SolutionStep step) {
        boolean handled = true;
        switch (step.getType()) {
            case FULL_HOUSE:
            case HIDDEN_SINGLE:
            case NAKED_SINGLE:
                sudoku.setCell(step.getIndices().get(0), step.getValues().get(0));
                break;
            case HIDDEN_PAIR:
            case HIDDEN_TRIPLE:
            case HIDDEN_QUADRUPLE:
            case NAKED_PAIR:
            case NAKED_TRIPLE:
            case NAKED_QUADRUPLE:
            case LOCKED_PAIR:
            case LOCKED_TRIPLE:
            case LOCKED_CANDIDATES:
            case LOCKED_CANDIDATES_1:
            case LOCKED_CANDIDATES_2:
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
    
    public List<SolutionStep> findAllFullHouses(Sudoku sudoku) {
        Sudoku oldSudoku = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldList = getSteps();
        List<SolutionStep> newList = new ArrayList<SolutionStep>();
        setSteps(newList);
        findFullHouseInEntity(SudokuCell.BLOCK, Sudoku.BLOCKS);
        findFullHouseInEntity(SudokuCell.LINE, Sudoku.LINES);
        findFullHouseInEntity(SudokuCell.COL, Sudoku.COLS);
        Collections.sort(getSteps());
        setSteps(oldList);
        if (oldSudoku != null) {
            setSudoku(oldSudoku);
        }
        return newList;
    }
    
    /**
     * "Full House": Wenn in einer Einheit nur noch eine Zahl fehlt.
     *
     * Alle Einheiten durchgehen und die Werte == 0 zählen. ist die Summe 1,
     * handelt es sich um ein "Full House".
     */
    private SolutionStep findFullHouse() {
        getSteps().clear();
        SolutionStep step = findFullHouseInEntity(SudokuCell.BLOCK, Sudoku.BLOCKS);
        if (step != null) return step;
        step = findFullHouseInEntity(SudokuCell.LINE, Sudoku.LINES);
        if (step != null) return step;
        step = findFullHouseInEntity(SudokuCell.COL, Sudoku.COLS);
        return step;
    }
    
    private SolutionStep findFullHouseInEntity(int type, int[][] indices) {
        int indexFound = -1;
        int entityFound = -1;
        //boolean found = false;
        
        // Alle Einheiten durchgehen
        for (int entity = 0; entity < indices.length; entity++) {
            // Initialisieren
            int sum = 0;
            indexFound = -1;
            
            // Einheit durchschauen
            for (int i = 0; i < indices[entity].length; i++) {
                int value = sudoku.getCell(indices[entity][i]).getValue();
                if (value == 0) {
                    indexFound = indices[entity][i];
                    entityFound = entity;
                    sum++;
                }
            }
            
            // Ergebnis prüfen
            if (sum == 1) {
                // gefunden
                // Wert bestimmen
                int value = 0;
                for (int i = 0; i < tmpArr1.length; i++) {
                    tmpArr1[i] = i + 1;
                }
                for (int i = 0; i < indices[entityFound].length; i++) {
                    int tmp = sudoku.getCell(indices[entityFound][i]).getValue();
                    if (tmp != 0) {
                        tmpArr1[tmp - 1] = 0;
                    }
                }
                for (int i = 0; i < tmpArr1.length; i++) {
                    if (tmpArr1[i] != 0) {
                        value = tmpArr1[i];
                        break;
                    }
                }
                SolutionStep step = new SolutionStep(SolutionType.FULL_HOUSE);
                step.setEntity(type);
                step.setEntityNumber(entityFound + 1);
                step.addValue(value);
                step.addIndex(indexFound);
                getSteps().add(step);
            }
        }
        if (getSteps().size() > 0) {
            return getSteps().get(0);
        }
        return null;
    }
    
    public List<SolutionStep> findAllNakedXle(Sudoku sudoku) {
        List<SolutionStep> oldList = getSteps();
        List<SolutionStep> newList = new ArrayList<SolutionStep>();
        Sudoku oldSudoku = getSudoku();
        setSudoku(sudoku);
        setSteps(newList);
        for (int i = 1; i <= 4; i++) {
            findAllNakedXle(sudoku, i, false);
        }
        Collections.sort(getSteps());
        setSteps(oldList);
        if (oldSudoku != null) {
            setSudoku(oldSudoku);
        }
        return newList;
    }
    
    public List<SolutionStep> findAllNakedXle(Sudoku sudoku, int anz, boolean createNewList) {
        Sudoku oldSudoku = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldList = getSteps();
        List<SolutionStep> newList = getSteps();
        if (createNewList) {
            newList = new ArrayList<SolutionStep>();
            setSteps(newList);
        }
        findNakedXleInEntity(SudokuCell.BLOCK, Sudoku.BLOCKS, anz);
        if (anz > 1) {
            findNakedXleInEntity(SudokuCell.LINE, Sudoku.LINES, anz);
            findNakedXleInEntity(SudokuCell.COL, Sudoku.COLS, anz);
        }
        if (createNewList) {
            setSteps(oldList);
            return newList;
        }
        if (oldSudoku != null) {
            setSudoku(oldSudoku);
        }
        return getSteps();
    }
    
    /**
     * Es wird nur auf die Anzahl der Kandidaten pro Zelle geschaut
     */
    private SolutionStep findNakedXle(int anz, boolean lockedOnly) {
        getSteps().clear();
        SolutionStep step = findNakedXleInEntity(SudokuCell.BLOCK, Sudoku.BLOCKS, anz, lockedOnly);
        if (step != null) return step;
        step = findNakedXleInEntity(SudokuCell.LINE, Sudoku.LINES, anz, lockedOnly);
        if (step != null) return step;
        step = findNakedXleInEntity(SudokuCell.COL, Sudoku.COLS, anz, lockedOnly);
        return step;
    }
    
    private SolutionStep findNakedXleInEntity(int type, int[][] indices, int anz) {
        return findNakedXleInEntity(type, indices, anz, false);
    }
    
    private SolutionStep findNakedXleInEntity(int type, int[][] indices, int anz, boolean lockedOnly) {
        boolean found = false;  // ACHTUNG: Eventuell falsche Optimierung!
        SolutionStep step = null;
        
        // Alle Einheiten durchgehen
        for (int entity = 0; entity < indices.length; entity++) {
            // Initialisieren
            for (int i = 0; i < tmpArr1.length; i++) {
                tmpArr1[i] = 0; // Zähler -> muss 0 sein
                for (int j = 0; j < tmpArrI[i].length; j++) {
                    tmpArrI[i][j] = -1; // -> muss -1 sein (0 ist gültiger Index)
                    tmpArrC[i][j] = 0;
                }
            }
            
            // Einheit durchschauen
            // Wir betrachten nur Zellen, die weniger als anz Kandidaten haben;
            // wird eine solche Zelle gefunden, wird die Anzahl Kandidaten in tmpArray1 abgelegt,
            // die Kandidaten selbst stehen in den folgenden Arrays
            int tmpIndex = 0;
            for (int i = 0; i < indices[entity].length; i++) {
                SudokuCell cell = sudoku.getCell(indices[entity][i]);
                if (cell.getValue() == 0) {
                    int candAnz = cell.getAnzCandidates(candType);
                    if (candAnz > 0 && candAnz <= anz) {
                        tmpArr1[tmpIndex] = candAnz;
                        tmpArrI[tmpIndex][0] = indices[entity][i];
                        for (int j = 1; j <= 9; j++) {
                            if (cell.isCandidate(candType, j)) {
                                // Kandidat gefunden -> in tmpArrC speichern
                                for (int k = 0; k < tmpArrC[tmpIndex].length; k++) {
                                    if (tmpArrC[tmpIndex][k] == 0) {
                                        tmpArrC[tmpIndex][k] = j;
                                        break;
                                    }
                                }
                            }
                        }
                        tmpIndex++;
                    }
                }
            }
            
            for (int i = 0; i < tmpArr1.length && found == false; i++) {
                if (tmpArr1[i] > 0 && tmpArr1[i] <= anz) {
                    if (anz == 1) {
                        // Naked Single gefunden
                        step = new SolutionStep(SolutionType.NAKED_SINGLE);
                        step.setEntity(type);
                        step.setEntityNumber(entity + 1);
                        step.addValue(tmpArrC[i][0]);
                        step.addIndex(tmpArrI[i][0]);
                        getSteps().add(step);
                    } else {
                        for (int j = i + 1; j < tmpArr1.length && found == false; j++) {
                            if (tmpArr1[j] > 0 && tmpArr1[j] <= anz) {
                                if (anz == 2) {
                                    // mögliches "Naked Pair":
                                    // es muss mindestens zwei Zellen mit nur noch zwei Kandidaten
                                    // geben, die Kandidaten müssen gleich sein und es
                                    // muss noch löschbare Kandidaten geben
                                    if (eq(2, false, tmpCandArr, tmpArrC[i], tmpArrC[j], null, null)) {
                                        // ok, es ist ein "Naked Pair" -> löschbare Kandidaten suchen
                                        // zuerst noch die Indexe ermitteln, dazu die Methode eq() zweckentfremden
                                        eq(2, tmpIndexArr, tmpArrI[i], tmpArrI[j], null, null);
                                        step = createHiddenXleSolutionStep(false, anz, type, entity, tmpIndexArr,
                                                tmpCandArr[0], tmpCandArr[1], 0, 0, indices[entity]);
                                        if (checkLockedPairTriple(step, type, entity, tmpIndexArr, tmpCandArr[0], tmpCandArr[1], 0, true)) {
                                            step.setType(SolutionType.LOCKED_PAIR);
                                        }
                                        if (lockedOnly && step.getType() != SolutionType.LOCKED_PAIR) {
                                            continue;
                                        }
                                        if (step.getCandidatesToDelete().size() != 0) {
                                            getSteps().add(step);
                                        } else {
                                            // weitersuchen
                                            step = null;
                                        }
                                    }
                                } else {
                                    for (int k = j + 1; k < tmpArr1.length && found == false; k++) {
                                        if (tmpArr1[k] > 0 && tmpArr1[k] <= anz) {
                                            if (anz == 3) {
                                                // drei gleiche gefunden
                                                if (eq(3, false, tmpCandArr, tmpArrC[i], tmpArrC[j], tmpArrC[k], null)) {
                                                    // ok, es ist ein "Naked Triple" -> löschbare Kandidaten suchen
                                                    eq(3, tmpIndexArr, tmpArrI[i], tmpArrI[j], tmpArrI[k], null);
                                                    step = createHiddenXleSolutionStep(false, anz, type, entity, tmpIndexArr,
                                                            tmpCandArr[0], tmpCandArr[1], tmpCandArr[2], 0, indices[entity]);
                                                    if (checkLockedPairTriple(step, type, entity, tmpIndexArr, tmpCandArr[0],
                                                            tmpCandArr[1], tmpCandArr[2], true)) {
                                                        step.setType(SolutionType.LOCKED_TRIPLE);
                                                    }
                                                    if (lockedOnly && step.getType() != SolutionType.LOCKED_TRIPLE) {
                                                        continue;
                                                    }
                                                    if (step.getCandidatesToDelete().size() != 0) {
                                                        getSteps().add(step);
                                                    } else {
                                                        // weitersuchen
                                                        step = null;
                                                    }
                                                }
                                            } else {
                                                for (int l = k + 1; l < tmpArr1.length && found == false; l++) {
                                                    if (tmpArr1[l] > 0 && tmpArr1[l] <= anz) {
                                                        // ok, kann Naked Quadruple sein
                                                        if (eq(4, false, tmpCandArr, tmpArrC[i], tmpArrC[j], tmpArrC[k], tmpArrC[l])) {
                                                            // ok, es ist ein "Naked Quadruple" -> löschbare Kandidaten suchen
                                                            eq(4, tmpIndexArr, tmpArrI[i], tmpArrI[j], tmpArrI[k], tmpArrI[l]);
                                                            step = createHiddenXleSolutionStep(false, anz, type, entity, tmpIndexArr,
                                                                    tmpCandArr[0], tmpCandArr[1], tmpCandArr[2], tmpCandArr[3], indices[entity]);
                                                            if (step.getCandidatesToDelete().size() != 0) {
                                                                getSteps().add(step);
                                                            } else {
                                                                // weitersuchen
                                                                step = null;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (getSteps().size() > 0) {
            return getSteps().get(0);
        }
        return null;
    }
    
    public List<SolutionStep> findAllHiddenXle(Sudoku sudoku) {
        List<SolutionStep> oldList = getSteps();
        List<SolutionStep> newList = new ArrayList<SolutionStep>();
        Sudoku oldSudoku = getSudoku();
        setSudoku(sudoku);
        setSteps(newList);
        for (int i = 1; i <= 4; i++) {
            findAllHiddenXle(sudoku, i, false);
        }
        Collections.sort(getSteps());
        setSteps(oldList);
        if (oldSudoku != null) {
            setSudoku(oldSudoku);
        }
        return newList;
    }
    
    public List<SolutionStep> findAllHiddenXle(Sudoku sudoku, int anz, boolean createNewList) {
        Sudoku oldSudoku = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldList = getSteps();
        List<SolutionStep> newList = getSteps();
        if (createNewList) {
            newList = new ArrayList<SolutionStep>();
            setSteps(newList);
        }
        findHiddenXleInEntity(SudokuCell.BLOCK, Sudoku.BLOCKS, anz);
        findHiddenXleInEntity(SudokuCell.LINE, Sudoku.LINES, anz);
        findHiddenXleInEntity(SudokuCell.COL, Sudoku.COLS, anz);
        if (createNewList) {
            setSteps(oldList);
            return newList;
        }
        if (oldSudoku != null) {
            setSudoku(oldSudoku);
        }
        return getSteps();
    }
    
    /**
     * In allen Blöcken werden die Kandidaten aufsummiert. Ist ein Kandidat nur noch einmal vorhanden,
     * muss es ein "Hidden Single" oder ein "Naked Single" sein.
     */
    private SolutionStep findHiddenXle(int anz) {
        getSteps().clear();
        SolutionStep step = findHiddenXleInEntity(SudokuCell.BLOCK, Sudoku.BLOCKS, anz);
        if (step != null) return step;
        step = findHiddenXleInEntity(SudokuCell.LINE, Sudoku.LINES, anz);
        if (step != null) return step;
        step = findHiddenXleInEntity(SudokuCell.COL, Sudoku.COLS, anz);
        return step;
    }
    
    private SolutionStep findHiddenXleInEntity(int type, int[][] indices, int anz) {
        boolean found = false;  // führt dazu, dass nur das erste Hidden Xle gefunden wird -> streichen
        SolutionStep step = null;
        
        // Alle Einheiten durchgehen
        for (int entity = 0; entity < indices.length; entity++) {
            // Werte sammeln: in tmpArr1[n-1] steht, wie oft Kandidat n in entity vorkommt,
            // in tmpArrI[n-1] stehen die Indexe der Zellen, in denen n vorkommt
            collectCandAnzFromEntity(indices[entity]);
            
            // Ergebnis prüfen
            for (int i = 0; i < tmpArr1.length && found == false; i++) {
                if (tmpArr1[i] > 0 && tmpArr1[i] <= anz) {
                    if (anz == 1) {
                        // Hidden Single gefunden, schauen, welcher es ist
                        step = new SolutionStep(SolutionType.HIDDEN_SINGLE);
                        step.setEntity(type);
                        step.setEntityNumber(entity + 1);
                        step.addValue(i + 1);
                        step.addIndex(tmpArrI[i][0]);
                        getSteps().add(step);
                    } else {
                        for (int j = i + 1; j < tmpArr1.length && found == false; j++) {
                            if (tmpArr1[j] > 0 && tmpArr1[j] <= anz) {
                                if (anz == 2) {
                                    // mögliches "Hidden Pair":
                                    // es muss mindestens zwei Kandidaten geben, die nur noch 2 Mal vorkommen,
                                    // von diesen  Kandidaten müssen 2 auf den selben Indexen sitzen und es
                                    // muss noch löschbare Kandidaten geben
                                    if (eq(2, tmpIndexArr, tmpArrI[i], tmpArrI[j], null, null)) {
                                        // ok, es ist ein "Hidden Pair" -> löschbare Kandidaten suchen
                                        step = createHiddenXleSolutionStep(anz, type, entity, tmpIndexArr, i + 1, j + 1, 0, 0);
                                        if (checkLockedPairTriple(step, type, entity, tmpIndexArr, i + 1, j + 1, 0, true)) {
                                            step.setType(SolutionType.LOCKED_PAIR);
                                        }
                                        if (step.getCandidatesToDelete().size() != 0) {
                                            getSteps().add(step);
                                        } else {
                                            // weitersuchen
                                            step = null;
                                        }
                                    }
                                } else {
                                    for (int k = j + 1; k < tmpArr1.length && found == false; k++) {
                                        if (tmpArr1[k] > 0 && tmpArr1[k] <= anz) {
                                            if (anz == 3) {
                                                // drei gleiche gefunden
                                                if (eq(3, tmpIndexArr, tmpArrI[i], tmpArrI[j], tmpArrI[k], null)) {
                                                    // ok, es ist ein "Hidden Triple" -> löschbare Kandidaten suchen
                                                    step = createHiddenXleSolutionStep(anz, type, entity, tmpIndexArr, i + 1, j + 1, k + 1, 0);
                                                    if (checkLockedPairTriple(step, type, entity, tmpIndexArr, i + 1, j + 1, k + 1, true)) {
                                                        step.setType(SolutionType.LOCKED_TRIPLE);
                                                    }
                                                    if (step.getCandidatesToDelete().size() != 0) {
                                                        getSteps().add(step);
                                                    } else {
                                                        // weitersuchen
                                                        step = null;
                                                    }
                                                }
                                            } else {
                                                for (int l = k + 1; l < tmpArr1.length && found == false; l++) {
                                                    if (tmpArr1[l] > 0 && tmpArr1[l] <= anz) {
                                                        // ok, kann Hidden Quadruple sein
                                                        if (eq(4, tmpIndexArr, tmpArrI[i], tmpArrI[j], tmpArrI[k], tmpArrI[l])) {
                                                            // ok, es ist ein "Hidden Quadruple" -> löschbare Kandidaten suchen
                                                            step = createHiddenXleSolutionStep(anz, type, entity, tmpIndexArr, i + 1, j + 1, k + 1, l + 1);
                                                            if (step.getCandidatesToDelete().size() != 0) {
                                                                getSteps().add(step);
                                                            } else {
                                                                // weitersuchen
                                                                step = null;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (getSteps().size() > 0) {
            return getSteps().get(0);
        }
        return null;
    }
    
    /**
     * Stellt für eine Entity fest, wie oft welcher Kandidat vorkommt; die Anzahl für Kandidat n wird nach
     * tmppArr1[n - 1] geschrieben, die Indexe stehen in tmpArrI[n - 1]
     */
    private void collectCandAnzFromEntity(int[] entityIndices) {
        // Initialisieren
        for (int i = 0; i < tmpArr1.length; i++) {
            tmpArr1[i] = 0; // Zähler -> muss 0 sein
            for (int j = 0; j < tmpArrI[i].length; j++) {
                tmpArrI[i][j] = -1; // -> muss -1 sein (0 ist gültiger Index)
            }
        }
        
        // Einheit durchschauen
        for (int i = 0; i < entityIndices.length; i++) {
            SudokuCell cell = sudoku.getCell(entityIndices[i]);
            if (cell.getValue() == 0) {
                for (int j = 1; j <= 9; j++) {
                    if (cell.isCandidate(candType, j)) {
                        // Kandidat gefunden -> Anzahl aufsummieren und Index merken
                        tmpArr1[j - 1]++;
                        for (int k = 0; k < tmpArrI[j - 1].length; k++) {
                            if (tmpArrI[j - 1][k] == -1) {
                                tmpArrI[j - 1][k] = entityIndices[i];
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
    
    private SolutionStep createHiddenXleSolutionStep(int anz, int type, int entity, int[] indexe, int n1, int n2, int n3, int n4) {
        return createHiddenXleSolutionStep(true, anz, type, entity, indexe, n1, n2, n3, n4, null);
    }
    
    private SolutionStep createHiddenXleSolutionStep(boolean hidden, int anz, int type, int entity, int[] indexe,
            int n1, int n2, int n3, int n4, int[] entityIndexes) {
        SolutionType sType = SolutionType.HIDDEN_SINGLE;
        switch (anz) {
            case 2: sType = hidden ? SolutionType.HIDDEN_PAIR : SolutionType.NAKED_PAIR; break;
            case 3: sType = hidden ? SolutionType.HIDDEN_TRIPLE : SolutionType.NAKED_TRIPLE; break;
            case 4: sType = hidden ? SolutionType.HIDDEN_QUADRUPLE : SolutionType.NAKED_QUADRUPLE; break;
            default: throw new RuntimeException("Invalid number: " + anz);
        }
        SolutionStep step = new SolutionStep(sType);
        step.setEntity(type);
        step.setEntityNumber(entity + 1);
        step.addValue(n1);
        step.addValue(n2);
        step.addIndex(tmpIndexArr[0]);
        step.addIndex(tmpIndexArr[1]);
        if (anz >= 3) {
            step.addValue(n3);
            step.addIndex(tmpIndexArr[2]);
        }
        if (anz >= 4) {
            step.addValue(n4);
            step.addIndex(tmpIndexArr[3]);
        }
        // löschbare Kandidaten suchen
        getCandidatesToDelete(hidden, n1, n2, n3, n4, tmpIndexArr, step, entityIndexes);
        return step;
    }
    
    private boolean eq(int maxAnz, int[] arr, int[] tmpArrI1, int[] tmpArrI2, int[] tmpArrI3, int[] tmpArrI4) {
        return eq(maxAnz, true, arr, tmpArrI1, tmpArrI2, tmpArrI3, tmpArrI4);
    }
    
    /**
     * Erhält 4/9/16 Indexe (je zwei/drei/vier Indexe für zwei/drei/vier Zahlen). Es dürfen beliebig viele
     * indexe -1 sein, aber es dürfen insgesamt nur zwei/drei/vier Zahlen != -1 vorkommen.
     */
    private boolean eq(int maxAnz, boolean isIndex, int[] arr, int[] tmpArrI1, int[] tmpArrI2, int[] tmpArrI3, int[] tmpArrI4) {
        int invalid = isIndex ? -1 : 0;
        for (int i = 0; i < arr.length; i++) {
            arr[i] = invalid;
        }
        eqSetIndex(tmpArrI1, arr, invalid);
        eqSetIndex(tmpArrI2, arr, invalid);
        eqSetIndex(tmpArrI3, arr, invalid);
        eqSetIndex(tmpArrI4, arr, invalid);
        int anz = 0;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != invalid) {
                anz++;
            }
        }
        return (anz == maxAnz);
    }
    
    /**
     * Wenn der übergebene Index ungleich invalid ist und in arr noch nicht vorkommt,
     * wird er an die erste freie (!= invalid) position geschrieben. In arr muss genügend Platz
     * für alle Aufrufe sein (es erfolgt bewusst keine Kontrolle der Arrayindexe).
     */
    private void eqSetIndex(int[] tmpArrIX, int[] arr, int invalid) {
        if (tmpArrIX == null) return;
        for (int i = 0; i < tmpArrIX.length; i++) {
            if (tmpArrIX[i] != invalid) {
                int j = 0;
                boolean found = false;
                while (arr[j] != invalid) {
                    if (arr[j] == tmpArrIX[i]) {
                        // schon enthalten
                        found = true;
                        break;
                    }
                    j++;
                }
                // Noch nicht enthalten -> einschreiben
                if (! found) {
                    arr[j] = tmpArrIX[i];
                }
            }
        }
    }
    
    /**
     * Soll für "Hidden" und "Naked" gleichermaßen funktieren; bei "Hidden" dürfen in den Zellen index nur mehr die Kandidaten
     * cand1 bis cand4 vorkommen, bei "Naked" dürfen die Kandidaten in keinen Zellen in entityIndexes vorkommen außer in indexes
     */
    private void getCandidatesToDelete(boolean hidden, int cand1, int cand2, int cand3, int cand4, int[] indexes, SolutionStep step, int[] entityIndexes) {
        if (hidden) {
            for (int j = 0; j < indexes.length; j++) {
                if (indexes[j] == -1)
                    continue;
                SudokuCell cell = sudoku.getCell(indexes[j]);
                for (int i = 1; i <= 9; i++) {
                    if ((cand1 != 0 && i == cand1) || (cand2 != 0 && i == cand2) ||
                            (cand3 != 0 && i == cand3) || (cand4 != 0 && i == cand4)) {
                        continue;
                    }
                    if (cell.isCandidate(candType, i)) {
                        step.addCandidateToDelete(indexes[j], i);
                    }
                }
            }
        } else {
            for (int i = 0; i < entityIndexes.length; i++) {
                boolean skip = false;
                for (int j = 0; j < indexes.length; j++) {
                    if (indexes[j] == entityIndexes[i]) {
                        // erschlägt auch gleich die ungültigen Indexe (können nicht in entityIndexes vorkommen)
                        skip = true;
                        break;
                    }
                }
                SudokuCell cell = sudoku.getCell(entityIndexes[i]);
                if (skip || cell.getValue() != 0) {
                    // gehört entweder zum "Naked Xle" oder ist bereits gesetzt
                    continue;
                }
                // ok: cand1 bis cand4 dürfen nicht vorkommen
                for (int k = 1; k <= 9; k++) {
                    if (((cand1 != 0 && k == cand1) || (cand2 != 0 && k == cand2) ||
                            (cand3 != 0 && k == cand3) || (cand4 != 0 && k == cand4)) &&
                            cell.isCandidate(candType, k)) {
                        step.addCandidateToDelete(entityIndexes[i], k);
                    }
                }
            }
        }
    }
    
    /**
     * Ein Naked oder Hidden Pair/Triple kann auch andere Kandidaten ausschließen: Wenn type SudokuCell.BLOCK ist
     * und alle betroffenen Zellen in einer Zeile oder Spalte sind, dürfen die Kandidaten außerhalb des Blocks
     * nicht mehr vorkommen.
     * Umgekehrt: Wenn type LINE oder COL ist und alle Kandidaten im selben Block sind, dürfen sie innerhalb des
     * Blocks nur in der Zeile/Spalte vorkommen.
     *
     * ACHTUNG: Es handelt sichnur um ein Locked Xle, wenn Eliminierungen in Block UND Zeile/Spalte vorkommen
     */
    private boolean checkLockedPairTriple(SolutionStep step, int type, int entity, int[] indexes, int n1, int n2, int n3, boolean noLockedCandidates) {
        if (noLockedCandidates && step.getCandidatesToDelete().size() == 0) {
            // wird von einer anderen Suche erfasst
            return false;
        }
        // enthält bei Vorliegen eines Locked xxx die indices der Einheit, die zusätzlich betroffen ist
        int[] cellIndexes2 = null;
        if (type == SudokuCell.BLOCK) {
            // feststellen, ob alle Werte in indexes in der selben Zeile liegen
            cellIndexes2 = checkEntity(SudokuCell.LINE, Sudoku.LINES, indexes, step);
            if (cellIndexes2 == null) {
                cellIndexes2 = checkEntity(SudokuCell.COL, Sudoku.COLS, indexes, step);
            }
        } else {
            // feststellen, ob indexes[i] im selben Block liegen
            cellIndexes2 = checkEntity(SudokuCell.BLOCK, Sudoku.BLOCKS, indexes, step);
        }
        // wenn ein Block gefunden wurde ist cellIndexes2 != null
        boolean isLocked = false;
        if (cellIndexes2 != null) {
            // Alle Kandidaten n1, n2 oder n3 in der betreffenden Einheit löschen,
            // außer in den Zellen indexes
            for (int i = 0; i < cellIndexes2.length; i++) {
                // leider ist indexes nicht sortiert, daher auf die harte Tour
                boolean found = false;
                for (int j = 0; j < indexes.length; j++) {
                    if (indexes[j] == cellIndexes2[i]) {
                        found = true;
                        break;
                    }
                    if (indexes[j] == -1)
                        break;
                }
                if (found)
                    continue;
                SudokuCell cell = sudoku.getCell(cellIndexes2[i]);
                if (cell.getValue() != 0) {
                    continue;
                }
                if (n1 != 0 && cell.isCandidate(candType, n1)) {
                    step.addCandidateToDelete(cellIndexes2[i], n1);
                    isLocked = true;
                }
                if (n2 != 0 && cell.isCandidate(candType, n2)) {
                    step.addCandidateToDelete(cellIndexes2[i], n2);
                    isLocked = true;
                }
                if (n3 != 0 && cell.isCandidate(candType, n3)) {
                    step.addCandidateToDelete(cellIndexes2[i], n3);
                    isLocked = true;
                }
            }
        }
        return isLocked;
    }
    
    /**
     * Prüft, ob alle in indexes enthaltenen Indexe in einer der von entityIndexes definierten
     * Entity liegen. Wenn ja, werden die indexe dieser Entity zurückgegeben, sonst null.
     */
    private int[] checkEntity(int type, int[][] entityIndexes, int[] indexes, SolutionStep step) {
        for (int i = 0; i < entityIndexes.length; i++) {
            if (Arrays.binarySearch(entityIndexes[i], indexes[0]) >= 0 &&
                    Arrays.binarySearch(entityIndexes[i], indexes[1]) >= 0 &&
                    (indexes[2] == -1 || Arrays.binarySearch(entityIndexes[i], indexes[2]) >= 0)) {
                // gefunden, vorsichtshalber in step setzen (LOCKED_CANDIDATES braucht es, LOCKED_PAIR und LOCKED_TRIPLE
                // schadet es nicht
                step.setEntity2(type);
                step.setEntity2Number(i + 1);
                return entityIndexes[i];
            }
        }
        return null;
    }
    
    public List<SolutionStep> findAllLockedCandidates(Sudoku sudoku) {
        Sudoku oldSudoku = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldList = getSteps();
        List<SolutionStep> newList = new ArrayList<SolutionStep>();
        setSteps(newList);
        findLockedCandidatesInEntity(SudokuCell.BLOCK, Sudoku.BLOCKS);
        findLockedCandidatesInEntity(SudokuCell.LINE, Sudoku.LINES);
        findLockedCandidatesInEntity(SudokuCell.COL, Sudoku.COLS);
        Collections.sort(getSteps());
        setSteps(oldList);
        if (oldSudoku != null) {
            setSudoku(oldSudoku);
        }
        return newList;
    }
    
    private SolutionStep findLockedCandidates() {
        getSteps().clear();
        SolutionStep step = findLockedCandidatesInEntity(SudokuCell.BLOCK, Sudoku.BLOCKS);
        if (step != null) return step;
        step = findLockedCandidatesInEntity(SudokuCell.LINE, Sudoku.LINES);
        if (step != null) return step;
        step = findLockedCandidatesInEntity(SudokuCell.COL, Sudoku.COLS);
        return step;
    }
    
    /**
     * In allen Einheiten des übergebenen Typs werden die Kandidaten aufsummiert.
     * Gibt es einen Kandidaten nur 2 oder drei Mal, wird geprüft, ob er eventuell
     * eine andere Einheit sperrt. In diesem Fall können alle Vorkommen des Kandidatens
     * in der anderen Einheit gelöscht werden.
     */
    private SolutionStep findLockedCandidatesInEntity(int type, int[][] indices) {
        SolutionStep step = null;
        
        // Alle Einheiten durchgehen
        for (int entity = 0; entity < indices.length; entity++) {
            // Werte sammeln: in tmpArr1[n-1] steht, wie oft Kandidat n in entity vorkommt,
            // in tmpArrI[n-1] stehen die Indexe der Zellen, in denen n vorkommt
            collectCandAnzFromEntity(indices[entity]);
            
            // Ergebnis prüfen
            SolutionType stepType = (type == SudokuCell.BLOCK) ? SolutionType.LOCKED_CANDIDATES_1 : SolutionType.LOCKED_CANDIDATES_2;
            step = new SolutionStep(stepType); // noch nichts einschreiben, zuerst prüfen
            for (int i = 0; i < tmpArr1.length; i++) {
                if (tmpArr1[i] <= 3) {
                    if (checkLockedPairTriple(step, type, entity, tmpArrI[i], i + 1, 0, 0, false) && step.getCandidatesToDelete().size() > 0) {
                        // entity2 und entity2Number werden in checkLockedPairTriple() geschrieben
                        step.setEntity(type);
                        step.setEntityNumber(entity + 1);
                        step.addValue(i + 1);
                        for (int j = 0; j < tmpArrI[i].length; j++) {
                            if (tmpArrI[i][j] != -1) {
                                step.addIndex(tmpArrI[i][j]);
                            }
                        }
                        getSteps().add(step);
                        step = new SolutionStep(stepType);
                        //return step;
                    }
                }
            }
        }
        if (getSteps().size() > 0) {
            return getSteps().get(0);
        }
        return null;
    }
    
    public List<SolutionStep> getSteps() {
        return steps;
    }
    
    public void setSteps(List<SolutionStep> steps) {
        this.steps = steps;
    }
}
