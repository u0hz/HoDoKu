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

import java.util.Enumeration;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 *
 * @author Bernhard Hobiger
 */
public class CheckNode extends DefaultMutableTreeNode {

    protected static final int NONE = 0;
    protected static final int HALF = 1;
    protected static final int FULL = 2;
    private int selectionState;
    private StepConfig step;
    private boolean allSteps;
    private SolutionCategory category;

    public CheckNode() {
        this(null);
    }

    public CheckNode(Object userObject) {
        this(userObject, true, NONE, null, false, null);
    }

    public CheckNode(Object userObject, boolean allowsChildren, int selectionState,
            StepConfig step, boolean allSteps, SolutionCategory category) {
        super(userObject, allowsChildren);
        this.selectionState = selectionState;
        this.step = step;
        this.allSteps = allSteps;
        this.category = category;
    }

    public void toggleSelectionState() {
        if (children == null) {
            // normaler Knoten, kann nur AN oder AUS sein
            selectionState = selectionState == FULL ? NONE : FULL;
            adjustModel(this);
            // der selectionState des parents muss ebenfalls überprüft werden
            int actState = -1;
            CheckNode tmpParent = (CheckNode) getParent();
            for (int i = 0; i < tmpParent.children.size(); i++) {
                CheckNode act = (CheckNode) tmpParent.children.get(i);
                if (actState == -1) {
                    actState = act.selectionState;
                } else {
                    if (actState != act.selectionState) {
                        actState = CheckNode.HALF;
                        break;
                    }
                }
            }
            tmpParent.selectionState = actState;
        } else {
            // NONE -> FULL
            // HALF -> FULL
            // FULL -> NONE
            selectionState = selectionState == FULL ? NONE : FULL;
            Enumeration enumeration = children.elements();
            while (enumeration.hasMoreElements()) {
                CheckNode node = (CheckNode) enumeration.nextElement();
                node.selectionState = selectionState;
                adjustModel(node);
            }
        }
    }

    private void adjustModel(CheckNode node) {
        if (node.step != null) {
            if (allSteps) {
                node.step.setAllStepsEnabled(node.selectionState == FULL);
            } else {
                node.step.setEnabled(node.selectionState == FULL);
            }
        }
    }

    public int getSelectionState() {
        return selectionState;
    }

    public void setSelectionState(int selectionState) {
        this.selectionState = selectionState;
    }

    public SolutionCategory getCategory() {
        return category;
    }
}
