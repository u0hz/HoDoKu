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
public class StepConfig implements Cloneable, Comparable<StepConfig> {
    private int index;
    private SolutionType type;
    private int level;     // Index in Options.difficultyLevels
    private SolutionCategory category;
    private int baseScore;
    private int adminScore;
    private boolean enabled;
    
    /** Creates a new instance of StepConfig */
    public StepConfig() {
    }
    
    public StepConfig(int index, SolutionType type, int level, SolutionCategory category,
            int baseScore, int adminScore, boolean enabled) {
        setIndex(index);
        setType(type);
        setLevel(level);
        setCategory(category);
        setBaseScore(baseScore);
        setAdminScore(adminScore);
        setEnabled(enabled);
    }

    @Override
    public String toString() {
        return type.getStepName();
    }
    
    public SolutionType getType() {
        return type;
    }

    public static String getLevelName(int level) {
        return Options.getInstance().getDifficultyLevels()[level].getName();
    }
    
    public static String getLevelName(DifficultyLevel level) {
        return level.getName();
    }
    
    public void setType(SolutionType type) {
        this.type = type;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getBaseScore() {
        return baseScore;
    }

    public void setBaseScore(int baseScore) {
        this.baseScore = baseScore;
    }

    public int getAdminScore() {
        return adminScore;
    }

    public void setAdminScore(int adminScore) {
        this.adminScore = adminScore;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SolutionCategory getCategory() {
        return category;
    }

    public void setCategory(SolutionCategory category) {
        this.category = category;
    }

    public String getCategoryName() {
        return category.getCategoryName();
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    @Override
    public int compareTo(StepConfig o) {
        return index - o.getIndex();
    }
}
