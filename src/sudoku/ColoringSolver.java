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
 *
 * @author Admin
 */
public class ColoringSolver extends AbstractSolver {

    private ArrayList<SudokuSet> onSets = new ArrayList<SudokuSet>(10);  // sets for the first color (synchronized with offSets)

    private ArrayList<SudokuSet> offSets = new ArrayList<SudokuSet>(10); // sets for the second color (synchronized with onSets)

    private SudokuSet startSet = new SudokuSet();  // contains all candidates, that are part of at least one conjugate pair

    private SudokuSet tmpSet1 = new SudokuSet();
    private SudokuSet tmpSet2 = new SudokuSet();
    private SudokuSet tmpSet3 = new SudokuSet();
    private int colorIndex = 0;  // index in onSets/offSets

    private List<SolutionStep> steps = new ArrayList<SolutionStep>();
    private SolutionStep globalStep = new SolutionStep();

    public ColoringSolver() {
    }

    @Override
    protected SolutionStep getStep(SolutionType type) {
        SolutionStep result = null;
        switch (type) {
            case SIMPLE_COLORS:
                result = findSimpleColorStep();
                break;
            case MULTI_COLORS:
                result = findMultiColorStep();
                break;
        }
        return result;
    }

    @Override
    protected boolean doStep(SolutionStep step) {
        boolean handled = true;
        switch (step.getType()) {
            case SIMPLE_COLORS:
            case MULTI_COLORS:
                for (Candidate cand : step.getCandidatesToDelete()) {
                    sudoku.delCandidate(cand.getIndex(), candType, cand.getValue());
                }
                break;
            default:
                handled = false;
        }
        return handled;
    }

    public List<SolutionStep> findAllSimpleColors(Sudoku sudoku) {
        Sudoku oldSudoku = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldList = steps;
        List<SolutionStep> newList = new ArrayList<SolutionStep>();
        steps = newList;
        long ticks = System.currentTimeMillis();
        findSimpleColorSteps();
        ticks = System.currentTimeMillis() - ticks;
        Logger.getLogger(getClass().getName()).log(Level.FINE, "end of findAllSimpleColors() (" + ticks + "ms)");
        Collections.sort(steps);
        steps = oldList;
        if (oldSudoku != null) {
            setSudoku(oldSudoku);
        }
        return newList;
    }

    public List<SolutionStep> findAllMultiColors(Sudoku sudoku) {
        Sudoku oldSudoku = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldList = steps;
        List<SolutionStep> newList = new ArrayList<SolutionStep>();
        steps = newList;
        long ticks = System.currentTimeMillis();
        findMultiColorSteps();
        ticks = System.currentTimeMillis() - ticks;
        Logger.getLogger(getClass().getName()).log(Level.FINE, "end of findAllMultiColors() (" + ticks + "ms)");
        Collections.sort(steps);
        steps = oldList;
        if (oldSudoku != null) {
            setSudoku(oldSudoku);
        }
        return newList;
    }

    private SolutionStep findSimpleColorStep() {
        steps.clear();
        findSimpleColorSteps();
        if (steps.size() > 0) {
            return steps.get(0);
        }
        return null;
    }

    /**
     */
    private void findSimpleColorSteps() {
        for (int i = 1; i <= 9; i++) {
            findSimpleColorStepsForCandidate(i);
        }
    }

    /**
     * Tries to find all Simple Colors steps for the given candidate.
     * 
     * @param cand candidate to check
     */
    private void findSimpleColorStepsForCandidate(int cand) {
        int anzColors = doColoring(cand);
        // now check for eliminations
        for (int i = 0; i < anzColors; i++) {
            SudokuSet onSet = onSets.get(i);
            SudokuSet offSet = offSets.get(i);

            // first: color trap - any candidate, that can see two cells with 
            // opposite colors, can be removed
            globalStep.reset();
            checkCandidateToDelete(onSet, offSet, cand);
            if (globalStep.getCandidatesToDelete().size() != 0) {
                globalStep.setType(SolutionType.SIMPLE_COLORS);
                globalStep.addValue(cand);
                globalStep.addColorCandidates(onSet, 0);
                globalStep.addColorCandidates(offSet, 1);
                try {
                    steps.add((SolutionStep) globalStep.clone());
                } catch (CloneNotSupportedException ex) {
                    Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                }
            }

            // second: color wrap - if two cells with the same color can see each other,
            // all candidates with that color can be removed
            globalStep.reset();
            if (checkColorWrap(onSet)) {
                for (int j = 0; j < onSet.size(); j++) {
                    globalStep.addCandidateToDelete(onSet.get(j), cand);
                    System.out.println("add: " + onSet.get(j) + "/" + cand);
                }
            }
            if (checkColorWrap(offSet)) {
                for (int j = 0; j < offSet.size(); j++) {
                    globalStep.addCandidateToDelete(offSet.get(j), cand);
                }
            }
            if (globalStep.getCandidatesToDelete().size() != 0) {
                globalStep.setType(SolutionType.SIMPLE_COLORS);
                globalStep.addValue(cand);
                globalStep.addColorCandidates(onSet, 0);
                globalStep.addColorCandidates(offSet, 1);
                System.out.println("onSet: " + onSet);
                System.out.println("offSet: " + offSet);
                try {
                    steps.add((SolutionStep) globalStep.clone());
                } catch (CloneNotSupportedException ex) {
                    Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                }
            }
        }
    }

    /**
     * Checks, if two cells in set can see each other. If so, all candidates
     * in set can be removed.
     * @param set The set to check
     */
    private boolean checkColorWrap(SudokuSet set) {
        for (int i = 0; i < set.size() - 1; i++) {
            for (int j = i + 1; j < set.size(); j++) {
                if (Sudoku.buddies[set.get(i)].contains(set.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Utility function: All candidates, that can see a cell in set1 and a cell in
     * set2, can be eliminated.
     * @param set1 The first set to check
     * @param set2 The second set to check
     * @param cand Candidate to check
     */
    private void checkCandidateToDelete(SudokuSet set1, SudokuSet set2, int cand) {
        for (int i = 0; i < set1.size(); i++) {
            for (int j = 0; j < set2.size(); j++) {
                tmpSet1.set(Sudoku.buddies[set1.get(i)]);
                tmpSet1.and(Sudoku.buddies[set2.get(j)]);
                tmpSet1.and(sudoku.getAllowedPositions()[cand]);
                if (!tmpSet1.isEmpty()) {
                    for (int k = 0; k < tmpSet1.size(); k++) {
                        globalStep.addCandidateToDelete(tmpSet1.get(k), cand);
                    }
                }
            }
        }
    }

    private SolutionStep findMultiColorStep() {
        steps.clear();
        findSimpleColorSteps();
        if (steps.size() > 0) {
            return steps.get(0);
        }
        return null;
    }

    /**
     */
    private void findMultiColorSteps() {
        for (int i = 1; i <= 9; i++) {
            findMultiColorStepsForCandidate(i);
        }
    }

    private void findMultiColorStepsForCandidate(int cand) {
        int anzColors = doColoring(cand);
        // first check whether cells of one color can see opposite cells of another color pair.
        // if so, all cells with that color can be eliminated
        // NOTE: a->b is not equal b->a, so ALL combinations have to be checked
        for (int i = 0; i < anzColors; i++) {
            for (int j = 0; j < anzColors; j++) {
                if (i == j) {
                    // color pairs have to be different
                    continue;
                }
                SudokuSet onSet1 = onSets.get(i);
                SudokuSet offSet1 = offSets.get(i);
                SudokuSet onSet2 = onSets.get(j);
                SudokuSet offSet2 = offSets.get(j);
                globalStep.reset();
                if (checkMultiColor1(onSet1, onSet2, offSet2)) {
                    for (int k = 0; k < onSet1.size(); k++) {
                        globalStep.addCandidateToDelete(onSet1.get(k), cand);
                    }
                }
                if (checkMultiColor1(offSet1, onSet2, offSet2)) {
                    for (int k = 0; k < offSet1.size(); k++) {
                        globalStep.addCandidateToDelete(offSet1.get(k), cand);
                    }
                }
                if (globalStep.getCandidatesToDelete().size() != 0) {
                    globalStep.setType(SolutionType.MULTI_COLORS);
                    globalStep.addValue(cand);
                    globalStep.addColorCandidates(onSet1, 0);
                    globalStep.addColorCandidates(offSet1, 1);
                    globalStep.addColorCandidates(onSet2, 2);
                    globalStep.addColorCandidates(offSet2, 3);
                    try {
                        steps.add((SolutionStep) globalStep.clone());
                    } catch (CloneNotSupportedException ex) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                    }
                }
                
                // now check, if a two cells of different color pairs can see each other. If so,
                // all candidates, that can see cells of the two other colors, can be eliminated
                globalStep.reset();
                if (checkMultiColor2(onSet1, onSet2)) {
                    checkCandidateToDelete(offSet1, offSet2, cand);
                }
                if (checkMultiColor2(onSet1, offSet2)) {
                    checkCandidateToDelete(offSet1, onSet2, cand);
                }
                if (checkMultiColor2(offSet1, onSet2)) {
                    checkCandidateToDelete(onSet1, offSet2, cand);
                }
                if (checkMultiColor2(offSet1, offSet2)) {
                    checkCandidateToDelete(onSet1, onSet2, cand);
                }
                if (globalStep.getCandidatesToDelete().size() != 0) {
                    globalStep.setType(SolutionType.MULTI_COLORS);
                    globalStep.addValue(cand);
                    globalStep.addColorCandidates(onSet1, 0);
                    globalStep.addColorCandidates(offSet1, 1);
                    globalStep.addColorCandidates(onSet2, 2);
                    globalStep.addColorCandidates(offSet2, 3);
                    try {
                        steps.add((SolutionStep) globalStep.clone());
                    } catch (CloneNotSupportedException ex) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                    }
                }
            }
        }
    }

    /**
     * Checks, whether cells in set can see cells of s21 and s22. If so, all candidates
     * in set can be eliminated.
     * @param set Set to be checked
     * @param s21 First color of other color pair
     * @param s22 Second color of ather color pair
     * @return
     */
    private boolean checkMultiColor1(SudokuSet set, SudokuSet s21, SudokuSet s22) {
        boolean seeS21 = false;
        boolean seeS22 = false;
        for (int i = 0; i < set.size(); i++) {
            tmpSet1.set(Sudoku.buddies[set.get(i)]);
            if (!tmpSet1.andEmpty(s21)) {
                seeS21 = true;
            }
            if (!tmpSet1.andEmpty(s22)) {
                seeS22 = true;
            }
            if (seeS21 && seeS22) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks, whether some cell in set1 can see a cell in set2.
     * @param set1 First set
     * @param set2 Second set
     * @return
     */
    private boolean checkMultiColor2(SudokuSet set1, SudokuSet set2) {
        for (int i = 0; i < set1.size(); i++) {
            for (int j = 0; j < set2.size(); j++) {
                if (Sudoku.buddies[set1.get(i)].contains(set2.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Actually colors the grid. "Coloring the grid" means,
     * that every candidate, that is part of at least on conjugate pair, is
     * aasigned a color. "Assigned a color" means, that the candidate is added
     * to one of the onSets/offSets.
     * 
     * The algorithm is easy:
     *   - first eliminate all candidates, that are not part of at least one conjugate pair
     *   - for every remaining uncolored candidate do the coloring
     * Colored candidates stay in the startSet.
     */
    private int doColoring(int cand) {
        // first: remove all candidates, that are not part of at least one conjugate pair
        startSet.set(sudoku.getAllowedPositions()[cand]);
        int[] values = startSet.getValues();
        for (int i = 0; i < values.length; i++) {
            tmpSet1.set(startSet);
            tmpSet1.and(Sudoku.lineTemplates[Sudoku.getLine(values[i])]);
            tmpSet2.set(startSet);
            tmpSet2.and(Sudoku.colTemplates[Sudoku.getCol(values[i])]);
            tmpSet3.set(startSet);
            tmpSet3.and(Sudoku.blockTemplates[Sudoku.getBlock(values[i])]);
            if (tmpSet1.size() != 2 && tmpSet2.size() != 2 && tmpSet3.size() != 2) {
                // cannot be part of a conjugate pair
                startSet.remove(values[i]);
                continue;
            }
        }
        // now do the coloring; startSet is changed during the process, so don't try to loop!
        colorIndex = 0;
        while (!startSet.isEmpty()) {
            // create the sets for the new color or reset them, if they already exist
            if (onSets.size() < (colorIndex + 1)) {
                onSets.add(new SudokuSet());
                offSets.add(new SudokuSet());
            } else {
                onSets.get(colorIndex).clear();
                offSets.get(colorIndex).clear();
            }
            int index = startSet.get(0);
            doColoringForColorRecursive(index, cand, true);
            // a color chain has to consist of tweo cells at least (one on, one off)
            // single candidates are discarded
            if (onSets.get(colorIndex).size() + offSets.get(colorIndex).size() <= 1) {
                onSets.get(colorIndex).clear();
                offSets.get(colorIndex).clear();
            } else {
                colorIndex++;
            }
        }
        return colorIndex;
    }

    /**
     * Colors the candidate index with color colorIndex/on and tries to
     * find all conjugate pairs. Every colored candidate is removed from startSet.
     * 
     * @param index index of the cell to color. -1 means stop the coloring
     * @param cand Candidate for which the coloring is performed
     * @param on true: use onColors, false: use offColors
     */
    private void doColoringForColorRecursive(int index, int cand, boolean on) {
        if (index == -1 || ! startSet.contains(index)) {
            return;
        }
        if (on) {
            onSets.get(colorIndex).add(index);
        } else {
            offSets.get(colorIndex).add(index);
        }
        startSet.remove(index);
        // recursion
        doColoringForColorRecursive(getConjugateIndex(index, cand, Sudoku.lineTemplates[Sudoku.getLine(index)]), cand, !on);
        doColoringForColorRecursive(getConjugateIndex(index, cand, Sudoku.colTemplates[Sudoku.getCol(index)]), cand, !on);
        doColoringForColorRecursive(getConjugateIndex(index, cand, Sudoku.blockTemplates[Sudoku.getBlock(index)]), cand, !on);
    }

    /**
     * Checks
     * @param index Index of cell for which a conjugate link is tried to find
     * @param cand The candidate for which the search is performed
     * @param house The house to check against
     * @return An index, if the house has only one cell left, or -1
     */
    private int getConjugateIndex(int index, int cand, SudokuSet house) {
        tmpSet1.set(sudoku.getAllowedPositions()[cand]);
        tmpSet1.and(house);
        if (!tmpSet1.isEmpty() && tmpSet1.size() == 2) {
            int result = tmpSet1.get(0);
            if (result == index) {
                result = tmpSet1.get(1);
            }
            return result;
        }
        return -1;
    }
}
