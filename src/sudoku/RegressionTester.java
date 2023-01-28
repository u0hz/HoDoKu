/*
 * Copyright (C) 2008/09/10  Bernhard Hobiger, MaNik-e Team
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class that implements a Regression Tester for HoDoKu
 * 
 * Changes 20090910:
 *   - call specialised solvers for performance reasons
 *   - report unknown techniques
 *   - implement subvariants in techniques
 *   - allow techniques that set values in cells
 *   - allow fail cases (no step of the technique must be available)
 *
 * @author MaNik-e Team, hobiwan
 */
public class RegressionTester {

    private SimpleSolver simpleSolver;
    private SingleDigitPatternSolver singleDigitPatternSolver;
    private MiscellaneousSolver miscellaneousSolver;
    private FishSolver fishSolver;
    private UniquenessSolver uniquenessSolver;
    private WingSolver wingSolver;
    private ColoringSolver coloringSolver;
    private ChainSolver chainSolver;
    private TemplateSolver templateSolver;
    private AlsSolver alsSolver;
    private TablingSolver tablingSolver;
    private int anzTestCases = 0;
    private int anzGoodCases = 0;
    private int anzBadCases = 0;
    private int anzIgnoreCases = 0;
    private Map<String, Integer> ignoredTechniques = new TreeMap<String, Integer>();
    private Map<String, String> failedCases = new TreeMap<String, String>();
    private boolean fastMode = false;

    public RegressionTester() {
        SudokuSolver solver = SudokuSolver.getInstance();
        simpleSolver = (SimpleSolver) solver.getSpecialisedSolver(SimpleSolver.class);
        singleDigitPatternSolver = (SingleDigitPatternSolver) solver.getSpecialisedSolver(SingleDigitPatternSolver.class);
        miscellaneousSolver = (MiscellaneousSolver) solver.getSpecialisedSolver(MiscellaneousSolver.class);
        fishSolver = (FishSolver) solver.getSpecialisedSolver(FishSolver.class);
        uniquenessSolver = (UniquenessSolver) solver.getSpecialisedSolver(UniquenessSolver.class);
        wingSolver = (WingSolver) solver.getSpecialisedSolver(WingSolver.class);
        coloringSolver = (ColoringSolver) solver.getSpecialisedSolver(ColoringSolver.class);
        chainSolver = (ChainSolver) solver.getSpecialisedSolver(ChainSolver.class);
        templateSolver = (TemplateSolver) solver.getSpecialisedSolver(TemplateSolver.class);
        alsSolver = (AlsSolver) solver.getSpecialisedSolver(AlsSolver.class);
        tablingSolver = (TablingSolver) solver.getSpecialisedSolver(TablingSolver.class);
    }

    public void runTest(String testFile) {
        runTest(testFile, false);
    }

    public void runTest(String testFile, boolean fastMode) {
        this.fastMode = fastMode;
        String msg = "Starting test run for file " + testFile;
        if (fastMode) {
            msg += " (fast mode)";
        }
        msg += "...";
        System.out.println(msg);
        // reset everything
        anzTestCases = 0;
        anzGoodCases = 0;
        anzBadCases = 0;
        anzIgnoreCases = 0;
        ignoredTechniques.clear();
        failedCases.clear();

        int anzLines = 0;
        try {
            BufferedReader in = new BufferedReader(new FileReader(testFile));
            String line = null;
            while ((line = in.readLine()) != null) {
                anzLines++;
                //System.out.println("line " + anzLines +": <" + line + ">");
                if ((anzLines % 10) == 0) {
                    System.out.print(".");
                }
                line = line.trim();
                if (line.startsWith("#")) {
                    continue;
                }
                if (line.isEmpty()) {
                    continue;
                }
                test(line);
            }
        } catch (IOException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "error reading test cases...", ex);
        }
        System.out.println("Test finished!");
        System.out.println((anzTestCases) + " cases total");
        System.out.println(anzGoodCases + " tests succeeded");
        System.out.println(anzBadCases + " tests failed");
        System.out.println(anzIgnoreCases + " tests were ignored");
        if (anzIgnoreCases != 0) {
            System.out.println("Ignored techniques:");
            Set<String> keys = ignoredTechniques.keySet();
            for (String key : keys) {
                System.out.println("  " + key + ": " + ignoredTechniques.get(key));
            }
        }
        if (anzBadCases != 0) {
            System.out.println("Failed Cases:");
            Set<String> keys = failedCases.keySet();
            for (String key : keys) {
                System.out.println("  Should be:" + key);
                System.out.println("  Was:      " + failedCases.get(key));
            }
        }
    }

    /**
     * Extract the technique needed, the puzzle, the candidates, for which
     * the search should be made, all candidates, that should
     * be deleted, and all cells, that must be set; fail cases start with
     * a minus sign before the technique.
     *
     * Now search for all occurences of that technique in the grid and
     * compare the results.
     * 
     * @param testCase test case sudoku in library format
     */
    public void test(String testCase) {
        anzTestCases++;
        //System.out.println("testCase: " + testCase);
        String[] parts = testCase.split(":");
        // check for variants and fail cases (step must not be found!)
        int variant = 0;
        boolean failCase = false;
        if (parts[1].contains("-")) {
            int vIndex = parts[1].indexOf("-");
            if (parts[1].charAt(vIndex + 1) == 'x') {
                failCase = true;
            } else {
                try {
                    variant = Integer.parseInt(parts[1].substring(vIndex + 1));
                } catch (NumberFormatException ex) {
                    System.out.println("Invalid variant: " + parts[1]);
                    addIgnoredTechnique(testCase);
                    return;
                }
            }
            parts[1] = parts[1].substring(0, vIndex);
            testCase = "";
            for (int i = 0; i < parts.length; i++) {
                testCase += parts[i];
                if (i < 7) {
                    testCase += ":";
                }
            }
            if (parts.length < 7) {
                testCase += ":";
            }
        }
        String start = ":" + parts[1] + ":" + parts[2] + ":";
        //System.out.println("   start: <" + start + ">");
        SolutionType type = SolutionType.getTypeFromLibraryType(parts[1]);
        if (type == null) {
            addIgnoredTechnique(testCase);
            return;
        }

        // Create and set a new Sudoku
        Sudoku sudoku = new Sudoku();
        //System.out.println(testCase);
        sudoku.setSudoku(testCase);
        //System.out.println("after set: " + sudoku.getSudoku(ClipboardMode.LIBRARY));

        // Find all steps for the technique at the current state
        List<SolutionStep> steps = null;
        List<SolutionStep> steps1 = null;
        boolean oldOption = false;
        boolean oldOption2 = false;
        switch (type) {
            case FULL_HOUSE:
                steps = simpleSolver.findAllFullHouses(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case HIDDEN_SINGLE:
            case HIDDEN_PAIR:
            case HIDDEN_TRIPLE:
            case HIDDEN_QUADRUPLE:
                steps = simpleSolver.findAllHiddenXle(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case NAKED_SINGLE:
            case NAKED_PAIR:
            case NAKED_TRIPLE:
            case NAKED_QUADRUPLE:
                steps = simpleSolver.findAllNakedXle(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case LOCKED_PAIR:
            case LOCKED_TRIPLE:
                steps = simpleSolver.findAllNakedXle(sudoku);
                steps1 = simpleSolver.findAllHiddenXle(sudoku);
                steps.addAll(steps1);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case LOCKED_CANDIDATES_1:
            case LOCKED_CANDIDATES_2:
                steps = simpleSolver.findAllLockedCandidates(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case SKYSCRAPER:
                oldOption = Options.getInstance().allowDualsAndSiamese;
                Options.getInstance().allowDualsAndSiamese = false;
                steps = singleDigitPatternSolver.findAllSkyScrapers(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                Options.getInstance().allowDualsAndSiamese = oldOption;
                break;
            case TWO_STRING_KITE:
                oldOption = Options.getInstance().allowDualsAndSiamese;
                Options.getInstance().allowDualsAndSiamese = false;
                steps = singleDigitPatternSolver.findAllTwoStringKites(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                Options.getInstance().allowDualsAndSiamese = oldOption;
                break;
            case DUAL_TWO_STRING_KITE:
                oldOption = Options.getInstance().allowDualsAndSiamese;
                Options.getInstance().allowDualsAndSiamese = true;
                steps = singleDigitPatternSolver.findAllTwoStringKites(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                Options.getInstance().allowDualsAndSiamese = oldOption;
                break;
            case EMPTY_RECTANGLE:
                oldOption = Options.getInstance().allowErsWithOnlyTwoCandidates;
                if (variant == 1) {
                    Options.getInstance().allowErsWithOnlyTwoCandidates = true;
                }
                steps = singleDigitPatternSolver.findAllEmptyRectangles(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                Options.getInstance().allowErsWithOnlyTwoCandidates = oldOption;
                break;
            case DUAL_EMPTY_RECTANGLE:
                oldOption = Options.getInstance().allowErsWithOnlyTwoCandidates;
                oldOption2 = Options.getInstance().allowDualsAndSiamese;
                Options.getInstance().allowErsWithOnlyTwoCandidates = true;
                Options.getInstance().allowDualsAndSiamese = true;
                steps = singleDigitPatternSolver.findAllEmptyRectangles(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                Options.getInstance().allowErsWithOnlyTwoCandidates = oldOption;
                Options.getInstance().allowDualsAndSiamese = oldOption2;
                break;
            case SIMPLE_COLORS:
            case SIMPLE_COLORS_TRAP:
            case SIMPLE_COLORS_WRAP:
                steps = coloringSolver.findAllSimpleColors(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case MULTI_COLORS:
            case MULTI_COLORS_1:
            case MULTI_COLORS_2:
                steps = coloringSolver.findAllMultiColors(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case UNIQUENESS_1:
            case UNIQUENESS_2:
            case UNIQUENESS_3:
            case UNIQUENESS_4:
            case UNIQUENESS_5:
            case UNIQUENESS_6:
            case HIDDEN_RECTANGLE:
            case AVOIDABLE_RECTANGLE_1:
            case AVOIDABLE_RECTANGLE_2:
                steps = uniquenessSolver.getAllUniqueness(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case BUG_PLUS_1:
                steps = new ArrayList<SolutionStep>();
                uniquenessSolver.setSudoku(sudoku);
                SolutionStep step = uniquenessSolver.getStep(type);
                if (step != null) {
                    steps.add(step);
                }
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case XY_WING:
            case XYZ_WING:
            case W_WING:
                steps = wingSolver.getAllWings(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case TURBOT_FISH:
            case X_CHAIN:
            case XY_CHAIN:
            case REMOTE_PAIR:
                oldOption = Options.getInstance().onlyOneChainPerStep;
                Options.getInstance().onlyOneChainPerStep = false;
                steps = chainSolver.getAllChains(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                Options.getInstance().onlyOneChainPerStep = oldOption;
                break;
            case CONTINUOUS_NICE_LOOP:
            case DISCONTINUOUS_NICE_LOOP:
            case AIC:
                oldOption = Options.getInstance().onlyOneChainPerStep;
                Options.getInstance().onlyOneChainPerStep = false;
                steps = tablingSolver.getAllNiceLoops(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                Options.getInstance().onlyOneChainPerStep = oldOption;
                break;
            case GROUPED_CONTINUOUS_NICE_LOOP:
            case GROUPED_DISCONTINUOUS_NICE_LOOP:
            case GROUPED_AIC:
                oldOption = Options.getInstance().onlyOneChainPerStep;
                oldOption2 = Options.getInstance().allowAlsInTablingChains;
                Options.getInstance().onlyOneChainPerStep = false;
                if ((type == SolutionType.GROUPED_CONTINUOUS_NICE_LOOP && variant == 2) ||
                        (type == SolutionType.GROUPED_DISCONTINUOUS_NICE_LOOP && (variant == 3 || variant == 4)) ||
                        (type == SolutionType.GROUPED_AIC && (variant == 3 || variant == 4))) {
                    Options.getInstance().allowAlsInTablingChains = true;
                } else {
                    Options.getInstance().allowAlsInTablingChains = false;
                }
                steps = tablingSolver.getAllGroupedNiceLoops(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                Options.getInstance().onlyOneChainPerStep = oldOption;
                Options.getInstance().allowAlsInTablingChains = oldOption2;
                break;
            case X_WING:
            case FINNED_X_WING:
            case SASHIMI_X_WING:
                steps = findAllFishes(sudoku, 2, 0);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case FRANKEN_X_WING:
            case FINNED_FRANKEN_X_WING:
                steps = findAllFishes(sudoku, 2, 1);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case MUTANT_X_WING:
            case FINNED_MUTANT_X_WING:
                steps = findAllFishes(sudoku, 2, 2);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case SWORDFISH:
            case FINNED_SWORDFISH:
            case SASHIMI_SWORDFISH:
                steps = findAllFishes(sudoku, 3, 0);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case FRANKEN_SWORDFISH:
            case FINNED_FRANKEN_SWORDFISH:
                if (! fastMode) {
                    steps = findAllFishes(sudoku, 3, 1);
                    checkResults(testCase, steps, sudoku, start, failCase);
                } else {
                    anzIgnoreCases++;
                    ignoredTechniques.put(testCase, 1);
                }
                break;
            case MUTANT_SWORDFISH:
            case FINNED_MUTANT_SWORDFISH:
                if (! fastMode) {
                    steps = findAllFishes(sudoku, 3, 2);
                    checkResults(testCase, steps, sudoku, start, failCase);
                } else {
                    anzIgnoreCases++;
                    ignoredTechniques.put(testCase, 1);
                }
                break;
            case JELLYFISH:
            case FINNED_JELLYFISH:
            case SASHIMI_JELLYFISH:
                steps = findAllFishes(sudoku, 4, 0);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case FRANKEN_JELLYFISH:
            case FINNED_FRANKEN_JELLYFISH:
                if (! fastMode) {
                    steps = findAllFishes(sudoku, 4, 1);
                    checkResults(testCase, steps, sudoku, start, failCase);
                } else {
                    anzIgnoreCases++;
                    ignoredTechniques.put(testCase, 1);
                }
                break;
            case MUTANT_JELLYFISH:
            case FINNED_MUTANT_JELLYFISH:
                if (! fastMode) {
                    steps = findAllFishes(sudoku, 4, 2);
                    checkResults(testCase, steps, sudoku, start, failCase);
                } else {
                    anzIgnoreCases++;
                    ignoredTechniques.put(testCase, 1);
                }
                break;
            case SQUIRMBAG:
            case FINNED_SQUIRMBAG:
            case SASHIMI_SQUIRMBAG:
                steps = findAllFishes(sudoku, 5, 0);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case FRANKEN_SQUIRMBAG:
            case FINNED_FRANKEN_SQUIRMBAG:
                if (! fastMode) {
                    steps = findAllFishes(sudoku, 5, 1);
                    checkResults(testCase, steps, sudoku, start, failCase);
                } else {
                    anzIgnoreCases++;
                    ignoredTechniques.put(testCase, 1);
                }
                break;
            case MUTANT_SQUIRMBAG:
            case FINNED_MUTANT_SQUIRMBAG:
                if (! fastMode) {
                    steps = findAllFishes(sudoku, 5, 2);
                    checkResults(testCase, steps, sudoku, start, failCase);
                } else {
                    anzIgnoreCases++;
                    ignoredTechniques.put(testCase, 1);
                }
                break;
            case WHALE:
            case FINNED_WHALE:
            case SASHIMI_WHALE:
                if (! fastMode) {
                    steps = findAllFishes(sudoku, 6, 0);
                    checkResults(testCase, steps, sudoku, start, failCase);
                } else {
                    anzIgnoreCases++;
                    ignoredTechniques.put(testCase, 1);
                }
                break;
            case FRANKEN_WHALE:
            case FINNED_FRANKEN_WHALE:
                if (! fastMode) {
                    steps = findAllFishes(sudoku, 6, 1);
                    checkResults(testCase, steps, sudoku, start, failCase);
                } else {
                    anzIgnoreCases++;
                    ignoredTechniques.put(testCase, 1);
                }
                break;
            case MUTANT_WHALE:
            case FINNED_MUTANT_WHALE:
                if (! fastMode) {
                    steps = findAllFishes(sudoku, 6, 2);
                    checkResults(testCase, steps, sudoku, start, failCase);
                } else {
                    anzIgnoreCases++;
                    ignoredTechniques.put(testCase, 1);
                }
                break;
            case LEVIATHAN:
            case FINNED_LEVIATHAN:
            case SASHIMI_LEVIATHAN:
                if (! fastMode) {
                    steps = findAllFishes(sudoku, 7, 0);
                    checkResults(testCase, steps, sudoku, start, failCase);
                } else {
                    anzIgnoreCases++;
                    ignoredTechniques.put(testCase, 1);
                }
                break;
            case FRANKEN_LEVIATHAN:
            case FINNED_FRANKEN_LEVIATHAN:
                if (! fastMode) {
                    steps = findAllFishes(sudoku, 7, 1);
                    checkResults(testCase, steps, sudoku, start, failCase);
                } else {
                    anzIgnoreCases++;
                    ignoredTechniques.put(testCase, 1);
                }
                break;
            case MUTANT_LEVIATHAN:
            case FINNED_MUTANT_LEVIATHAN:
                if (! fastMode) {
                    steps = findAllFishes(sudoku, 7, 2);
                    checkResults(testCase, steps, sudoku, start, failCase);
                } else {
                    anzIgnoreCases++;
                    ignoredTechniques.put(testCase, 1);
                }
                break;
            case SUE_DE_COQ:
                steps = miscellaneousSolver.getAllSueDeCoqs(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case ALS_XZ:
            case ALS_XY_WING:
            case ALS_XY_CHAIN:
                oldOption = Options.getInstance().onlyOneAlsPerStep;
                oldOption2 = Options.getInstance().allowAlsOverlap;
                Options.getInstance().onlyOneAlsPerStep = false;
                Options.getInstance().allowAlsOverlap = false;
                if ((type == SolutionType.ALS_XY_CHAIN && variant == 2) ||
                     (type == SolutionType.ALS_XY_WING && variant == 2)) {
                    Options.getInstance().allowAlsOverlap = true;
                }
                steps = alsSolver.getAllAlses(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                Options.getInstance().onlyOneAlsPerStep = oldOption;
                Options.getInstance().allowAlsOverlap = oldOption2;
                break;
            case DEATH_BLOSSOM:
                oldOption = Options.getInstance().onlyOneAlsPerStep;
                oldOption2 = Options.getInstance().allowAlsOverlap;
                Options.getInstance().onlyOneAlsPerStep = false;
                Options.getInstance().allowAlsOverlap = false;
                if (variant == 2) {
                    Options.getInstance().allowAlsOverlap = true;
                }
                steps = alsSolver.getAllDeathBlossoms(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                Options.getInstance().onlyOneAlsPerStep = oldOption;
                Options.getInstance().allowAlsOverlap = oldOption2;
                break;
            case TEMPLATE_SET:
            case TEMPLATE_DEL:
                steps = templateSolver.getAllTemplates(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                break;
            case FORCING_CHAIN_CONTRADICTION:
            case FORCING_CHAIN_VERITY:
                oldOption = Options.getInstance().onlyOneChainPerStep;
                oldOption2 = Options.getInstance().allowAlsInTablingChains;
                Options.getInstance().onlyOneChainPerStep = false;
                Options.getInstance().allowAlsInTablingChains = false;
                steps = tablingSolver.getAllForcingChains(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                Options.getInstance().onlyOneChainPerStep = oldOption;
                Options.getInstance().allowAlsInTablingChains = oldOption2;
                break;
            case FORCING_NET_CONTRADICTION:
            case FORCING_NET_VERITY:
                oldOption = Options.getInstance().onlyOneChainPerStep;
                oldOption2 = Options.getInstance().allowAlsInTablingChains;
                Options.getInstance().onlyOneChainPerStep = false;
                Options.getInstance().allowAlsInTablingChains = false;
                steps = tablingSolver.getAllForcingNets(sudoku);
                checkResults(testCase, steps, sudoku, start, failCase);
                Options.getInstance().onlyOneChainPerStep = oldOption;
                Options.getInstance().allowAlsInTablingChains = oldOption2;
                break;
            case KRAKEN_FISH_TYPE_1:
            case KRAKEN_FISH_TYPE_2:
                oldOption = Options.getInstance().onlyOneFishPerStep;
                oldOption2 = Options.getInstance().checkTemplates;
                Options.getInstance().onlyOneFishPerStep = false;
                Options.getInstance().checkTemplates = true;
                steps = fishSolver.getAllKrakenFishes(sudoku, 2, 4,
                        Options.getInstance().allStepsMaxFins,
                        Options.getInstance().allStepsMaxEndoFins, null, -1, 1);
                checkResults(testCase, steps, sudoku, start, failCase);
                Options.getInstance().onlyOneFishPerStep = oldOption;
                Options.getInstance().checkTemplates = oldOption2;
                break;
            default:
                anzIgnoreCases++;
                addIgnoredTechnique(testCase);
                break;
        }
    }

    private List<SolutionStep> findAllFishes(Sudoku sudoku, int size, int type) {
        boolean oldOption = Options.getInstance().onlyOneFishPerStep;
        boolean oldOption2 = Options.getInstance().checkTemplates;
        Options.getInstance().onlyOneFishPerStep = false;
        Options.getInstance().checkTemplates = true;
        List<SolutionStep> steps = fishSolver.getAllFishes(sudoku, size, size,
                Options.getInstance().allStepsMaxFins,
                Options.getInstance().allStepsMaxEndoFins, null, -1, type);
        Options.getInstance().onlyOneFishPerStep = oldOption;
        Options.getInstance().checkTemplates = oldOption2;
        return steps;
    }

    /**
     * Checking the result of a test is a bit more complicated that it looks:
     * We have to assure, that the case has only one solution and that that
     * solution is correct. If a good and a bad result is present the test failes.
     *
     * With chains more than one chain with different chain lengths may exist. This
     * has to be tested separately.
     * 
     * @param testCase
     * @param steps
     * @param sudoku
     * @param start
     * @param failCase
     */
    private void checkResults(String testCase, List<SolutionStep> steps, Sudoku sudoku,
            String start, boolean failCase) {
        boolean found = false;
        boolean exactMatch = false;
        boolean good = true; // always be optimistic...
        for (SolutionStep step : steps) {
            String result = sudoku.getSudoku(ClipboardMode.LIBRARY, step);
            if (result.startsWith(start)) {
                found = true;
                // should match case!
                // careful: for chains one exact match has to be found,
                // but if a match already exists, a deviation in chain
                // length only does not give a fail case!
                if (!result.equals(testCase)) {
                    if (exactMatch == true) {
                        // test for everything but <comment>
                        int index1 = testCase.lastIndexOf(":");
                        int index2 = result.lastIndexOf(":");
                        if (testCase.substring(0, index1).equals(result.substring(0, index2))) {
                            // does not constitue a fail case!
                            continue;
                        }
                    }
                    good = false;
                    failedCases.put(testCase, result);
                } else {
                    exactMatch = true;
                }
            }
        }
        if (failCase) {
            if (found) {
                anzBadCases++;
                failedCases.put(testCase, "Step found for fail case!");
            } else {
                anzGoodCases++;
            }
        } else {
            if (!found) {
                anzBadCases++;
                failedCases.put(testCase, "No step found!");
            } else if (!good) {
                anzBadCases++;
            } else {
                anzGoodCases++;
            }
        }
    }

    private void addIgnoredTechnique(String technique) {
        int count = 1;
        if (ignoredTechniques.containsKey(technique)) {
            count = ignoredTechniques.get(technique);
            count++;
        }
        ignoredTechniques.put(technique, count);
        anzIgnoreCases++;
    }

    public static void main(String[] args) {
        RegressionTester tester = new RegressionTester();
//        boolean result = tester.test(":0100:3:.....4..9.49....2.172..9..5......8..3...7...6..5......4..5..698.9....7..6..39....::315 317 318:");
//        System.out.println("Result: " + result);
        tester.runTest("lib02.txt");
    }
}
