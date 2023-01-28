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
public class SudokuSolver {

    private static List<SudokuSolver> instances = new ArrayList<SudokuSolver>();
    private Sudoku sudoku;
    private int candType;
    private List<SolutionStep> steps = new ArrayList<SolutionStep>();
    private AbstractSolver[] solvers = null;
    private boolean[] firstInstance = new boolean[Options.getInstance().solverSteps.length];
    private DifficultyLevel level;
    private DifficultyLevel maxLevel;
    private int score;
    private int[] anzSteps = new int[Options.getInstance().solverSteps.length];

    /** Creates a new instance of SudokuSolver */
    public SudokuSolver() {
        solvers = new AbstractSolver[]{
                    new SimpleSolver(this), new FishSolver(this), new SingleDigitPatternSolver(this),
                    new UniquenessSolver(this), new WingSolver(this),
                    new ColoringSolver(this), new ChainSolver(this),
                    new TemplateSolver(this), new BruteForceSolver(this), new MiscellaneousSolver(this),
                    new AlsSolver(this), new TemplateSolver(this), new TablingSolver(this), 
                    new IncompleteSolver(this),
                    new GiveUpSolver(this)
                };
    }

    public static SudokuSolver getInstance() {
        return getInstance(0);
    }

    public static SudokuSolver getInstance(int index) {
        if (index == instances.size()) {
            SudokuSolver newSolver = new SudokuSolver();
            instances.add(newSolver);
            return newSolver;
        } else if (index < instances.size()) {
            return instances.get(index);
        } else {
            Logger.getLogger(SudokuSolver.class.getName()).log(Level.SEVERE, "Cannot create SudokuSolver: indices not contiguous");
            throw new RuntimeException("Cannot create SudokuSolver: indices not contiguous");
        }
    }
    
    public AbstractSolver getSpecialisedSolver(Class solverClass) {
        for (AbstractSolver as : solvers) {
            if (as.getClass().getName().equals(solverClass.getName())) {
                return as;
            }
        }
        return null;
    }

    public void set(SudokuSolver ss) {
        sudoku = ss.sudoku;
        candType = ss.candType;
        steps = ss.steps;
        firstInstance = ss.firstInstance;
        level = ss.level;
        maxLevel = ss.maxLevel;
        score = ss.score;
        anzSteps = ss.anzSteps;
    }

    /**
     * If the time to solve the sudoku exceeds a certain limit (2s),
     * a progress dialog is displayed. The dialog is created anyway,
     * it starts the solver in a seperate thread. If the thread does not complete
     * in a given time, setVisible(true) is called.
     * 
     * @param withGui
     * @return
     */
    public boolean solve(boolean withGui) {
        if (!withGui) {
            return solve();
        }
        SolverProgressDialog dlg = new SolverProgressDialog(null, true, this);
        Thread thread = dlg.getThread();
        try {
            thread.join(2000);
        } catch (InterruptedException ex) {
            Logger.getLogger(getClass().getName()).log(Level.INFO, "Solver thread was interrupted", ex);
        }
        if (thread.isAlive()) {
            dlg.setVisible(true);
        }
        if (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(getClass().getName()).log(Level.INFO, "Solver thread was interrupted", ex);
            }
        }
        if (dlg.isVisible()) {
            dlg.setVisible(false);
        }

        return dlg.isSolved();
    }

    public boolean solve() {
        return solve(Options.getInstance().getDifficultyLevels()[DifficultyType.EXTREME.ordinal()], null, false, null);
    }

    public boolean solve(DifficultyLevel maxLevel) {
        return solve(maxLevel, null, true, null);
    }

    public boolean solve(DifficultyLevel maxLevel, Sudoku tmpSudoku, boolean rejectTooLowScore,
            final SolverProgressDialog dlg) {
        if (tmpSudoku != null) {
            setSudoku(tmpSudoku);
        }

        // Eine Lösung wird nur gesucht, wenn zumindest 10 Kandidaten gesetzt sind
        this.maxLevel = maxLevel;
        int anzKand = 0;
        for (int i = 0; i < sudoku.getCells().length; i++) {
            if (sudoku.getCell(i).getValue() != 0) {
                anzKand++;
            }
        }
        if (anzKand < 10) {
            return false;
        }

        // alle Elemente in firstInstance zurücksetzen (für adminScore)
        for (int i = 0; i < firstInstance.length; i++) {
            firstInstance[i] = false;
        }
        score = 0;
        level = Options.getInstance().getDifficultyLevels()[DifficultyType.EASY.ordinal()];

        SolutionStep step = null;

        for (int i = 0; i < anzSteps.length; i++) {
            anzSteps[i] = 0;
        }

        do {
            // show progress if progress dialog is enabled
            if (dlg != null) {
                dlg.setProgressState(sudoku.getUnsolvedCellsAnz(), sudoku.getUnsolvedCandidatesAnz());
            }
            
            // jetzt eine Methode nach der anderen, aber immer nur einmal; wenn etwas gefunden wurde continue
            step = getHint(false);
            if (step != null) {
                steps.add(step);
                doStep(step);
                if (step.getType() == SolutionType.GIVE_UP) {
                    step = null;
                }
            }
        } while (step != null);
        // wenn der Score größer als der MaxScore der aktuellen Stufe, dann wird das
        // Puzzle höhergestuft.
        while (score > level.getMaxScore()) {
            level = Options.getInstance().difficultyLevels[level.getOrdinal() + 1];
        }
        // Puzzle zu schwer -> ungültig
        if (level.getOrdinal() > maxLevel.getOrdinal()) {
            return false;
        }
        // umgekehrter Fall: Das Puzzle passt vom Level, aber der Score ist zu niedrig (niedriger
        // als der MaxScore einer geringeren Stufe
        if (rejectTooLowScore && level.getOrdinal() > DifficultyType.EASY.ordinal()) {

            if (score < Options.getInstance().difficultyLevels[level.getOrdinal() - 1].getMaxScore()) {
                return false;
            }
        }
        sudoku.setScore(score);
        if (sudoku.isSolved()) {
            sudoku.setLevel(level);
            return true;
        } else {
            sudoku.setLevel(Options.getInstance().difficultyLevels[DifficultyType.EXTREME.ordinal()]);
            return false;
        }
    }

    public SolutionStep getHint(Sudoku sudoku, boolean singlesOnly) {
        Sudoku save = this.sudoku;
        DifficultyLevel oldLevel = maxLevel;
        maxLevel = Options.getInstance().difficultyLevels[DifficultyType.EXTREME.ordinal()];
        level = Options.getInstance().difficultyLevels[DifficultyType.EASY.ordinal()];
        setSudoku(sudoku);
        SolutionStep step = getHint(singlesOnly);
        maxLevel = oldLevel;
        setSudoku(save);
        return step;
    }

    private SolutionStep getHint(boolean singlesOnly) {
        if (sudoku.isSolved()) {
            return null;
        }
        SolutionStep hint = null;

        for (int i = 0; i < Options.getInstance().solverSteps.length; i++) {
            if (Options.getInstance().solverSteps[i].isEnabled() == false) {
                // diesen Schritt nicht ausführen
                continue;
            }
            SolutionType type = Options.getInstance().solverSteps[i].getType();
            if (singlesOnly &&
                    (type != SolutionType.HIDDEN_SINGLE && type != SolutionType.NAKED_SINGLE && type != SolutionType.FULL_HOUSE)) {
                continue;
            }
            Logger.getLogger(getClass().getName()).log(Level.FINER, "trying " + SolutionStep.getStepName(type) + ": ");
            long ticks = System.currentTimeMillis();
            hint = getStep(type);
            ticks = System.currentTimeMillis() - ticks;
            Logger.getLogger(getClass().getName()).log(Level.FINER, ticks + "ms ");
            if (ticks > 20) {
                Logger.getLogger(getClass().getName()).log(Level.FINE, "trying " + SolutionStep.getStepName(type) + ": " + ticks + "ms");
            }
            if (hint != null) {
                anzSteps[i]++;
                score += Options.getInstance().solverSteps[i].getBaseScore();
                if (firstInstance[i] == false) {
                    firstInstance[i] = true;
                    score += Options.getInstance().solverSteps[i].getAdminScore();
                }
                if (Options.getInstance().difficultyLevels[Options.getInstance().solverSteps[i].getLevel()].getOrdinal() > level.getOrdinal()) {
                    level = Options.getInstance().difficultyLevels[Options.getInstance().solverSteps[i].getLevel()];
                }
                // Wenn das Puzzle zu schwer ist, gleich abbrechen
                if (level.getOrdinal() > maxLevel.getOrdinal() || score >= maxLevel.getMaxScore()) {
                    // zu schwer!
                    return null;
                }
                return hint;
            }
        }
        return null;
    }

    private SolutionStep getStep(SolutionType type) {
        SolutionStep result = null;
        for (int i = 0; i < solvers.length; i++) {
            if ((result = solvers[i].getStep(sudoku, type)) != null) {
                return result;
            }
        }
        return result;
    }

    private void doStep(SolutionStep step) {
        doStep(sudoku, step);
    }

    public void doStep(Sudoku sudoku, SolutionStep step) {
        for (int i = 0; i < solvers.length; i++) {
            if (solvers[i].doStep(sudoku, step)) {
                return;
            }
        }
        throw new RuntimeException("Invalid solution step in doStep() (" + step.getType() + ")");
    }

    public Sudoku getSudoku() {
        return sudoku;
    }

    public void setSudoku(Sudoku sudoku, List<SolutionStep> partSteps) {
        steps = new ArrayList<SolutionStep>();
        for (int i = 0; i < partSteps.size(); i++) {
            steps.add(partSteps.get(i));
        }
        candType = SudokuCell.PLAY;
        this.sudoku = sudoku;
    }

    public void setSudoku(Sudoku sudoku) {
        steps.clear();
        candType = SudokuCell.PLAY;
        this.sudoku = sudoku;
    }

    public List<SolutionStep> getSteps() {
        return steps;
    }

    public int getAnzUsedSteps() {
        int anz = 0;
        for (int i = 0; i < anzSteps.length; i++) {
            if (anzSteps[i] > 0) {
                anz++;
            }
        }
        return anz;
    }

    public int[] getAnzSteps() {
        return anzSteps;
    }

    public int getScore() {
        return score;
    }

    public String getLevelString() {
        return StepConfig.getLevelName(level);
    }

    public DifficultyLevel getLevel() {
        return level;
    }

    public SolutionCategory getCategory(SolutionType type) {
        for (StepConfig configStep : Options.getInstance().solverSteps) {
            if (type == configStep.getType()) {
                return configStep.getCategory();
            }
        }
        return null;
    }

    public String getCategoryName(SolutionType type) {
        SolutionCategory cat = getCategory(type);
        if (cat == null) {
            return null;
        }
        return cat.getCategoryName();
    }

    public int getCandType() {
        return candType;
    }

    public void setCandType(int candType) {
        this.candType = candType;
    }

    public void setSteps(List<SolutionStep> steps) {
        this.steps = steps;
    }

    public void setLevel(DifficultyLevel level) {
        this.level = level;
    }

    public DifficultyLevel getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(DifficultyLevel maxLevel) {
        this.maxLevel = maxLevel;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void setAnzSteps(int[] anzSteps) {
        this.anzSteps = anzSteps;
    }
}
