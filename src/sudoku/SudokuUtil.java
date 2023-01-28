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

import java.util.List;

/**
 *
 * @author Bernhard_2
 */
public class SudokuUtil {

    /**
     * Clears the list. To avoid memory leaks all steps in the list
     * are explicitly nullified.
     * @param steps
     */
    public static void clearStepListWithNullify(List<SolutionStep> steps) {
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                steps.get(i).reset();
                steps.set(i, null);
            }
            steps.clear();
        }
    }

    /**
     * Clears the list. The steps are not nullfied, but the list items are.
     * @param steps
     */
    public static void clearStepList(List<SolutionStep> steps) {
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                steps.set(i, null);
            }
            steps.clear();
        }
    }
}
