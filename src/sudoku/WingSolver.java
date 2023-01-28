/*
 * Copyright (C) 2008/09  Bernhard Hobiger
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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Bernhard Hobiger
 */
public class WingSolver extends AbstractSolver {
    
    // Optimierungen
    private SolutionStep globalStep = new SolutionStep(SolutionType.FULL_HOUSE);
    private List<SolutionStep> steps;  // die bisher gefundenen Steps
    private int[] cands = new int[3];  // enthält x, y und z in dieser Reihenfolge
    private boolean xyz = false;  // XYZ-Wing oder nur XY-Wing
    private SudokuSet pincers = new SudokuSet(); // für seesCells()
    private int wIndex1 = -1; // für W-Wing
    private int wIndex2 = -1; // für W-Wing
    
    /** Creates a new instance of WingSolver */
    public WingSolver(SudokuSolver solver) {
        super(solver);
    }
    
    @Override
    protected SolutionStep getStep(SolutionType type) {
        SolutionStep result = null;
        switch (type) {
            case XY_WING:
                result = getXYWing();
                break;
            case XYZ_WING:
                result = getXYZWing();
                break;
            case W_WING:
                result = getWWing();
                break;
        }
        return result;
    }
    
    @Override
    protected boolean doStep(SolutionStep step) {
        boolean handled = true;
        switch (step.getType()) {
            case XY_WING:
            case W_WING:
            case XYZ_WING:
                for (Candidate cand : step.getCandidatesToDelete()) {
                    sudoku.delCandidate(cand.getIndex(), candType, cand.getValue());
                }
                break;
            default:
                handled = false;
        }
        return handled;
    }
    
    private SolutionStep getXYWing() {
        steps = new ArrayList<SolutionStep>();
        getWing(false);
        if (steps.size() > 0) {
            return steps.get(0);
        }
        return null;
    }
    
    private SolutionStep getXYZWing() {
        steps = new ArrayList<SolutionStep>();
        getWing(true);
        if (steps.size() > 0) {
            return steps.get(0);
        }
        return null;
    }
    
    private SolutionStep getWWing() {
        steps = new ArrayList<SolutionStep>();
        getWWingInt();
        if (steps.size() > 0) {
            return steps.get(0);
        }
        return null;
    }
    
    public List<SolutionStep> getAllWings(Sudoku sudoku) {
        Sudoku save = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> tmpSteps = new ArrayList<SolutionStep>();
        tmpSteps = getAllWings(tmpSteps);
        if (save != null) {
            setSudoku(save);
        }
        return tmpSteps;
    }
    
    public List<SolutionStep> getAllWings(List<SolutionStep> steps) {
        // initialisieren
        this.steps = steps;
        
        getWing(true);
        getWing(false);
        getWWingInt();
        
        return steps;
    }
    
    /**
     * Wir suchen das Grid ab nach Zellen mit nur 2(3) Kandidaten xy (xyz). Wenn wir eine finden,
     * versuchen wir eine weitere Zelle mit zwei Kandidaten im gleichen Block oder in der gleichen
     * Spalte (im gleichen Block) zu finden, bei der ein Kandidat gleich, der zweite aber verschieden
     * (beide gleich) ist - xz (xz). Wenn wir eine solche Zelle finden, suchen wir in der gleichen Zeile oder
     * Spalte des ersten Elements (zweites Element in Spalte: nur Zeile) eine dritte Bivalue-Zelle, die
     * den neuen Kandidaten mit der zweiten Zelle gemeinsam hat und deren zweiter Kandidat der
     * fehlende Kandidat der ersten Zelle ist - yz (yz). Alle Zellen die die zweite und dritte Zelle
     * (alle drei Zellen) sehen können, können z nicht als Kandidaten haben.
     */
    private void getWing(boolean xyz) {
        this.xyz = xyz;
        cands[0] = cands[1] = cands[2] = 0;
        for (int i = 0; i < sudoku.getCells().length; i++) {
            SudokuCell cell = sudoku.getCell(i);
            if (cell.getValue() != 0) {
                continue;
            }
            if ((!xyz && cell.getAnzCandidates(candType) == 2) || (xyz && cell.getAnzCandidates(candType) == 3)) {
                // Mögliches Pivot
                short[] c = cell.getAllCandidates(candType);
                cands[0] = c[0];      // x
                cands[1] = c[1];     // y
                cands[2] = 0;
                if (xyz) {
                    cands[2] = c[2];  // z
                }
                // im gleichen Block oder in der gleichen Spalte suchen (xyz nur Block)
                checkPincer1(i, Sudoku.BLOCKS[Sudoku.getBlock(i)]);
                if (! xyz) {
                    checkPincer1(i, Sudoku.COLS[Sudoku.getCol(i)]);
                }
            }
        }
    }
    
    private void checkPincer1(int pivot, int[] unit) {
        for (int i = 0; i < unit.length; i++) {
            if (unit[i] == pivot) {
                continue;
            }
            // von einem vorhergehenden Versuch könnte noch ein z gesetzt sein -> löschen
            if (!xyz) {
                cands[2] = 0;
            }
            SudokuCell cell = sudoku.getCell(unit[i]);
            if (cell.getValue() != 0 || cell.getAnzCandidates(candType) != 2) {
                // hier nur mehr Bivalue-Cells
                continue;
            }
            // ok nur zwei Kandidaten -> prüfen
            short[] c = cell.getAllCandidates(candType);
            int c1 = getIndex(cands, c[0]);
            int c2 = getIndex(cands, c[1]);
            int anz = c1 != -1 ? 1 : 0;
            if (c2 != -1) anz++;
            if (xyz) {
                if (anz != 2) {
                    // kann kein XYZ-Wing mehr werden
                    continue;
                }
                // zwei Kandidaten stimmen überein -> als c1 als y und c2 als z behandeln
                int i1 = cands[c1];
                int i2 = cands[c2];
                cands[c1] = cands[c2] = 0;
                int i3 = cands[0];
                if (i3 == 0) i3 = cands[1];
                if (i3 == 0) i3 = cands[2];
                cands[0] = i3;
                cands[1] = i1;
                cands[2] = i2;
            } else {
                if (anz != 1) {
                    // kann kein XY-Wing mehr werden
                    continue;
                }
                // ein Kandidat stimmt überein -> wird y, neuer Kandidat wird z
                if (c1 == -1) {
                    if (c2 == 0) swap(cands, 0, 1);
                    cands[2] = c[0];
                } else {
                    if (c1 == 0) swap(cands, 0, 1);
                    cands[2] = c[1];
                }
            }
            // ok, es gibt ein Pivot und einen Pincer, xyz sind bekannt -> weitersuchen
            // gesucht wird: in Zeile und Spalte, wenn Pivot und Pincer1 im gleichen Block sind (xy und xyz)
            //               in der Zeile, wenn Pivot und Pincer in der gleichen Spalte sind (nur xy)
            if (Sudoku.getBlock(pivot) == Sudoku.getBlock(unit[i])) {
                checkPincer2(pivot, unit[i], Sudoku.LINES[Sudoku.getLine(pivot)]);
                checkPincer2(pivot, unit[i], Sudoku.COLS[Sudoku.getCol(pivot)]);
            } else {
                // pivot und Pincer1 sind in der gleichen Spalte (kann nur xy sein)
                checkPincer2(pivot, unit[i], Sudoku.LINES[Sudoku.getLine(pivot)]);
            }
        }
    }
    
    private void checkPincer2(int pivot, int pincer1, int[] unit) {
        for (int i = 0; i < unit.length; i++) {
            if (unit[i] == pivot || unit[i] == pincer1) {
                continue;
            }
            SudokuCell cell = sudoku.getCell(unit[i]);
            if (cell.getValue() != 0 || cell.getAnzCandidates(candType) != 2) {
                // hier nur mehr Bivalue-Cells
                continue;
            }
            // ok nur zwei Kandidaten -> prüfen: müssen x und z sein
            // ACHTUNG: bei XYZ können y und z vertauscht sein!
            if (cell.isCandidate(candType, cands[0]) &&
                    (cell.isCandidate(candType, cands[2]) || (xyz && cell.isCandidate(candType, cands[1])))) {
                // ok, Wing gefunden. Alles vorbereiten und nach zu löschenden Kandidaten suchen
                // zuerst das richtige z für xyz finden
                if (xyz && cell.isCandidate(candType, cands[1])) {
                    swap(cands, 1, 2);
                }
                pincers.clear();
                if (xyz) {
                    // bei XYZ-Wing müssen alle drei Zellen gesehen werden!
                    pincers.add(pivot);
                }
                pincers.add(pincer1);
                pincers.add(unit[i]);
                globalStep.reset();
                if (xyz) {
                    globalStep.setType(SolutionType.XYZ_WING);
                } else {
                    globalStep.setType(SolutionType.XY_WING);
                }
                globalStep.addValue(cands[0]);
                globalStep.addValue(cands[1]);
                globalStep.addValue(cands[2]);
                globalStep.addIndex(pivot);
                globalStep.addIndex(pincer1);
                globalStep.addIndex(unit[i]);
                if (xyz) {
                    globalStep.addFin(pivot, cands[2]);
                }
                globalStep.addFin(pincer1, cands[2]);
                globalStep.addFin(unit[i], cands[2]);
                // können Kandidaten gelöscht werden?
                for (int j = 0; j < sudoku.getCells().length; j++) {
                    if (j == pivot || j == pincer1 || j == unit[i]) {
                        continue;
                    }
                    SudokuCell cell1 = sudoku.getCell(j);
                    if (cell1.getValue() == 0 && cell1.isCandidate(candType, cands[2]) && seesCells(j, pincers)) {
                        globalStep.addCandidateToDelete(j, cands[2]);
                    }
                }
                if (globalStep.getCandidatesToDelete().size() > 0) {
                    try {
                        steps.add((SolutionStep)globalStep.clone());
                    } catch (CloneNotSupportedException ex) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                    }
                }
            }
        }
    }
    
    /**
     * Alle Bivalue-Zellen durchgehen: wird eine gefunden, wird sie mit allen
     * anderen Bivalue-Zellen mit gleichen Kandidaten kombiniert. Für alle
     * gültigen Kombinationen wird versucht einen Strong-Link mit einem der
     * Kandidaten zu finden, der die beiden Zellen verbindet. Ist das der Fall,
     * kann der zweite Kandidat in allen Zellen, die beide Bivalue-Zellen sehen,
     * gelöscht werden.
     */
    private void getWWingInt() {
        for (int i = 0; i < sudoku.getCells().length; i++) {
            SudokuCell cell1 = sudoku.getCell(i);
            if (cell1.getValue() != 0 || cell1.getAnzCandidates(candType) != 2) {
                continue;
            }
            // Bivalue-Zelle gefunden
            int cand1 = cell1.getAllCandidates(candType)[0];
            int cand2 = cell1.getAllCandidates(candType)[1];
            
            // Alle anderen Zellen finden
            for (int j = i + 1; j < sudoku.getCells().length; j++) {
                SudokuCell cell2 = sudoku.getCell(j);
                if (cell2.getValue() != 0 || cell2.getAnzCandidates(candType) != 2) {
                    continue;
                }
                if (cand1 != sudoku.getCell(j).getAllCandidates(candType)[0] ||
                        cand2 != sudoku.getCell(j).getAllCandidates(candType)[1]) {
                    // passt nicht
                    continue;
                }
                // ok, wir haben ein Paar -> Link suchen
                checkLink(cand1, cand2, i, j, Sudoku.LINES);
                checkLink(cand2, cand1, i, j, Sudoku.LINES);
                checkLink(cand1, cand2, i, j, Sudoku.COLS);
                checkLink(cand2, cand1, i, j, Sudoku.COLS);
                checkLink(cand1, cand2, i, j, Sudoku.BLOCKS);
                checkLink(cand2, cand1, i, j, Sudoku.BLOCKS);
            }
        }
    }
    
    private void checkLink(int cand1, int cand2, int cI1, int cI2, int[][]entities) {
        if (getLink(cand2, cI1, cI2, entities)) {
            // Alle Peers ermitteln
            SudokuSet buddies = new SudokuSet();
            buddies.set(Sudoku.buddies[cI1]);
            buddies.and(Sudoku.buddies[cI2]);
            if (! buddies.isEmpty()) {
                globalStep.reset();
                for (int i = 0; i < buddies.size(); i++) {
                    int index = buddies.get(i);
                    SudokuCell cell = sudoku.getCell(index);
                    if (index != cI1 && index != cI2 && cell.getValue() == 0 && cell.isCandidate(candType, cand1)) {
                        globalStep.addCandidateToDelete(index, cand1);
                    }
                }
                if (globalStep.getCandidatesToDelete().size() > 0) {
                    globalStep.setType(SolutionType.W_WING);
                    globalStep.addValue(cand1);
                    globalStep.addValue(cand2);
                    globalStep.addIndex(cI1);
                    globalStep.addIndex(cI2);
                    globalStep.addFin(cI1, cand2);
                    globalStep.addFin(cI2, cand2);
                    globalStep.addFin(wIndex1, cand2);
                    globalStep.addFin(wIndex2, cand2);
                    boolean stepAlreadyFound = false;
                    for (int i = 0; i < steps.size(); i++) {
                        if (steps.get(i).isEqual(globalStep)) {
                            stepAlreadyFound = true;
                            break;
                        }
                    }
                    if (! stepAlreadyFound) {
                        try {
                            steps.add((SolutionStep)globalStep.clone());
                        } catch (CloneNotSupportedException ex) {
                            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Verbindungslink suchen: Ein beliebiger Strong-Lnk mit Kandidat cand, bei dem jeweils
     * eine der beteiligten Zellen index1 bzw. index2 sieht.
     */
    private boolean getLink(int cand, int index1, int index2, int[][]entities) {
        for (int i = 0; i < entities.length; i++) {
            int anz = 0;
            boolean sees1 = false;
            boolean sees2 = false;
            for (int j = 0; j < entities[i].length; j++) {
                int aktIndex = entities[i][j];
                SudokuCell cell = sudoku.getCell(aktIndex);
                boolean exists = cell.getValue() == 0 && cell.isCandidate(candType, cand);
                // Testen, ob die Zelle eine der beiden Bivalue-Zellen sieht
                if (exists && aktIndex != index1 && aktIndex != index2) {
                    if (sudoku.buddies[aktIndex].contains(index1)) {
                        sees1 = true;
                        wIndex1 = aktIndex;
                    }
                    if (sudoku.buddies[aktIndex].contains(index2)) {
                        sees2 = true;
                        wIndex2 = aktIndex;
                    }
                }
                if (exists) {
                    anz++;
                }
            }
            if (sees1 && sees2 && anz == 2 && wIndex1 != wIndex2) {
                // Link gefunden
                return true;
            }
        }
        return false;
    }
    
    private int getIndex(int[] cands, int cand) {
        for (int i = 0; i < cands.length; i++) {
            if (cand != 0 && cand == cands[i]) {
                return i;
            }
        }
        return -1;
    }
    
    private void swap(int[] arr, int ind1, int ind2) {
        if (ind1 != ind2) {
            int tmp = arr[ind1];
            arr[ind1] = arr[ind2];
            arr[ind2] = tmp;
        }
    }
}
