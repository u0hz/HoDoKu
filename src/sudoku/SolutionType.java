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

    FULL_HOUSE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Full_House"), "0000"),
    HIDDEN_SINGLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Hidden_Single"), "0002"),
    HIDDEN_PAIR(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Hidden_Pair"), "0210"),
    HIDDEN_TRIPLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Hidden_Triple"), "0211"),
    HIDDEN_QUADRUPLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Hidden_Quadruple"), "0212"),
    NAKED_SINGLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Naked_Single"), "0003"),
    NAKED_PAIR(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Naked_Pair"), "0200"),
    NAKED_TRIPLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Naked_Triple"), "0201"),
    NAKED_QUADRUPLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Naked_Quadruple"), "0202"),
    LOCKED_PAIR(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Locked_Pair"), "0110"),
    LOCKED_TRIPLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Locked_Triple"), "0111"),
    LOCKED_CANDIDATES(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Locked_Candidates"), "010x"),
    LOCKED_CANDIDATES_1(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Locked_Candidates_Type_1_(Pointing)"), "0100"),
    LOCKED_CANDIDATES_2(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Locked_Candidates_Type_2_(Claiming)"), "0101"),
    SKYSCRAPER(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Skyscraper"), "0400"),
    TWO_STRING_KITE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("2-String_Kite"), "0401"),
    UNIQUENESS_1(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Uniqueness_Test_1"), "0600"),
    UNIQUENESS_2(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Uniqueness_Test_2"), "0601"),
    UNIQUENESS_3(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Uniqueness_Test_3"), "0602"),
    UNIQUENESS_4(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Uniqueness_Test_4"), "0603"),
    UNIQUENESS_5(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Uniqueness_Test_5"), "0604"),
    UNIQUENESS_6(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Uniqueness_Test_6"), "0605"),
    BUG_PLUS_1(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Bivalue_Universal_Grave_+_1"), "0610"),
    XY_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("XY-Wing"), "0800"),
    XYZ_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("XYZ-Wing"), "0801"),
    W_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("W-Wing"), "0803"),
    X_CHAIN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("X-Chain"), "0701"),
    XY_CHAIN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("XY-Chain"), "0702"),
    REMOTE_PAIR(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Remote_Pair"), "0703"),
    NICE_LOOP(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Nice_Loop/AIC"), "0706"),
    CONTINUOUS_NICE_LOOP(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Continuous_Nice_Loop"), "0706"),
    DISCONTINUOUS_NICE_LOOP(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Discontinuous_Nice_Loop"), "0706"),
    X_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("X-Wing"), "0300"),
    SWORDFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Swordfish"), "0301"),
    JELLYFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Jellyfish"), "0302"),
    SQUIRMBAG(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Squirmbag"), "0303"),
    WHALE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Whale"), "0304"),
    LEVIATHAN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Leviathan"), "0305"),
    FINNED_X_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_X-Wing"), "0310"),
    FINNED_SWORDFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Swordfish"), "0311"),
    FINNED_JELLYFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Jellyfish"), "0312"),
    FINNED_SQUIRMBAG(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Squirmbag"), "0313"),
    FINNED_WHALE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Whale"), "0314"),
    FINNED_LEVIATHAN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Leviathan"), "0315"),
    SASHIMI_X_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Sashimi_X-Wing"), "0320"),
    SASHIMI_SWORDFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Sashimi_Swordfish"), "0321"),
    SASHIMI_JELLYFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Sashimi_Jellyfish"), "0322"),
    SASHIMI_SQUIRMBAG(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Sashimi_Squirmbag"), "0323"),
    SASHIMI_WHALE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Sashimi_Whale"), "0324"),
    SASHIMI_LEVIATHAN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Sashimi_Leviathan"), "0325"),
    FRANKEN_X_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Franken_X-Wing"), "0330"),
    FRANKEN_SWORDFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Franken_Swordfish"), "0331"),
    FRANKEN_JELLYFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Franken_Jellyfish"), "0332"),
    FRANKEN_SQUIRMBAG(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Franken_Squirmbag"), "0333"),
    FRANKEN_WHALE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Franken_Whale"), "0334"),
    FRANKEN_LEVIATHAN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Franken_Leviathan"), "0335"),
    FINNED_FRANKEN_X_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Franken_X-Wing"), "0340"),
    FINNED_FRANKEN_SWORDFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Franken_Swordfish"), "0341"),
    FINNED_FRANKEN_JELLYFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Franken_Jellyfish"), "0342"),
    FINNED_FRANKEN_SQUIRMBAG(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Franken_Squirmbag"), "0343"),
    FINNED_FRANKEN_WHALE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Franken_Whale"), "0344"),
    FINNED_FRANKEN_LEVIATHAN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Franken_Leviathan"), "0345"),
    MUTANT_X_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Mutant_X-Wing"), "0350"),
    MUTANT_SWORDFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Mutant_Swordfish"), "0351"),
    MUTANT_JELLYFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Mutant_Jellyfish"), "0352"),
    MUTANT_SQUIRMBAG(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Mutant_Squirmbag"), "0353"),
    MUTANT_WHALE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Mutant_Whale"), "0354"),
    MUTANT_LEVIATHAN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Mutant_Leviathan"), "0355"),
    FINNED_MUTANT_X_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Mutant_X-Wing"), "0360"),
    FINNED_MUTANT_SWORDFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Mutant_Swordfish"), "0361"),
    FINNED_MUTANT_JELLYFISH(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Mutant_Jellyfish"), "0362"),
    FINNED_MUTANT_SQUIRMBAG(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Mutant_Squirmbag"), "0363"),
    FINNED_MUTANT_WHALE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Mutant_Whale"), "0364"),
    FINNED_MUTANT_LEVIATHAN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Finned_Mutant_Leviathan"), "0365"),
    SUE_DE_COQ(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Sue_de_Coq"), "1101"),
    ALS_XZ(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Almost_Locked_Set_XZ-Rule"), "9001"),
    ALS_XY_WING(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Almost_Locked_Set_XY-Wing"), "9002"),
    ALS_XY_CHAIN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Almost_Locked_Set_XY-Chain"), "9003"),
    DEATH_BLOSSOM(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Death_Blossom"), "9004"),
    TEMPLATE_SET(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Template_Set"), "xxxx"),
    TEMPLATE_DEL(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Template_Delete"), "xxxx"),
    FORCING_CHAIN(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Forcing_Chain"), "xxxx"),
    FORCING_CHAIN_CONTRADICTION(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Forcing_Chain_Contradiction"), "xxxx"),
    FORCING_CHAIN_VERITY(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Forcing_Chain_Verity"), "xxxx"),
    FORCING_NET(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Forcing_Net"), "xxxx"),
    FORCING_NET_CONTRADICTION(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Forcing_Net_Contradiction"), "xxxx"),
    FORCING_NET_VERITY(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Forcing_Net_Verity"), "xxxx"),
    BRUTE_FORCE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Brute_Force"), "xxxx"),
    INCOMPLETE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Incomplete_Solution"), "0000"),
    GIVE_UP(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Give_Up"), "0000"),
    GROUPED_NICE_LOOP(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Grouped_Nice_Loop/AIC"), "0706"),
    GROUPED_CONTINUOUS_NICE_LOOP(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Grouped_Continuous_Nice_Loop"), "0706"),
    GROUPED_DISCONTINUOUS_NICE_LOOP(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Grouped_Discontinuous_Nice_Loop"), "0706"),
    EMPTY_RECTANGLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Empty_Rectangle"), "0402"),
    HIDDEN_RECTANGLE(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Hidden_Rectangle"), "0606"),
    AVOIDABLE_RECTANGLE_1(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Avoidable_Rectangle_Type_1"), "0607"),
    AVOIDABLE_RECTANGLE_2(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Avoidable_Rectangle_Type_2"), "0608"),
    AIC(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("AIC"), "0706"),
    GROUPED_AIC(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Grouped_AIC"), "0706"),
    SIMPLE_COLORS(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Simple_Colors"), "0500"),
    MULTI_COLORS(java.util.ResourceBundle.getBundle("intl/SolutionType").getString("Multi_Colors"), "0501");
    private String stepName;
    private String libraryType;

    SolutionType() {
        // für XMLEncoder
    }

    SolutionType(String stepName, String libraryType) {
        this.setStepName(stepName);
        this.setLibraryType(libraryType);
    }

    @Override
    public String toString() {
        return "enum SolutionType: " + stepName + " (" + libraryType + ")";
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
}
