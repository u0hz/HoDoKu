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
 * Verboten sind alle Templates, die keine 1 an einer der bereits gesetzten Positionen haben:
 *    (positions & template) != positions
 * Verboten sind alle Templates, die eine 1 an einer nicht mehr erlaubten Position haben:
 *    (~(positions | allowedPositions) & template) != 0
 * Verboten sind alle Templates, die eine 1 an einer Position eines Templates haben, das aus
 *    allen verundeten Templates eines anderen Kandidaten gebildet wurde
 * Verboten sind alle Templates, die keine einzige �berlappungsfreie Kombination mit wenigstens
 *    einem Template einer anderen Ziffer haben
 *
 * Wenn die Templates bekannt sind:
 *    alle Templates OR: Alle Kandidaten, die nicht enthalten sind, k�nnen gel�scht werden
 *    alle Templates AND: Alle Positionen, die �brig bleiben, k�nnen gesetzt werden
 *    alle g�ltigen Kombinationen aus Templates zweier Ziffern bilden (OR), alle Ergebnisse
 *           AND: An allen verbliebenen Positionen k�nnen alle Kandidaten, die nicht zu einer dieser
 *           Ziffern geh�ren, eliminiert werden.
 *
 * @author Bernhard Hobiger
 */
public class TemplateSolver extends AbstractSolver {
    
    private List<SolutionStep> steps; // gefundene L�sungsschritte
    private SolutionStep globalStep = new SolutionStep(SolutionType.HIDDEN_SINGLE);
    
    /** Creates a new instance of TemplateSolver */
    public TemplateSolver(SudokuSolver solver) {
        super(solver);
    }
    
    @Override
    protected SolutionStep getStep(SolutionType type) {
        SolutionStep result = null;
        switch (type) {
            case TEMPLATE_SET:
                getTemplateSet(true);
                if (steps.size() > 0) {
                    result = steps.get(0);
                }
                break;
            case TEMPLATE_DEL:
                getTemplateDel(true);
                if (steps.size() > 0) {
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
            case TEMPLATE_SET:
                int value = step.getValues().get(0);
                for (int index : step.getIndices()) {
                    sudoku.setCell(index, value);
                }
                break;
            case TEMPLATE_DEL:
                for (Candidate cand : step.getCandidatesToDelete()) {
                    sudoku.delCandidate(cand.getIndex(), candType, cand.getValue());
                }
                break;
            default:
                handled = false;
        }
        return handled;
    }
    
    public List<SolutionStep> getAllTemplates(Sudoku sudoku) {
        Sudoku save = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldSteps = steps;
        steps = new ArrayList<SolutionStep>();
        long millis1 = System.currentTimeMillis();
        getTemplateSet(false);
        getTemplateDel(false);
        millis1 = System.currentTimeMillis() - millis1;
        Logger.getLogger(getClass().getName()).log(Level.FINE, "getAllTemplates() gesamt: " + millis1 + "ms");
        List<SolutionStep> result = steps;
        steps = oldSteps;
        if (save != null) {
            setSudoku(save);
        }
        return result;
    }
    
    private void getTemplateSet(boolean initSteps) {
        if (initSteps) {
            steps = new ArrayList<SolutionStep>();
        }
        
        // Listen anlegen
        initCandTemplates(true);
        // und ein bisschen verfeinern
        
        // k�nnen Zellen gesetzt werden?
        SudokuSet setSet = new SudokuSet();
        for (int i = 1; i <= 9; i++) {
            setSet.set(setValueTemplates[i]);
            setSet.andNot(sudoku.getPositions()[i]);
            if (! setSet.isEmpty()) {
                // Zellen k�nnen gesetzt werden
                globalStep.reset();
                globalStep.setType(SolutionType.TEMPLATE_SET);
                globalStep.addValue(i);
                for (int j = 0; j < setSet.size(); j++) {
                    globalStep.addIndex(setSet.get(j));
                }
                try {
                    steps.add((SolutionStep)globalStep.clone());
                } catch (CloneNotSupportedException ex) {
                    Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                }
            }
        }
    }
    
    private void getTemplateDel(boolean initSteps) {
        if (initSteps) {
            steps = new ArrayList<SolutionStep>();
        }
        
        // Listen anlegen
        initCandTemplates(true);
        // und ein bischen verfeinern
        
        // k�nnen Kandidaten gel�scht werden?
        SudokuSet setSet = new SudokuSet();
        for (int i = 1; i <= 9; i++) {
            setSet.set(delCandTemplates[i]);
            setSet.and(sudoku.getAllowedPositions()[i]);
            if (! setSet.isEmpty()) {
                // Kandidaten k�nnen gel�scht werden
                globalStep.reset();
                globalStep.setType(SolutionType.TEMPLATE_DEL);
                globalStep.addValue(i);
                for (int j = 0; j < setSet.size(); j++) {
                    globalStep.addCandidateToDelete(setSet.get(j), i);
                }
                try {
                    steps.add((SolutionStep)globalStep.clone());
                } catch (CloneNotSupportedException ex) {
                    Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                }
            }
        }
    }
    
    public static void main(String[] args) {
        //Sudoku sudoku = new Sudoku(true);
        Sudoku sudoku = new Sudoku();
        //sudoku.setSudoku(":0361:4:..5.132673268..14917...2835..8..1.262.1.96758.6..283...12....83693184572..723.6..:434 441 442 461 961 464 974:411:r7c39 r6c1b9 fr3c3");
        sudoku.setSudoku(":0000:x:7.2.34.8.........2.8..51.74.......51..63.27..29.......14.76..2.8.........2.51.8.7:::");
//        for (int i = 1; i <= 9; i++) {
//            System.out.println("allowedPositions[" + i + "]: " + sudoku.getAllowedPositions()[i]);
//            System.out.println("positions[" + i + "]: " + sudoku.getPositions()[i]);
//        }
        TemplateSolver ts = new TemplateSolver(null);
        long millis = System.currentTimeMillis();
        for (int i = 0; i < 1; i++) {
            List<SolutionStep> steps = ts.getAllTemplates(sudoku);
        }
        millis = System.currentTimeMillis() - millis;
        System.out.println("Zeit: " + (millis / 100) + "ms");
    }
}
