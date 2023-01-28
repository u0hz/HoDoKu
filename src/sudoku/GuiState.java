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

import java.util.Date;
import java.util.List;
import java.util.SortedMap;
import java.util.Stack;

/**
 * Holds the complete GUI state at a given time. The state consists of the
 * following items:<br>
 * {@link SudokuPanel}:
 * <ul>
 *  <li>{@link SudokuPanel#sudoku sudoku}: The actual sudoku</li>
 *  <li>{@link SudokuPanel#solvedSudoku solvedSudoku}: The solved sudoku for displaying invalid values (optional)</li>
 *  <li>{@link SudokuPanel#undoStack undoStack}/{@link SudokuPanel#redoStack redoStack}: The undo/redo stack (optional)</li>
 *  <li>{@link SudokuPanel#step step}: The step that is currently highlighted (may be null if no step is selected)</li>
 *  <li>{@link SudokuPanel#chainIndex chainIndex}: The chain from step that is currently shown</li>
 *  <li>{@link SudokuPanel#coloringMap coloringMap}: All colored cells (optional)</li>
 *  <li>{@link SudokuPanel#coloringCandidateMap coloringCandidateMap}: All colored candidates (optional)</li>
 * </ul>
 * {@link SudokuSolver}:
 * <ul>
 *  <li>{@link SudokuSolver#steps steps}: The current solution</li>
 *  <li>{@link SudokuSolver#anzSteps anzSteps}: The solution summary</li>
 * </ul>
 * {@link SolutionPanel}:
 * <ul>
 *  <li>{@link SolutionPanel#titels titels}: The titles of all available solutions</li>
 *  <li>{@link SolutionPanel#tabSteps tabSteps}: The available solutions</li>
 * </ul>
 *
 * Since this is only data structure that is never saved all attributes are package protected
 * and without getters or setters.<br>
 * Every class referenced above holds a <code>getState()</code> method that retrieves all items from that
 * class.<br>
 * In addition to the attributes described above every GUI state can have a
 * name and a timestamp.
 * @author hobiwan
 */
public class GuiState {
    // items from SudokuPanel
    Sudoku sudoku = null;
    Sudoku solvedSudoku = null;
    Stack<Sudoku> undoStack = null;
    Stack<Sudoku> redoStack = null;
    SolutionStep step = null;
    int chainIndex = -1;
    SortedMap<Integer, Integer> coloringMap = null;
    SortedMap<Integer, Integer> coloringCandidateMap = null;

    // items from SudokuSolver
    List<SolutionStep> steps;
    int[] anzSteps;

    // items from SolutionPanel
    List<String> titels;
    List<List<SolutionStep>> tabSteps;

    // name and timestamp
    String name;
    Date timestamp;
    
    // internal fields
    private SudokuPanel sudokuPanel;
    private SudokuSolver sudokuSolver;
    private SolutionPanel solutionPanel;

    /**
     * Default constructor, only for XmlEncoder/XmlDecoder.<br>
     */
    public GuiState() {

    }

    /**
     * Initializes a state object. If the parameters are null, {@link #get(boolean)} and
     * {@link #set()} ignore the respective objects.
     * @param sudokuPanel
     * @param sudokuSolver
     * @param solutionPanel
     */
    public GuiState(SudokuPanel sudokuPanel, SudokuSolver sudokuSolver, SolutionPanel solutionPanel) {
        initialize(sudokuPanel, sudokuSolver, solutionPanel);
    }

    /**
     * Sets the internal fields for the state. Is used by {@link MainFrame#loadFromFile(boolean)}
     * (the internal fields cannot be stored, they have no meaning outside the running program).
     * @param sudokuPanel
     * @param sudokuSolver
     * @param solutionPanel
     */
    public void initialize(SudokuPanel sudokuPanel, SudokuSolver sudokuSolver, SolutionPanel solutionPanel) {
        this.sudokuPanel = sudokuPanel;
        this.sudokuSolver = sudokuSolver;
        this.solutionPanel = solutionPanel;
    }

    /**
     * Gets all necessary GUI state information. NULL objectes are ignored.
     *
     * @param copy
     */
    public void get(boolean copy) {
        if (sudokuSolver != null) {
            sudokuSolver.getState(this, copy);
        }
        if (solutionPanel != null) {
            solutionPanel.getState(this, copy);
        }
        if (sudokuPanel != null) {
            sudokuPanel.getState(this, copy);
        }
    }

    /**
     * Sets all necessary GUI state information. NULL objectes are ignored.<br>
     * @param ignoreSolutions
     */
    public void set() {
        if (sudokuSolver != null) {
            sudokuSolver.setState(this);
        }
        if (solutionPanel != null) {
            solutionPanel.setState(this);
        }
        if (sudokuPanel != null) {
            sudokuPanel.setState(this);
        }
    }

    /**
     * @return the sudoku
     */
    public // items from SudokuPanel
    Sudoku getSudoku() {
        return sudoku;
    }

    /**
     * @param sudoku the sudoku to set
     */
    public void setSudoku(Sudoku sudoku) {
        this.sudoku = sudoku;
    }

    /**
     * @return the solvedSudoku
     */
    public Sudoku getSolvedSudoku() {
        return solvedSudoku;
    }

    /**
     * @param solvedSudoku the solvedSudoku to set
     */
    public void setSolvedSudoku(Sudoku solvedSudoku) {
        this.solvedSudoku = solvedSudoku;
    }

    /**
     * @return the undoStack
     */
    public Stack<Sudoku> getUndoStack() {
        return undoStack;
    }

    /**
     * @param undoStack the undoStack to set
     */
    public void setUndoStack(Stack<Sudoku> undoStack) {
        this.undoStack = undoStack;
    }

    /**
     * @return the redoStack
     */
    public Stack<Sudoku> getRedoStack() {
        return redoStack;
    }

    /**
     * @param redoStack the redoStack to set
     */
    public void setRedoStack(Stack<Sudoku> redoStack) {
        this.redoStack = redoStack;
    }

    /**
     * @return the step
     */
    public SolutionStep getStep() {
        return step;
    }

    /**
     * @param step the step to set
     */
    public void setStep(SolutionStep step) {
        this.step = step;
    }

    /**
     * @return the chainIndex
     */
    public int getChainIndex() {
        return chainIndex;
    }

    /**
     * @param chainIndex the chainIndex to set
     */
    public void setChainIndex(int chainIndex) {
        this.chainIndex = chainIndex;
    }

    /**
     * @return the coloringMap
     */
    public SortedMap<Integer, Integer> getColoringMap() {
        return coloringMap;
    }

    /**
     * @param coloringMap the coloringMap to set
     */
    public void setColoringMap(SortedMap<Integer, Integer> coloringMap) {
        this.coloringMap = coloringMap;
    }

    /**
     * @return the coloringCandidateMap
     */
    public SortedMap<Integer, Integer> getColoringCandidateMap() {
        return coloringCandidateMap;
    }

    /**
     * @param coloringCandidateMap the coloringCandidateMap to set
     */
    public void setColoringCandidateMap(SortedMap<Integer, Integer> coloringCandidateMap) {
        this.coloringCandidateMap = coloringCandidateMap;
    }

    /**
     * @return the steps
     */
    public // items from SudokuSolver
    List<SolutionStep> getSteps() {
        return steps;
    }

    /**
     * @param steps the steps to set
     */
    public void setSteps(List<SolutionStep> steps) {
        this.steps = steps;
    }

    /**
     * @return the anzSteps
     */
    public int[] getAnzSteps() {
        return anzSteps;
    }

    /**
     * @param anzSteps the anzSteps to set
     */
    public void setAnzSteps(int[] anzSteps) {
        this.anzSteps = anzSteps;
    }

    /**
     * @return the titels
     */
    public // items from SolutionPanel
    List<String> getTitels() {
        return titels;
    }

    /**
     * @param titels the titels to set
     */
    public void setTitels(List<String> titels) {
        this.titels = titels;
    }

    /**
     * @return the tabSteps
     */
    public List<List<SolutionStep>> getTabSteps() {
        return tabSteps;
    }

    /**
     * @param tabSteps the tabSteps to set
     */
    public void setTabSteps(List<List<SolutionStep>> tabSteps) {
        this.tabSteps = tabSteps;
    }

    /**
     * @return the name
     */
    public // name and timestamp
    String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the timestamp
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * @param timestamp the timestamp to set
     */
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
}
