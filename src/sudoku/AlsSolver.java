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
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author user
 */
public class AlsSolver extends AbstractSolver {
    private static boolean DEBUG = false;

    private SortedMap<String, Integer> deletesMap = new TreeMap<String, Integer>();  // alle bisher gefundenen Chains: Eliminierungen und Index in steps
    private static AlsComparator alsComparator = null;
    private List<SolutionStep> steps; // gefundene Lösungsschritte
    private SolutionStep globalStep = new SolutionStep(SolutionType.HIDDEN_SINGLE);
    private SudokuSet indexSet = new SudokuSet();
    //private SudokuSet candSet = new SudokuSet();
    //private SudokuSet[] candAddSets = new SudokuSet[10];
    private List<Als> alses = new ArrayList<Als>(500);
    private int[] startIndices;
    private int[] endIndices;
    private List<RestrictedCommon> restrictedCommons = new ArrayList<RestrictedCommon>(2000);
    //private List<RestrictedCommon> chain = new ArrayList<RestrictedCommon>(40); // gespeichert werden für die Chain nur die Links
    private RestrictedCommon[] chain = new RestrictedCommon[100]; // gespeichert werden für die Chain nur die Links
    private int chainIndex = -1; // index into chain
    private RestrictedCommon firstRC = null; // the first RC in the chain (needed for test for eliminations)
    private boolean[] alsInChain; // chain search: for every ALS already contained in the chain the respective index is true
    //private SudokuSet chainSet = new SudokuSet(); // Alle Indices der Chain bisher
    private Als startAls;  // erstes ALS in der Chain (für Prüfung auf Eliminierungen)
    private int recDepth = 0; // aktuelle Tiefe der Rekursion (Chain-Suche)
    private int maxRecDepth = 0; // maximale Tiefe der Rekursion (Chain-Suche)
    private SudokuSet possibleRestrictedCommonsSet = new SudokuSet(); // alle Kandidaten, die in beiden ALS vorkommen
    private SudokuSet restrictedCommonSet = new SudokuSet(); // zum Prüfen auf restricted commons (Buddies aufsummieren)
    private SudokuSet restrictedCommonTmpSet = new SudokuSet(); // Position mit Buddies vereinen
    private SudokuSet restrictedCommonIndexSet = new SudokuSet(); // Ale Positionen eines Kandidaten in beiden ALS
    private SudokuSet forbiddenIndexSet = new SudokuSet();  // Alle Positionen in allen ALS -> kommen nicht in Frage!
    private SudokuSet intersectionSet = new SudokuSet();  // für Prüfung auf Überlappen
    private RCForDeathBlossom[] rcdb = new RCForDeathBlossom[81];
    private RCForDeathBlossom aktRcdb = null;  // ALS for stem cell that is currently checked
    private SudokuSet aktDBIndices = new SudokuSet(); // all indices of all ALS for a given stem cell (for recursive search)
    private SudokuSet aktDBCandidates = new SudokuSet(); // all common candidates in the current combination of ALS
    private SudokuSet[] incDBCand = new SudokuSet[10]; // the common candidates that were reduced by the current ALS
    private SudokuSet tmpSet = new SudokuSet();
    private int[] aktDBAls = new int[10]; // the indices of all ALS in the current try
    private SudokuSet dbIndicesPerCandidate = new SudokuSet(); // all indices of all ALS in a DB for a given candidate
    private int maxDBCand = 0;  // maximum candidate for which a recursive search for DeathBlossom has to be made
    private int stemCellIndex = 0; // The index of the current stem cell for Death Blossom
    
    /** Creates a new instance of AlsSolver */
    public AlsSolver(SudokuSolver solver) {
        super(solver);
        if (alsComparator == null) {
            alsComparator = new AlsComparator();
        }
        for (int i = 0; i < incDBCand.length; i++) {
            incDBCand[i] = new SudokuSet();
        }
    }

    @Override
    protected SolutionStep getStep(SolutionType type) {
        SolutionStep result = null;
        switch (type) {
            case ALS_XZ:
                getAlsXZ();
                if (steps.size() > 0) {
                    Collections.sort(steps, alsComparator);
                    result = steps.get(0);
                }
                break;
            case ALS_XY_WING:
                getAlsXYWing();
                if (steps.size() > 0) {
                    Collections.sort(steps, alsComparator);
                    result = steps.get(0);
                }
                break;
            case ALS_XY_CHAIN:
                getAlsXYChain();
                if (steps.size() > 0) {
                    Collections.sort(steps, alsComparator);
                    result = steps.get(0);
                }
                break;
            case DEATH_BLOSSOM:
                getAlsDeathBlossom();
                if (steps.size() > 0) {
                    Collections.sort(steps, alsComparator);
                    result = steps.get(0);
                }
                break;
        }
        return result;
    }

    @Override
    protected boolean doStep(SolutionStep step) {
        boolean handled = true;
        switch (step.getType()) {
            case ALS_XZ:
            case ALS_XY_WING:
            case ALS_XY_CHAIN:
            case DEATH_BLOSSOM:
                for (Candidate cand : step.getCandidatesToDelete()) {
                    sudoku.delCandidate(cand.getIndex(), candType, cand.getValue());
                }
                break;
            default:
                handled = false;
        }
        return handled;
    }

    public List<SolutionStep> getAllAlses(Sudoku sudoku) {
        Sudoku save = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldSteps = steps;
        List<SolutionStep> resultSteps = new ArrayList<SolutionStep>();
        long millis1 = System.currentTimeMillis();
        collectAllAlses();
        collectAllRestrictedCommons(true, Options.getInstance().allowAlsOverlap);
        steps = new ArrayList<SolutionStep>();
        getAlsXZInt();
        Collections.sort(steps, alsComparator);
        resultSteps.addAll(steps);
        steps.clear();
        getAlsXYWingInt();
        Collections.sort(steps, alsComparator);
        resultSteps.addAll(steps);
        steps.clear();
        getAlsXYChainInt();
        Collections.sort(steps, alsComparator);
        resultSteps.addAll(steps);
        millis1 = System.currentTimeMillis() - millis1;
        if (DEBUG) System.out.println("getAllAlses() total: " + millis1 + "ms");
        steps = oldSteps;
        if (save != null) {
            setSudoku(save);
        }
        return resultSteps;
    }

    public List<SolutionStep> getAllDeathBlossoms(Sudoku sudoku) {
        Sudoku save = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldSteps = steps;
        List<SolutionStep> resultSteps = new ArrayList<SolutionStep>();
        long millis1 = System.currentTimeMillis();
        collectAllAlses();
        collectAllRCsForDeathBlossom();
        steps = new ArrayList<SolutionStep>();
        getAlsDeathBlossomInt();
        Collections.sort(steps, alsComparator);
        resultSteps.addAll(steps);
        millis1 = System.currentTimeMillis() - millis1;
        if (DEBUG) System.out.println("getAllDeathBlossoms() total: " + millis1 + "ms");
        steps = oldSteps;
        if (save != null) {
            setSudoku(save);
        }
        return resultSteps;
    }

    private void getAlsDeathBlossom() {
        steps = new ArrayList<SolutionStep>();
        collectAllAlses();
        collectAllRCsForDeathBlossom();
        getAlsDeathBlossomInt();
    }

    private void getAlsXYChain() {
        steps = new ArrayList<SolutionStep>();
        collectAllAlses();
        collectAllRestrictedCommons(true, Options.getInstance().allowAlsOverlap);
        getAlsXYChainInt();
    }

    private void getAlsXYWing() {
        steps = new ArrayList<SolutionStep>();
        collectAllAlses();
        collectAllRestrictedCommons(false, Options.getInstance().allowAlsOverlap);
        getAlsXYWingInt();
    }

    private void getAlsXZ() {
        steps = new ArrayList<SolutionStep>();
        collectAllAlses();
        collectAllRestrictedCommons(false, Options.getInstance().allowAlsOverlap);
        getAlsXZInt();
    }

    /**
     * Check all restricted commons: For every RC check all candidates common to both ALS but
     * minus the RC candidate(s). If buddies exist outside the ALS they can be eliminated.<br>
     * 
     * Doubly linked ALS-XZ: If two ALS are linked by 2 RCs, the rest of
     *    each ALS becomes a locked set and eliminates additional candidates; plus each
     *    of the two RCs can be used for "normal" ALS-XZ eliminations.
     */
    private void getAlsXZInt() {
        globalStep.reset();
        for (int i = 0; i < restrictedCommons.size(); i++) {
            RestrictedCommon rc = restrictedCommons.get(i);
            Als als1 = alses.get(rc.als1);
            Als als2 = alses.get(rc.als2);
            checkCandidatesToDelete(als1, als2, rc.cand1);
            if (rc.cand2 != 0) {
                // als1 and als2 are doubly linked -> check for additional eliminations
                checkCandidatesToDelete(als1, als2, rc.cand2);
                checkDoublyLinkedAls(als1, als2, rc.cand1, rc.cand2);
                checkDoublyLinkedAls(als2, als1, rc.cand1, rc.cand2);
            }
            if (globalStep.getCandidatesToDelete().size() > 0) {
                // Step zusammenbauen
                globalStep.setType(SolutionType.ALS_XZ);
                globalStep.addAls(als1.indices, als1.candidates);
                globalStep.addAls(als2.indices, als2.candidates);
                addRestrictedCommonToStep(als1, als2, rc.cand1, false);
                if (rc.cand2 != 0) {
                    addRestrictedCommonToStep(als1, als2, rc.cand2, false);
                }
                steps.add((SolutionStep) globalStep.clone());
                globalStep.reset();
            }
        }
    }

    /**
     * Check all combinations of two RCs and check whether it is possible to construct
     * an ALS XY-Wing:
     *   - we need three different ALS
     *   - if RC1 and RC2 both have only one candidate that candidate must differ
     * 
     * If a valid combination could be found, identify ALS C and check ALS A and B
     * for possible eliminations.
     */
    private void getAlsXYWingInt() {
        globalStep.reset();
        for (int i = 0; i < restrictedCommons.size(); i++) {
            RestrictedCommon rc1 = restrictedCommons.get(i);
            for (int j = i + 1; j < restrictedCommons.size(); j++) {
                RestrictedCommon rc2 = restrictedCommons.get(j);
                // at least two different candidates in rc1 and rc2!
                // must alway be true, if the two rcs have a different
                // number of digits;
                if (rc1.cand2 == 0 && rc2.cand2 == 0 && rc1.cand1 == rc2.cand1) {
                    // both RCs have only one digit and the digits dont differ
                    continue;
                }
                // the two RCs have to connect 3 different ALS; since
                // rc1.als1 != rc1.als2 && rc2.als1 != rc2.als2 not many possibilites are left
                if (!((rc1.als1 == rc2.als1 && rc1.als2 != rc2.als2) ||
                        (rc1.als2 == rc2.als1 && rc1.als1 != rc2.als2) ||
                        (rc1.als1 == rc2.als2 && rc1.als2 != rc2.als1) ||
                        (rc1.als2 == rc2.als2 && rc1.als1 != rc2.als1))) {
                    // cant be an XY-Wing
                    continue;
                }
                // Identify C so we can check for eliminations
                Als a = null;
                Als b = null;
                Als c = null;
                if (rc1.als1 == rc2.als1) {
                    c = alses.get(rc1.als1);
                    a = alses.get(rc1.als2);
                    b = alses.get(rc2.als2);
                }
                if (rc1.als1 == rc2.als2) {
                    c = alses.get(rc1.als1);
                    a = alses.get(rc1.als2);
                    b = alses.get(rc2.als1);
                }
                if (rc1.als2 == rc2.als1) {
                    c = alses.get(rc1.als2);
                    a = alses.get(rc1.als1);
                    b = alses.get(rc2.als2);
                }
                if (rc1.als2 == rc2.als2) {
                    c = alses.get(rc1.als2);
                    a = alses.get(rc1.als1);
                    b = alses.get(rc2.als1);
                }
                if (! Options.getInstance().allowAlsOverlap) {
                    // Check overlaps: the RCs have already been checked, a and b are missing:
                    // since a/c and b/c dont overlap (a | c) & (b | c) has to be c
                    intersectionSet.set(a.indices);
                    intersectionSet.or(c.indices);
                    indexSet.set(b.indices);
                    indexSet.or(c.indices);
                    intersectionSet.and(indexSet);
                    if (!intersectionSet.equals(c.indices)) {
                        // overlap -> not allowed
                        continue;
                    }
                }
                // even if overlaps are allowed, a/b must not be a subset of b/a
                indexSet.set(a.indices);
                indexSet.or(b.indices);
                if (indexSet.equals(a.indices) || indexSet.equals(b.indices)) {
                    continue;
                }
                // now check candidates of A and B
                //checkCandidatesToDelete(a, b, c, rc1.cand1, rc2.cand1);
                checkCandidatesToDelete(a, b, rc1.cand1, rc1.cand2, rc2.cand1, rc2.cand2);
                if (globalStep.getCandidatesToDelete().size() > 0) {
                    // Step zusammenbauen
                    globalStep.setType(SolutionType.ALS_XY_WING);
                    globalStep.addAls(a.indices, a.candidates);
                    globalStep.addAls(b.indices, b.candidates);
                    globalStep.addAls(c.indices, c.candidates);
                    addRestrictedCommonToStep(a, c, rc1.cand1, false);
                    if (rc1.cand2 != 0) {
                        addRestrictedCommonToStep(a, c, rc1.cand2, false);
                    }
                    addRestrictedCommonToStep(b, c, rc2.cand1, false);
                    if (rc2.cand2 != 0) {
                        addRestrictedCommonToStep(b, c, rc2.cand2, false);
                    }
                    steps.add((SolutionStep) globalStep.clone());
                    globalStep.reset();
                }
            }
        }
    }

    /**
     * Check all combinations starting with every ALS. The following rules are applied:
     *   - Two adjacent ALS may overlap as long as the overlapping area doesnt contain an RC
     *   - Two non adjacent ALS may overlap without restrictions
     *   - If the first and last ALS are identical or if the first ALS is contained in the last
     *     or vice versa the chain becomes a loop -> check later
     *   - Two adjacent RCs must follow the adjency rules (see below)
     *   - each chain must be at least 4 ALS long
     *   - start and end ALS must have a common candidate that exists outside the chain -> can be eliminated
     * 
     * Adjacency rules for RCs joining ALS1 and ALS2:
     *   - get all RCs between ALS1 and ALS2 ("possible RCs" - "PRC")
     *   - subtract the "actual RCs" ("ARC") of the previous step; the remainder
     *     becomes the new ARC(s)
     *   - if no ARC is left, the chain ends at ALS1
     * 
     * If a new ALS is already contained within the chain, the chain becomes a whip (not handled)
     * Its unclear whether the search has to go in both directions
     */
    private void getAlsXYChainInt() {
        recDepth = 0;
        maxRecDepth = 0;
        deletesMap.clear();
        for (int i = 0; i < alses.size(); i++) {
//            if (i != 7) {
//                continue;
//            }
            startAls = alses.get(i);
            chainIndex = 0;
            alsInChain = new boolean[alses.size()];
            alsInChain[i] = true;
            firstRC = null;
            getAlsXYChainRecursive(i, null);
        }
        if (DEBUG) System.out.println(steps.size() + " (maxRecDepth: " + maxRecDepth + ")");
    }

    /**
     * Hier passierts: Für das ALS mit Index alsIndex alle restricted commons durchgehen.
     * Wenn das ALS, auf das der restricted common verweist, sich nicht mit den
     * bisherigen ALSs überschneidet und der neue restricted common verschieden ist
     * vom letzten, neues ALS hinzufügen und rekursiv weitermachen. Sobald die
     * Chain 4 Glieder lang ist, bei jedem Schritt auf mögliche Eliminierungen testen.
     */
    /**
     * Real search: for als with index alsIndex check all RCs. If the RC fulfills
     * the adjacency rules and the als to which the RC points is not already
     * part of the chain the als is added and the search is continued recursively.
     * When the chain size reaches 4, every step is tested for possible eliminations.
     * 
     * Careful: If the first RC has two candidates, both of them have to be tried
     * independently.
     * 
     * @param alsIndex index of the last added ALS
     * @param lastRC RC of the last step (needed for adjacency check)
     */
    private void getAlsXYChainRecursive(int alsIndex, RestrictedCommon lastRC) {
        // Abbruch der Rekursion?
        if (alsIndex >= alses.size()) {
            return;
        }
        recDepth++;
        if (recDepth > maxRecDepth) {
            maxRecDepth = recDepth;
        }
        if (recDepth % 100 == 0) {
            if (DEBUG) System.out.println("Recursion depth: " + recDepth);
        }
        // check all RCs; if none exist the loop is never entered
        boolean firstTry = true;
        for (int i = startIndices[alsIndex]; i < endIndices[alsIndex]; i++) {
            RestrictedCommon rc = restrictedCommons.get(i);
            //if (chain.size() > 0 && rc.cand1 == chain.get(chain.size() - 1).cand1) {
            if (chainIndex >= chain.length || ! rc.checkRC(lastRC, firstTry)) {
                // chain is full or RC doesnt adhere to the adjacency rules
                continue;
            }
            if (alsInChain[rc.als2]) {
                // ALS already part of the chain -> whips are not handled!
                continue;
            }
            Als aktAls = alses.get(rc.als2);

            // ok, ALS can be added
            if (chainIndex == 0) {
                firstRC = rc;
            }
            chain[chainIndex++] = rc;
            alsInChain[rc.als2] = true;
            // wenn die Chain mindestens 4 Glieder lang ist, auf zu löschende Kandidaten prüfen
            if (chainIndex >= 3) {
                globalStep.getCandidatesToDelete().clear();
                //checkCandidatesToDelete(startAls, aktAls, firstRestrictedCommon, rc.cand1, chainSet);
                int c1 = 0, c2 = 0, c3 = 0, c4 = 0;
                c1 = firstRC.cand1;
                c2 = firstRC.cand2;
                if (firstRC.actualRC == 1) {
                    c2 = 0;
                } else if (firstRC.actualRC == 2) {
                    c1 = 0;
                }
                if (rc.actualRC == 1) {
                    c3 = rc.cand1;
                } else if (rc.actualRC == 2) {
                    c3 = rc.cand2;
                } else if (rc.actualRC == 3) {
                    c3 = rc.cand1;
                    c4 = rc.cand2;
                }
                checkCandidatesToDelete(startAls, aktAls, c1, c2, c3, c4, null);
                if (globalStep.getCandidatesToDelete().size() > 0) {
                    // Chain gefunden -> Step zusammenbauen und einschreiben
                    globalStep.setType(SolutionType.ALS_XY_CHAIN);
                    globalStep.addAls(startAls.indices, startAls.candidates);
                    Als tmpAls = startAls;
                    for (int j = 0; j < chainIndex; j++) {
                        Als tmp = alses.get(chain[j].als2);
                        globalStep.addAls(tmp.indices, tmp.candidates);
                        try {
                            globalStep.addRestrictedCommon((RestrictedCommon)chain[j].clone());
                        } catch (CloneNotSupportedException ex) {
                            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning (RC)", ex);
                        }

                        // die restricted commons müssen noch für die gesamte Chain geschrieben werden
                        //if (DEBUG) System.out.println("chain[" + j + "]: " + chain[j] + " (" + tmpAls + "/" + tmp + ")");
                        if (chain[j].actualRC == 1 || chain[j].actualRC == 3) {
                            addRestrictedCommonToStep(tmpAls, tmp, chain[j].cand1, true);
                        }
                        if (chain[j].actualRC == 2 || chain[j].actualRC == 3) {
                            addRestrictedCommonToStep(tmpAls, tmp, chain[j].cand2, true);
                        }
                        tmpAls = tmp;
                    }
//                    try {
//                        steps.add((SolutionStep) globalStep.clone());
//                    } catch (CloneNotSupportedException ex) {
//                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
//                    }

                    boolean writeIt = true;
                    int replaceIndex = -1;
                    String elim = null;
                    if (Options.getInstance().onlyOneAlsPerStep) {
                        elim = globalStep.getCandidateString();
                        Integer alreadyThere = deletesMap.get(elim);
                        if (alreadyThere != null) {
                            // a step already exists!
                            SolutionStep tmp = steps.get(alreadyThere);
                            if (tmp.getAlsesIndexCount() > globalStep.getAlsesIndexCount()) {
                                writeIt = true;
                                replaceIndex = alreadyThere;
                            } else {
                                writeIt = false;
                            }
                        }
                    }
                    if (writeIt) {
                        if (replaceIndex != -1) {
                            steps.remove(replaceIndex);
                            steps.add(replaceIndex, (SolutionStep) globalStep.clone());
                        } else {
                            steps.add((SolutionStep) globalStep.clone());
                            if (elim != null) {
                                deletesMap.put(elim, steps.size() - 1);
                            }
                        }
                    }
                    globalStep.reset();
                }
            }

            // und weiter geht die wilde Hatz...
            getAlsXYChainRecursive(rc.als2, rc);

            // und wieder retour
            alsInChain[rc.als2] = false;
            chainIndex--;
            
            if (lastRC == null) {
                if (rc.cand2 != 0 && firstTry) {
                    // first RC in chain and a second RC is present: try it!
                    firstTry = false;
                    i--;
                } else {
                    firstTry = true;
                }
            }
        }
        recDepth--;
    }

    /**
     * Searches for all available Death blossoms: if a cell exists that
     * has at least one ALS for every candidate check all combinations of
     * available ALS for that cell. Any combination of (non overlapping) ALS
     * has to be checked for common candidates that can eliminate candidates
     * outside the ALS and the stem cell.
     */
    private void getAlsDeathBlossomInt() {
        deletesMap.clear();
        globalStep.reset();
        globalStep.setType(SolutionType.DEATH_BLOSSOM);
        SudokuCell[] cells = sudoku.getCells();
        for (int i = 0; i < cells.length; i++) {
            if (cells[i].getValue() != 0) {
                // cell already set -> ignore
                continue;
            }
            if (rcdb[i] == null || cells[i].getCandidateMask(candType) != rcdb[i].candMask) {
                // there are candidates left without ALS -> impossible
                //System.out.println("Cell " + i + ": " + rcdb[i].candMask + "/" + cells[i].getCandidateMask(candType));
                continue;
            }
            // ok here it starts: try all combinations of ALS
            stemCellIndex = i;
            aktRcdb = rcdb[i];
            maxDBCand = 0;
            for (int j = 1; j <= 9; j++) {
                if (aktRcdb.indices[j] > 0) {
                    maxDBCand = j;
                }
            }
            aktDBIndices.clear();
            aktDBCandidates.setAll();
            for (int j = 0; j < aktDBAls.length; j++) {
                aktDBAls[j] = -1;
            }
            checkAlsDeathBlossomRecursive(1);
        }
    }
    
    private void checkAlsDeathBlossomRecursive(int cand) {
        if (cand > maxDBCand) {
            // nothing left to do
            return;
        }
        if (aktRcdb.indices[cand] > 0) {
            // There are ALS to try
            for (int i = 0; i < aktRcdb.indices[cand]; i++) {
                Als als = alses.get(aktRcdb.alsPerCandidate[cand][i]);
                //if (DEBUG) System.out.println("cand = " + cand + ", i = " + i + ", ALS: " + als.toString());
                // check for overlap
                if (! Options.getInstance().allowAlsOverlap && ! als.indices.andNotEquals(aktDBIndices)) {
                    // new ALS overlaps -> we dont need to look further
                    //if (DEBUG) System.out.println(" Overlap!");
                    continue;
                }
                // check for common candidates
                tmpSet.set(aktDBCandidates);
                if (tmpSet.andEmpty(als.candidates)) {
                    // no common candidates -> nothing to do
                    //if (DEBUG) System.out.println(" No common candidates: " + aktDBCandidates + "|||" + als.candidates);
                    continue;
                }
                // ALS does not overlap and common candidates exist
                aktDBAls[cand] = aktRcdb.alsPerCandidate[cand][i];
                //if (DEBUG) System.out.println(" setting aktDBAls[" + cand + "] = " + aktRcdb.alsPerCandidate[cand][i]);
                // get the candidates that are deleted from aktDBCandidates by als
                incDBCand[cand].set(aktDBCandidates);
                incDBCand[cand].andNot(als.candidates);
                // now get the common candidates of the new combination
                aktDBCandidates.and(als.candidates);
                // add the new indices
                aktDBIndices.or(als.indices);
                if (cand < maxDBCand) {
                    // look further
                    checkAlsDeathBlossomRecursive(cand + 1);
                } else {
                    // a valid ALS combination: check for eliminations
                    //if (DEBUG) System.out.println(" Valid combination!");
                    boolean found = false;
                    for (int j = 0; j < aktDBCandidates.size(); j++) {
                        int checkCand = aktDBCandidates.get(j);
                        if (aktDBAls[checkCand] != -1) {
                            // checkCand is used in the stemCell -> cant eliminate anything
                            //if (DEBUG) System.out.println(" checkCand " + checkCand + " skipped!");
                            continue;
                        }
                        boolean first = true;
                        for (int k = 0; k < aktDBAls.length; k++) {
                            if (aktDBAls[k] == -1) {
                                // no ALS for that candidate
                                continue;
                            }
                            if (first) {
                                dbIndicesPerCandidate.set(alses.get(aktDBAls[k]).indicesPerCandidat[checkCand]);
                                first = false;
                            } else {
                                dbIndicesPerCandidate.or(alses.get(aktDBAls[k]).indicesPerCandidat[checkCand]);
                            }
                        }
                        Sudoku.getBuddies(dbIndicesPerCandidate, tmpSet);
                        // no cannibalism
                        tmpSet.andNot(aktDBIndices);
                        // not in the stemCell
                        tmpSet.remove(stemCellIndex);
                        
                        
                        // possible eliminations?
                        //if (DEBUG) System.out.println(" checkCand = " + checkCand + ", buddies: " + tmpSet);
                        tmpSet.and(sudoku.getAllowedPositions()[checkCand]);
                        //if (DEBUG) System.out.println(" eliminations: " + tmpSet);
                        if (! tmpSet.isEmpty()) {
                            // we found a Death Blossom
                            // record the eliminations
                            found = true;
                            for (int k = 0; k < tmpSet.size(); k++) {
                                globalStep.addCandidateToDelete(tmpSet.get(k), checkCand);
                            }
                        }
                    }
                    // if eliminations were found, record the step
                    if (found) {
                        globalStep.addIndex(stemCellIndex);
                        // for every ALS record the RCs as fins and add the als
                        for (int k = 1; k <= 9; k++) {
                            if (aktDBAls[k] == -1) {
                                continue;
                            }
                            Als tmpAls = alses.get(aktDBAls[k]);
                            for (int l = 0; l < tmpAls.indicesPerCandidat[k].size(); l++) {
                                globalStep.addFin(tmpAls.indicesPerCandidat[k].get(l), k);
                            }
                            globalStep.addFin(stemCellIndex, k);
                            globalStep.addAls(tmpAls.indices, tmpAls.candidates);
                            globalStep.addRestrictedCommon(new RestrictedCommon(0, 0, k, 0, 1));
                        }

                        boolean writeIt = true;
                        int replaceIndex = -1;
                        String elim = null;
                        if (Options.getInstance().onlyOneAlsPerStep) {
                            elim = globalStep.getCandidateString();
                            Integer alreadyThere = deletesMap.get(elim);
                            if (alreadyThere != null) {
                                // a step already exists!
                                SolutionStep tmp = steps.get(alreadyThere);
                                if (tmp.getAlsesIndexCount() > globalStep.getAlsesIndexCount()) {
                                    writeIt = true;
                                    replaceIndex = alreadyThere;
                                } else {
                                    writeIt = false;
                                }
                            }
                        }
                        if (writeIt) {
                            if (replaceIndex != -1) {
                                steps.remove(replaceIndex);
                                steps.add(replaceIndex, (SolutionStep) globalStep.clone());
                            } else {
                                steps.add((SolutionStep) globalStep.clone());
                                if (elim != null) {
                                    deletesMap.put(elim, steps.size() - 1);
                                }
                            }
                        }
                        globalStep.reset();
                        globalStep.setType(SolutionType.DEATH_BLOSSOM);
                    }
                }
                // and back again
                aktDBCandidates.or(incDBCand[cand]);
                aktDBIndices.andNot(als.indices);
            }
        } else {
            // nothing to do -> next candidate
            aktDBAls[cand] = -1;
            checkAlsDeathBlossomRecursive(cand + 1);
        }
    }

    /**
     * Kann für XZ und XY-Wing verwendet werden: Die gemeinsamen Kandidaten (!= restricted common) zweier ALS prüfen,
     * schauen, ob es Kandidaten gibt, die außerhalb der ALS liegen und alle Kandidaten sehen -> können gelöscht werden
     */
    private void checkCandidatesToDelete(Als als1, Als als2, int restr1) {
        checkCandidatesToDelete(als1, als2, restr1, -1, -1, -1, null);
    }

    private void checkCandidatesToDelete(Als als1, Als als2, int restr1, int restr2, int restr3, int restr4) {
        checkCandidatesToDelete(als1, als2, restr1, restr2, restr3, restr4, null);
    }

    /**
     * Used for XZ, XY-Wing and Chain: Check the common candidates of als1 and als2 (minus all restrx).
     * If candidates exist, that are outside (als1 + als2) and see all occurences
     * of one of the common candidates (see above) they can be eliminated.<br>
     * Aside from the eliminations RCs are written as endo fins (merely for display).
     * 
     * @param als1 The first flanking ALS
     * @param als2 The second flanking ALS
     * @param als3 The middle ALS in an XY-Wing, the second last ALS for an ALS Chain;
     *   only used for correctly adding the RCs to the step
     * @param restr1 First RC (unused if -1)
     * @param restr2 Second RC (unused if -1)
     * @param restr3 Third RC (unused if -1)
     * @param restr4 Fourth RC (unused if -1)
     * @param forChain True if method is called for a chain: No restricted commons are added to step
     * @param forbiddenIndices If not null describes the cells where no eliminations are allowed
     *   (can be set for non cannibalistic chains)
     */
    private void checkCandidatesToDelete(Als als1, Als als2, int restr1, int restr2, 
            int restr3, int restr4, SudokuSet forbiddenIndices) {
        //boolean rcWritten = false;
        possibleRestrictedCommonsSet.set(als1.candidates);
        possibleRestrictedCommonsSet.and(als2.candidates);
        if (restr1 != -1 && restr1 != 0) {
            possibleRestrictedCommonsSet.remove(restr1);
        }
        if (restr2 != -1 && restr2 != 0) {
            possibleRestrictedCommonsSet.remove(restr2);
        }
        if (restr3 != -1 && restr3 != 0) {
            possibleRestrictedCommonsSet.remove(restr3);
        }
        if (restr4 != -1 && restr4 != 0) {
            possibleRestrictedCommonsSet.remove(restr4);
        }
        // possibleRestrictedCommons enthält jetzt alle Kandidaten, die in beiden ALS vorkommen,
        // außer dem restricted common selbst
        if (possibleRestrictedCommonsSet.isEmpty()) {
            // nichts zu tun
            return;
        }
        // Alle Positionen aller ALS sammeln (als3 kann null sein!)
        if (forbiddenIndices != null) {
            forbiddenIndexSet.set(forbiddenIndices);
        } else {
            forbiddenIndexSet.set(als1.indices);
            forbiddenIndexSet.or(als2.indices);
            // in an ALS-XY candidates may be eliminated from ALS c!
            //if (als3 != null) {
            //    forbiddenIndexSet.or(als3.indices);
            //}
        }
        // jetzt alle gemeinsamen Kandidaten prüfen
        for (int j = 0; j < possibleRestrictedCommonsSet.size(); j++) {
            int cand = possibleRestrictedCommonsSet.get(j);
            // Alle Positionen sammeln, in denen Kandidat cand1 in beiden ALS vorkommt
            restrictedCommonIndexSet.set(als1.indicesPerCandidat[cand]);
            restrictedCommonIndexSet.or(als2.indicesPerCandidat[cand]);
            // jetzt prüfen, ob es Buddies für alle diese Kandidaten gibt
            restrictedCommonSet.setAll();
//            for (int k = 0; k < restrictedCommonIndexSet.size(); k++) {
//                int rIndex = restrictedCommonIndexSet.get(k);
//                // die Position des Kandidatens selbst ist hier egal
//                restrictedCommonSet.and(Sudoku.buddies[rIndex]);
//            }
            Sudoku.getBuddies(restrictedCommonIndexSet, restrictedCommonSet);
            // die Positionen in den ALS werden explizit ausgenommen (für XY-Wing/XY-Chain nötig!)
            restrictedCommonSet.andNot(forbiddenIndexSet);
            restrictedCommonSet.and(sudoku.getAllowedPositions()[cand]);
            // jetzt alle noch offenen Kandidaten checken, die nicht Teil der ALS sind
            if (!restrictedCommonSet.isEmpty()) {
                // gefunden -> können alle gelöscht werden
                for (int l = 0; l < restrictedCommonSet.size(); l++) {
                    globalStep.addCandidateToDelete(restrictedCommonSet.get(l), cand);
                }
                // die gemeinsamen Kandidaten selbst werden fins
                for (int l = 0; l < restrictedCommonIndexSet.size(); l++) {
                    globalStep.addFin(restrictedCommonIndexSet.get(l), cand);
                }
            }
        }
//        for (int j = 0; j < possibleRestrictedCommonsSet.size(); j++) {
//            int cand = possibleRestrictedCommonsSet.get(j);
//            // Alle Positionen sammeln, in denen Kandidat cand1 in beiden ALS vorkommt
//            restrictedCommonIndexSet.set(als1.indicesPerCandidat[cand]);
//            restrictedCommonIndexSet.or(als2.indicesPerCandidat[cand]);
//            // jetzt prüfen, ob es Buddies für alle diese Kandidaten gibt
//            restrictedCommonSet.setAll();
//            for (int k = 0; k < restrictedCommonIndexSet.size(); k++) {
//                int rIndex = restrictedCommonIndexSet.get(k);
//                // die Position des Kandidatens selbst ist hier egal
//                restrictedCommonTmpSet.set(Sudoku.buddies[rIndex]);
//                restrictedCommonSet.and(restrictedCommonTmpSet);
//            }
//            // die Positionen in den ALS werden explizit ausgenommen (für XY-Wing/XY-Chain nötig!)
//            restrictedCommonSet.andNot(forbiddenIndexSet);
//            // jetzt alle noch offenen Kandidaten checken, die nicht Teil der ALS sind
//            indexSet.set(restrictedCommonIndexSet);
//            restrictedCommonIndexSet.not();
//            restrictedCommonIndexSet.and(sudoku.getAllowedPositions()[cand]);
//            restrictedCommonSet.and(restrictedCommonIndexSet);
//            if (!restrictedCommonSet.isEmpty()) {
//                // gefunden -> können alle gelöscht werden
//                for (int l = 0; l < restrictedCommonSet.size(); l++) {
//                    globalStep.addCandidateToDelete(restrictedCommonSet.get(l), cand);
//                }
//                // die gemeinsamen Kandidaten selbst werden fins
//                for (int l = 0; l < indexSet.size(); l++) {
//                    globalStep.addFin(indexSet.get(l), cand);
//                }
//            }
//        }
    }

    private void addRestrictedCommonToStep(Als als1, Als als2, int cand, boolean withChain) {
        // endoFins mit restricted common belegen und chain einschreiben
        indexSet.set(als1.indicesPerCandidat[cand]);
        indexSet.or(als2.indicesPerCandidat[cand]);
        for (int i = 0; i < indexSet.size(); i++) {
            globalStep.addEndoFin(indexSet.get(i), cand);
        }
        if (withChain) {
            // die chain soll für den kürzesten Weg erstellt werden
            int minDist = Integer.MAX_VALUE;
            int minIndex1 = -1;
            int minIndex2 = -1;
            for (int i1 = 0; i1 < als1.indicesPerCandidat[cand].size(); i1++) {
                for (int i2 = 0; i2 < als2.indicesPerCandidat[cand].size(); i2++) {
                    int index1 = als1.indicesPerCandidat[cand].get(i1);
                    int index2 = als2.indicesPerCandidat[cand].get(i2);
                    int dx = Sudoku.getLine(index1) - Sudoku.getLine(index2);
                    int dy = Sudoku.getCol(index1) - Sudoku.getCol(index2);
                    int dist = dx * dx + dy * dy;
                    if (dist < minDist) {
                        minDist = dist;
                        minIndex1 = index1;
                        minIndex2 = index2;
                    }
                }
            }
            int[] tmpChain = new int[2];
//            chain[0] = minIndex1 * 10 + cand1;
//            chain[1] = minIndex2 * 10 + cand1;
            tmpChain[0] = Chain.makeSEntry(minIndex1, cand, false);
            tmpChain[1] = Chain.makeSEntry(minIndex2, cand, false);
            globalStep.addChain(0, 1, tmpChain);
        }
    }

    /**
     * als1 and als2 are doubly linked by RCs rc1 and rc2; check whether the locked
     * set {als1 - rc1 - rc2 } can eliminate candidates that are not in als2.
     * 
     * The method has to be called twice with als1 and als2 swapped.
     * 
     * @param als1 The als that becomes a locked set
     * @param als2 The doubly linked second als, no candidates can be eliminated from it
     * @param rc1 The first Restricted Common
     * @param rc2 The second Restricted Common
     */
    private void checkDoublyLinkedAls(Als als1, Als als2, int rc1, int rc2) {
        // collect the remaining candidates
        possibleRestrictedCommonsSet.set(als1.candidates);
        possibleRestrictedCommonsSet.remove(rc1);
        possibleRestrictedCommonsSet.remove(rc2);
        if (possibleRestrictedCommonsSet.isEmpty()) {
            // nothing can be eliminated
            return;
        }
        // for any candidate left get all buddies, subtract als1 and als2 and check for eliminations
        for (int i = 0; i < possibleRestrictedCommonsSet.size(); i++) {
            int cand = possibleRestrictedCommonsSet.get(i);
            restrictedCommonIndexSet.setAll();
            for (int j = 0; j < als1.indicesPerCandidat[cand].size(); j++) {
                restrictedCommonIndexSet.and(Sudoku.buddies[als1.indicesPerCandidat[cand].get(j)]);
            }
            restrictedCommonIndexSet.andNot(als1.indices);
            restrictedCommonIndexSet.andNot(als2.indices);
            // check whether something can be eliminated
            restrictedCommonIndexSet.and(sudoku.getAllowedPositions()[cand]);
            if (! restrictedCommonIndexSet.isEmpty()) {
                for (int j = 0; j < restrictedCommonIndexSet.size(); j++) {
                    globalStep.addCandidateToDelete(restrictedCommonIndexSet.get(j), cand);
                }
            }
        }
    }

    /**
     * For all combinations of two ALS check whether they have one or two RC(s). An
     * RC is a candidate that is common to both ALS and where all instances of that
     * candidate in both ALS see each other.<br>
     * ALS with RC(s) may overlap as long as the overlapping area doesnt contain an RC.<br>
     * Two ALS can have a maximum of two RCs.
     * 
     * @param writeIndices If set the index of the first RC for alses[i] is written
     *   to startIndices[i], the index of the last RC is written to endIndices[i]
     *   (needed for chain search)
     * @param withOverlap If <code>false</code> overlapping ALS are not allowed
     */
    private void collectAllRestrictedCommons(boolean writeIndices, boolean withOverlap) {
        if (DEBUG) System.out.println("Entering collectAllRestrictedCommons");
        long ticks = System.currentTimeMillis();
        restrictedCommons.clear();
        if (writeIndices) {
            startIndices = new int[alses.size()];
            endIndices = new int[alses.size()];
        }
        // Try all combinations
        for (int i = 0; i < alses.size(); i++) {
            Als als1 = alses.get(i);
            if (writeIndices) {
                startIndices[i] = restrictedCommons.size();
            }
            //if (DEBUG) System.out.println("als1: " + SolutionStep.getAls(als1));
            for (int j = i + 1; j < alses.size(); j++) {
                Als als2 = alses.get(j);
                // check whether the ALS overlap (intersectionSet is needed later on anyway)
                intersectionSet.set(als1.indices);
                intersectionSet.and(als2.indices);
                if (!withOverlap && !intersectionSet.isEmpty()) {
                    // overlap is not allowed!
                    continue;
                }
                //if (DEBUG) System.out.println("als2: " + SolutionStep.getAls(als2));
                // restricted common: all buddies + the positions of the candidates themselves ANDed
                // check whether als1 and als2 have common candidates
                possibleRestrictedCommonsSet.set(als1.candidates);
                possibleRestrictedCommonsSet.and(als2.candidates);
                // possibleRestrictedCommons now contains all candidates common to both ALS
                if (possibleRestrictedCommonsSet.isEmpty()) {
                    // nothing to do!
                    continue;
                }
                int rcAnz = 0;
                RestrictedCommon newRC = null;
                for (int k = 0; k < possibleRestrictedCommonsSet.size(); k++) {
                    int cand = possibleRestrictedCommonsSet.get(k);
                    // Get all positions of cand in both ALS
                    restrictedCommonIndexSet.set(als1.indicesPerCandidat[cand]);
                    restrictedCommonIndexSet.or(als2.indicesPerCandidat[cand]);
                    // non of these positions may be in the overlapping area of the two ALS
                    if (! restrictedCommonIndexSet.andEmpty(intersectionSet)) {
                        // at least on occurence of cand is in overlap -> forbidden
                        continue;
                    }
                    // now check if all those candidates see each other
                    // cannot be replaced by Sudoku.getBuddies() (positions of candidates)
                    restrictedCommonSet.setAll();
                    for (int l = 0; l < restrictedCommonIndexSet.size(); l++) {
                        int rIndex = restrictedCommonIndexSet.get(l);
                        // the positions of the candidates themselves have to be included
                        restrictedCommonTmpSet.set(Sudoku.buddies[rIndex]);
                        restrictedCommonTmpSet.add(rIndex);
                        restrictedCommonSet.and(restrictedCommonTmpSet);
                    }
                    // we now know all common buddies, all common candidates must be in that set
                    if (restrictedCommonIndexSet.andEquals(restrictedCommonSet)) {
                        // found -> cand is RC
                        if (rcAnz == 0) {
                            newRC = new RestrictedCommon(i, j, cand);
                            restrictedCommons.add(newRC);
                        } else {
                            newRC.cand2 = cand;
                        }
                        rcAnz++;
                    }
                }
                if (rcAnz > 0) {
                    //if (DEBUG) System.out.println(newRC + ": " + rcAnz + " RCs for ALS " + SolutionStep.getAls(als1) + "/" + SolutionStep.getAls(als2));
                }
            }
            if (writeIndices) {
                endIndices[i] = restrictedCommons.size();
            }
        }
        ticks = System.currentTimeMillis() - ticks;
        if (DEBUG) System.out.println("collectAllRestrictedCommons(): " + ticks + "ms; restrictedCommon size: " + restrictedCommons.size());
    }

    private void collectAllAlses() {
        long ticks = System.currentTimeMillis();
        alses = Als.getAlses(sudoku);
        ticks = System.currentTimeMillis() - ticks;
        if (DEBUG) System.out.println("collectAllAlses(): " + ticks + "ms");
//        for (int i = 0; i < alses.size(); i++) {
//            System.out.println(i + ": " + alses.get(i));
//        }
    }

    /**
     * Collect all cells, that can see all instances of a candidate within an ALS.
     * For every cell thats not yet set all ALS per candidate are stored; when an ALS
     * for a cell is found a mask is updated with the respective candidate; after that
     * method that mask is checked against the real candidate mask of the cell: if they are
     * equal, a possible Death Blossom exists
     * 
     * Calculate all buddies for all candidates in all ALSs -> they are all possible stem cells
     */
    private void collectAllRCsForDeathBlossom() {
        long ticks = System.currentTimeMillis();
        for (int i = 0; i < rcdb.length; i++) {
            rcdb[i] = null;
        }
        for (int i = 0; i < alses.size(); i++) {
            Als act = alses.get(i);
            for (int j = 1; j <= 9; j++) {
                if (! act.candidates.contains(j)) {
                    // candidate not in als -> nothing to do
                    continue;
                }
                // collect all buddies for that candidate
//                restrictedCommonSet.setAll();
//                for (int k = 0; k < act.indicesPerCandidat[j].size(); k++) {
//                    restrictedCommonSet.and(Sudoku.buddies[act.indicesPerCandidat[j].get(k)]);
//                }
                Sudoku.getBuddies(act.indicesPerCandidat[j], restrictedCommonSet);
                // the ALS itself cant be a stem cell
                restrictedCommonSet.andNot(act.indices);
                // restrict to available candidates
                restrictedCommonSet.and(sudoku.getAllowedPositions()[j]);
                // now write them
                if (restrictedCommonSet.isEmpty()) {
                    //nothing left!
                    continue;
                }
                for (int k = 0; k < restrictedCommonSet.size(); k++) {
                    int index = restrictedCommonSet.get(k);
                    if (rcdb[index] == null) {
                        rcdb[index] = new RCForDeathBlossom();
                    }
                    rcdb[index].addAlsForCandidate(i, j);
                }
            }
        }
        ticks = System.currentTimeMillis() - ticks;
        if (DEBUG) System.out.println("collectAllRCsForDeathBlossom(): " + ticks + "ms");
        // DEBUG printout
//        if (DEBUG) {
//            for (int i = 0; i < rcdb.length; i++) {
//                if (rcdb[i] == null) {
//                    continue;
//                }
//                String out = "  " + SolutionStep.getCellPrint(i) + " (" + sudoku.getCell(i).getAnzCandidates(candType) + "):";
//                for (int j = 1; j <= 9; j++) {
//                    out += " " + rcdb[i].indices[j];
//                }
//                System.out.println(out);
//            }
//        }
    }

    class RCForDeathBlossom {
        short candMask;       // mask with every candidate set that has at least one ALS
        int[][] alsPerCandidate = new int[10][100]; // all ALSs for every candidate
        int[] indices = new int[10]; // indices into alsPerCandidate
        
        public RCForDeathBlossom() {
        }
        
        public void addAlsForCandidate(int als, int candidate) {
            if (indices[candidate] < alsPerCandidate[candidate].length) {
                alsPerCandidate[candidate][indices[candidate]++] = als;
                candMask |= (1 << (candidate - 1));
            }
        }
    }

    public static void main(String[] args) {
        DEBUG = true;
        //Sudoku sudoku = new Sudoku(true);
        Sudoku sudoku = new Sudoku();
        //sudoku.setSudoku(":0361:4:..5.132673268..14917...2835..8..1.262.1.96758.6..283...12....83693184572..723.6..:434 441 442 461 961 464 974:411:r7c39 r6c1b9 fr3c3");
        //sudoku.setSudoku(":0300:4:135792..4.9.8315278725461...8.917.5....3.4.783.72.89.1...673.1....1297..7..485..6:653 472 473 277 483 683 388:481:c28 r68");
        //sudoku.setSudoku(":0000:x:7.2.34.8.........2.8..51.74.......51..63.27..29.......14.76..2.8.........2.51.8.7:::");
        //sudoku.setSudoku(":0000:x:5837..4.2.1.............1....9...63.........47.1.45928..52.38....6..427.12..6.3..:::");
//        sudoku.setSudoku(":0000:x:..65.849..15.4.7.2..9...65.9..867315681.5.279..7.9.864.63...5..1...3.94..9.7..1..:324 326 331 332 339 261 262 364 366 871 891::");        
//        sudoku.setSudoku(":0000:x:.78.6519393..1..7.516739842.9..76.1..6539.28..4..2..69657142938.2.983.5.389657421:::");        
//        sudoku.setSudoku(":0000:x:65.17....382469.5...18..6...36.4...5.27...46.845.1....2.3.845.6...5...825.8.21.34:917 931 738 739 246 147 747 947 355 356 959 266 366 767 967 978 181 981::");        
//        sudoku.setSudoku(":0000:x:8...742.5.248.57...3.621.9...94.2....1...8.2.2....63...5.263.7...214965....587..2:541 847 849 653 469 869 391 491 497::");        
//        sudoku.setSudoku(":0000:x:1.7.5.....8.17..3.3...98...7628394..8.1245.67..471682....58...6.1..67.9....92.5..:927 237 637 438 569 372 277 377 281 389 392 199::");        
//        sudoku.setSudoku(":0000:x:8..7...4...43....667.1248.9.6.2.9...4..871625...6.3.9.3.6.12.871....73.2.2..3...4:112 512 513 913 515 922 525 128 343 543 743 167 972 982 485 585 985 596::");        
        // doubly linked ALS-XZ, 16 eliminations
        //sudoku.setSudoku(":9001:23568:3.2....1.1..97........1....5..18.6....15...7..8.74.1..9....1.27.1.4.......5..79.1::285 286 291 292 385 386 585 612 616 619 626 634 636 685 686 834:");
        // DeathBlossom Beispiel aus Sudopedia
        //sudoku.setSudoku(":0000:x:6....9...38..7....1....8.745...2..1946..9..3.9..8......9....1.57.5.8.6.2.16.4.7.3:517 818 624 626 928 537 665 666 468 273 873::");
        // Death Blossom von http://www.gamesudoku.blogspot.com/2007/02/advanced-death-blossom.html r5c1<>1
        //sudoku.setSudoku(":0000:x:+36+1..+492+7.7.23.8.....7.1...8.........+356.84.........+83...4+27.....3+915.6..92....4.:432 535 835 747 148 751 261 761 463 767 577 178 179 691 397 899::");
        // Five Petal Death Blossom (PIsaacson
        //sudoku.setSudoku(":0000:x:.......124...9...........54.7.2.....6.....4.....1.8...718......9...3.7..532......:723 724 326 526 626 726 331 831 232 133 635 735 835 136 236 143 947 149 262 967 268 276 977 278 484 684 486 586 686 488 688 689 196 997 199::");
        // Very long running time
        //sudoku.setSudoku(":9001:369:.......173.+1.8+7.+5+2+7......+3+8+4+371..+5+86+1..+84+53+7+9+985.+7.+1+2+42+7....84+5+51.7..+2.+3...5..+7.+1:215 216 416 235 236 436:396 615 635 686 686 686 686 696 696 696 696 915 935 986 996:");
        // ALS Chain not found: Almost Locked Set Chain: A=r3c4 {57}, B=r4c4 {57}, C=r4c69 {257}, D=r56c8 {567}, RCs=5,7, X=7 => r3c8<>7
        sudoku.setSudoku(":9003:7:.+6.+4.+9+213+94.+2+3+1+68.2+1+3.6+8+9.+46+89.+1.4+3.+3..8..+1.....3..+8..+43+61..5+9+8.+5.+94+37+26+7+9+2+6+8+5+3+4+1:753 256 756 763 266 766 569:738:");
        AlsSolver as = new AlsSolver(null);
        as.setSudoku(sudoku);
        long millis = System.currentTimeMillis();
        int itAnz = 1;
        for (int i = 0; i < itAnz; i++) {
//            as.getAlsXZ();
//            as.getAlsXYWing();
            as.getAlsXYChain();
//            as.getAlsDeathBlossom();
//            as.steps = as.getAllAlses(sudoku);
        }
        millis = (System.currentTimeMillis() - millis) / itAnz;
        System.out.println("Find all ALS-XX: " + millis + "ms");
        Collections.sort(as.steps);
        for (int i = 0; i < as.steps.size(); i++) {
            System.out.println(as.steps.get(i));
        }
        System.out.println("Total: " + as.steps.size());
    }
}

class AlsComparator implements Comparator<SolutionStep> {

    /**
     * - Nach Anzahl zu löschender Kandidaten
     * - Nach Äquivalenz (gleiche zu löschende Kandidaten)
     * - Nach Indexsumme der zu löschenden Kandidaten
     * - Nach Anzahl ALS (gibt XZ vor XY-Wing)
     * - Nach Anzahl Indexe in allen ALS zusammen
     */
    @Override
    public int compare(SolutionStep o1, SolutionStep o2) {
        int sum1 = 0,  sum2 = 0;

        // zuerst nach Anzahl zu löschende Kandidaten (absteigend!)
        int result = o2.getCandidatesToDelete().size() - o1.getCandidatesToDelete().size();
        if (result != 0) {
            return result;        // nach Äquivalenz (gleiche zu löschende Kandidaten)
        }
        if (!o1.isEquivalent(o2)) {
            // nicht äquivalent: nach Indexsumme der zu löschenden Kandidaten
            sum1 = o1.getIndexSumme(o1.getCandidatesToDelete());
            sum2 = o1.getIndexSumme(o2.getCandidatesToDelete());
            return sum1 == sum2 ? 1 : sum1 - sum2;
        }

        // Nach Anzahl ALS
        result = o1.getAlses().size() - o2.getAlses().size();
        if (result != 0) {
            return result;        // Nach Anzahl Kandidaten in allen ALS
        }
        result = o1.getAlsesIndexCount() - o2.getAlsesIndexCount();
        if (result != 0) {
            return result;        // zuletzt nach Typ
        }
        return o1.getType().ordinal() - o2.getType().ordinal();
    }
}
