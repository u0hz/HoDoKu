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
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Originale Beschreibung aus dem Player's Forum:
 *    Consider the set of unfilled cells C that lies at the intersection of Box B and Row (or Column) R.
 *    Suppose |C|>=2. Let V be the set of candidate values to occur in C. Suppose |V|>= |C|+2.
 *    The pattern requires that we find |V|-|C| cells in B and R, with at least one cell in each,
 *    with candidates drawn entirely from V. Label the sets of cells CB and CR and their candidates VB and VR.
 *    Crucially, no candidate is allowed to appear in VB and VR. Then C must contain V\(VB U VR) [possibly empty],
 *    |VB|-|CB| elements of VB and |VR|-|CR| elements of VR. The construction allows us to eliminate the
 *    candidates V\VR from B\(C U CB) and the candidates V\VB from R\(C U CR).
 *
 * Erweiterungen:
 *    - C muss nicht alle Zellen der Intersection enthalten
 *    - VB und VR dürfen zusätzlich Kandidaten enthalten, es muss allerdings dann eine zusätzliche Zelle
 *      pro zusätzlichem Kandidaten geben (Naked Subset)
 *
 * - Finde ungelöste Zellen am Schnittpunkt zwischen Zeile/Spalte und Block
 * - Für alle Kombinationen aus diesen Zellen:
 *     - Ermittle die Kandidaten -> Anzahl muss um mindestens 2 (N) größer sein als Anzahl Zellen
 *     - Finde N Zellen in der Zeile/Spalte und N Zellen im Block, die nicht Teil der Intersection sind,
 *       die jeweils nur Kandidaten enthalten, die in der Intersection enthalten sind
 *       (die Kandidaten-Sets aus Zeile/Spalte und Block dürfen sich nicht überschneiden)
 *     - Wenn in den N Zellen Kandidaten enthalten sind, die nicht Teil der Intersection sind, muss es
 *       eine zusätzliche Zelle pro zusätzlichem Kandidaten geben (am Besten alle Kombinationen durchgehen)
 *     - In den Zellen der Zeile/Spalte, die bis jetzt noch nicht verwendet wurden, können alle Kandidaten
 *       gelöscht werden, die in der Intersection, aber nicht im Set des Blocks vorkommen.
 *     - Analog im Block
 *
 * @author user
 */
public class MiscellaneousSolver extends AbstractSolver {
    
    private List<SolutionStep> steps; // gefundene Fische
    private SolutionStep globalStep = new SolutionStep(SolutionType.HIDDEN_SINGLE);
    
    private SudokuSet nonBlockSet = new SudokuSet();       // Alle Indexe der Zeile/Spalte (nur ungesetzte Zellen)
    private SudokuSet blockSet = new SudokuSet();          // Alle Indexe des Blocks (nur ungesetzte Zellen)
    private SudokuSet intersectionSet = new SudokuSet();   // Alle Indexe der Intersection (nur ungesetzte Zellen)
    private SudokuSet intersectionActSet = new SudokuSet();       // Indexe der Intersection, die gerade geprüft werden
    private SudokuSet intersectionActCandSet = new SudokuSet();   // Alle Kandidaten der Zellen in intersectionActSet
    private SudokuSet[] intersectionAddCandSets = new SudokuSet[3];   // Kandidaten, die im letzten Schritt neu dazugekommen sind
    private SudokuSet nonBlockSourceSet = new SudokuSet();  // Alle Indexe in nonBlocks, die geprüft werden können
    private SudokuSet nonBlockActSet = new SudokuSet();     // Indexe des nonBlocks, die gerade dran sind
    private SudokuSet nonBlockActCandSet = new SudokuSet(); // Alle Kandidaten in den Zellen von nonBlockActSet
    private SudokuSet[] nonBlockAddCandSets = new SudokuSet[9]; // Alle Kandidaten, die im letzten Schritt neu dazugekommen sind
    private SudokuSet nonBlockAllowedCandSet = new SudokuSet(); // Alle erlaubten Kandidaten für nonBlock
    private SudokuSet blockSourceSet = new SudokuSet();  // Alle Indexe in blocks, die geprüft werden können
    private SudokuSet blockActSet = new SudokuSet();     // Indexe des blocks, die gerade dran sind
    private SudokuSet blockActCandSet = new SudokuSet(); // Alle Kandidaten in den Zellen von blockActSet
    private SudokuSet[] blockAddCandSets = new SudokuSet[9]; // Alle Kandidaten, die im letzten Schritt neu dazugekommen sind
    private SudokuSet blockAllowedCandSet = new SudokuSet(); // Alle erlaubten Kandidaten für block
    private SudokuSet tmpSet = new SudokuSet();
    private SudokuSet tmpCandSet = new SudokuSet();
    private SudokuSet tmpCandSet1 = new SudokuSet();
    
    /** Creates a new instance of MiscellaneousSolver */
    public MiscellaneousSolver(SudokuSolver solver) {
        super(solver);
    }
    
    @Override
    protected SolutionStep getStep(SolutionType type) {
        SolutionStep result = null;
        switch (type) {
            case SUE_DE_COQ:
                getSueDeCoq();
                if (steps.size() > 0) {
                    Collections.sort(steps);
                    result = steps.get(0);
                }
                break;
        }
        return result;
    }
    
    @Override
    protected boolean doStep(SolutionStep step) {
        boolean handled = true;
        switch (step.getType()) {
            case SUE_DE_COQ:
                for (Candidate cand : step.getCandidatesToDelete()) {
                    sudoku.delCandidate(cand.getIndex(), candType, cand.getValue());
                }
                break;
            default:
                handled = false;
        }
        return handled;
    }
    
    public List<SolutionStep> getAllSueDeCoqs(Sudoku sudoku) {
        Sudoku save = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldSteps = steps;
        steps = new ArrayList<SolutionStep>();
        long millis1 = System.currentTimeMillis();
        getSueDeCoqInt(Sudoku.LINES, Sudoku.BLOCKS);
        getSueDeCoqInt(Sudoku.COLS, Sudoku.BLOCKS);
        millis1 = System.currentTimeMillis() - millis1;
        Logger.getLogger(getClass().getName()).log(Level.FINE, "getAllSueDeCoqs() gesamt: " + millis1 + "ms");
        List<SolutionStep> result = steps;
        steps = oldSteps;
        if (save != null) {
            setSudoku(save);
        }
        return result;
    }
    
    private void getSueDeCoq() {
        steps = new ArrayList<SolutionStep>();
        getSueDeCoqInt(Sudoku.LINES, Sudoku.BLOCKS);
        getSueDeCoqInt(Sudoku.COLS, Sudoku.BLOCKS);
    }
    
    /**
     * Generelle Beschreibung siehe oben...
     *
     * Konkreter Algorithmus:
     *  - Ermittle Intersection
     *  - Bilde alle Kombinationen aus Zellen der Intersection und ermittle Anzahl Kandidaten
     *  - Wenn Anzahl um mindestens 2 größer ist als Anzahl Zellen, durchsuche Zeile/Spalte und Block
     */
    private void getSueDeCoqInt(int[][] nonBlocks, int[][]blocks) {
        // Schnittmenge für alle Kombinationen aus blocks und nonBlocks bilden
        for (int i = 0; i < nonBlocks.length; i++) {
            nonBlockSet.set(nonBlocks[i]);
            // nur ungesetzt Zellen!
            for (int t = 0; t < nonBlocks[i].length; t++) {
                if (sudoku.getCell(nonBlocks[i][t]).getValue() != 0) {
                    nonBlockSet.remove(nonBlocks[i][t]);
                }
            }
            for (int j = 0; j < blocks.length; j++) {
                blockSet.set(blocks[j]);
                // nur ungesetzt Zellen!
                for (int t = 0; t < blocks[j].length; t++) {
                    if (sudoku.getCell(blocks[j][t]).getValue() != 0) {
                        blockSet.remove(blocks[j][t]);
                    }
                }
                // Zellen der Intersection ermitteln
                intersectionSet.set(nonBlockSet);
                intersectionSet.and(blockSet);
                // jetzt alle Kombinationen durchschauen
                for (int k = 0; k < intersectionSet.size(); k++) {
                    intersectionActSet.clear();
                    intersectionActSet.add(intersectionSet.get(k));
                    sudoku.getCell(intersectionSet.get(k)).getCandidateSet(intersectionActCandSet, candType);
                    //getCands(intersectionSet.get(k), intersectionActCandSet);
                    checkIntersectionRecursive(1, k + 1);
                }
            }
        }
    }
    
    private void checkIntersectionRecursive(int anzahl, int startIndex) {
        anzahl++;
        if (anzahl > intersectionSet.size()) {
            // Rekursion abbrechen
            return;
        }
        for (int i = startIndex; i < intersectionSet.size(); i++) {
            int blockIndex = intersectionSet.get(i);
            intersectionActSet.add(blockIndex);
            if (intersectionAddCandSets[anzahl - 1] == null) {
                intersectionAddCandSets[anzahl - 1] = new SudokuSet();
            }
            sudoku.getCell(blockIndex).getCandidateSet(intersectionAddCandSets[anzahl - 1], candType);
            //getCands(blockIndex, intersectionAddCandSets[anzahl - 1]);
            intersectionAddCandSets[anzahl - 1].andNot(intersectionActCandSet);
            intersectionActCandSet.or(intersectionAddCandSets[anzahl - 1]);
            
            // Wenn die Anzahl der Kandidaten um mindestens zwei größer ist als die
            // Anzahl der Zellen, nonBlocks und blocks checken (mögliches Sue de Coq)
            int nPlus = intersectionActCandSet.size() - anzahl;
            if (nPlus >= 2) {
                // nonBlocks checken: gültig sind alle Zellen, die nicht in der Intersection
                // verwendet worden sind
                nonBlockSourceSet.set(nonBlockSet);
                nonBlockSourceSet.andNot(intersectionActSet);
                nonBlockActSet.clear();
                nonBlockActCandSet.clear();
                nonBlockAllowedCandSet.setAll();
                checkHousesRecursive(0, 0, nPlus, nonBlockSourceSet, nonBlockActSet, nonBlockActCandSet, nonBlockAddCandSets,
                        nonBlockAllowedCandSet, false);
            }
            
            // weiter in der Rekursion
            checkIntersectionRecursive(anzahl, i + 1);
            
            // diese Stufe rückgängig machen
            intersectionActCandSet.andNot(intersectionAddCandSets[anzahl - 1]);
            intersectionActSet.remove(blockIndex);
        }
    }
    
    private void checkHousesRecursive(int anzahl, int startIndex, int nPlus, SudokuSet sourceSet, SudokuSet actSet,
            SudokuSet actCandSet, SudokuSet[] addCandSets, SudokuSet allowedCandSet, boolean secondCheck) {
        anzahl++;
        if (anzahl > sourceSet.size()) {
            // Rekursion abbrechen
            return;
        }
        for (int i = startIndex; i < sourceSet.size(); i++) {
            int sourceIndex = sourceSet.get(i);
            actSet.add(sourceIndex);
            if (addCandSets[anzahl - 1] == null) {
                addCandSets[anzahl - 1] = new SudokuSet();
            }
            sudoku.getCell(sourceIndex).getCandidateSet(addCandSets[anzahl - 1], candType);
            //getCands(sourceIndex, addCandSets[anzahl - 1]);
            addCandSets[anzahl - 1].andNot(actCandSet);
            actCandSet.or(addCandSets[anzahl - 1]);
            
            // Die geprüfte Zellenkombination muss mindestens einen Kandidaten im intersectionActCandSet eliminieren,
            // sonst brauchen wir gar nicht weiterschauen
            // Außerdem dürfen die Zellen nur Kandidaten enthalten, die in allowedCandSet enthalten sind
            if (allowedCandSet.contains(actCandSet)) {
                tmpSet.set(actCandSet);
                tmpSet.and(intersectionActCandSet);
                int anzContained = tmpSet.size(); // Anzahl Kandidaten aus der Intersection
                tmpSet.set(actCandSet);
                tmpSet.andNot(intersectionActCandSet);
                int anzExtra = tmpSet.size();     // Anzahl Kandidaten, die nicht in der Intersection enthalten sind
                
                // Hier kommt jetzt der Unterschied zwischen erstem und zweiten Durchlauf:
                // Beim ersten Mal muss irgendeine Eliminierung möglich sein, dann können wir im Block weitersuchen
                // (es muss aber noch was zu suchen geben!)
                // Beim zweiten Mal muss die Gesamtsumme der eliminierten Kandidaten nPlus sein
                if (! secondCheck) {
                    if (anzContained > 0 && actSet.size() > anzExtra && actSet.size() - anzExtra < nPlus) {
                        // Die Kombination enthält Kandidaten aus der Intersection und es gibt zumindest eine Zusatzzelle ->
                        // im Block weitersuchen
                        blockSourceSet.set(blockSet);
                        blockSourceSet.andNot(intersectionActSet);
                        blockSourceSet.andNot(actSet); // Zellen, die von der Zeile/Spalte bereits verwendet wurden, gehen nicht mehr
                        blockActSet.clear();
                        blockActCandSet.clear();
                        // Kandidaten, die im Zeilen-/Spalten-Set vorkommen, sind verboten!
                        blockAllowedCandSet.set(actCandSet);
                        // 20090216 ACHTUNG: Die Extra-Kandidaten dürfen in beiden Sets vorkommen (stehen noch in tmpSet)!
                        blockAllowedCandSet.andNot(tmpSet);
                        blockAllowedCandSet.not();
                        checkHousesRecursive(0, 0, nPlus - (actSet.size() - anzExtra), blockSourceSet, blockActSet, blockActCandSet, blockAddCandSets,
                                blockAllowedCandSet, true);
                    }
                } else {
                    // Es müssen jetzt insgesamt so viele Kandidaten sein, dass nPlus erfüllt ist
                    if (anzContained > 0 && actSet.size() - anzExtra == nPlus) {
                        // Sue de Coq! Gibt's was zu eliminieren?
                        // eliminiert werden kann:
                        //  - (intersectionActCandSet + blockActCandSet) - nonBlockActCandSet in blockSet - blockActSet - intersectionActSet
                        //  - (intersectionActCandSet + nonBlockActCandSet) - blockActCandSet in nonBlockSet - nonBlockActSet - intersectionActSet
                        // 20090216 Wenn Extra-Kandidaten in beiden Sets gleich sind, dürfen sie auch in beiden Sets eliminiert werden!
                        globalStep.reset();
//                        System.out.println("===========================");
//                        System.out.println("intersectionSet: " + intersectionSet);
//                        System.out.println("intersectionActSet: " + intersectionActSet);
//                        System.out.println("intersectionActCandSet: " + intersectionActCandSet);
//                        System.out.println("blockSet: " + blockSet);
//                        System.out.println("blockActSet: " + blockActSet);
//                        System.out.println("blockActCandSet: " + blockActCandSet);
//                        System.out.println("nonBlockSet: " + nonBlockSet);
//                        System.out.println("nonBlockActSet: " + nonBlockActSet);
//                        System.out.println("nonBlockActCandSet: " + nonBlockActCandSet);
                        tmpCandSet1.set(blockActCandSet);
                        tmpCandSet1.and(nonBlockActCandSet); // 20090216 das sind jetzt die gleichen Extra-Kandidaten
                        tmpSet.set(blockSet);
                        tmpSet.andNot(blockActSet);
                        tmpSet.andNot(intersectionActSet);
                        tmpCandSet.set(intersectionActCandSet);
                        tmpCandSet.or(blockActCandSet);
                        tmpCandSet.andNot(nonBlockActCandSet);
                        // 20090216
                        tmpCandSet.or(tmpCandSet1);
                        checkCandidatesToDelete(tmpSet, tmpCandSet);
                        tmpSet.set(nonBlockSet);
                        tmpSet.andNot(nonBlockActSet);
                        tmpSet.andNot(intersectionActSet);
                        tmpCandSet.set(intersectionActCandSet);
                        tmpCandSet.or(nonBlockActCandSet);
                        tmpCandSet.andNot(blockActCandSet);
                        // 20090216
                        tmpCandSet.or(tmpCandSet1);
                        checkCandidatesToDelete(tmpSet, tmpCandSet);
                        if (globalStep.getCandidatesToDelete().size() > 0) {
                            // GEFUNDEN!
                            globalStep.setType(SolutionType.SUE_DE_COQ);
                            // Intersection kommt in indices und values
                            for (int j = 0; j < intersectionActSet.size(); j++) {
                                globalStep.addIndex(intersectionActSet.get(j));
                            }
                            for (int j = 0; j < intersectionActCandSet.size(); j++) {
                                globalStep.addValue(intersectionActCandSet.get(j));
                            }
                            // Alle Kandidaten im nonBlockActSet (und die passenden im intersectionActSet) werden fins
                            getSetCandidates(nonBlockActSet, intersectionActSet, nonBlockActCandSet, globalStep.getFins());
                            getSetCandidates(blockActSet, intersectionActSet, blockActCandSet, globalStep.getEndoFins());

                            globalStep.addAls(intersectionActSet, intersectionActCandSet);
                            globalStep.addAls(blockActSet, blockActCandSet);
                            globalStep.addAls(nonBlockActSet, nonBlockActCandSet);
                            try {
                                steps.add((SolutionStep)globalStep.clone());
                            } catch (CloneNotSupportedException ex) {
                                Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                            }
                        }
                    }
                }
            }
            
            // weiter in der Rekursion
            checkHousesRecursive(anzahl, i + 1, nPlus, sourceSet, actSet, actCandSet, addCandSets, allowedCandSet, secondCheck);
            
            // diese Stufe rückgängig machen
            actCandSet.andNot(addCandSets[anzahl - 1]);
            actSet.remove(sourceIndex);
        }
        
    }
    
    private void getSetCandidates(SudokuSet srcSet1, SudokuSet srcSet2, SudokuSet candSet, List<Candidate> dest) {
        tmpSet.set(srcSet1);
        tmpSet.or(srcSet2);
        for (int j = 0; j < tmpSet.size(); j++) {
            int index = tmpSet.get(j);
            for (int k = 0; k < candSet.size(); k++) {
                int value = candSet.get(k);
                if (sudoku.getCell(index).isCandidate(candType, value)) {
                    dest.add(new Candidate(index, value));
                }
            }
        }
    }
    
    private void checkCandidatesToDelete(SudokuSet tmpSet, SudokuSet tmpCandSet) {
        //System.out.println("checkCandidatesToDelete(" + tmpSet + ", " + tmpCandSet + ")");
        if (tmpSet.size() > 0 && tmpCandSet.size() > 0) {
            for (int j = 0; j < tmpSet.size(); j++) {
                int index = tmpSet.get(j);
                for (int k = 0; k < tmpCandSet.size(); k++) {
                    int value = tmpCandSet.get(k);
                    if (sudoku.getCell(index).isCandidate(candType, value)) {
                        globalStep.addCandidateToDelete(index, value);
                        //System.out.println("   " + SolutionStep.getCellPrint(index) + "<>" + value);
                    }
                }
            }
        }
    }
}
