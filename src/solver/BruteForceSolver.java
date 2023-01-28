/*
 * Copyright (C) 2008-11  Bernhard Hobiger
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

package solver;

import sudoku.SolutionStep;
import sudoku.SolutionType;
import sudoku.Sudoku2;
import sudoku.SudokuSet;

/**
 *
 * @author hobiwan
 */
public class BruteForceSolver extends AbstractSolver {
    
    /** Creates a new instance of BruteForceSolver */
    public BruteForceSolver(SudokuStepFinder finder) {
        super(finder);
    }
    
    @Override
    protected SolutionStep getStep(SolutionType type) {
        SolutionStep result = null;
        sudoku = finder.getSudoku();
        switch (type) {
            case BRUTE_FORCE:
                result = getBruteForce();
                break;
        }
        return result;
    }
    
    @Override
    protected boolean doStep(SolutionStep step) {
        boolean handled = true;
        sudoku = finder.getSudoku();
        switch (step.getType()) {
            case BRUTE_FORCE:
                int value = step.getValues().get(0);
                for (int index : step.getIndices()) {
                    sudoku.setCell(index, value);
                }
                break;
            default:
                handled = false;
        }
        return handled;
    }
    
    /**
     * Das Sudoku2 wird mit Dancing-Links gel�st. Anschlie�end wird aus den nicht gesetzten Zellen
     * die mittlere ausgesucht und gesetzt.<br>
     * If the sudoku is invalid, no result is returned.
     */
    private SolutionStep getBruteForce() {
        if (! sudoku.isSolutionSet()) {
            // can happen, when invalid puzzles are pasted
            return null;
        }
        
        // alle Positionen ermitteln, die im ungel�sten Sudoku2 noch nicht gesetzt sind
        SudokuSet unsolved = new SudokuSet();
        for (int i = 0; i < Sudoku2.LENGTH; i++) {
            if (sudoku.getValue(i) == 0) {
                unsolved.add(i);
            }
        }
        
        // jetzt die mittlere Zelle aussuchen
        int index = unsolved.size() / 2;
        index = unsolved.get(index);
        
        // Step zusammenbauen
        SolutionStep step = new SolutionStep(SolutionType.BRUTE_FORCE);
        step.addIndex(index);
        step.addValue(sudoku.getSolution(index));
        
        return step;
    }
}
