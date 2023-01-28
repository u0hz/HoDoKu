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
import java.util.List;

/**
 *
 * @author Admin
 */
public class Als {

    public SudokuSet indices;    // all indices of the ALS
    public SudokuSet candidates; // all candidate of the ALS
    public SudokuSet[] indicesPerCandidat = new SudokuSet[10]; // for every candidate all cells containing that candidate
    public SudokuSet[] buddiesPerCandidat = new SudokuSet[10]; // for every candidate all cells that are buddies to all ALS cells holding that candidate
    public SudokuSet buddies = new SudokuSet();  // all cells, that contain at least one candidate, that is a buddy to the ALS
    public int chainPenalty = -1;
    private static SudokuSet indexSet = new SudokuSet();
    private static SudokuSet candSet = new SudokuSet();
    private static SudokuSet[] candAddSets = new SudokuSet[10];

    Als(SudokuSet indices, SudokuSet candidates, Sudoku sudoku) {
        this.indices = new SudokuSet(indices);
        this.candidates = new SudokuSet(candidates);
        for (int i = 1; i <= 9; i++) {
            if (candidates.contains(i)) {
                indicesPerCandidat[i] = new SudokuSet();
                buddiesPerCandidat[i] = new SudokuSet();
                buddiesPerCandidat[i].setAll();
                for (int j = 0; j < indices.size(); j++) {
                    int index = indices.get(j);
                    SudokuCell cell = sudoku.getCell(index);
                    if (cell.isCandidate(SudokuCell.PLAY, i)) {
                        indicesPerCandidat[i].add(index);
                        buddiesPerCandidat[i].and(Sudoku.buddies[index]);
                    }
                }
                buddies.or(buddiesPerCandidat[i]);
            }
        }
    }

    public static int getChainPenalty(int candSize) {
//        return 0;
        if (candSize == 0 || candSize == 1) {
            return 0;
        } else if (candSize == 2) {
            return candSize - 1;
        } else {
            return (candSize - 1) * 2;
        }
    }

    public int getChainPenalty() {
        if (chainPenalty == -1) {
            chainPenalty = getChainPenalty(candidates.size());
        }
        return chainPenalty;
    }

    public void getBuddies(SudokuSet buddies) {
        buddies.clear();
        if (indices.size() == 0) {
            return;
        }
        buddies.set(Sudoku.buddies[indices.get(0)]);
        for (int i = 1; i < indices.size(); i++) {
            buddies.and(Sudoku.buddies[indices.get(i)]);
        }
    }

    @Override
    public boolean equals(Object o) {
        Als a = (Als) o;
        return indices.equals(a.indices);
    }

    @Override
    public String toString() {
        //return "ALS: " + candidates.toString() + " - " + indices.toString();
        return "ALS: " + SolutionStep.getAls(this);
    }

    /**
     * Gets all ALS from the given sudoku and puts them in an ArrayList.
     *
     */
    public static List<Als> getAlses(Sudoku sudoku) {
        return getAlses(sudoku, false);
    }

    public static List<Als> getAlses(Sudoku sudoku, boolean onlyLargerThanOne) {
        List<Als> alses = new ArrayList<Als>();

        collectAllAlsesForHouse(Sudoku.LINES, sudoku, alses, onlyLargerThanOne);
        collectAllAlsesForHouse(Sudoku.COLS, sudoku, alses, onlyLargerThanOne);
        collectAllAlsesForHouse(Sudoku.BLOCKS, sudoku, alses, onlyLargerThanOne);

        return alses;
    }

    private static void collectAllAlsesForHouse(int[][] indexe, Sudoku sudoku, List<Als> alses,
            boolean onlyLargerThanOne) {
        for (int i = 0; i < indexe.length; i++) {
            for (int j = 0; j < indexe[i].length; j++) {
                indexSet.clear();
                candSet.clear();
                checkAlsRecursive(0, j, indexe[i], sudoku, alses, onlyLargerThanOne);
            }
        }
    }

    private static void checkAlsRecursive(int anzahl, int startIndex, int[] indexe,
            Sudoku sudoku, List<Als> alses, boolean onlyLargerThanOne) {
        anzahl++;
        if (anzahl > indexe.length - 1) {
            // Rekursion abbrechen (maximal 8 Zellen in einem ALS möglich!)
            return;
        }
        for (int i = startIndex; i < indexe.length; i++) {
            int houseIndex = indexe[i];
            if (sudoku.getCell(houseIndex).getValue() != 0) {
                // gesetzte Zellen ignorieren
                continue;
            }
            indexSet.add(houseIndex);
            if (candAddSets[anzahl - 1] == null) {
                candAddSets[anzahl - 1] = new SudokuSet();
            }
            sudoku.getCell(houseIndex).getCandidateSet(candAddSets[anzahl - 1]);
            candAddSets[anzahl - 1].andNot(candSet);
            candSet.or(candAddSets[anzahl - 1]);

            // Wenn die Anzahl der Kandidaten um genau 1 größer ist als die
            // Anzahl der Zellen, handelt es sich um ein ALS
            if (candSet.size() - anzahl == 1) {
                if (!onlyLargerThanOne || indexSet.size() > 1) {
                    // ok, ALS gefunden -> merken, wenn es es nicht schon gibt!
                    Als newAls = new Als(indexSet, candSet, sudoku);
                    if (!alses.contains(newAls)) {
                        alses.add(newAls);
                    }
                }
            }

            // weiter in der Rekursion
            checkAlsRecursive(anzahl, i + 1, indexe, sudoku, alses, onlyLargerThanOne);

            // diese Stufe rückgängig machen
            candSet.andNot(candAddSets[anzahl - 1]);
            indexSet.remove(houseIndex);
        }
    }
}
