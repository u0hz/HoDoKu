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
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Es gelten die Definitionen aus dem Ultimate Fish Guide: http://www.sudoku.com/boards/viewtopic.php?t=4993
 *
 * Zusätze:
 *   - Ein Base-Candidate ist eine Potential Elimination, wenn er in mindestens zwei Cover-Units enthalten ist
 *   - Ein Basic-Fish ist Sashimi, wenn die Base-unit, die die Fins enthält, ohne Fins nur noch einen
 *     Kandidaten hat. -- stimmt nicht mehr!
 *
 * Kraken Fish:
 * 
 * In a finned fish either there is a fish or one of the fins is true. This gives the easiest way
 * to find a Kraken Fish (Type 1):
 * 
 *   - If a candidate that would be eliminated by the unfinned fish can be linked to all fins
 *     (fin set -> candidate not set) than that candidate can be eliminated
 *   - In a Type 1 KF the eliminated candidate is the same candidate as the fish candidate
 * 
 * The other way is a bit more complicated: In an unfinned fish in every cover set
 * exactly one of the base candidates has to be true. In a finned fish either this is true or
 * one of the fins is true. That leads to the second type of Kraken Fish (Type 2):
 * 
 *   - If chains can be found that link all base candidates of one cover set to a specific candidate (CEC)
 *     (base candidate set -> CEC candidate not set) than that candidate can be eliminated.
 *   - If the fish has fins, additional chains have to be found for every fin
 *   - In a Type 2 KF the deleted candidate can be an arbitrary candidate
 * 
 * Endo fins: Have to be treated like normal fins. In Type 2 KF nothing is changed
 * Cannibalism: In Type 1 KF they count as normal possible elimination. In Type 2 KF
 *   no chain from the cannibalistic candidate to the CEC has to be found.
 * 
 * Implementation:
 *   - Type 1: For every possible elimination look after a chain to all the fins
 *   - Type 2: For every intersection of a cover set with all base candidates look for
 *     possible eliminations; if they exist, try to link them to the fins
 * 
 * Caution: Since the Template optimization cannot be applied, KF search can be very slow!
 * 
 * @author zhobigbe
 */
public class FishSolver extends AbstractSolver {

    private static final SolutionType[] BASIC_TYPES = {SolutionType.X_WING, SolutionType.SWORDFISH, SolutionType.JELLYFISH, SolutionType.SQUIRMBAG, SolutionType.WHALE, SolutionType.LEVIATHAN};
    private static final SolutionType[] FINNED_BASIC_TYPES = {SolutionType.FINNED_X_WING, SolutionType.FINNED_SWORDFISH, SolutionType.FINNED_JELLYFISH, SolutionType.FINNED_SQUIRMBAG, SolutionType.FINNED_WHALE, SolutionType.FINNED_LEVIATHAN};
    private static final SolutionType[] SASHIMI_BASIC_TYPES = {SolutionType.SASHIMI_X_WING, SolutionType.SASHIMI_SWORDFISH, SolutionType.SASHIMI_JELLYFISH, SolutionType.SASHIMI_SQUIRMBAG, SolutionType.SASHIMI_WHALE, SolutionType.SASHIMI_LEVIATHAN};
    private static final SolutionType[] FRANKEN_TYPES = {SolutionType.FRANKEN_X_WING, SolutionType.FRANKEN_SWORDFISH, SolutionType.FRANKEN_JELLYFISH, SolutionType.FRANKEN_SQUIRMBAG, SolutionType.FRANKEN_WHALE, SolutionType.FRANKEN_LEVIATHAN};
    private static final SolutionType[] FINNED_FRANKEN_TYPES = {SolutionType.FINNED_FRANKEN_X_WING, SolutionType.FINNED_FRANKEN_SWORDFISH, SolutionType.FINNED_FRANKEN_JELLYFISH, SolutionType.FINNED_FRANKEN_SQUIRMBAG, SolutionType.FINNED_FRANKEN_WHALE, SolutionType.FINNED_FRANKEN_LEVIATHAN};
    private static final SolutionType[] MUTANT_TYPES = {SolutionType.MUTANT_X_WING, SolutionType.MUTANT_SWORDFISH, SolutionType.MUTANT_JELLYFISH, SolutionType.MUTANT_SQUIRMBAG, SolutionType.MUTANT_WHALE, SolutionType.MUTANT_LEVIATHAN};
    private static final SolutionType[] FINNED_MUTANT_TYPES = {SolutionType.FINNED_MUTANT_X_WING, SolutionType.FINNED_MUTANT_SWORDFISH, SolutionType.FINNED_MUTANT_JELLYFISH, SolutionType.FINNED_MUTANT_SQUIRMBAG, SolutionType.FINNED_MUTANT_WHALE, SolutionType.FINNED_MUTANT_LEVIATHAN};
    private static final int LINE_MASK = 0x1;
    private static final int COL_MASK = 0x2;
    private static final int BLOCK_MASK = 0x4;    
    
    private static final int UNDEFINED = -1;
    private static final int BASIC = 0;
    private static final int FRANKEN = 1;
    private static final int MUTANT = 2;
    
    // Array mit Sets, in jedem Set stehen für die entsprechende Unit alle Indexe,
    // an denen ein bestimmter Kandidat vorkommt
    private SudokuSet[] baseCandidates = null;
    private SudokuSet[] coverCandidates = null;    // Unit-Arrays für die aktuelle Suche
    private int[][] baseUnits = null;
    private int[][] coverUnits = null;
    private SudokuSet[] cInt = new SudokuSet[7]; // die Cover-Sets für den Vergleich
    private SudokuSet[] cInt1 = new SudokuSet[7]; // for calculating possible cannibalistic candidates
    private SudokuSet fins = new SudokuSet();
    private SudokuSet endoFins = new SudokuSet();
    private SudokuSet endoFinCheck = new SudokuSet(); // nur für Optimierung
    private int candidate;  // der Kandidat, für den gesucht wird
    private List<SolutionStep> steps; // gefundene Fische
    private int anzCheckBaseUnitsRecursive;
    private int anzCheckCoverUnitsRecursive;
    private SolutionStep globalStep = new SolutionStep(SolutionType.HIDDEN_SINGLE);
    private List<Candidate> candidatesToDelete = new ArrayList<Candidate>();
    private List<Candidate> cannibalistic = new ArrayList<Candidate>();
    private SudokuSet baseUnitsIncluded = new SudokuSet();
    private SudokuSet baseCandSet = null; // all base candidates for one cover set search
    private SudokuSet endoFinSet = null;  // all endo fins for one cover set search
    private SudokuSet cannibalisticSet = new SudokuSet(); // all base candidates that are in more than one cover set
    private SudokuSet possibleCoverUnits = new SudokuSet();  // Set mit allen Cover-Units, die für den aktuellen Versuch gültig sind
    private SudokuSet coverUnitsIncluded = new SudokuSet(); // Indexe aller aktuellen Cover-Units
    private SudokuSet coverCandSet = new SudokuSet(); // alle aktuellen Cover-Candidates
    private SudokuSet[] coverCandidatesMasks = new SudokuSet[8]; // 1 Maske pro Rekursionsebene: Kandidaten, die in coverCandSet neu hinzugefügt wurden
    private SudokuSet deleteCandSet = new SudokuSet(); // Set mit allen Kandidaten, die gelöscht werden können
    private SudokuSet finBuddies = new SudokuSet();    // Set mit allen Kandidaten, die von allen fins gesehen werden können (exkl. Base-Kandidaten)
    private SudokuSet checkSashimiSet = new SudokuSet(); // für Sashimi-Check
    private SudokuSet tmpSet = new SudokuSet(); // for various checks
    private SudokuSet tmpSet1 = new SudokuSet(); // for various checks
    private SudokuSet tmpSet2 = new SudokuSet(); // for various checks
    private boolean withoutFins; // true, wenn finnless Fische gesucht werden sollen
    private boolean withFins; // true, wenn Finned-Fische gesucht werden sollen
    private boolean withEndoFins; // Auch Fische mit EndoFins
    private boolean sashimi; // true, wenn Sashimi-Fische gesucht werden sollen (withFins muss ebenfalls true sein)
    private boolean kraken;  // true, wenn nach Kraken Fish gesucht werden soll
    private boolean searchAll; // true, if all fishes should be found (searches for inverse in Kraken Fish)
    private int fishType = UNDEFINED; // which type of fish should be searched for?
    private int minSize;      // minimale Anzahl Base-Units für Suche
    private int maxSize;      // maximale Anzahl Base-Units für Suche
    private int anzBaseUnits; // Anzahl der Base-Units für eine Cover-Set-Suche
    private int maxBaseCombinations = 0; // Anzahl möglicher Kombinationen aus base-units
    private FindAllStepsProgressDialog dlg = null;
    private SudokuSet templateSet = new SudokuSet();
    private int baseGesamt = 0;
    private int baseShowGesamt = 0;
    private int coverGesamt = 0;
    private int versucheFisch = 0;
    private int versucheFins = 0;
    private int[] anzFins = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private TablingSolver tablingSolver = null; // for kraken fish search
    private SortedMap<String, Integer> deletesMap = new TreeMap<String, Integer>();  // alle bisher gefundenen Chains: Eliminierungen und Index in steps

    /** Creates a new instance of FishSolver */
    public FishSolver(SudokuSolver solver) {
        super(solver);
        for (int i = 0; i < coverCandidatesMasks.length; i++) {
            coverCandidatesMasks[i] = new SudokuSet();
        }
        for (int i = 0; i < cInt1.length; i++) {
            cInt1[i] = new SudokuSet();
        }
    }

    @Override
    protected SolutionStep getStep(SolutionType type) {
        SolutionStep result = null;
        int size = 2;
        switch (type) {
            case LEVIATHAN:
                size++;
            case WHALE:
                size++;
            case SQUIRMBAG:
                size++;
            case JELLYFISH:
                size++;
            case SWORDFISH:
                size++;
            case X_WING:
                searchAll = false;
                result = getAnyFish(size, lineUnits, colUnits, true, false, false, false, BASIC);
                break;
            case FINNED_LEVIATHAN:
                size++;
            case FINNED_WHALE:
                size++;
            case FINNED_SQUIRMBAG:
                size++;
            case FINNED_JELLYFISH:
                size++;
            case FINNED_SWORDFISH:
                size++;
            case FINNED_X_WING:
                searchAll = false;
                result = getAnyFish(size, lineUnits, colUnits, false, true, false, false, BASIC);
                break;
            case SASHIMI_LEVIATHAN:
                size++;
            case SASHIMI_WHALE:
                size++;
            case SASHIMI_SQUIRMBAG:
                size++;
            case SASHIMI_JELLYFISH:
                size++;
            case SASHIMI_SWORDFISH:
                size++;
            case SASHIMI_X_WING:
                searchAll = false;
                result = getAnyFish(size, lineUnits, colUnits, false, true, true, false, BASIC);
                break;
            case FRANKEN_LEVIATHAN:
                size++;
            case FRANKEN_WHALE:
                size++;
            case FRANKEN_SQUIRMBAG:
                size++;
            case FRANKEN_JELLYFISH:
                size++;
            case FRANKEN_SWORDFISH:
                size++;
            case FRANKEN_X_WING:
                searchAll = false;
                result = getAnyFish(size, lineBlockUnits, colBlockUnits, true, false, false, true, FRANKEN);
                break;
            case FINNED_FRANKEN_LEVIATHAN:
                size++;
            case FINNED_FRANKEN_WHALE:
                size++;
            case FINNED_FRANKEN_SQUIRMBAG:
                size++;
            case FINNED_FRANKEN_JELLYFISH:
                size++;
            case FINNED_FRANKEN_SWORDFISH:
                size++;
            case FINNED_FRANKEN_X_WING:
                searchAll = false;
                result = getAnyFish(size, lineBlockUnits, colBlockUnits, false, true, false, true, FRANKEN);
                break;
            case MUTANT_LEVIATHAN:
                size++;
            case MUTANT_WHALE:
                size++;
            case MUTANT_SQUIRMBAG:
                size++;
            case MUTANT_JELLYFISH:
                size++;
            case MUTANT_SWORDFISH:
                size++;
            case MUTANT_X_WING:
                result = getAnyFish(size, allUnits, allUnits, true, false, false, true, MUTANT);
                break;
            case FINNED_MUTANT_LEVIATHAN:
                size++;
            case FINNED_MUTANT_WHALE:
                size++;
            case FINNED_MUTANT_SQUIRMBAG:
                size++;
            case FINNED_MUTANT_JELLYFISH:
                size++;
            case FINNED_MUTANT_SWORDFISH:
                size++;
            case FINNED_MUTANT_X_WING:
                result = getAnyFish(size, allUnits, allUnits, false, true, false, true, MUTANT);
                break;
            case KRAKEN_FISH:
            case KRAKEN_FISH_TYPE_1:
            case KRAKEN_FISH_TYPE_2:
                result = getKrakenFish();
                break;
        }
        return result;
    }

    @Override
    protected boolean doStep(SolutionStep step) {
        boolean handled = true;

        switch (step.getType()) {
            case X_WING:
            case SWORDFISH:
            case JELLYFISH:
            case SQUIRMBAG:
            case WHALE:
            case LEVIATHAN:
            case FINNED_X_WING:
            case FINNED_SWORDFISH:
            case FINNED_JELLYFISH:
            case FINNED_SQUIRMBAG:
            case FINNED_WHALE:
            case FINNED_LEVIATHAN:
            case SASHIMI_X_WING:
            case SASHIMI_SWORDFISH:
            case SASHIMI_JELLYFISH:
            case SASHIMI_SQUIRMBAG:
            case SASHIMI_WHALE:
            case SASHIMI_LEVIATHAN:
            case FRANKEN_X_WING:
            case FRANKEN_SWORDFISH:
            case FRANKEN_JELLYFISH:
            case FRANKEN_SQUIRMBAG:
            case FRANKEN_WHALE:
            case FRANKEN_LEVIATHAN:
            case FINNED_FRANKEN_X_WING:
            case FINNED_FRANKEN_SWORDFISH:
            case FINNED_FRANKEN_JELLYFISH:
            case FINNED_FRANKEN_SQUIRMBAG:
            case FINNED_FRANKEN_WHALE:
            case FINNED_FRANKEN_LEVIATHAN:
            case MUTANT_X_WING:
            case MUTANT_SWORDFISH:
            case MUTANT_JELLYFISH:
            case MUTANT_SQUIRMBAG:
            case MUTANT_WHALE:
            case MUTANT_LEVIATHAN:
            case FINNED_MUTANT_X_WING:
            case FINNED_MUTANT_SWORDFISH:
            case FINNED_MUTANT_JELLYFISH:
            case FINNED_MUTANT_SQUIRMBAG:
            case FINNED_MUTANT_WHALE:
            case FINNED_MUTANT_LEVIATHAN:
            case KRAKEN_FISH:
            case KRAKEN_FISH_TYPE_1:
            case KRAKEN_FISH_TYPE_2:
                for (Candidate cand : step.getCandidatesToDelete()) {
                    sudoku.delCandidate(cand.getIndex(), candType, cand.getValue());
                }
                break;
            default:
                handled = false;
        }
        return handled;
    }

    public List<SolutionStep> getAllFishes(Sudoku sudoku, int minSize, int maxSize,
            int maxFins, int maxEndoFins, FindAllStepsProgressDialog dlg, int forCandidate, int type) {
        this.dlg = dlg;
        Sudoku save = getSudoku();
        setSudoku(sudoku);
        int oldMaxFins = Options.getInstance().maxFins;
        int oldEndoFins = Options.getInstance().maxEndoFins;
        Options.getInstance().maxFins = maxFins;
        Options.getInstance().maxEndoFins = maxEndoFins;
        List<SolutionStep> oldSteps = steps;
        steps = new ArrayList<SolutionStep>();
        kraken = false;
        searchAll = true;
        fishType = UNDEFINED;
        int[][] units1 = lineUnits;
        int[][] units2 = colUnits;
        if (type == 1) {
            units1 = lineBlockUnits;
            units2 = colBlockUnits;
        }
        if (type == 2) {
            units1 = allUnits;
            units2 = allUnits;
        }
        long millis1 = System.currentTimeMillis();
        // Templates initialisieren (für Optimierung)
        initCandTemplates();
        for (int i = 1; i <= 9; i++) {
            if (forCandidate != -1 && forCandidate != i) {
                // not now
                continue;
            }
            Logger.getLogger(getClass().getName()).log(Level.FINE, "getAllFishes() for Candidate " + i);
            long millis = System.currentTimeMillis();
            baseGesamt = 0;
            baseShowGesamt = 0;
            getFishes(i, minSize, maxSize, units1, units2, true, true, false, true);
            if (type != 2) {
                getFishes(i, minSize, maxSize, units2, units1, true, true, false, true);
            }
            millis = System.currentTimeMillis() - millis;
            Logger.getLogger(getClass().getName()).log(Level.FINE, "getAllFishes(" + i + "): " + millis + "ms");
            Logger.getLogger(getClass().getName()).log(Level.FINE, steps.size() + " fishes found!");
        }
        millis1 = System.currentTimeMillis() - millis1;
        Logger.getLogger(getClass().getName()).log(Level.FINE, "getAllFishes() gesamt: " + millis1 + "ms");
        Logger.getLogger(getClass().getName()).log(Level.FINER, "baseAnz: " + baseGesamt + "(" + baseShowGesamt + "), coverAnz: " + coverGesamt + ", Fische: " + versucheFisch + ", Fins: " + versucheFins);
        StringBuffer tmpBuffer = new StringBuffer();
        for (int i = 0; i < anzFins.length; i++) {
            tmpBuffer.append(" " + anzFins[i]);
        }
        Logger.getLogger(getClass().getName()).log(Level.FINER, tmpBuffer.toString());
        List<SolutionStep> result = steps;
        if (result != null) {
            findSiameseFish(result);
            Collections.sort(result);
        }
        steps = oldSteps;
        if (save != null) {
            setSudoku(save);
        }
        Options.getInstance().maxFins = oldMaxFins;
        Options.getInstance().maxEndoFins = oldEndoFins;
        this.dlg = null;
        return result;
    }

    private SolutionStep getAnyFish(int size, int[][] units1, int[][] units2, boolean withoutFins,
            boolean withFins, boolean sashimi, boolean withEndoFins, int fishType) {
        anzCheckBaseUnitsRecursive = 0;
        anzCheckCoverUnitsRecursive = 0;
        baseGesamt = 0;
        baseShowGesamt = 0;
        steps = new ArrayList<SolutionStep>();
        kraken = false;
        this.fishType = fishType;
        // Templates initialisieren (für Optimierung)
        initCandTemplates();
        for (int i = 1; i <= 9; i++) {
            getFishes(i, size, size, units1, units2, withoutFins, withFins, sashimi, withEndoFins);
            // CAUTION: There could be a "better" fish in the columns
//            if (!searchAll && steps.size() > 0) {
//                break;
//            }
            if (units1 != allUnits && units2 != allUnits) {
                getFishes(i, size, size, units2, units1, withoutFins, withFins, sashimi, withEndoFins);
                if (steps.size() > 0) {
                    break;
                }
            }
        }
        if (steps.size() > 0) {
            findSiameseFish(steps);
            Collections.sort(steps);
            return steps.get(0);
        }
        return null;
    }

    public List<SolutionStep> getAllKrakenFishes(Sudoku sudoku, int minSize, int maxSize,
            int maxFins, int maxEndoFins, FindAllStepsProgressDialog dlg, int forCandidate, int type) {
        //System.out.println("getAllKrakenFishes: " + minSize + "/" + maxSize + "/" + forCandidate);
        this.dlg = dlg;
        Sudoku save = getSudoku();
        setSudoku(sudoku);
        boolean oldCheckTemplates = Options.getInstance().checkTemplates;
        Options.getInstance().checkTemplates = false;
        int oldMaxFins = Options.getInstance().maxFins;
        int oldEndoFins = Options.getInstance().maxEndoFins;
        Options.getInstance().maxFins = maxFins;
        Options.getInstance().maxEndoFins = maxEndoFins;
        List<SolutionStep> oldSteps = steps;
        steps = new ArrayList<SolutionStep>();
        kraken = true;
        searchAll = true;
        fishType = UNDEFINED;
        int[][] units1 = lineUnits;
        int[][] units2 = colUnits;
        if (type == 1) {
            units1 = lineBlockUnits;
            units2 = colBlockUnits;
        }
        if (type == 2) {
            units1 = allUnits;
            units2 = allUnits;
        }
        getTablingSolver();
        tablingSolver.setSudoku(sudoku);
        tablingSolver.initForKrakenSearch();
        long millis1 = System.currentTimeMillis();
        for (int i = 1; i <= 9; i++) {
            if (forCandidate != -1 && forCandidate != i) {
                // not now
                continue;
            }
            Logger.getLogger(getClass().getName()).log(Level.FINE, "getAllKrakenFishes() for Candidate " + i);
            long millis = System.currentTimeMillis();
            baseGesamt = 0;
            baseShowGesamt = 0;
            //getFishes(i, minSize, maxSize, lineUnits, colUnits, true, true, false, true);
            getFishes(i, minSize, maxSize, units1, units2, true, true, false, true);
            if (type != 2) {
                getFishes(i, minSize, maxSize, units2, units1, true, true, false, true);
            }
            millis = System.currentTimeMillis() - millis;
            Logger.getLogger(getClass().getName()).log(Level.FINE, "getAllKrakenFishes(" + i + "): " + millis + "ms");
            Logger.getLogger(getClass().getName()).log(Level.FINE, steps.size() + " kraken fishes found!");
        }
        millis1 = System.currentTimeMillis() - millis1;
        Logger.getLogger(getClass().getName()).log(Level.FINE, "getAllKrakenFishes() gesamt: " + millis1 + "ms");
        Logger.getLogger(getClass().getName()).log(Level.FINER, "baseAnz: " + baseGesamt + "(" + baseShowGesamt + "), coverAnz: " + coverGesamt + ", Fische: " + versucheFisch + ", Fins: " + versucheFins);
        StringBuffer tmpBuffer = new StringBuffer();
        for (int i = 0; i < anzFins.length; i++) {
            tmpBuffer.append(" " + anzFins[i]);
        }
        Logger.getLogger(getClass().getName()).log(Level.FINER, tmpBuffer.toString());
        List<SolutionStep> result = steps;
        if (result != null) {
            //findSiameseFish(result);
            Collections.sort(result);
        }
        steps = oldSteps;
        if (save != null) {
            setSudoku(save);
        }
        Options.getInstance().checkTemplates = oldCheckTemplates;
        Options.getInstance().maxFins = oldMaxFins;
        Options.getInstance().maxEndoFins = oldEndoFins;
        kraken = false;
        this.dlg = null;
        //System.out.println("   " + result.size() + " steps!");
        return result;
    }

    private SolutionStep getKrakenFish() {
        anzCheckBaseUnitsRecursive = 0;
        anzCheckCoverUnitsRecursive = 0;
        baseGesamt = 0;
        baseShowGesamt = 0;
        steps = new ArrayList<SolutionStep>();
        boolean oldCheckTemplates = Options.getInstance().checkTemplates;
        Options.getInstance().checkTemplates = false;
        int oldMaxFins = Options.getInstance().maxFins;
        int oldEndoFins = Options.getInstance().maxEndoFins;
        Options.getInstance().maxFins = Options.getInstance().maxKrakenFins;
        Options.getInstance().maxEndoFins = Options.getInstance().maxKrakenEndoFins;
        kraken = true;
        fishType = UNDEFINED;
        getTablingSolver();
        tablingSolver.setSudoku(sudoku);
        tablingSolver.initForKrakenSearch();
        // Endo fins are only searched if the fish type is other than basic and if the max endo fin size > 0
        withEndoFins = Options.getInstance().maxKrakenEndoFins != 0 && Options.getInstance().krakenMaxFishType > 0;
        int[][] units1 = null;
        int[][] units2 = null;
        switch (Options.getInstance().krakenMaxFishType) {
            case 1:
                units1 = lineBlockUnits;
                units2 = colBlockUnits;
                break;
            case 2:
                units1 = allUnits;
                units2 = allUnits;
                break;
            default:
                units1 = lineUnits;
                units2 = colUnits;
                break;
        }
        int size = Options.getInstance().krakenMaxFishSize;
        for (int i = 1; i <= 9; i++) {
            getFishes(i, 2, size, units1, units2, false, true, true, withEndoFins);
//            if (steps.size() > 0) {
//                break;
//            }
            if (units1 != allUnits && units2 != allUnits) {
                getFishes(i, 2, size, units2, units1, false, true, true, withEndoFins);
                if (steps.size() > 0) {
                    break;
                }
            }
        }
        kraken = false;
        Options.getInstance().checkTemplates = oldCheckTemplates;
        Options.getInstance().maxFins = oldMaxFins;
        Options.getInstance().maxEndoFins = oldEndoFins;
        if (steps.size() > 0) {
            findSiameseFish(steps);
            Collections.sort(steps);
            return steps.get(0);
        }
        return null;
    }

    /**
     * Ermittelt alle Fische mit dem übergebenen Set an Base- und Cover-Units und
     * der übergebenen Anzahl an Units im Base-Set.<br />
     *
     * Um die Anzahl der Parameter in den Methodenaufrufen kleiner zu halten, werden
     * <code>baseUnits</code> und <code>coverUnits</code> in Eigenschaften gespeichert.
     *
     * @param candidate Nummer des Kandidaten, für den die Fische gesucht werden sollen
     * @param minSize Minimale Anzahl an Base-Units im Base-Set (es werden keine kleineren Fische gefunden)
     * @param maxSize Maximale Anzahl an Base-Units im Base-Set (es werden auch kleinere Fische gefunden)
     * @param baseUnits Alle möglichen Base-Units (jeweils ein sortiertes Array mit allen Indexen dieser Unit)
     * @param coverUnits Alle möglichen Cover-Units (jeweils ein sortiertes Array mit allen Indexen dieser Unit)
     * @param withoutFins <code>true</code>, wenn Finnless-Fische gesucht werden sollen
     * @param withFins <code>true</code>, wenn Finned-Fische gesucht werden sollen
     * @param sashimi <code>true</code>, wenn Sashimi-Fische gesucht werden sollen (<code>withFins</code> muss ebenfalls true sein).
     * @return liste mit gefundenen Fischen (Liste kann leer sein, ist aber nie <code>null</code>).
     */
    private List<SolutionStep> getFishes(int candidate, int minSize, int maxSize, int[][] baseUnits, int[][] coverUnits,
            boolean withoutFins, boolean withFins, boolean sashimi, boolean withEndoFins) {
        // die ganze Rechnung braucht nur gemacht werden, wenn es überhaupt ein Ergebnis geben kann!
        if (Options.getInstance().isCheckTemplates()) {
            templateSet.set(delCandTemplates[candidate]);
            templateSet.and(sudoku.getAllowedPositions()[candidate]);
            if (templateSet.isEmpty()) {
                // vergebliche Liebesmüh...
                return steps;
            }
        }
        for (int i = 0; i < cInt.length; i++) {
            cInt[i] = SudokuSet.EMPTY_SET;
        }

        deletesMap.clear();
        this.baseUnits = baseUnits;
        this.coverUnits = coverUnits;
        this.candidate = candidate;
        this.withoutFins = withoutFins;
        this.withFins = withFins;
        this.withEndoFins = withEndoFins;
        this.sashimi = sashimi;
        this.minSize = minSize;
        this.maxSize = maxSize;
        // fills baseCandidates and coverCandidates from baseUnits/coverUnits
        initForCandidat(candidate, baseUnits, coverUnits);

        // Set mit den Indexen der im aktuellen Versuch inkludierten Base-Units (Index in baseUnits)
        baseUnitsIncluded.clear();
        for (int i = 0; i < baseUnits.length; i++) {
            if (!baseCandidates[i].isEmpty()) {
                // aktuelle Base-Unit enthält mindestens einen Kandidaten -> versuchen
                baseUnitsIncluded.add(i);
                endoFins.clear();
                getFishesRecursive(1, baseCandidates[i], i + 1, endoFins);
                baseUnitsIncluded.remove(i);
            }
        }

        return steps;
    }

    /**
     * Macht die eigentliche Arbeit: Fügt sukzessive Base-units hinzu, bis alle möglichen Kombinationen
     * bearbeitet wurden. Für jede Kombination wird ein Set mit allen von der aktuellen Kombination an
     * Base-Units abgedeckten Kandidaten erstellt, dann werden alle in Betracht kommenden Cover-Units
     * ermittelt (nur Cover-Units, die einen der aktuellen Kandidaten enthalten und mit einer der
     * aktuellen Base-Units eine nicht leere Schnittmenge haben) und alle Kombinationen werden durchprobiert.
     *
     * Aus Performancegründen Eigenschaften der Klasse:
     *   baseUnitsIncluded: Set mit den Indexen der aktuell verwendeten Base-Units.
     * 
     * @param aktSize Derzeitige Anzahl Base-Units
     * @param fishBaseCandSet Alle Kandidaten der bisher aufgenommenen Base-Units.
     * @param startIndex Index in <code>baseUnits</code>, ab dem neue Units hinzugefügt werden sollen.
     * @param fishEndoFinSet Set mit allen Endo-Fins der aktuellen BaseUnits
     */
    private void getFishesRecursive(int aktSize, SudokuSet fishBaseCandSet, int startIndex,
            SudokuSet fishEndoFinSet) {
        anzCheckBaseUnitsRecursive++;
        aktSize++;
        if (aktSize > maxSize) {
            // Abbruch der Rekursion -> bereits maximale Anzahl Base-Units im Set
            return;
        }
        // Alle noch übrigen Base-Units durchgehen, checken und in die nächste Ebene
        for (int i = startIndex; i < baseUnits.length; i++) {
            // wenn die aktuelle Unit keinen Kandidaten enthält, wird sie übersprungen
            if (baseCandidates[i].isEmpty()) {
                continue;
            }
            baseGesamt++; // counter for progress bar
            baseShowGesamt++; // counter for progress bar
            if (dlg != null && baseShowGesamt % 100 == 0) {
                dlg.updateFishProgressBar(baseShowGesamt);
            }
            // if the new unit has common candidates with the current base set, those candidates
            // have to be treated as fin cells (endo fins).
            endoFinCheck.clear();
            if (fishBaseCandSet.intersects(baseCandidates[i], endoFinCheck)) {
                // intersects() == true means: there are endoFins!
                if (!withFins || !withEndoFins || (fishEndoFinSet.size() + endoFinCheck.size()) > Options.getInstance().maxEndoFins) {
                    // every invalid combination eliminates a lot of possibilities:
                    // (all non-zero baseUnits greater than i) over (maxSize - aktSize)
                    if (dlg != null) {
                        int tmpBaseAnz = 0;
                        for (int j = i + 1; j < baseUnits.length; j++) {
                            if (!baseCandidates[j].isEmpty()) {
                                tmpBaseAnz++;
                            }
                        }
                        //baseShowGesamt += combinations(tmpBaseAnz, maxSize - aktSize);
                        for (int j = 1; j <= maxSize - aktSize; j++) {
                            baseShowGesamt += combinations(tmpBaseAnz, j);
                        }
                    }
                    continue;
                }
            }
            // calculate union of existing sets with new base unit (make copy - recursion!)
            // and store the new base unit in baseUnitsIncluded
            baseCandSet = fishBaseCandSet.clone();
            endoFinSet = fishEndoFinSet.clone();
            baseCandSet.or(baseCandidates[i]);
            endoFinSet.or(endoFinCheck);
            baseUnitsIncluded.add(i);
            // check if this set of endo fins can even give eliminations (pays off because
            // the whole cover set check can be skipped)
            finBuddies.setAll();
            if (Options.getInstance().isCheckTemplates() && ! endoFinSet.isEmpty()) {
                // all cells that can see all the endo fins
                for (int j = 0; j < endoFinSet.size(); j++) {
                    finBuddies.and(Sudoku.buddies[endoFinSet.get(j)]);
                }
                // now only those cells that have the candidate we are searching for
                finBuddies.andNot(sudoku.getAllowedPositions()[candidate]);
                // and from those only the ones that can actually be eliminated
                finBuddies.and(delCandTemplates[candidate]);
            }
            // if aktSize lies between minSize and maxSize -> check cover units
            if (aktSize >= minSize && aktSize <= maxSize && !finBuddies.isEmpty()) {
                // check for fish: build a set with all cover units that contain at least on 
                // candidate from aktBaseCandSet and are not identical to a base set
                possibleCoverUnits = getPossibleCoverUnits(baseCandSet, baseUnitsIncluded);
                coverUnitsIncluded.clear();
                for (int j = 0; j < possibleCoverUnits.size(); j++) {
                    coverUnitsIncluded.add(possibleCoverUnits.get(j));
                    // all candidates in the current cover set
                    coverCandSet.set(coverCandidates[possibleCoverUnits.get(j)]);
                    cInt[0] = coverCandidates[possibleCoverUnits.get(j)];
                    // we need the current number of base units, the cover sets start with 1
                    anzBaseUnits = aktSize;
                    checkCoverUnitsRecursive(1, j + 1);
                    coverUnitsIncluded.remove(possibleCoverUnits.get(j));
                    cInt[0] = SudokuSet.EMPTY_SET;
                }
            }
            // one more base unit
            getFishesRecursive(aktSize, baseCandSet, i + 1, endoFinSet);
            baseUnitsIncluded.remove(i);
        }
    }

    /**
     * Bildet rekursiv alle möglichen Kombinationen aus möglichen Cover-Sets. Wenn die Anzahl der
     * aktuell betrachteten Cover-Sets gleich ist wie die Anzahl der aktuell
     * enthaltenen Base-Sets, kann geprüft werden, ob ein Fisch vorliegt.
     *
     * Aus Performancegründen sind folgende Variablen Eigenschaft der Klasse:
     *   coverCandSet: Die im aktuellen Versuch enthaltenen Cover-Candidaten
     *   anzBaseUnits: Anzahl der Base-Units in diesem Versuch.
     *   baseCandSet: Alle Kandidaten der aktuellen Base-Units.
     *   baseUnitsIncluded: Alle Indexe der Base-Units, die im derzeitigen Versuch enthalten sind.
     *   coverUnitsIncluded: Alle Indexe der Cover-Units, die im derzeitigen Versuch enthalten sind.
     *   possibleCoverUnits: Set mit allen Cover-Units, die prinzipiell in Frage kommen (Indexe in coverUnits)
     *   endoFinSet: Set mit allen Endo-Fins der aktuellen Base-Units
     *
     * @param aktSize Anzahl der Cover-Units in diesem Versuch.
     * @param startIndex Index in possibleCoverUnits, ab dem neue Units hinzugefügt werden sollen.
     */
    private void checkCoverUnitsRecursive(int aktSize, int startIndex) {
        anzCheckCoverUnitsRecursive++;
        aktSize++;
        if (aktSize > anzBaseUnits) {
            return;
        }
        for (int i = startIndex; i < possibleCoverUnits.size(); i++) {
            coverUnitsIncluded.add(possibleCoverUnits.get(i));
            cInt[aktSize - 1] = coverCandidates[possibleCoverUnits.get(i)];
            // Maske bilden, die nur die Kandidaten enthält, die in diesem Versuch neu dazukommen
            coverCandidatesMasks[aktSize].set(coverCandidates[possibleCoverUnits.get(i)]);
            coverCandidatesMasks[aktSize].andNot(coverCandSet);
            // und wirklich dazufügen
            coverCandSet.or(coverCandidatesMasks[aktSize]);
            coverGesamt++;
            if (aktSize == anzBaseUnits) {
                if (!endoFinSet.isEmpty() && !withFins) { // should be redundant...
                    continue;
                }
                versucheFisch++;
                // jetzt kann es ein Fisch sein (mit oder ohne Flossen) -> prüfen
                fins.clear();
                boolean isCovered = baseCandSet.isCovered(coverCandSet, fins);
                fins.or(endoFinSet);
                // for kraken search withoutFins must be false!
                if (isCovered && withoutFins && fins.isEmpty()) {
                    anzFins[0]++;
                    // ********* FINNLESS FISCH ****************
                    // prüfen, ob Kandidaten entfernt werden können
                    // für jeden Kandidaten feststellen, ob er im Base-Set enthalten ist. Wenn nicht,
                    // kann er gelöscht werden; wenn er in einem Base-Set ist, aber in mehr als einer
                    // Cover-Unit vorkommt, kann er ebenfalls gelöscht werden
                    candidatesToDelete.clear();
                    cannibalistic.clear();

                    // Cover-Kandidaten, die nicht im Base-Set vorkommen
                    deleteCandSet.set(coverCandSet);
                    deleteCandSet.andNot(baseCandSet);
                    if (!deleteCandSet.isEmpty()) {
                        for (int j = 0; j < deleteCandSet.size(); j++) {
                            // kann gelöscht werden
                            addCandidateToDelete(deleteCandSet.get(j), candidate);
                        }
                    }

                    // cannibalism
                    calculateCannibalisticSet();
                    if (!cannibalisticSet.isEmpty()) {
                        for (int j = 0; j < cannibalisticSet.size(); j++) {
                            // kann gelöscht werden
                            addCandidateToDelete(cannibalisticSet.get(j), candidate);
                            addCannibalistic(cannibalisticSet.get(j), candidate);
                        }
                    }
//                    // Kannibalismus (optimieren?)
//                    for (int j = 0; j < baseCandSet.size(); j++) {
//                        int aktCand = baseCandSet.get(j);
//                        int anz = 0;
//                        for (int k = 0; k < cInt.length; k++) {
//                            if (cInt[k].contains(aktCand)) {
//                                anz++;
//                            }
//                        }
//                        if (anz >= 2) {
//                            // kann gelöscht werden
//                            addCandidateToDelete(aktCand, candidate);
//                            addCannibalistic(aktCand, candidate);
//                        }
//                    }

                    // Wenn es zu löschende Kandidaten gibt, wird der SolutionStep in steps aufgenommen
                    if (candidatesToDelete.size() > 0) {
                        initSolutionStep(globalStep, aktSize, candidate, baseCandSet,
                                baseUnitsIncluded, coverUnitsIncluded, false, false, fins, endoFinSet,
                                candidatesToDelete, cannibalistic, deleteCandSet, cannibalisticSet);
                        try {
                            addFishStep();
                            //steps.add((SolutionStep) globalStep.clone());
                        } catch (CloneNotSupportedException ex) {
                            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                        }
                    }
                } else if (withFins && fins.size() > 0 && fins.size() <= Options.getInstance().maxFins) {
                    /*********** MÖGLICHER FINNED/SASHIMI-FISCH **********/
                    versucheFins++;
                    anzFins[fins.size()]++;
                    // Prüfen: Es können nur Kandidaten gelöscht werden, die alle Fins sehen
                    // für jeden Kandidaten feststellen, ob er alle Fins sieht. Wenn ja, kann er
                    // gelöscht werden (außer er gehört zum BaseSet)
                    candidatesToDelete.clear();
                    cannibalistic.clear();

                    // Alle Kandidaten ermitteln, die alle fins sehen können
                    finBuddies.set(Sudoku.buddies[fins.get(0)]);
                    for (int j = 1; j < fins.size(); j++) {
                        finBuddies.and(Sudoku.buddies[fins.get(j)]);
                    }
                    // weiter geht's nur, wenn es überhaupt Kandidaten geben kann, die gelöscht werden können
                    if (!finBuddies.isEmpty()) {
                        // es könnte was gelöscht werden können :-)

                        // Cover-Kandidaten, die nicht im Base-Set vorkommen, aber alle fins sehen
                        deleteCandSet.set(coverCandSet);
                        deleteCandSet.andNot(baseCandSet);
                        deleteCandSet.and(finBuddies);
                        if (!deleteCandSet.isEmpty()) {
                            for (int j = 0; j < deleteCandSet.size(); j++) {
                                // kann gelöscht werden
                                addCandidateToDelete(deleteCandSet.get(j), candidate);
                            }
                        }

                        // jetzt noch Kannibalismus: es kommen natürlich auch nur Base-Kandidaten in Frage,
                        // die alle Fins sehen können
                        calculateCannibalisticSet();
                        deleteCandSet.set(cannibalisticSet);
                        deleteCandSet.and(finBuddies);
                        if (! deleteCandSet.isEmpty()) {
                            for (int j = 0; j < deleteCandSet.size(); j++) {
                                // kann gelöscht werden
                                addCandidateToDelete(deleteCandSet.get(j), candidate);
                                addCannibalistic(deleteCandSet.get(j), candidate);
                            }
                        }
//                        deleteCandSet.set(baseCandSet);
//                        for (int j = 0; j < deleteCandSet.size(); j++) {
//                            int aktCand = deleteCandSet.get(j);
//                            int anz = 0;
//                            for (int k = 0; k < cInt.length; k++) {
//                                if (cInt[k].contains(aktCand)) {
//                                    anz++;
//                                }
//                            }
//                            if (anz >= 2) {
//                                // kann gelöscht werden
//                                addCandidateToDelete(aktCand, candidate);
//                                addCannibalistic(aktCand, candidate);
//                            }
//                        }
                    }
                    // Wenn es zu löschende Kandidaten gibt, wird der SolutionStep in steps aufgenommen
                    if (! kraken && candidatesToDelete.size() > 0) {
                        boolean isSashimi = checkSashimi(baseCandSet, baseUnitsIncluded, coverUnitsIncluded);
                        if (!sashimi || isSashimi) {
                            // recalculate possible eliminations
                            deleteCandSet.set(coverCandSet);
                            deleteCandSet.andNot(baseCandSet);
                            initSolutionStep(globalStep, aktSize, candidate, baseCandSet,
                                    baseUnitsIncluded, coverUnitsIncluded, withFins, isSashimi, fins, endoFinSet,
                                    candidatesToDelete, cannibalistic, deleteCandSet, cannibalisticSet);
                            try {
                                addFishStep();
                                //steps.add((SolutionStep) globalStep.clone());
                            } catch (CloneNotSupportedException ex) {
                                Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                            }
                        }
                    } else if (kraken && candidatesToDelete.isEmpty() && ! finBuddies.isEmpty()) {
                        // search for possible kraken fishes
                        // Type 1: We have fins but nothing to delete -> check all
                        // cover candidates that are not base candidates wether they can be linked
                        // to every fin (if fin set -> cover candidate is not set)
                        // only one candidate at a time!
                        // cannibalistic candidates can be deleted to
                        deleteCandSet.set(coverCandSet);
                        deleteCandSet.andNot(baseCandSet);
                        deleteCandSet.or(cannibalisticSet);
                        if (! deleteCandSet.isEmpty()) {
                            //System.out.println("Possible Kraken: " + baseUnitsIncluded + "/" + coverUnitsIncluded);
                            for (int j = 0; j < deleteCandSet.size(); j++) {
                                int endIndex = deleteCandSet.get(j);
                                if (tablingSolver.checkKrakenTypeOne(fins, endIndex, candidate)) {
                                    // kraken fish found -> add!
                                    if (cannibalisticSet.contains(endIndex)) {
                                        addCannibalistic(endIndex, candidate);
                                    }
                                    boolean isSashimi = checkSashimi(baseCandSet, baseUnitsIncluded, coverUnitsIncluded);
                                    initSolutionStep(globalStep, aktSize, candidate, baseCandSet,
                                            baseUnitsIncluded, coverUnitsIncluded, withFins, isSashimi, fins, endoFinSet,
                                            candidatesToDelete, cannibalistic, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET);
                                    globalStep.setSubType(globalStep.getType());
                                    globalStep.setType(SolutionType.KRAKEN_FISH_TYPE_1);
                                    globalStep.addCandidateToDelete(deleteCandSet.get(j), candidate);
                                    try {
                                        // now the chains
                                        for (int k = 0; k < fins.size(); k++) {
                                            Chain tmpChain = tablingSolver.getKrakenChain(fins.get(k), candidate, endIndex, candidate);
                                            globalStep.addChain((Chain) tmpChain.clone());
                                        }
                                        tablingSolver.adjustChains(globalStep);
                                        addKrakenStep();
                                    } catch (CloneNotSupportedException ex) {
                                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                                    }
                                }
                            }
                        }
                        // Type 2: For every cover set find chains from all base candidates and all fins to
                        // a single candidate
                        // a check is only necessary if the cover unit doesnt only contain base candidates
                        // for cannibalistic candidates no chain is needed
                        cannibalistic.clear();
                        for (int coverIndex = 0; coverIndex < aktSize; coverIndex++) {
                            // get all base candidates for that cover unit that are not cannibalistic
                            tmpSet.set(cInt[coverIndex]);
                            tmpSet.and(baseCandSet);
                            tmpSet.andNot(cannibalisticSet);
                            if (cInt[coverIndex].equals(tmpSet)) {
                                // would be a normal Forcing Chain -> skip it
                                continue;
                            }
                            // now add the fins and check all candidates
                            tmpSet.or(fins);
                            for (int endCandidate = 1; endCandidate <= 9; endCandidate++) {
                                if (tablingSolver.checkKrakenTypeTwo(tmpSet, tmpSet1, candidate, endCandidate)) {
                                    // kraken fishes found -> add!
                                    for (int j = 0; j < tmpSet1.size(); j++) {
                                        int endIndex = tmpSet1.get(j);
                                        boolean isSashimi = checkSashimi(baseCandSet, baseUnitsIncluded, coverUnitsIncluded);
                                        initSolutionStep(globalStep, aktSize, candidate, baseCandSet,
                                                baseUnitsIncluded, coverUnitsIncluded, withFins, isSashimi, fins, endoFinSet,
                                                candidatesToDelete, cannibalistic, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET);
                                        globalStep.setSubType(globalStep.getType());
                                        globalStep.setType(SolutionType.KRAKEN_FISH_TYPE_2);
                                        globalStep.addCandidateToDelete(endIndex, endCandidate);
                                        try {
                                            // now the chains
                                            for (int k = 0; k < tmpSet.size(); k++) {
                                                Chain tmpChain = tablingSolver.getKrakenChain(tmpSet.get(k), candidate, endIndex, endCandidate);
                                                globalStep.addChain((Chain) tmpChain.clone());
                                            }
                                            tablingSolver.adjustChains(globalStep);
                                            addKrakenStep();
                                        } catch (CloneNotSupportedException ex) {
                                            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // eine neue Unit hinzufügen und wieder probieren.
            checkCoverUnitsRecursive(aktSize, i + 1);
            cInt[aktSize - 1] = SudokuSet.EMPTY_SET;
            // in diesem Schritt dazugefügte Kandidaten wieder entfernen
            coverCandSet.andNot(coverCandidatesMasks[aktSize]);
            coverUnitsIncluded.remove(possibleCoverUnits.get(i));
        }
    }

    private void addFishStep() throws CloneNotSupportedException {
        if (fishType != UNDEFINED) {
            SolutionType type = globalStep.getType();
            if (fishType == BASIC && ! type.isBasicFish()) {
                return;
            }
            if (fishType == FRANKEN && ! type.isFrankenFish()) {
                return;
            }
            if (fishType == MUTANT && ! type.isMutantFish()) {
                return;
            }
        }
        if (Options.getInstance().onlyOneFishPerStep) {
            //String del = globalStep.getCandidateString() + " " + globalStep.getValues().get(0);
            String delOrg = globalStep.getCandidateString();
            int startIndex = delOrg.indexOf(")");
            startIndex = delOrg.indexOf("(", startIndex);
            String del = delOrg.substring(0, startIndex);
            Integer oldIndex = deletesMap.get(del);
            SolutionStep tmpStep = null;
            if (oldIndex != null) {
                tmpStep = steps.get(oldIndex.intValue());
            }
            if (tmpStep == null || globalStep.getType().compare(tmpStep.getType()) < 0) {
                if (oldIndex != null) {
                    steps.remove(oldIndex.intValue());
                    steps.add(oldIndex.intValue(), (SolutionStep) globalStep.clone());
                } else {
                    steps.add((SolutionStep) globalStep.clone());
                    deletesMap.put(del, steps.size() - 1);
                }
            }
        } else {
            steps.add((SolutionStep) globalStep.clone());
        }
    }
    
    private void addKrakenStep() throws CloneNotSupportedException {
        String del = globalStep.getCandidateString() + " " + globalStep.getValues().get(0);
        Integer oldIndex = deletesMap.get(del);
        SolutionStep tmpStep = null;
        if (oldIndex != null) {
            tmpStep = steps.get(oldIndex);
        }
        if (tmpStep == null || globalStep.getSubType().compare(tmpStep.getSubType()) < 0 ||
                (globalStep.getSubType().compare(tmpStep.getSubType()) == 0 &&
                globalStep.getChainLength() < tmpStep.getChainLength())) {
//            if (candidate != 1) {
//                System.out.println(globalStep.toString(2));
//            }
            steps.add((SolutionStep) globalStep.clone());
            deletesMap.put(del, steps.size() - 1);
        }
    }
    
    /**
     * Siamese Fish are two fishes that have the same base sets and differ
     * only in which candidates are fins; they provide different eliminations.
     * only fishes of the same category are checked
     * 
     * To find them: Compare all pairs of fishes, if the base sets match create
     * a new steps, that contains the same base set and both cover sets/fins/
     * eliminations.
     * 
     * @param fishes All available fishes
     */
    private void findSiameseFish(List<SolutionStep> fishes) {
        if (! Options.getInstance().allowDualsAndSiamese) {
            // not allowed!
            return;
        }
        // read current size (list can be changed by Siamese Fishes)
        int maxIndex = fishes.size();
        for (int i = 0; i < maxIndex - 1; i++) {
            for (int j = i + 1; j < maxIndex; j++) {
                SolutionStep step1 = fishes.get(i);
                SolutionStep step2 = fishes.get(j);
                if (step1.getValues().get(0) != step2.getValues().get(0)) {
                    // different candidate
                    continue;
                }
                if (step1.getBaseEntities().size() != step2.getBaseEntities().size()) {
                    // different fish size -> no dual
                    continue;
                }
                if (SolutionType.getStepConfig(step1.getType()).getCategory().ordinal() !=
                        SolutionType.getStepConfig(step2.getType()).getCategory().ordinal()) {
                    // not the same type of fish
                    continue;
                }
                boolean baseSetEqual = true;
                for (int k = 0; k < step1.getBaseEntities().size(); k++) {
                    if (! step1.getBaseEntities().get(k).equals(step2.getBaseEntities().get(k))) {
                        baseSetEqual = false;
                        break;
                    }
                }
                if (! baseSetEqual) {
                    // not the same base set -> cant be a siamese fish
                    continue;
                }
                // possible siamese fish; different eliminations?
                if (step1.getCandidatesToDelete().get(0).equals(step2.getCandidatesToDelete().get(0))) {
                    // same step twice -> no siamese fish
                    continue;
                }
                // ok: siamese fish!
                SolutionStep siamese = (SolutionStep) step1.clone();
                siamese.setIsSiamese(true);
                for (int k = 0; k < step2.getCoverEntities().size(); k++) {
                    siamese.addCoverEntity(step2.getCoverEntities().get(k));
                }
                for (int k = 0; k < step2.getFins().size(); k++) {
                    siamese.addFin(step2.getFins().get(k));
                }
                for (int k = 0; k < step2.getCandidatesToDelete().size(); k++) {
                    siamese.addCandidateToDelete(step2.getCandidatesToDelete().get(k));
                }
                siamese.getPotentialEliminations().or(step2.getPotentialEliminations());
                siamese.getPotentialCannibalisticEliminations().or(step2.getPotentialCannibalisticEliminations());
                fishes.add(siamese);
            }
        }
    }
    
    /**
     * Gets all candidates that are in more than one cover set and writes them to
     * cannibalisticSet. All necessary data is available in attributes:
     *   - baseCandSet: all base candidates
     *   - cInt[]: the cover candidates for all cover units (empty if unused)
     * 
     * Implementation:
     *   - Get only the base candidates for all cover sets (in cInt1)
     *   - AND all combinations of those reduced cover sets; every one
     *     in an ANDed set is a base candidate that is a member of at least
     *     two cover sets and thus a possible cannibalistic elimination 
     *   - OR all results of the ANDs
     */
    private void calculateCannibalisticSet() {
        cannibalisticSet.clear();
        int maxCoverIndex = 0;
        for (int i = 0; i < cInt.length; i++) {
            if (cInt[i].isEmpty()) {
                cInt1[i].clear();
            } else {
                cInt1[i].set(cInt[i]);
                cInt1[i].and(baseCandSet);
                if (!cInt1[i].isEmpty()) {
                    maxCoverIndex = i;
                }
            }
        }
        for (int i = 0; i < maxCoverIndex; i++) {
            if (cInt1[i].isEmpty()) {
                // cant produce a canibalistic candidate
                continue;
            }
            for (int j = i + 1; j <= maxCoverIndex; j++) {
                tmpSet.set(cInt1[i]);
                tmpSet.and(cInt1[j]);
                cannibalisticSet.or(tmpSet);
            }
        }
    }
    
    private void addCandidateToDelete(int index, int candidate) {
        candidatesToDelete.add(new Candidate(index, candidate));
    }

    private void addCannibalistic(int index, int candidate) {
        cannibalistic.add(new Candidate(index, candidate));
    }

    /**
     * Sashimi wird nur für basic-Fische geprüft: Wenn die fins gelöscht werden, muss mehr als ein
     * Kandidat in der entsprechenden base-unit übrig sein, sonst ist der Fisch Sashimi
     */
    private boolean checkSashimi(SudokuSet baseCandSet, SudokuSet baseUnitsIncluded, SudokuSet coverUnitsIncluded) {
        int baseMask = getUnitMask(baseUnitsIncluded, baseUnits);
        int coverMask = getUnitMask(coverUnitsIncluded, coverUnits);
        if ((baseMask == LINE_MASK && coverMask == COL_MASK) ||
                (baseMask == COL_MASK && coverMask == LINE_MASK)) {
            // alle base units durchschauen: wenn eine base unit mindestens eine fin enthält, werden alle
            // fins gelöscht; es müssen dann noch mehr als ein base-Kandidat übrig sein
            for (int i = 0; i < baseUnitsIncluded.size(); i++) {
                checkSashimiSet.set(baseCandidates[baseUnitsIncluded.get(i)]);
                checkSashimiSet.andNot(fins);
                if (checkSashimiSet.size() <= 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private void initSolutionStep(SolutionStep step, int aktSize, int candidate, SudokuSet baseCandSet,
            SudokuSet baseUnitsIncluded, SudokuSet coverUnitsIncluded, boolean withFins, boolean sashimi,
            SudokuSet fins, SudokuSet endoFins, List<Candidate> candidatesToDelete, List<Candidate> cannibalistic,
            SudokuSet deleteCandSet, SudokuSet cannibalisticSet) {
        step.reset();
        // Als erstes den Typ feststellen:
        //  BASIC: l/c oder c/l
        //  FRANKEN: lb/cb oder cb/lb
        //  MUTANT: alles andere

        // Masken bilden für Base- und Cover-Units
        SolutionType type = SolutionType.X_WING;
        int baseMask = getUnitMask(baseUnitsIncluded, baseUnits);
        int coverMask = getUnitMask(coverUnitsIncluded, coverUnits);
//        for (int i = 0; i < coverUnitsIncluded.size(); i++) {
//            int entity = getEntity(coverUnits[coverUnitsIncluded.get(i)]).entityName;
//            if (entity == SudokuCell.LINE) {
//                coverMask |= LINE_MASK;
//            } else if (entity == SudokuCell.COL) {
//                coverMask |= COL_MASK;
//            } else {
//                coverMask |= BLOCK_MASK;
//            }
//        }
        // Typ feststellen
        if ((baseMask == LINE_MASK && coverMask == COL_MASK) || (baseMask == COL_MASK && coverMask == LINE_MASK)) {
            // Basic Fish
            if (sashimi) {
                type = SASHIMI_BASIC_TYPES[aktSize - 2];
            } else if (withFins) {
                type = FINNED_BASIC_TYPES[aktSize - 2];
            } else {
                type = BASIC_TYPES[aktSize - 2];
            }
        } else if ((((baseMask == LINE_MASK) || (baseMask == (LINE_MASK | BLOCK_MASK))) && ((coverMask == COL_MASK) || (coverMask == (COL_MASK | BLOCK_MASK)))) ||
                (((baseMask == COL_MASK) || (baseMask == (COL_MASK | BLOCK_MASK))) && ((coverMask == LINE_MASK) || (coverMask == (LINE_MASK | BLOCK_MASK))))) {
            // Franken Fish
            if (withFins) {
                type = FINNED_FRANKEN_TYPES[aktSize - 2];
            } else {
                type = FRANKEN_TYPES[aktSize - 2];
            }
        } else {
            // Mutant Fish
            if (withFins) {
                type = FINNED_MUTANT_TYPES[aktSize - 2];
            } else {
                type = MUTANT_TYPES[aktSize - 2];
            }
        }
        step.setType(type);
        step.addValue(candidate); // candidate ist Eigenschaft, die die Nummer des Kandidatens für den aktuellen Versuch enthält
        for (int i = 0; i < baseCandSet.size(); i++) {
            int index = baseCandSet.get(i);
            if (!fins.contains(index)) {
                step.addIndex(index);
            }
        }
        for (int i = 0; i < baseUnitsIncluded.size(); i++) {
            step.addBaseEntity(getEntity(baseUnits[baseUnitsIncluded.get(i)]));
        }
        for (int i = 0; i < coverUnitsIncluded.size(); i++) {
            step.addCoverEntity(getEntity(coverUnits[coverUnitsIncluded.get(i)]));
        }
        // zu löschende Kandidaten
        for (int k = 0; k < candidatesToDelete.size(); k++) {
            step.addCandidateToDelete(candidatesToDelete.get(k));
        }
        for (int k = 0; k < cannibalistic.size(); k++) {
            step.addCannibalistic(cannibalistic.get(k));
        }
        // Fins hinzufügen
        for (int i = 0; i < fins.size(); i++) {
            int index = fins.get(i);
            if (!endoFins.contains(index)) {
                globalStep.addFin(index, candidate);
            }
        }
        // Endo-Fins hinzufügen
        for (int i = 0; i < endoFins.size(); i++) {
            globalStep.addEndoFin(endoFins.get(i), candidate);
        }
        // add potential (cannibalistic) eliminations
        globalStep.getPotentialEliminations().set(deleteCandSet);
        globalStep.getPotentialCannibalisticEliminations().set(cannibalisticSet);
    }

    private int getUnitMask(SudokuSet units, int[][] allUnits) {
        int mask = 0;
        for (int i = 0; i < units.size(); i++) {
            int entity = getEntity(allUnits[units.get(i)]).getEntityName();
            if (entity == SudokuCell.LINE) {
                mask |= LINE_MASK;
            } else if (entity == SudokuCell.COL) {
                mask |= COL_MASK;
            } else {
                mask |= BLOCK_MASK;
            }
        }
        return mask;
    }

    /**
     * Alle Cover-Units durchgehen und prüfen, ob sie einen Kandidaten aus baseCandSet enthalten; wenn ja,
     * prüfen, ob sie mit keiner der baseUnits identisch sind; wenn beides zutrifft, dem neuen Set hinzufügen.
     *
     * @param baseCandSet Indexe aller von den Base-Units in baseUnitsIncluded abgedeckten Kandidaten.
     * @param baseUnitsIncluded Indexe aller Base-Units, die für den aktuellen Versuch verwendet werden.
     * @return Set mit den Indexen aller Cover-Sets, die eventuell für einen Fisch in Frage kommen.
     */
    private SudokuSet getPossibleCoverUnits(SudokuSet baseCandSet, SudokuSet baseUnitsIncluded) {
        possibleCoverUnits.clear();
        for (int i = 0; i < coverUnits.length; i++) {
            if (baseCandSet.intersects(coverCandidates[i])) {
                // coverUnit[i] enthält mindestens einen Kandidaten aus dem Base-Set -> auf Base-Unit prüfen
                boolean isBaseUnit = false;
                for (int j = 0; j < baseUnitsIncluded.size(); j++) {
                    // Cover-Units dürfen nicht mit Base-Units übereinstimmen!
                    // Vergleich auf gleiche Referenzen geht, weil alle Unit-Arrays aus denselben
                    // statischen Konstanten befüllt werden
                    if (baseUnits[baseUnitsIncluded.get(j)] == coverUnits[i]) {
                        isBaseUnit = true;
                        break;
                    }
                }
                if (!isBaseUnit) {
                    possibleCoverUnits.add(i);
                }
            }
        }
        return possibleCoverUnits;
    }

    /**
     * Erzeugt zwei Arrays vom Typ <code>SudokuSet</code>: {@link baseCandidates} und
     * {@link coverCandidates}. <CODE>baseCandidates</CODE> enthält ein Set mit allen Indexpositionen
     * des gesuchten Kandidaten in der entsprechenden <CODE>baseUnit</CODE>,
     * <CODE>coverCandidates</CODE> enthält entsprechend die Kandidaten der <CODE>coverUnits</CODE>.
     * @param candidate Kandidat, für den der Fisch gesucht werden soll.
     * @param baseUnits Arrays mit jeweils allen Indexpositionen der entsprechenden Base-Unit.
     * @param coverUnits Arrays mit jeweils allen Indexpositionen der entsprechenden Cover-Unit.
     */
    private void initForCandidat(int candidate, int[][] baseUnits, int[][] coverUnits) {
        this.candidate = candidate;
        baseCandidates = new SudokuSet[baseUnits.length];
        doInitForCandidates(candidate, baseCandidates, baseUnits);

        if (coverUnits == baseUnits) {
            coverCandidates = baseCandidates;
        } else {
            coverCandidates = new SudokuSet[coverUnits.length];
            doInitForCandidates(candidate, coverCandidates, coverUnits);
        }
        double anzBaseUnits = 0;
        for (int i = 0; i < baseCandidates.length; i++) {
            if (!baseCandidates[i].isEmpty()) {
                anzBaseUnits++;
            }
        }
        maxBaseCombinations = 0;
        // we have only maxSize combinations (the smaller fishes are automatically
        // included
        for (int i = 1; i <= maxSize; i++) {
            maxBaseCombinations += combinations((int) anzBaseUnits, i);
        }
        if (dlg != null) {
            dlg.resetFishProgressBar(maxBaseCombinations);
        }
    }

//    /**
//     * Calculates n over k
//     *
//     * @param n
//     * @param k
//     * @return
//     */
//    private int combinations(int n, int k) {
//        double fakN = 1;
//        for (int i = 2; i <= n; i++) {
//            fakN *= i;
//        }
//        double fakNMinusK = 1;
//        for (int i = 2; i <= n - k; i++) {
//            fakNMinusK *= i;
//        }
//        double fakK = 1;
//        for (int i = 2; i <= k; i++) {
//            fakK *= i;
//        }
//        return (int) (fakN / (fakNMinusK * fakK));
//    }

    /**
     * Does the actual calculations for {@link initForCandidat(int,int[][],int[][])}.
     * @param candidate Candidate, for that the search is made.
     * @param sets Array of <CODE>SudokuSet</CODE> in which the indices of candidate shall be stored.
     * @param units Arrays with the inidces of the corresponding units.
     */
    private void doInitForCandidates(int candidate, SudokuSet[] sets, int[][] units) {
        for (int i = 0; i < sets.length; i++) {
            sets[i] = new SudokuSet();
        }
        for (int i = 0; i < units.length; i++) {
            for (int j = 0; j < units[i].length; j++) {
                SudokuCell cell = sudoku.getCell(units[i][j]);
                if (cell.getValue() == 0 && cell.isCandidate(candType, candidate)) {
                    sets[i].add(units[i][j]);
                }
            }
        }
    }

    private Entity getEntity(int[] unit) {
        for (int i = 0; i < Sudoku.LINES.length; i++) {
            if (unit == Sudoku.LINES[i]) {
                return new Entity(SudokuCell.LINE, i + 1);
            }
        }
        for (int i = 0; i < Sudoku.COLS.length; i++) {
            if (unit == Sudoku.COLS[i]) {
                return new Entity(SudokuCell.COL, i + 1);
            }
        }
        for (int i = 0; i < Sudoku.BLOCKS.length; i++) {
            if (unit == Sudoku.BLOCKS[i]) {
                return new Entity(SudokuCell.USER, i + 1);
            }
        }
        return null;
    }
    
    private void getTablingSolver() {
        if (tablingSolver != null) {
            return;
        }
        if (solver != null) {
            tablingSolver = (TablingSolver) solver.getSpecialisedSolver(TablingSolver.class);
        } else {
            tablingSolver = new TablingSolver(null);
        }
    }

    public static void main(String[] args) {
        //Sudoku sudoku = new Sudoku(true);
        Sudoku sudoku = new Sudoku();
        //sudoku.setSudoku(":0361:4:..5.132673268..14917...2835..8..1.262.1.96758.6..283...12....83693184572..723.6..:434 441 442 461 961 464 974:411:r7c39 r6c1b9 fr3c3");
        //sudoku.setSudoku(":0300:4:135792..4.9.8315278725461...8.917.5....3.4.783.72.89.1...673.1....1297..7..485..6:653 472 473 277 483 683 388:481:c28 r68");
        //sudoku.setSudoku(":0000:x:7.2.34.8.........2.8..51.74.......51..63.27..29.......14.76..2.8.........2.51.8.7:::");
        // 7, 7, 20, 20: 18297 / 23422
        // 4, 4, 20, 20: 1329  / 1609
        sudoku.setSudoku(":0000:x:1.......2.9.4...5...6...7...5.9.3......+67+4......85..4.7.....6...3...9.8...2.....1:126 226 826 148 261 167::");
        // 7, 7, 20, 20: 327.453 / 457.859
        // 4, 4, 20, 20: 3328    / 3672
        //sudoku.setSudoku(":0000:x:9.7..5...1..7..9..86..9.57..8...61.9316.59..72.91..65.....2..96.9...4..8...9..3.5:214 314 414 315 615 815 217 118 218 222 325 228 328 428 234 339 439 448 854 458 772 473 374 384 185 493 795::");
        sudoku.setSudoku(":0000:x:..53..6...23.7..4.....1..383584....1.64251.8.21..38..443..2.....72.4..9...1..34..:811 916 536 636 936 678 891 894 698::");
        FishSolver fs = new FishSolver(null);
        long millis = System.currentTimeMillis();
        //List<SolutionStep> steps = fs.getAllFishes(sudoku, 7, 7, 20, 20, null, -1);
        List<SolutionStep> steps = fs.getAllKrakenFishes(sudoku, 3, 3, 2, 0, null, -1, 2);
        millis = System.currentTimeMillis() - millis;
        System.out.println("Zeit: " + millis + "ms");
    }
}
