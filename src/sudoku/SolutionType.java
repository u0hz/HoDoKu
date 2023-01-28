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
public enum SolutionType {

    FULL_HOUSE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Full_House"), "0000", "fh"),
    HIDDEN_SINGLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Hidden_Single"), "0002", "h1"),
    HIDDEN_PAIR(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Hidden_Pair"), "0210", "h2"),
    HIDDEN_TRIPLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Hidden_Triple"), "0211", "h3"),
    HIDDEN_QUADRUPLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Hidden_Quadruple"), "0212", "h4"),
    NAKED_SINGLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Naked_Single"), "0003", "n1"),
    NAKED_PAIR(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Naked_Pair"), "0200", "n2"),
    NAKED_TRIPLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Naked_Triple"), "0201", "n3"),
    NAKED_QUADRUPLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Naked_Quadruple"), "0202", "n4"),
    LOCKED_PAIR(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Locked_Pair"), "0110", "l2"),
    LOCKED_TRIPLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Locked_Triple"), "0111", "l3"),
    LOCKED_CANDIDATES(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Locked_Candidates"), "010x", "lc"),
    LOCKED_CANDIDATES_1(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Locked_Candidates_Type_1_(Pointing)"), "0100", "lc1"),
    LOCKED_CANDIDATES_2(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Locked_Candidates_Type_2_(Claiming)"), "0101", "lc2"),
    SKYSCRAPER(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Skyscraper"), "0400", "sk"),
    TWO_STRING_KITE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("2-String_Kite"), "0401", "2sk"),
    UNIQUENESS_1(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Uniqueness_Test_1"), "0600", "u1"),
    UNIQUENESS_2(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Uniqueness_Test_2"), "0601", "u2"),
    UNIQUENESS_3(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Uniqueness_Test_3"), "0602", "u3"),
    UNIQUENESS_4(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Uniqueness_Test_4"), "0603", "u4"),
    UNIQUENESS_5(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Uniqueness_Test_5"), "0604", "u5"),
    UNIQUENESS_6(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Uniqueness_Test_6"), "0605", "u6"),
    BUG_PLUS_1(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Bivalue_Universal_Grave_+_1"), "0610", "bug1"),
    XY_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("XY-Wing"), "0800", "xy"),
    XYZ_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("XYZ-Wing"), "0801", "xyz"),
    W_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("W-Wing"), "0803", "w"),
    X_CHAIN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("X-Chain"), "0701", "x"),
    XY_CHAIN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("XY-Chain"), "0702", "xyc"),
    REMOTE_PAIR(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Remote_Pair"), "0703", "rp"),
    NICE_LOOP(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Nice_Loop/AIC"), "0706", "nl"),
    CONTINUOUS_NICE_LOOP(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Continuous_Nice_Loop"), "0706", "cnl"),
    DISCONTINUOUS_NICE_LOOP(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Discontinuous_Nice_Loop"), "0706", "dnl"),
    X_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("X-Wing"), "0300", "bf2"),
    SWORDFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Swordfish"), "0301", "bf3"),
    JELLYFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Jellyfish"), "0302", "bf4"),
    SQUIRMBAG(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Squirmbag"), "0303", "bf5"),
    WHALE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Whale"), "0304", "bf6"),
    LEVIATHAN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Leviathan"), "0305", "bf7"),
    FINNED_X_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_X-Wing"), "0310", "fbf2"),
    FINNED_SWORDFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Swordfish"), "0311", "fbf3"),
    FINNED_JELLYFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Jellyfish"), "0312", "fbf4"),
    FINNED_SQUIRMBAG(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Squirmbag"), "0313", "fbf5"),
    FINNED_WHALE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Whale"), "0314", "fbf6"),
    FINNED_LEVIATHAN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Leviathan"), "0315", "fbf7"),
    SASHIMI_X_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Sashimi_X-Wing"), "0320", "sbf2"),
    SASHIMI_SWORDFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Sashimi_Swordfish"), "0321", "sbf3"),
    SASHIMI_JELLYFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Sashimi_Jellyfish"), "0322", "sbf4"),
    SASHIMI_SQUIRMBAG(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Sashimi_Squirmbag"), "0323", "sbf5"),
    SASHIMI_WHALE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Sashimi_Whale"), "0324", "sbf6"),
    SASHIMI_LEVIATHAN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Sashimi_Leviathan"), "0325", "sbf7"),
    FRANKEN_X_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Franken_X-Wing"), "0330", "ff2"),
    FRANKEN_SWORDFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Franken_Swordfish"), "0331", "ff3"),
    FRANKEN_JELLYFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Franken_Jellyfish"), "0332", "ff4"),
    FRANKEN_SQUIRMBAG(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Franken_Squirmbag"), "0333", "ff5"),
    FRANKEN_WHALE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Franken_Whale"), "0334", "ff6"),
    FRANKEN_LEVIATHAN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Franken_Leviathan"), "0335", "ff7"),
    FINNED_FRANKEN_X_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Franken_X-Wing"), "0340", "fff2"),
    FINNED_FRANKEN_SWORDFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Franken_Swordfish"), "0341", "fff3"),
    FINNED_FRANKEN_JELLYFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Franken_Jellyfish"), "0342", "fff4"),
    FINNED_FRANKEN_SQUIRMBAG(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Franken_Squirmbag"), "0343", "fff5"),
    FINNED_FRANKEN_WHALE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Franken_Whale"), "0344", "fff6"),
    FINNED_FRANKEN_LEVIATHAN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Franken_Leviathan"), "0345", "fff7"),
    MUTANT_X_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Mutant_X-Wing"), "0350", "mf2"),
    MUTANT_SWORDFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Mutant_Swordfish"), "0351", "mf3"),
    MUTANT_JELLYFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Mutant_Jellyfish"), "0352", "mf4"),
    MUTANT_SQUIRMBAG(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Mutant_Squirmbag"), "0353", "mf5"),
    MUTANT_WHALE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Mutant_Whale"), "0354", "mf6"),
    MUTANT_LEVIATHAN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Mutant_Leviathan"), "0355", "mf7"),
    FINNED_MUTANT_X_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Mutant_X-Wing"), "0360", "fmf2"),
    FINNED_MUTANT_SWORDFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Mutant_Swordfish"), "0361", "fmf3"),
    FINNED_MUTANT_JELLYFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Mutant_Jellyfish"), "0362", "fmf4"),
    FINNED_MUTANT_SQUIRMBAG(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Mutant_Squirmbag"), "0363", "fmf5"),
    FINNED_MUTANT_WHALE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Mutant_Whale"), "0364", "fmf6"),
    FINNED_MUTANT_LEVIATHAN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Mutant_Leviathan"), "0365", "fmf7"),
    SUE_DE_COQ(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Sue_de_Coq"), "1101", "sdc"),
    ALS_XZ(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Almost_Locked_Set_XZ-Rule"), "9001", "axz"),
    ALS_XY_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Almost_Locked_Set_XY-Wing"), "9002", "axy"),
    ALS_XY_CHAIN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Almost_Locked_Set_XY-Chain"), "9003", "ach"),
    DEATH_BLOSSOM(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Death_Blossom"), "9004", "db"),
    TEMPLATE_SET(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Template_Set"), "xxxx", "ts"),
    TEMPLATE_DEL(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Template_Delete"), "xxxx", "td"),
    FORCING_CHAIN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Forcing_Chain"), "xxxx", "fc"),
    FORCING_CHAIN_CONTRADICTION(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Forcing_Chain_Contradiction"), "xxxx", "fcc"),
    FORCING_CHAIN_VERITY(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Forcing_Chain_Verity"), "xxxx", "fcv"),
    FORCING_NET(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Forcing_Net"), "xxxx", "fn"),
    FORCING_NET_CONTRADICTION(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Forcing_Net_Contradiction"), "xxxx", "fnc"),
    FORCING_NET_VERITY(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Forcing_Net_Verity"), "xxxx", "fnv"),
    BRUTE_FORCE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Brute_Force"), "xxxx", "bf"),
    INCOMPLETE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Incomplete_Solution"), "0000", "in"),
    GIVE_UP(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Give_Up"), "0000", "gu"),
    GROUPED_NICE_LOOP(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Grouped_Nice_Loop/AIC"), "0706", "gnl"),
    GROUPED_CONTINUOUS_NICE_LOOP(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Grouped_Continuous_Nice_Loop"), "0706", "gcnl"),
    GROUPED_DISCONTINUOUS_NICE_LOOP(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Grouped_Discontinuous_Nice_Loop"), "0706", "gdnl"),
    EMPTY_RECTANGLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Empty_Rectangle"), "0402", "er"),
    HIDDEN_RECTANGLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Hidden_Rectangle"), "0606", "hr"),
    AVOIDABLE_RECTANGLE_1(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Avoidable_Rectangle_Type_1"), "0607", "ar1"),
    AVOIDABLE_RECTANGLE_2(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Avoidable_Rectangle_Type_2"), "0608", "ar2"),
    AIC(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("AIC"), "0706", "aic"),
    GROUPED_AIC(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Grouped_AIC"), "0706", "gaic"),
    SIMPLE_COLORS(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Simple_Colors"), "0500", "sc"),
    MULTI_COLORS(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Multi_Colors"), "0501", "mc");
    private String stepName;
    private String libraryType;
    private String argName;

    SolutionType() {
        // für XMLEncoder
    }

    SolutionType(String stepName, String libraryType, String argName) {
        this.setStepName(stepName);
        this.setLibraryType(libraryType);
        this.setArgName(argName);
    }

    @Override
    public String toString() {
        return "enum SolutionType: " + stepName + " (" + libraryType + "|" + argName + ")";
    }
    
    public static boolean isSingle(SolutionType type) {
        if (type == HIDDEN_SINGLE || type == NAKED_SINGLE || type == FULL_HOUSE) {
            return true;
        }
        return false;
    }
    
    public boolean isSingle() {
        return isSingle(this);
    }
    
    public static boolean isSSTS(SolutionType type) {
        if (type.isSingle() ||
                type == HIDDEN_PAIR || type == HIDDEN_TRIPLE || type == HIDDEN_QUADRUPLE ||
                type == NAKED_PAIR || type == NAKED_TRIPLE || type == NAKED_QUADRUPLE ||
                type == LOCKED_PAIR || type == LOCKED_TRIPLE ||
                type == LOCKED_CANDIDATES || type == LOCKED_CANDIDATES_1 || type == LOCKED_CANDIDATES_2 ||
                type == X_WING || type == SWORDFISH || type == JELLYFISH ||
                type == XY_WING || type == SIMPLE_COLORS || type == MULTI_COLORS) {
            return true;
        }
        return false;
    }
    
    public boolean isSSTS() {
        return isSSTS(this);
    }
    
    public static boolean isFish(SolutionType type) {
        StepConfig[] configs = Options.getInstance().solverSteps;
        for (int i = 0; i < configs.length; i++) {
            if (configs[i].getType() == type) {
                if (configs[i].getCategory() == SolutionCategory.BASIC_FISH || 
                        configs[i].getCategory() == SolutionCategory.FINNED_BASIC_FISH ||
                        configs[i].getCategory() == SolutionCategory.FINNED_FRANKEN_FISH ||
                        configs[i].getCategory() == SolutionCategory.FINNED_MUTANT_FISH ||
                        configs[i].getCategory() == SolutionCategory.FRANKEN_FISH ||
                        configs[i].getCategory() == SolutionCategory.MUTANT_FISH) {
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }
    
    public boolean isFish() {
        return isFish(this);
    }
    
    public static int getNonSinglesAnz() {
        int anz = 0;
        for (SolutionType tmp : values()) {
            if (!tmp.isSingle()) {
                anz++;
            }
        }
        return anz;
    }
    
    public static int getNonSSTSAnz() {
        int anz = 0;
        for (SolutionType tmp : values()) {
            if (!tmp.isSingle() && !tmp.isSSTS()) {
                anz++;
            }
        }
        return anz;
    }
    
    public String getLibraryType() {
        return libraryType;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public void setLibraryType(String libraryType) {
        this.libraryType = libraryType;
    }

    public String getArgName() {
        return argName;
    }

    public void setArgName(String argName) {
        this.argName = argName;
    }
}
