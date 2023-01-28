/*
 * Copyright (C) 2008/09/10  Bernhard Hobiger
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
    private List<SolutionStep> tmpSteps = new ArrayList<SolutionStep>(); // can be freely changed
    private AbstractSolver[] solvers = null;
    private DifficultyLevel level = Options.getInstance().getDifficultyLevels()[DifficultyType.EXTREME.ordinal()];
    private DifficultyLevel maxLevel = Options.getInstance().getDifficultyLevels()[DifficultyType.EXTREME.ordinal()];
    private int score;
    private int[] anzSteps = new int[Options.getInstance().solverSteps.length];
    private int[] anzStepsProgress = new int[Options.getInstance().solverSteps.length];
    private long[] stepsNanoTime = new long[Options.getInstance().solverSteps.length];

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

    /**
     * Solves the sudoku without any restrictions.
     * @return
     */
    public boolean solve() {
        return solve(Options.getInstance().getDifficultyLevels()[DifficultyType.EXTREME.ordinal()], null, false, null, false);
    }

    /**
     * Tries to solve the sudoku using only singles.<br>
     * The internal variables are not changed
     * @param sudoku
     * @return
     */
    public boolean solveSinglesOnly(Sudoku newSudoku) {
        Sudoku tmpSudoku = sudoku;
        List<SolutionStep> oldList = steps;
        sudoku = newSudoku;
        steps = tmpSteps;
        SudokuUtil.clearStepListWithNullify(steps);
        boolean solved = solve(Options.getInstance().getDifficultyLevels()[DifficultyType.EXTREME.ordinal()], null, false, null, true);
        steps = oldList;
        sudoku = tmpSudoku;
        return solved;
    }

    /**
     * Tries to solve the sudoku using only singles.<br>
     * The internal variables are not changed
     * @param sudoku
     * @return
     */
    public boolean solveWithSteps(Sudoku newSudoku, StepConfig[] stepConfigs) {
        Sudoku tmpSudoku = sudoku;
        List<SolutionStep> oldList = steps;
        sudoku = newSudoku;
        steps = tmpSteps;
        SudokuUtil.clearStepListWithNullify(steps);
//        boolean solved = solve(Options.getInstance().getDifficultyLevels()[DifficultyType.EXTREME.ordinal()], null, false, null, true);
        boolean solved = solve(Options.getInstance().getDifficultyLevels()[DifficultyType.EXTREME.ordinal()],
                    null, false, null, false, stepConfigs);
        steps = oldList;
        sudoku = tmpSudoku;
        return solved;
    }

    /**
     * Solves a sudoku using all available techniques.
     * @param maxLevel
     * @param tmpSudoku
     * @param rejectTooLowScore
     * @param dlg
     * @return
     */
    public boolean solve(DifficultyLevel maxLevel, Sudoku tmpSudoku, boolean rejectTooLowScore,
            final SolverProgressDialog dlg) {
        return solve(maxLevel, tmpSudoku, rejectTooLowScore, dlg, false);
    }

    /**
     * Solves a sudoku using all available techniques.
     * @param maxLevel
     * @param tmpSudoku
     * @param rejectTooLowScore
     * @param dlg
     * @param singlesOnly
     * @return
     */
    public boolean solve(DifficultyLevel maxLevel, Sudoku tmpSudoku, boolean rejectTooLowScore,
            final SolverProgressDialog dlg, boolean singlesOnly) {
        return solve(maxLevel, tmpSudoku, rejectTooLowScore, dlg, singlesOnly, Options.getInstance().solverSteps);
    }
    
    /**
     * The real solver method. Can reject a possible solution if the {@link DifficultyLevel}
     * doesnt match or if the score of the sudoku is too low. If a progress dialog
     * is passed in, the counters in the dialog are updated.<br>
     * If <code>stepConfig</code> is {@link Options#solverStepsProgress}, the method can
     * be used to measure progress or find backdoors.
     * @param maxLevel
     * @param tmpSudoku
     * @param rejectTooLowScore
     * @param dlg
     * @param singlesOnly
     * @param stepConfig
     * @return
     */
    public boolean solve(DifficultyLevel maxLevel, Sudoku tmpSudoku, boolean rejectTooLowScore,
            final SolverProgressDialog dlg, boolean singlesOnly, StepConfig[] stepConfigs) {
        if (tmpSudoku != null) {
            setSudoku(tmpSudoku);
        }

        // Eine Lösung wird nur gesucht, wenn zumindest 10 Kandidaten gesetzt sind
        int anzCells = sudoku.getUnsolvedCellsAnz();
        if ((81 - anzCells) < 10) {
            return false;
        }
        int anzCand = sudoku.getUnsolvedCandidatesAnz();
        if (dlg != null) {
            dlg.initializeProgressState(anzCand);
        }

        this.maxLevel = maxLevel;
        score = 0;
        level = Options.getInstance().getDifficultyLevels()[DifficultyType.EASY.ordinal()];

        //SudokuUtil.clearStepList(steps);
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
            step = getHint(singlesOnly, stepConfigs);
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

    /**
     * Calculates the progress scores of all steps in <code>steps</code>
     * (see {@link #getProgressScoreSingles(sudoku.Sudoku, sudoku.SolutionStep) }).
     * @param tmpSudoku
     * @param steps
     * @param dlg
     */
    public void getProgressScore(Sudoku tmpSudoku, List<SolutionStep> stepsTocheck, FindAllStepsProgressDialog dlg) {
        if (dlg != null) {
            dlg.resetFishProgressBar(stepsTocheck.size());
        }
        resetProgressStepCounters();
        int delta = stepsTocheck.size() / 10;
        if (delta == 0) {
            // avoid exceptions
            delta = 1;
        }
        boolean oldCheckTemplates = Options.getInstance().checkTemplates;
        Options.getInstance().checkTemplates = false;
        long nanos = System.nanoTime();
        Sudoku workingSudoku = (Sudoku) tmpSudoku.clone();
        for (int i = 0; i < stepsTocheck.size(); i++) {
            SolutionStep step = stepsTocheck.get(i);
            workingSudoku.set(tmpSudoku);
            getProgressScore(workingSudoku, step);
            if ((i % delta) == 0 && dlg != null) {
                dlg.updateFishProgressBar(i);
            }
        }
        Options.getInstance().checkTemplates = oldCheckTemplates;
        workingSudoku = null;
        nanos = System.nanoTime() - nanos;
//        System.out.println("getProgressScore(): " + (nanos / 1000000) + "ms (" + steps.size() + ")");
//        for (SolutionStep step : steps) {
//            System.out.println("progressScore: " + step.getProgressScoreSinglesOnly() + "/" +
//                    step.getProgressScoreSingles() + "/" + step.getProgressScore() + " (" + step + ")");
//        }
//        System.out.println("Timing:");
//        for (int i = 0; i < Options.getInstance().solverStepsProgress.length; i++) {
//            if (anzStepsProgress[i] > 0) {
//                System.out.printf("  %5d/%8.2fus/%12.2fms: %s\r\n", anzStepsProgress[i], (stepsNanoTime[i] / anzStepsProgress[i] / 1000.0),
//                        (stepsNanoTime[i] / 1000000.0), Options.getInstance().solverStepsProgress[i].getType().getStepName());
//            }
//        }
    }

    /**
     * Calculates the progress score for <code>step</code>. The progress score is
     * defined as the number of singles the step unlocks in the sudoku, if
     * {@link Options#solverStepsProgress} is used.
     * @param tmpSudoku
     * @param step
     */
    public void getProgressScore(Sudoku tmpSudoku, SolutionStep orgStep) {
        Sudoku save = this.sudoku;
        setSudoku(tmpSudoku);

        int progressScoreSingles = 0;
        int progressScoreSinglesOnly = 0;
        int progressScore = 0;
        boolean direct = true;
        // if step sets a cell it should count as single
        SolutionType type = orgStep.getType();
        if (type == SolutionType.FORCING_CHAIN_VERITY ||
                type == SolutionType.FORCING_NET_VERITY ||
                type == SolutionType.TEMPLATE_SET) {
            int anz = orgStep.getIndices().size();
            progressScoreSingles += anz;
            progressScoreSinglesOnly += anz;
        }

        // execute the step
        doStep(orgStep);
        //System.out.println(orgStep.getType().getStepName() + ":");
        // now solve as far as possible
        SolutionStep step = null;
        do {
            // jetzt eine Methode nach der anderen, aber immer nur einmal; wenn etwas gefunden wurde continue
            step = getHint(false, Options.getInstance().solverStepsProgress);
            if (step != null) {
                if (step.getType().isSingle()) {
                    progressScoreSingles++;
                    if (direct) {
                        progressScoreSinglesOnly++;
                    }
                } else {
                    direct = false;
                }
                progressScore += step.getType().getStepConfig().getBaseScore();
                //System.out.println("        " + step);
                doStep(step);
                if (step.getType() == SolutionType.GIVE_UP) {
                    step = null;
                }
            }
        } while (step != null);
        //set the score
        orgStep.setProgressScoreSingles(progressScoreSingles);
        orgStep.setProgressScoreSinglesOnly(progressScoreSinglesOnly);
        orgStep.setProgressScore(progressScore);
        
        setSudoku(save);
    }

    /**
     * Get the next logical step for a given sudoku. If <code>singlesOnly</code>
     * is set, only singles are tried.<br>
     * The current state of the solver instance is saved and restored after the search
     * is complete.
     * @param sudoku
     * @param singlesOnly
     * @return
     */
    public SolutionStep getHint(Sudoku sudoku, boolean singlesOnly) {
        Sudoku save = this.sudoku;
        DifficultyLevel oldMaxLevel = maxLevel;
        DifficultyLevel oldLevel = level;
        maxLevel = Options.getInstance().difficultyLevels[DifficultyType.EXTREME.ordinal()];
        level = Options.getInstance().difficultyLevels[DifficultyType.EASY.ordinal()];
        setSudoku(sudoku);
        SolutionStep step = getHint(singlesOnly);
        maxLevel = oldMaxLevel;
        level = oldLevel;
        setSudoku(save);
        return step;
    }

    /**
     * Get the next logical step for the internal sudoku. If <code>singlesOnly</code>
     * is set, only singles are tried.
     * @param singlesOnly
     * @return
     */
    private SolutionStep getHint(boolean singlesOnly) {
        return getHint(singlesOnly, Options.getInstance().solverSteps);
    }

    /**
     * Get the next logical step for the internal sudoku. If <code>singlesOnly</code>
     * is set, only singles are tried.<br>
     * Since the steps are passed as argument this method can be used to
     * calculate the next step and to calculate the progress measure for
     * a given sudoku state.
     * @param singlesOnly
     * @param solverSteps
     * @return
     */
    private SolutionStep getHint(boolean singlesOnly, StepConfig[] solverSteps) {
        if (sudoku.isSolved()) {
            return null;
        }
        SolutionStep hint = null;

        for (int i = 0; i < solverSteps.length; i++) {
            if (solverSteps == Options.getInstance().solverStepsProgress) {
                if (solverSteps[i].isEnabledProgress() == false) {
                    continue;
                }
            } else {
                if (solverSteps[i].isEnabled() == false) {
                    // diesen Schritt nicht ausführen
                    continue;
                }
            }
            SolutionType type = solverSteps[i].getType();
            if (singlesOnly &&
                    (type != SolutionType.HIDDEN_SINGLE && type != SolutionType.NAKED_SINGLE &&
                    type != SolutionType.FULL_HOUSE)) {
                continue;
            }
//            Logger.getLogger(getClass().getName()).log(Level.FINER, "trying " + SolutionStep.getStepName(type) + ": ");
            long nanos = System.nanoTime();
            hint = getStep(type);
            nanos = System.nanoTime() - nanos;
//            Logger.getLogger(getClass().getName()).log(Level.FINER, (nanos / 1000) + "ms (" + hint + ")");
//            if (nanos > 20000) {
//                Logger.getLogger(getClass().getName()).log(Level.FINE, "trying " + SolutionStep.getStepName(type) + ": " + nanos + "ms");
//            }
            anzStepsProgress[i]++;
            stepsNanoTime[i] += nanos;
            if (hint != null) {
                anzSteps[i]++;
                score += solverSteps[i].getBaseScore();
                if (Options.getInstance().difficultyLevels[solverSteps[i].getLevel()].getOrdinal() > level.getOrdinal()) {
                    level = Options.getInstance().difficultyLevels[solverSteps[i].getLevel()];
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
        // not really sure whether the list may be cleared savely here...
        //SudokuUtil.clearStepList(steps);
        steps = new ArrayList<SolutionStep>();
        for (int i = 0; i < partSteps.size(); i++) {
            steps.add(partSteps.get(i));
        }
        candType = SudokuCell.PLAY;
        this.sudoku = sudoku;
    }

    public void setSudoku(Sudoku sudoku) {
        SudokuUtil.clearStepList(steps);
        for (int i = 0; i < anzSteps.length; i++) {
            anzSteps[i] = 0;
        }
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

    /**
     * Loads all relevant objects into <code>state</code>. If <code>copy</code> is true,
     * all objects are copied.<br>
     * Some objects have to be copied regardless of parameter <code>copy</code>.
     * @param state
     * @param copy
     */
    public void getState(GuiState state, boolean copy) {
        if (copy) {
            state.anzSteps = (int[]) anzSteps.clone();
            state.steps = (List<SolutionStep>) ((ArrayList)steps).clone();
        } else {
            state.anzSteps = anzSteps;
            state.steps = steps;
        }
    }

    /**
     * Loads back a saved state. Whether the objects had been copied
     * before is irrelevant here.
     * @param state
     */
    public void setState(GuiState state) {
        // corrections for newer versions: anzSteps might be too small if steps types have been added
        // contents of state could come from a file created with an older version fo HoDoKu
        if (state.anzSteps.length < anzSteps.length) {
            int[] tmpArray = new int[anzSteps.length];
            for (int i = 0; i < state.anzSteps.length; i++) {
                tmpArray[i] = state.anzSteps[i];
            }
            state.anzSteps = tmpArray;
        }
        anzSteps = state.anzSteps;
        steps = state.steps;
    }

    private void resetProgressStepCounters() {
        for (int i = 0; i < anzStepsProgress.length; i++) {
            anzStepsProgress[i] = 0;
            stepsNanoTime[i] = 0;
        }
    }
}
