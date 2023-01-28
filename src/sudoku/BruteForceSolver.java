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

/**
 *
 * @author Bernhard Hobiger
 */
public class BruteForceSolver extends AbstractSolver {
    
    private SudokuCreator creator = new SudokuCreator();
    
    /** Creates a new instance of BruteForceSolver */
    public BruteForceSolver(SudokuSolver solver) {
        super(solver);
    }
    
    @Override
    protected SolutionStep getStep(SolutionType type) {
        SolutionStep result = null;
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
     * Das Sudoku wird mit Dancing-Links gelöst. Anschließend wird aus den nicht gesetzten Zellen
     * die mittlere ausgesucht und gesetzt.
     */
    private SolutionStep getBruteForce() {
        Sudoku solvedSudoku = sudoku.clone();
        creator.validSolution(solvedSudoku);
        solvedSudoku = creator.getSolvedSudoku();
        
        // alle Positionen ermitteln, die im ungelösten Sudoku noch nicht gesetzt sind
        SudokuSet unsolved = new SudokuSet();
        for (int i = 0; i < sudoku.getPositions().length; i++) {
            unsolved.or(sudoku.getPositions()[i]);
        }
        unsolved.not();
        
        // jetzt die mittlere Zelle aussuchen
        int index = unsolved.size() / 2;
        index = unsolved.get(index);
        
        // Step zusammenbauen
        SolutionStep step = new SolutionStep(SolutionType.BRUTE_FORCE);
        step.addIndex(index);
        step.addValue(solvedSudoku.getCell(index).getValue());
        
        return step;
    }
}
