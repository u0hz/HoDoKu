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

/**
 *
 * @author Bernhard Hobiger
 */
public class GroupNode {
    public SudokuSet indices = new SudokuSet(); // indices as bit mask
    public SudokuSet buddies = new SudokuSet(); // all buddies that can see all cells in the group node
    public int cand;      // candidate for grouped link
    public int line = -1; // row (index in Sudoku.ROWS), -1 if not applicable
    public int col = -1;  // col (index in Sudoku.COLS), -1 if not applicable
    public int block;     // block (index in Sudoku.BLOCKS)
    public int index1;    // index of first cell
    public int index2;    // index of second cell
    public int index3;    // index of third cell or -1, if grouped node consists only of two cells
    
    private static SudokuSet candInHouse = new SudokuSet(); // all positions for a given candidate in a given house
    private static SudokuSet tmpSet = new SudokuSet();      // for check with blocks
    
    /**
     * Creates a new instance of GroupNode
     */
    public GroupNode(int cand, SudokuSet indices) {
        this.cand = cand;
        this.indices.set(indices);
        index1 = indices.get(0);
        index2 = indices.get(1);
        index3 = -1;
        if (indices.size() > 2) {
            index3 = indices.get(2);
        }
        block = Sudoku.getBlock(index1);
        if (Sudoku.getLine(index1) == Sudoku.getLine(index2)) {
            line = Sudoku.getLine(index1);
        }
        if (Sudoku.getCol(index1) == Sudoku.getCol(index2)) {
            col = Sudoku.getCol(index1);
        }
        // calculate the buddies
        buddies.set(Sudoku.buddies[index1]);
        buddies.and(Sudoku.buddies[index2]);
        if (index3 >= 0) {
            buddies.and(Sudoku.buddies[index3]);
        }
    }
    
    @Override
    public String toString() {
        return "GroupNode: " + cand + " - " + SolutionStep.getCompactCellPrint(index1, index2, index3) + "  - " + index1 + "/" + index2 + "/" + index3 +
                " (" + line + "/" + col + "/" + block + ")";
    }
    
    /**
     * Gets all group nodes from the given sudoku and puts them in an ArrayList.
     *
     * For all candidates in all lines and all cols do:
     *   - check if they have a candidate left
     *   - if so, check if an intersection of line/col and a block contains
     *     more than one candidate; if yes -> group node found
     */
    public static List<GroupNode> getGroupNodes(Sudoku sudoku) {
        List<GroupNode> groupNodes = new ArrayList<GroupNode>();
        
        getGroupNodesForHouseType(groupNodes, sudoku, Sudoku.lineTemplates);
        getGroupNodesForHouseType(groupNodes, sudoku, Sudoku.colTemplates);
        
        return groupNodes;
    }
    
    private static void getGroupNodesForHouseType(List<GroupNode> groupNodes, Sudoku sudoku, SudokuSet[] houses) {
        for (int i = 0; i < houses.length; i++) {
            for (int cand = 1; cand <= 9; cand++) {
                candInHouse.set(houses[i]);
                candInHouse.and(sudoku.getAllowedPositions()[cand]);
                if (candInHouse.isEmpty()) {
                    // no candidates left in this house -> proceed
                    continue;
                }
                
                // candidates left in house -> check blocks
                for (int j = 0; j < Sudoku.blockTemplates.length; j++) {
                    tmpSet.set(candInHouse);
                    tmpSet.and(Sudoku.blockTemplates[j]);
                    if (tmpSet.isEmpty()) {
                        // no candidates in this house -> proceed with next block
                        continue;
                    } else {
                        // rather complicated for performance reasons (isEmpty() is much faster than size())
                        if (tmpSet.size() >= 2) {
                            // group node found
                            groupNodes.add(new GroupNode(cand, tmpSet));
                        }
                    }
                }
            }
        }
    }
    
    public static void main(String[] args) {
        Sudoku sudoku = new Sudoku();
        sudoku.setSudoku(":0000:x:.4..1..........5.6......3.15.38.2...7......2..........6..5.7....2.....1....3.14..:211 213 214 225 235 448 465 366 566 468 469::");
        long ticks = System.currentTimeMillis();
        List<GroupNode> groupNodes = GroupNode.getGroupNodes(sudoku);
        ticks = System.currentTimeMillis() - ticks;
        System.out.println("getGroupNodes(): " + ticks + "ms, " + groupNodes.size() + " group nodes");
        for (GroupNode node : groupNodes) {
            System.out.println("  " + node);
        }
    }
}
