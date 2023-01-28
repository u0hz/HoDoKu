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
 * Anzahl Kandidaten im Grid ermitteln, verdoppeln -> Anzahl Table-Einträge
 *
 * Zum Ermitteln der Möglichkeiten Sudoku kopieren, Kandidat setzen/löschen und alle
 * Singles und resultierenden Eliminierungen loggen (2 Sets pro Kandidat -> 18 Sets pro Startpunkt).
 *
 * Beim Expandieren nur jene Möglichkeiten dazu nehmen, die neu sind (Index auf expandierte
 * Knoten mitführen, Länge begrenzen)
 *
 * Mögliche Tests:
 *   1. Eine Chain:
 *        - Zwei Werte gesetzt in einer Zelle (Setz-Sets verunden) -> Startbedingung falsch
 *        - Zwei Werte gesetzt in einem Haus (Setz-Sets mit Haus-Sets (?) verunden, anz > 1 -> Konflikt) -> Startbedingung falsch
 *        - Zelle ohne Kandidat (Lösch-Sets mit ~allowedPositions verodern, alles verunden, mit ~Setz-Sets verunden -> darf keine 1 sein) -> Startbedingung falsch
 *        - Kandidat in einer Zelle gesetzt und gelöscht (Setz-Sets mit Lösch-Sets verunden -> muss leer sein) -> Startbedingung falsch
 *        - Alle Kandidaten für einen Wert in einem Haus gelöscht (allowedPositions auf ein Haus einschränken, mit Lösch-Set verunden, darf nicht gleich sein)
 *   2. Zwei Chains für selben Start-Kandidaten (gesetzt und gelöscht)
 *        - Beide Chains enthalten den selben gesetzten Wert -> Wert kann gesetzt werden (Setz-Sets verunden)
 *        - Beide Chains enthalten den selben gelöschten Kandidaten -> Kandidat kann gelöscht werden
 *   3. Chains für alle Kandidaten in einem Haus/Zelle gesetzt
 *        - Alle Chains enthalten den selben gesetzten Wert -> Wert kann gesetzt werden (Setz-Sets verunden)
 *        - Alle Chains enthalten den selben gelöschten Kandidaten -> Kandidat kann gelöscht werden
 *
// * Chains werden in einem Array gespeichert: aaa b cc d
// *   aaa: Index auf Eintrag, der diesen Eintrag bewirkt (für Chain-Rekonstruktion)
// *   b:   0 für Kandidat wird gelöscht, 1 für Kandidat wird gesetzt
// *   cc:  Index der betroffenen Zelle
// *   d:   Betroffener Kandidat
// *   Versuch: aaa dreifach in eigenem Array speichern
 * Chains werden in einem Array gespeichert: aaa b c
 *   aaa: Index der betroffenen Zelle
 *   b:   0 für Kandidat wird gelöscht, 1 für Kandidat wird gesetzt
 *   c:   Betroffener Kandidat
 *   Versuch: aaa dreifach in eigenem Array speichern
 *
 * PROBLEM Rekonstruktion der Chain
 *    Bsp: Originalgrid, Chain für r9c1=4: die vorletzte 4 wird auf einem anderen Weg erreicht -> Fehler in Chain!
 *
 * OPTIMIERUNGEN:
 *    Es gibt zu viele chains: Für jedes Setzen/Löschen nur eine contradiction und eine verity (jeweils die mit
 *    der kürzesten chain). Dazu die Chains in globalStep statisch machen, befüllen, zählen und nur die kürzeste kopieren
 * 
 * 20081013: AIC added (combined with Nice Loops)
 *    For every Nice Loop that starts with a string inference out of the start cell and ends
 *    with a weak inference into the start cell the AIC (start cell - last strong inference)
 *    is checked. If ot gives more than one elimination, it is stored as AIC instead of as Nice Loop.
 *    The check is done for discontinuous loops only.
 *  
 *    AIC eliminations: 
 *      - if the candidates of the endpoints are equal, all candidates can be eliminated
 *        that see both endpoints
 *      - if the candidates are not equal, cand A can be eliminated in cell b and vice versa
 *
 * @author Bernhard Hobiger
 */
public class TablingSolver extends AbstractSolver {
    private static boolean DEBUG = false;
    private static final int MAX_REC_DEPTH = 50;
    private static TablingComparator tablingComparator = null;
    private List<SolutionStep> steps; // gefundene Lösungsschritte
    private SolutionStep globalStep = new SolutionStep(SolutionType.HIDDEN_SINGLE);
    private SortedMap<String, Integer> deletesMap = new TreeMap<String, Integer>();  // alle bisher gefundenen Chains: Eliminierungen und Index in steps
    private TableEntry[] onTable = null;   // Ein Eintrag für jede Zelle und jeden Kandidaten (Wert gesetzt)
    private TableEntry[] offTable = null;  // Ein Eintrag für jede Zelle und jeden Kandidaten (Kandidat gelöscht)
    private Sudoku savedSudoku;            // Sudoku im Ausgangszustand (für Erstellen der Tables)
    private int[][] retIndices = new int[MAX_REC_DEPTH][5]; // indices ermitteln
//    private int[][] retIndices1 = new int[MAX_REC_DEPTH][5]; // indices ermitteln
    private boolean chainsOnly = true; // search only for chains, not for nets
    private boolean allSteps = false; // search for all steps; if false, chains with ALS nodes are only searched for if no other chain exists
    private boolean withGroupNodes = false;
    private boolean withAlsNodes = false;
    private boolean onlyGroupedNiceLoops = false;
    private List<GroupNode> groupNodes = null;  // a list with all group nodes for a given sudoku
    private List<Als> alses = null; // a list with all available ALS for a given sudoku
//    private SudokuSet alsBuddies = new SudokuSet(); // cells that can see all the cells of the als
    private SudokuSet[] alsEliminations = new SudokuSet[10]; // all cells with elminations for an als, sorted by candidate
    private TreeMap<Integer, Integer> chainAlses = new TreeMap<Integer, Integer>(); // Map containing the new indices of all alses, that have already been written to globalStep
    private List<TableEntry> extendedTable = new ArrayList<TableEntry>(); // Tables for group nodes, ALS, AUR...
    private SortedMap<Integer, Integer> extendedTableMap = new TreeMap<Integer, Integer>(); // entry -> index in extendedTable
    private int extendedTableIndex = 0; // current index in extendedTable
    private SimpleSolver simpleSolver; // zum Erstellen der TableEntries
    private SudokuSet tmpSet = new SudokuSet();  // Für alle möglichen Operationen
    private SudokuSet tmpSet1 = new SudokuSet(); // Für alle möglichen Operationen
    private SudokuSet tmpSet2 = new SudokuSet(); // Für alle möglichen Operationen
    private SudokuSet tmpSetC = new SudokuSet(); // Für Chain-Aufbau
    private SudokuSet[] tmpOnSets = new SudokuSet[10];  // Für Checks: alle Kandidaten in einem Haus gesetzt
    private SudokuSet[] tmpOffSets = new SudokuSet[10]; // Für Checks: alle Kandidaten in einem Haus gesetzt
    private List<TableEntry> entryList = new ArrayList<TableEntry>();  // Tableeinträge für ein Haus oder eine Zelle
    private List<SolutionStep> singleSteps = new ArrayList<SolutionStep>();  // für Naked und Hidden Singles
    private int[] chain = new int[Options.getInstance().maxTableEntryLength]; // globale chain für buildChain()
    private int chainIndex = 0; // Index des nächsten Elements in chain[]
    private int[][] mins = new int[200][Options.getInstance().maxTableEntryLength]; // globale chains für networks
    private int[] minIndexes = new int[mins.length]; // Indexe der nächsten Elemente in mins[]
    private int actMin = 0;                          // derzeit aktuelles min
    private int[] tmpChain = new int[Options.getInstance().maxTableEntryLength]; // globale chain für addChain()
    private Chain[] tmpChains = new Chain[9];
    private int tmpChainsIndex = 0;
    private SudokuSet lassoSet = new SudokuSet();  // für addChain: enthält alle Zellen-Indices der Chain

    /** Creates a new instance of TablingSolver */
    public TablingSolver(SudokuSolver solver) {
        super(solver);
        
        simpleSolver = new SimpleSolver(null);
        
        for (int i = 0; i < tmpOnSets.length; i++) {
            tmpOnSets[i] = new SudokuSet();
            tmpOffSets[i] = new SudokuSet();
        }
        steps = new ArrayList<SolutionStep>();
        if (tablingComparator == null) {
            tablingComparator = new TablingComparator();
        }
        for (int i = 0; i < tmpChains.length; i++) {
            tmpChains[i] = new Chain();
            tmpChains[i].chain = new int[Options.getInstance().maxTableEntryLength];
        }

        onTable = new TableEntry[810];
        offTable = new TableEntry[810];
        for (int i = 0; i < onTable.length; i++) {
            onTable[i] = new TableEntry();
            offTable[i] = new TableEntry();
        }

        for (int i = 0; i < alsEliminations.length; i++) {
            alsEliminations[i] = new SudokuSet();
        }
    }

    private void resetTmpChains() {
        for (int i = 0; i < tmpChains.length; i++) {
            tmpChains[i].reset();
        }
        tmpChainsIndex = 0;
    }

    @Override
    protected SolutionStep getStep(SolutionType type) {
        SolutionStep result = null;
        switch (type) {
            case NICE_LOOP:
            case CONTINUOUS_NICE_LOOP:
            case DISCONTINUOUS_NICE_LOOP:
            case AIC:
                withGroupNodes = false;
                withAlsNodes = false;
                allSteps = false;
                result = getNiceLoops();
                break;
            case GROUPED_NICE_LOOP:
            case GROUPED_CONTINUOUS_NICE_LOOP:
            case GROUPED_DISCONTINUOUS_NICE_LOOP:
            case GROUPED_AIC:
                withGroupNodes = true;
                withAlsNodes = Options.getInstance().allowAlsInTablingChains;
                allSteps = false;
                result = getNiceLoops();
                break;
            case FORCING_CHAIN:
            case FORCING_CHAIN_CONTRADICTION:
            case FORCING_CHAIN_VERITY:
                steps.clear();
                withGroupNodes = true;
                withAlsNodes = Options.getInstance().allowAlsInTablingChains;
                allSteps = false;
                getForcingChains();
                if (steps.size() > 0) {
                    Collections.sort(steps, tablingComparator);
                    result = steps.get(0);
                }
                break;
            case FORCING_NET:
            case FORCING_NET_CONTRADICTION:
            case FORCING_NET_VERITY:
                steps.clear();
                withGroupNodes = true;
                withAlsNodes = Options.getInstance().allowAlsInTablingChains;
                allSteps = false;
                getForcingNets();
                if (steps.size() > 0) {
                    Collections.sort(steps, tablingComparator);
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
            case NICE_LOOP:
            case CONTINUOUS_NICE_LOOP:
            case DISCONTINUOUS_NICE_LOOP:
            case AIC:
            case GROUPED_NICE_LOOP:
            case GROUPED_CONTINUOUS_NICE_LOOP:
            case GROUPED_DISCONTINUOUS_NICE_LOOP:
            case GROUPED_AIC:
                for (Candidate cand : step.getCandidatesToDelete()) {
                    sudoku.delCandidate(cand.getIndex(), candType, cand.getValue());
                }
                break;
            case FORCING_CHAIN:
            case FORCING_CHAIN_CONTRADICTION:
            case FORCING_CHAIN_VERITY:
            case FORCING_NET:
            case FORCING_NET_CONTRADICTION:
            case FORCING_NET_VERITY:
                if (step.getValues().size() > 0) {
                    for (int i = 0; i < step.getValues().size(); i++) {
                        int value = step.getValues().get(i);
                        int index = step.getIndices().get(i);
                        sudoku.setCell(index, value);
                    }
                } else {
                    for (Candidate cand : step.getCandidatesToDelete()) {
                        sudoku.delCandidate(cand.getIndex(), candType, cand.getValue());
                    }
                }
                break;
            default:
                handled = false;
        }
        return handled;
    }

    public List<SolutionStep> getAllNiceLoops(Sudoku sudoku) {
        long ticks = System.currentTimeMillis();
        Sudoku save = getSudoku();
        setSudoku(sudoku);
        steps = new ArrayList<SolutionStep>();
        withGroupNodes = false;
        withAlsNodes = false;
        allSteps = true;
        doGetNiceLoops();
        Collections.sort(this.steps);
        ticks = System.currentTimeMillis() - ticks;
        if (DEBUG) System.out.println("getAllNiceLoops() gesamt: " + ticks + "ms");
        if (save != null) {
            setSudoku(save);
        }
        return steps;
    }

    public List<SolutionStep> getAllGroupedNiceLoops(Sudoku sudoku) {
        long ticks = System.currentTimeMillis();
        Sudoku save = getSudoku();
        setSudoku(sudoku);
        steps = new ArrayList<SolutionStep>();
        withGroupNodes = true;
        withAlsNodes = Options.getInstance().allowAlsInTablingChains;
        allSteps = true;
        onlyGroupedNiceLoops = true;
        doGetNiceLoops();
        onlyGroupedNiceLoops = false;
        Collections.sort(this.steps);
        ticks = System.currentTimeMillis() - ticks;
        if (DEBUG) System.out.println("getAllGroupedNiceLoops() gesamt: " + ticks + "ms");
        if (save != null) {
            setSudoku(save);
        }
        return steps;
    }

    public List<SolutionStep> getAllForcingChains(Sudoku sudoku) {
        Sudoku save = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldSteps = steps;
        steps = new ArrayList<SolutionStep>();
        long millis1 = System.currentTimeMillis();
        withGroupNodes = true;
        withAlsNodes = Options.getInstance().allowAlsInTablingChains;
        allSteps = true;
        getForcingChains();
        Collections.sort(steps, tablingComparator);
        millis1 = System.currentTimeMillis() - millis1;
        if (DEBUG) System.out.println("getAllForcingChains() gesamt: " + millis1 + "ms");
        List<SolutionStep> result = steps;
        steps = oldSteps;
        if (save != null) {
            setSudoku(save);
        }
        return result;
    }

    public List<SolutionStep> getAllForcingNets(Sudoku sudoku) {
        Sudoku save = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> oldSteps = steps;
        steps = new ArrayList<SolutionStep>();
        long millis1 = System.currentTimeMillis();
        //withGroupNodes = true;
        withGroupNodes = true;
        withAlsNodes = Options.getInstance().allowAlsInTablingChains;
        allSteps = true;
        getForcingNets();
        Collections.sort(steps, tablingComparator);
        millis1 = System.currentTimeMillis() - millis1;
        if (DEBUG) System.out.println("getAllForcingNets() gesamt: " + millis1 + "ms");
        List<SolutionStep> result = steps;
        steps = oldSteps;
        if (save != null) {
            setSudoku(save);
        }
        return result;
    }

    public void initForKrakenSearch() {
        deletesMap.clear();
        // Tabellen befüllen
        long ticks = System.currentTimeMillis();
        chainsOnly = true;
        // search for everything
        fillTables();
        fillTablesWithGroupNodes();
        if (Options.getInstance().allowAlsInTablingChains) {
            fillTablesWithAls();
        }
        ticks = System.currentTimeMillis() - ticks;
        if (DEBUG) System.out.println("fillTables(): " + ticks + "ms");
        printTableAnz();
        //printTable("r1c6=6 fill", onTable[56]);
        //printTable("r3c2<>8 fill", offTable[198]);

        // Einträge expandieren
        ticks = System.currentTimeMillis();
        expandTables(onTable);
        expandTables(offTable);
        ticks = System.currentTimeMillis() - ticks;
        if (DEBUG) System.out.println("expandTables(): " + ticks + "ms");
        printTableAnz();
        //printTable("r1c6=6 expand", onTable[56]);
        //printTable("r3c2<>8 expand", offTable[198]);
    }
    
    /**
     * Search for Kraken Fish Type 1: if a chain starting and ending with
     * a weak link exists from every cell in fins to candidate in index,
     * a KF Type 1 exists.
     * 
     * @param fins Set with all fins
     * @param index Index of destination cell
     * @param candidate Candidate in destination cell
     * @return true if a KF exists, false otherwise
     */
    public boolean checkKrakenTypeOne(SudokuSet fins, int index, int candidate) {
        for (int i = 0; i < fins.size(); i++) {
            int tableIndex = fins.get(i) * 10 + candidate;
            if (! onTable[tableIndex].offSets[candidate].contains(index)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check for Kraken Fish Type 2: If for all cells in indices chains starting
     * and ending in a weak link exist to a candidate, a Kraken Fish Type 2 exists.
     * A set with all cells holding a target for the KF is returned.
     * 
     * @param indices Set with all starting cells
     * @param result Set that contains possible targets for Kraken Fishes
     * @param startCandidate The fish candidate
     * @param endCandidate The candidate for which the search is made
     * @return true if a KF exists, false otherwise
     */
    public boolean checkKrakenTypeTwo(SudokuSet indices, SudokuSet result, int startCandidate, int endCandidate) {
        result.set(sudoku.getAllowedPositions()[endCandidate]);
        result.andNot(indices);
        for (int i = 0; i < indices.size(); i++) {
            int tableIndex = indices.get(i) * 10 + startCandidate;
            result.and(onTable[tableIndex].offSets[endCandidate]);
        }
        return ! result.isEmpty();
    }
    
    public Chain getKrakenChain(int startIndex, int startCandidate, int endIndex, int endCandidate) {
        globalStep.reset();
        resetTmpChains();
        addChain(onTable[startIndex * 10 + startCandidate], endIndex, endCandidate, false);
        return globalStep.getChains().get(0);
    }

    private SolutionStep getNiceLoops() {
        steps = new ArrayList<SolutionStep>();
        doGetNiceLoops();
        if (steps.size() > 0) {
            Collections.sort(steps);
            return steps.get(0);
        }
        return null;
    }

    private void getForcingChains() {
        chainsOnly = true;
        doGetForcingChains();
    }

    private void getForcingNets() {
        chainsOnly = false;
        doGetForcingChains();
    }

    private void doGetNiceLoops() {
        deletesMap.clear();
        // Tabellen befüllen
        long ticks = System.currentTimeMillis();
        chainsOnly = true;
        fillTables();
        if (withGroupNodes) {
            fillTablesWithGroupNodes();
        }
        if (withAlsNodes) {
            fillTablesWithAls();
        }
        ticks = System.currentTimeMillis() - ticks;
        if (DEBUG) System.out.println("fillTables(): " + ticks + "ms");
        printTableAnz();
        //printTable("r5c6=2 fill", onTable[412]);
        //printTable("r8c6<>4 fill", offTable[684]);

        // Einträge expandieren
        ticks = System.currentTimeMillis();
        expandTables(onTable);
        expandTables(offTable);
        ticks = System.currentTimeMillis() - ticks;
        if (DEBUG) System.out.println("expandTables(): " + ticks + "ms");
        printTableAnz();
        //printTable("r5c6=2 expand", onTable[412]);
        //printTable("r8c6<>4 expand", offTable[684]);

        // ok, hier beginnt der Spass!
        ticks = System.currentTimeMillis();
        checkNiceLoops(onTable);
        checkNiceLoops(offTable);
        checkAics(offTable);
        ticks = System.currentTimeMillis() - ticks;
        if (DEBUG) System.out.println("checkNiceLoops(): " + ticks + "ms");

//        if (! allSteps && steps.size() > 0) {
//            // we already have a step, skip the ALS search!
//            return;
//        }
//        
//        // Jetzt noch einmal alles, aber mit ALS
//        if (withAlsNodes) {
//            // Tabellen neu befüllen
//            ticks = System.currentTimeMillis();
//            fillTablesWithAls();
//            ticks = System.currentTimeMillis() - ticks;
//            Logger.getLogger(getClass().getName()).log(Level.FINE, "fillTablesAls(): " + ticks + "ms");
//            printTableAnz();
//            //printTable("r6c3=1 fill", onTable[471]);
//            //printTable("r3c2<>8 fill", offTable[198]);
//
//            // Einträge expandieren
//            ticks = System.currentTimeMillis();
//            expandTables(onTable);
//            expandTables(offTable);
//            ticks = System.currentTimeMillis() - ticks;
//            Logger.getLogger(getClass().getName()).log(Level.FINE, "expandTablesAls(): " + ticks + "ms");
//            printTableAnz();
//            //printTable("r6c3=1 expand", onTable[471]);
//            //printTable("r3c2<>8 expand", offTable[198]);
//
//            // ok, hier beginnt der Spass!
//            ticks = System.currentTimeMillis();
//            checkNiceLoops(onTable);
//            checkNiceLoops(offTable);
//            ticks = System.currentTimeMillis() - ticks;
//            Logger.getLogger(getClass().getName()).log(Level.FINE, "checkNiceLoopsAls(): " + ticks + "ms");
//        }
    }

    private void doGetForcingChains() {
        deletesMap.clear();
        // Tabellen befüllen
        long ticks = System.currentTimeMillis();
        fillTables();
        if (withGroupNodes) {
            fillTablesWithGroupNodes();
        }
        if (withAlsNodes) {
            fillTablesWithAls();
        }
        ticks = System.currentTimeMillis() - ticks;
        if (DEBUG) System.out.println("fillTables(): " + ticks + "ms");
        printTableAnz();
        //printTable("r6c8=1 fill", onTable[521]);
        //printTable("r6c8<>1 fill", offTable[521]);

        // Einträge expandieren
        ticks = System.currentTimeMillis();
        expandTables(onTable);
        expandTables(offTable);
        ticks = System.currentTimeMillis() - ticks;
        if (DEBUG) System.out.println("expandTables(): " + ticks + "ms");
        printTableAnz();
        //printTable("r6c8=1 expand", onTable[521]);
        //printTable("r6c8<>1 expand", offTable[521]);

        // ok, hier beginnt der Spass!
        ticks = System.currentTimeMillis();
        checkForcingChains();
        ticks = System.currentTimeMillis() - ticks;
        if (DEBUG) System.out.println("checkChains(): " + ticks + "ms");

        if (! allSteps && steps.size() > 0) {
            // we already have a step, skip the ALS search!
            return;
        }
        
//        if (withAlsNodes) {
//            // Tabellen neu befüllen
//            ticks = System.currentTimeMillis();
//            fillTablesWithAls();
//            ticks = System.currentTimeMillis() - ticks;
//            Logger.getLogger(getClass().getName()).log(Level.FINE, "fillTablesAls(): " + ticks + "ms");
//            printTableAnz();
//            //printTable("r9c2=1 fill", onTable[731]);
//            //printTable("r6c8<>1 fill", offTable[521]);
//
//            // Einträge expandieren
//            ticks = System.currentTimeMillis();
//            expandTables(onTable);
//            expandTables(offTable);
//            ticks = System.currentTimeMillis() - ticks;
//            Logger.getLogger(getClass().getName()).log(Level.FINE, "expandTablesAls(): " + ticks + "ms");
//            printTableAnz();
//            //printTable("r9c2=1 expand", onTable[731]);
//            //printTable("r6c8<>1 expand", offTable[521]);
//
//            // ok, hier beginnt der Spass!
//            ticks = System.currentTimeMillis();
//            checkForcingChains();
//            ticks = System.currentTimeMillis() - ticks;
//            Logger.getLogger(getClass().getName()).log(Level.FINE, "checkChainsAls(): " + ticks + "ms");
//        }
    }

    private void checkForcingChains() {
        /* Mögliche Tests:
         *   1. Eine Chain:
         *        - Zwei verschiedene Werte gesetzt in einer Zelle (Setz-Sets verunden) -> Startbedingung falsch
         *        - Zwei gleiche Werte gesetzt in einem Haus (Setz-Sets mit Haus-Sets (?) verunden, anz > 1 -> Konflikt) -> Startbedingung falsch
         *        - Zelle ohne Kandidat (Lösch-Sets mit ~allowedPositions verodern, alles verunden, mit ~Setz-Sets verunden -> darf keine 1 sein) -> Startbedingung falsch
         *        - Kandidat in einer Zelle gesetzt und gelöscht (Setz-Sets mit Lösch-Sets verunden -> muss leer sein) -> Startbedingung falsch
         *        - Alle Kandidaten für einen Wert in einem Haus gelöscht (allowedPositions auf ein Haus einschränken, mit Lösch-Set verunden, darf nicht gleich sein)
         */
        for (int i = 0; i < onTable.length; i++) {
            checkOneChain(onTable[i]);
            checkOneChain(offTable[i]);
        }
        /**
         *   2. Zwei Chains für selben Start-Kandidaten (gesetzt und gelöscht)
         *        - Beide Chains enthalten den selben gesetzten Wert -> Wert kann gesetzt werden (Setz-Sets verunden)
         *        - Beide Chains enthalten den selben gelöschten Kandidaten -> Kandidat kann gelöscht werden
         */
        for (int i = 0; i < onTable.length; i++) {
            checkTwoChains(onTable[i], offTable[i]);
        }
        /**
         *   3. Chains für alle Kandidaten in einem Haus/Zelle gesetzt
         *        - Alle Chains enthalten den selben gesetzten Wert -> Wert kann gesetzt werden (Setz-Sets verunden)
         *        - Alle Chains enthalten den selben gelöschten Kandidaten -> Kandidat kann gelöscht werden
         */
        checkAllChainsForHouse(null);
        checkAllChainsForHouse(Sudoku.lineTemplates);
        checkAllChainsForHouse(Sudoku.colTemplates);
        checkAllChainsForHouse(Sudoku.blockTemplates);
    }

    private void checkAllChainsForHouse(SudokuSet[] houseSets) {
        if (houseSets == null) {
            // alle Zellen durchgehen
            for (int i = 0; i < sudoku.getCells().length; i++) {
                if (sudoku.getCell(i).getValue() != 0) {
                    continue;
                }
                // Tabling-Einträge für alle Kandidaten gesetzt sammeln
                entryList.clear();
                short[] cands = sudoku.getCell(i).getAllCandidates(candType);
                for (int j = 0; j < cands.length; j++) {
                    entryList.add(onTable[i * 10 + cands[j]]);
                }
                checkEntryList(entryList);
            }
        } else {
            // entsprechendes Haus durchgehen: Jeweils alle Einträge für einen bestimmten Kandidaten checken
            for (int i = 0; i < houseSets.length; i++) {
                // 1 Mal für jedes Haus
                for (int j = 1; j < sudoku.getAllowedPositions().length; j++) {
                    // 1 Mal für jeden Kandidaten in diesem Haus
                    tmpSet.set(houseSets[i]);
                    tmpSet.and(sudoku.getAllowedPositions()[j]);
                    if (!tmpSet.isEmpty()) {
                        // Alle Table-Einträge sammeln und dann prüfen
                        entryList.clear();
                        for (int k = 0; k < tmpSet.size(); k++) {
                            entryList.add(onTable[tmpSet.get(k) * 10 + j]);
                        }
                        checkEntryList(entryList);
                    }
                }
            }
        }
    }

    /**
     * entryList enthält Tabling-Einträge für "alle Kandidaten eines Hauses/einer Zelle gesetzt".
     * Wenn in allen diesen Chains derselbe Wert gesetzt ist, kann er gesetzt werden (das
     * Gleiche für gelöscht)
     *
     * ACHTUNG: Der Zielwert darf nicht mit einem der Ausgangswerte übereinstimmen!
     */
    private void checkEntryList(List<TableEntry> entryList) {
        for (int i = 0; i < entryList.size(); i++) {
            TableEntry entry = entryList.get(i);
            for (int j = 1; j < tmpOnSets.length; j++) {
                if (i == 0) {
                    tmpOnSets[j].set(entry.onSets[j]);
                    tmpOffSets[j].set(entry.offSets[j]);
                } else {
                    tmpOnSets[j].and(entry.onSets[j]);
                    tmpOffSets[j].and(entry.offSets[j]);
                }
            }
        }
        // Jetzt prüfen, ob noch etwas übrig ist
        for (int j = 1; j < tmpOnSets.length; j++) {
            if (!tmpOnSets[j].isEmpty()) {
                for (int k = 0; k < tmpOnSets[j].size(); k++) {
                    globalStep.reset();
                    globalStep.setType(SolutionType.FORCING_CHAIN_VERITY);
                    globalStep.addIndex(tmpOnSets[j].get(k));
                    globalStep.addValue(j);
                    resetTmpChains();
                    for (int l = 0; l < entryList.size(); l++) {
                        addChain(entryList.get(l), tmpOnSets[j].get(k), j, true);
                    }
                    replaceOrCopyStep();
                }
            }
            if (!tmpOffSets[j].isEmpty()) {
                for (int k = 0; k < tmpOffSets[j].size(); k++) {
                    globalStep.reset();
                    globalStep.setType(SolutionType.FORCING_CHAIN_VERITY);
                    globalStep.addCandidateToDelete(tmpOffSets[j].get(k), j);
                    resetTmpChains();
                    for (int l = 0; l < entryList.size(); l++) {
                        addChain(entryList.get(l), tmpOffSets[j].get(k), j, false);
                    }
                    replaceOrCopyStep();
                }
            }
        }
    }

    private void adjustType(SolutionStep step) {
        if (step.isNet()) {
            if (step.getType() == SolutionType.FORCING_CHAIN_CONTRADICTION) {
                step.setType(SolutionType.FORCING_NET_CONTRADICTION);
            }
            if (step.getType() == SolutionType.FORCING_CHAIN_VERITY) {
                step.setType(SolutionType.FORCING_NET_VERITY);
            }
        }
    }

    /**
     * Chains that contain ALS_NODEs have to be handled carefully:
     * The als for every ALS_NODE must be added to globalStep, the index
     * of the als in the chain entry has to be adjusted and all candidates
     * for the entry have to be put as endo fins
     */
    public void adjustChains(SolutionStep step) {
        int alsIndex = step.getAlses().size();
        chainAlses.clear();
        for (int i = 0; i < step.getChainAnz(); i++) {
            Chain adjChain = step.getChains().get(i);
            for (int j = adjChain.start; j <= adjChain.end; j++) {
                if (Chain.getSNodeType(adjChain.chain[j]) == Chain.ALS_NODE) {
                    int which = Chain.getSAlsIndex(adjChain.chain[j]);
                    if (chainAlses.containsKey(which)) {
                        int newIndex = chainAlses.get(which);
                        adjChain.replaceAlsIndex(j, newIndex);
                    } else {
                        step.addAls(alses.get(which).indices, alses.get(which).candidates);
                        chainAlses.put(which, alsIndex);
                        adjChain.replaceAlsIndex(j, alsIndex);
                        alsIndex++;
                    }
//                    int cand = Chain.getSCandidate(adjChain.chain[j]);
//                    SudokuSet adjSet = alses.get(which).indicesPerCandidat[cand];
//                    for (int k = 0; k < adjSet.size(); k++) {
//                        step.addEndoFin(adjSet.get(k), cand);
//                    }
                }
            }
        }
    }

    private void replaceStep(SolutionStep src, SolutionStep dest) {
        adjustType(globalStep);
        dest.setType(src.getType());
        if (src.getIndices().size() > 0) {
            dest.getIndices().set(0, src.getIndices().get(0));
            dest.getValues().set(0, src.getValues().get(0));
        } else {
            dest.getCandidatesToDelete().set(0, src.getCandidatesToDelete().get(0));
        }
        if (src.getAlses().size() > 0) {
            dest.getAlses().clear();
            for (int i = 0; i < src.getAlses().size(); i++) {
                dest.addAls(src.getAlses().get(i));
            }
        }
        dest.getEndoFins().clear();
        for (int i = 0; i < src.getEndoFins().size(); i++) {
            dest.getEndoFins().add(src.getEndoFins().get(i));
        }
        dest.setEntity(src.getEntity());
        dest.setEntityNumber(src.getEntityNumber());
        int i = 0;
        for (i = 0; i < src.getChains().size(); i++) {
            Chain localTmpChain = src.getChains().get(i);
            boolean toShort = dest.getChains().size() > i && dest.getChains().get(i).chain.length < (localTmpChain.end + 1);
            if (i >= dest.getChains().size() || toShort) {
                int[] tmp = new int[localTmpChain.end + 1];
                for (int j = 0; j <= localTmpChain.end; j++) {
                    tmp[j] = localTmpChain.chain[j];
                }
                if (toShort) {
                    Chain destChain = dest.getChains().get(i);
                    destChain.chain = tmp;
                    destChain.start = localTmpChain.start;
                    destChain.end = localTmpChain.end;
                    destChain.resetLength();
                } else {
                    dest.addChain(0, localTmpChain.end, tmp);
                }
            } else {
                Chain destChain = dest.getChains().get(i);
                for (int j = 0; j <= localTmpChain.end; j++) {
                    destChain.chain[j] = localTmpChain.chain[j];
                }
                destChain.start = localTmpChain.start;
                destChain.end = localTmpChain.end;
                destChain.resetLength();
            }
        }
        while (i < dest.getChains().size()) {
            dest.getChains().remove(i);
        }
    }

    //private void replaceOrCopyStep(int setIndex, int setCandidate, int delIndex, int delCandidate, SolutionType type) {
    private void replaceOrCopyStep() {
        adjustType(globalStep);
        if (!chainsOnly && (globalStep.getType() == SolutionType.FORCING_CHAIN_CONTRADICTION ||
                globalStep.getType() == SolutionType.FORCING_CHAIN_VERITY)) {
            return;
        }
        adjustChains(globalStep);
        try {
            String del = null;
            if (Options.getInstance().onlyOneChainPerStep) {
                if (globalStep.getCandidatesToDelete().size() > 0) {
                    del = globalStep.getCandidateString();
                } else {
                    del = globalStep.getSingleCandidateString();
                }
                Integer oldIndex = deletesMap.get(del);
                SolutionStep actStep = null;
                if (oldIndex != null) {
                    actStep = steps.get(oldIndex.intValue());
                }
                if (actStep != null) {
                    if (actStep.getChainLength() > globalStep.getChainLength()) {
                        replaceStep(globalStep, actStep);
                    }
                    return;
                }
            }
            // Da die chains jetzt gecached sind, müssen sie vor dem Kopieren geclont werden
            List<Chain> oldChains = globalStep.getChains();
            int chainAnz = oldChains.size();
            oldChains.clear();
            for (int i = 0; i < chainAnz; i++) {
                oldChains.add((Chain) tmpChains[i].clone());
            }
            steps.add((SolutionStep) globalStep.clone());
            if (del != null) {
                // Index der chain merken
                deletesMap.put(del, steps.size() - 1);
            }
        } catch (CloneNotSupportedException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
        }
    }

    private String printEntryList(List<TableEntry> entryList) {
        StringBuffer tmp = new StringBuffer();
        for (int i = 0; i < entryList.size(); i++) {
            if (i != 0) {
                tmp.append(", ");
            }
            tmp.append(printTableEntry(entryList.get(i).entries[0]));
        }
        return tmp.toString();
    }

    /**
     * ACHTUNG: Wenn eine der chains auf das Ausgangsfeld zurückführt, ist die
     *          zweite chain nur 1 ELement lang und wird daher ignoriert -> ist eigentlich eine
     *          Contradiction und keine Verity -> ignorieren!
     */
    private void checkTwoChains(TableEntry on, TableEntry off) {
        if (on.index == 0 || off.index == 0) {
            return;
        }
        // Beide Chains enthalten den selben gesetzten Wert -> Wert kann gesetzt werden (Setz-Sets verunden)
        for (int i = 1; i < on.onSets.length; i++) {
            tmpSet.set(on.onSets[i]);
            tmpSet.and(off.onSets[i]);
            tmpSet.remove(on.getCellIndex(0));
            if (!tmpSet.isEmpty()) {
                for (int j = 0; j < tmpSet.size(); j++) {
                    globalStep.reset();
                    globalStep.setType(SolutionType.FORCING_CHAIN_VERITY);
                    globalStep.addIndex(tmpSet.get(j));
                    globalStep.addValue(i);
                    resetTmpChains();
                    addChain(on, tmpSet.get(j), i, true);
                    addChain(off, tmpSet.get(j), i, true);
                    replaceOrCopyStep();
                }
            }
        }
        // Beide Chains enthalten den selben gelöschten Kandidaten -> Kandidat kann gelöscht werden
        for (int i = 1; i < on.offSets.length; i++) {
            tmpSet.set(on.offSets[i]);
            tmpSet.and(off.offSets[i]);
            tmpSet.remove(on.getCellIndex(0));
            if (!tmpSet.isEmpty()) {
                for (int j = 0; j < tmpSet.size(); j++) {
                    globalStep.reset();
                    globalStep.setType(SolutionType.FORCING_CHAIN_VERITY);
                    globalStep.addCandidateToDelete(tmpSet.get(j), i);
                    resetTmpChains();
                    addChain(on, tmpSet.get(j), i, false);
                    addChain(off, tmpSet.get(j), i, false);
                    replaceOrCopyStep();
                }
            }
        }

    }

    private void checkOneChain(TableEntry entry) {
        if (entry.index == 0) {
            return;
        }
        // Chain widerspricht sich selbst: Table enthält den inversen Eintrag zum Starteintrag
        if ((entry.isStrong(0) && entry.offSets[entry.getCandidate(0)].contains(entry.getCellIndex(0))) ||
                (!entry.isStrong(0) && entry.onSets[entry.getCandidate(0)].contains(entry.getCellIndex(0)))) {
            globalStep.reset();
            globalStep.setType(SolutionType.FORCING_CHAIN_CONTRADICTION);
            if (entry.isStrong(0)) {
                globalStep.addCandidateToDelete(entry.getCellIndex(0), entry.getCandidate(0));
            } else {
                globalStep.addIndex(entry.getCellIndex(0));
                globalStep.addValue(entry.getCandidate(0));
            }
            globalStep.setEntity(SudokuCell.CELL);
            globalStep.setEntityNumber(tmpSet.get(0));
            resetTmpChains();
            addChain(entry, entry.getCellIndex(0), entry.getCandidate(0), !entry.isStrong(0));
            if (entry.isStrong(0)) {
                replaceOrCopyStep();
            } else {
                replaceOrCopyStep();
            }
        }
        // Kandidat in einer Zelle gesetzt und gelöscht (Setz-Sets mit Lösch-Sets verunden -> muss leer sein) -> Startbedingung falsch
        for (int i = 0; i < entry.onSets.length; i++) {
            tmpSet.set(entry.onSets[i]);
            tmpSet.and(entry.offSets[i]);
            if (!tmpSet.isEmpty()) {
                globalStep.reset();
                globalStep.setType(SolutionType.FORCING_CHAIN_CONTRADICTION);
                if (entry.isStrong(0)) {
                    globalStep.addCandidateToDelete(entry.getCellIndex(0), entry.getCandidate(0));
                } else {
                    globalStep.addIndex(entry.getCellIndex(0));
                    globalStep.addValue(entry.getCandidate(0));
                }
                globalStep.setEntity(SudokuCell.CELL);
                globalStep.setEntityNumber(tmpSet.get(0));
                resetTmpChains();
                addChain(entry, tmpSet.get(0), i, false);
                addChain(entry, tmpSet.get(0), i, true);
                if (entry.isStrong(0)) {
                    replaceOrCopyStep();
                } else {
                    replaceOrCopyStep();
                }
            }
        }
        // Zwei verschiedene Werte gesetzt in einer Zelle
        // Alle Kombinationen durchprobieren
        for (int i = 1; i < entry.onSets.length; i++) {
            for (int j = i + 1; j < entry.onSets.length; j++) {
                tmpSet.set(entry.onSets[i]);
                tmpSet.and(entry.onSets[j]);
                if (!tmpSet.isEmpty()) {
                    globalStep.reset();
                    globalStep.setType(SolutionType.FORCING_CHAIN_CONTRADICTION);
                    if (entry.isStrong(0)) {
                        globalStep.addCandidateToDelete(entry.getCellIndex(0), entry.getCandidate(0));
                    } else {
                        globalStep.addIndex(entry.getCellIndex(0));
                        globalStep.addValue(entry.getCandidate(0));
                    }
                    globalStep.setEntity(SudokuCell.CELL);
                    globalStep.setEntityNumber(tmpSet.get(0));
                    resetTmpChains();
                    addChain(entry, tmpSet.get(0), i, true);
                    addChain(entry, tmpSet.get(0), j, true);
                    if (entry.isStrong(0)) {
                        replaceOrCopyStep();
                    } else {
                        replaceOrCopyStep();
                    }
                }
            }
        }
        // Zwei gleiche Werte gesetzt in einem Haus
        checkHouseSet(entry, Sudoku.lineTemplates, SudokuCell.LINE);
        checkHouseSet(entry, Sudoku.colTemplates, SudokuCell.COL);
        checkHouseSet(entry, Sudoku.blockTemplates, SudokuCell.BLOCK);
        // Zelle ohne Kandidat (Lösch-Sets mit ~allowedPositions verodern, alles verunden, mit ~Setz-Sets verunden -> darf keine 1 sein) -> Startbedingung falsch
        // ACHTUNG: Am Schluss noch alle Zellen ausnehmen, in denen ein Wert gesetzt ist
        tmpSet.setAll();
        for (int i = 1; i < entry.offSets.length; i++) {
            tmpSet1.set(entry.offSets[i]);
            tmpSet1.orNot(sudoku.getAllowedPositions()[i]);
            tmpSet.and(tmpSet1);
        }
        for (int i = 0; i < entry.onSets.length; i++) {
            tmpSet.andNot(entry.onSets[i]);
        }
        tmpSet2.clear();
        for (int i = 1; i < sudoku.getPositions().length; i++) {
            tmpSet2.or(sudoku.getPositions()[i]);
        }
        tmpSet.andNot(tmpSet2);
        if (!tmpSet.isEmpty()) {
            for (int i = 0; i < tmpSet.size(); i++) {
                globalStep.reset();
                globalStep.setType(SolutionType.FORCING_CHAIN_CONTRADICTION);
                if (entry.isStrong(0)) {
                    globalStep.addCandidateToDelete(entry.getCellIndex(0), entry.getCandidate(0));
                } else {
                    globalStep.addIndex(entry.getCellIndex(0));
                    globalStep.addValue(entry.getCandidate(0));
                }
                globalStep.setEntity(SudokuCell.CELL);
                globalStep.setEntityNumber(tmpSet.get(i));
                resetTmpChains();
                short[] cands = sudoku.getCell(tmpSet.get(i)).getAllCandidates(candType);
                for (int j = 0; j < cands.length; j++) {
                    addChain(entry, tmpSet.get(i), cands[j], false);
                }
                if (entry.isStrong(0)) {
                    replaceOrCopyStep();
                } else {
                    replaceOrCopyStep();
                }
            }
        }
        // Alle Kandidaten für einen Wert in einem Haus gelöscht (allowedPositions auf ein Haus einschränken, mit Lösch-Set verunden, darf nicht gleich sein)
        checkHouseDel(entry, Sudoku.lineTemplates, SudokuCell.LINE);
        checkHouseDel(entry, Sudoku.colTemplates, SudokuCell.COL);
        checkHouseDel(entry, Sudoku.blockTemplates, SudokuCell.BLOCK);
    }

    /**
     * Alle Kandidaten für einen Wert in einem Haus gelöscht
     */
    private void checkHouseDel(TableEntry entry, SudokuSet[] houseSets, int entityTyp) {
        for (int i = 1; i < entry.offSets.length; i++) {
            for (int j = 0; j < houseSets.length; j++) {
                tmpSet.set(houseSets[j]);
                tmpSet.and(sudoku.getAllowedPositions()[i]);
                if (!tmpSet.isEmpty() && tmpSet.andEquals(entry.offSets[i])) {
                    globalStep.reset();
                    globalStep.setType(SolutionType.FORCING_CHAIN_CONTRADICTION);
                    if (entry.isStrong(0)) {
                        globalStep.addCandidateToDelete(entry.getCellIndex(0), entry.getCandidate(0));
                    } else {
                        globalStep.addIndex(entry.getCellIndex(0));
                        globalStep.addValue(entry.getCandidate(0));
                    }
                    globalStep.setEntity(entityTyp);
                    globalStep.setEntityNumber(j);
                    resetTmpChains();
                    for (int k = 0; k < tmpSet.size(); k++) {
                        addChain(entry, tmpSet.get(k), i, false);
                    }
                    if (entry.isStrong(0)) {
                        replaceOrCopyStep();
                    } else {
                        replaceOrCopyStep();
                    }
                }
            }
        }
    }

    /**
     * Zwei gleiche Werte gesetzt in einem Haus
     */
    private void checkHouseSet(TableEntry entry, SudokuSet[] houseSets, int entityTyp) {
        for (int i = 1; i < entry.onSets.length; i++) {
            for (int j = 0; j < houseSets.length; j++) {
                tmpSet.set(houseSets[j]);
                tmpSet.and(entry.onSets[i]);
                if (tmpSet.size() > 1) {
                    globalStep.reset();
                    globalStep.setType(SolutionType.FORCING_CHAIN_CONTRADICTION);
                    if (entry.isStrong(0)) {
                        globalStep.addCandidateToDelete(entry.getCellIndex(0), entry.getCandidate(0));
                    } else {
                        globalStep.addIndex(entry.getCellIndex(0));
                        globalStep.addValue(entry.getCandidate(0));
                    }
                    globalStep.setEntity(entityTyp);
                    globalStep.setEntityNumber(j);
                    resetTmpChains();
                    for (int k = 0; k < tmpSet.size(); k++) {
                        addChain(entry, tmpSet.get(k), i, true);
                    }
                    if (entry.isStrong(0)) {
                        replaceOrCopyStep();
                    } else {
                        replaceOrCopyStep();
                    }
                }
            }
        }
    }

    /**
     * Alle Implikationen durchgehen und schauen, ob sie auf den Anfang zurückverweisen
     * (alle entries durchgehen, für jeden außer dem ersten prüfen, ob in der
     * Original-Table des Entries ein direkter Link auf die Quell-Zelle existiert -
     * muss so kompliziert gemacht werden, weil beim Expandieren der Tables keine Links auf
     * die Ausgangszelle selbst geschrieben werden)
     *
     * ACHTUNG: NICHT WAHR! Alle Implikationen stehen in jeder Table!
     */
    private void checkNiceLoops(TableEntry[] tables) {
        for (int i = 0; i < tables.length; i++) {
            int startIndex = tables[i].getCellIndex(0);
            for (int j = 1; j < tables[i].index; j++) {
                // Test
                if (tables[i].getNodeType(j) == Chain.NORMAL_NODE &&
                        tables[i].getCellIndex(j) == startIndex) {
                    // ok - direkter Loop
                    checkNiceLoop(tables[i], j);
                }
            }
        }
    }
    
    /**
     * AICs are checked separately: The end of the chain has to be:
     *   - on-entry for the same candidate as the start cell (Type 1), if
     *     the combined buddies of start and end cell can eliminate more
     *     than one candidate
     *   - on-entry for a different candidate if the end cell sees the start cell
     *     and if the start cell contains a candidate of the chain end and the 
     *     end cell contains a candidate of the start cell
     * 
     * @param tables Only offTables are allowed (AICs start with a strong link)
     */
    private void checkAics(TableEntry[] tables) {
        for (int i = 0; i < tables.length; i++) {
            int startIndex = tables[i].getCellIndex(0);
            int startCandidate = tables[i].getCandidate(0);
            SudokuSetBase buddies = Sudoku.buddies[startIndex];
            for (int j = 1; j < tables[i].index; j++) {
                if (tables[i].getNodeType(j) != Chain.NORMAL_NODE ||
                        ! tables[i].isStrong(j) || tables[i].getCellIndex(j) == startIndex) {
                    // not now
                    continue;
                }
                if (startCandidate == tables[i].getCandidate(j)) {
                    // check Type 1
                    tmpSet.set(buddies);
                    tmpSet.and(Sudoku.buddies[tables[i].getCellIndex(j)]);
                    tmpSet.and(sudoku.getAllowedPositions()[startCandidate]);
                    if (! tmpSet.isEmpty() && tmpSet.size() >= 2) {
                        // everything else is already covered by a Nice Loop
                        checkAic(tables[i], j);
                    }
                } else {
                    if (! buddies.contains(tables[i].getCellIndex(j))) {
                        // cant be Type 2
                        continue;
                    }
                    if (sudoku.getCell(tables[i].getCellIndex(j)).isCandidate(candType, startCandidate) &&
                            sudoku.getCell(startIndex).isCandidate(candType, tables[i].getCandidate(j))) {
                        // Type 2
                        checkAic(tables[i], j);
                    }
                }
            }
        }
    }

    /**
     * Wenn die erste und die letzte Zelle der Chain identisch sind, ist es ein
     * Nice Loop.
     * KOPIERT VON ChainSolver, OPTIMIERUNG GEÄNDERT
     *
     *  Discontinuous Nice Loop:
     *    - Erster und letzter Link sind weak für den selben Kandidaten
     *      -> Kandidat kann in erster Zelle gelöscht werden
     *    - Erster und letzter Link sind strong für den selben Kandidaten
     *      -> Kandidat kann in erster Zelle gesetzt werden (alle anderen Kandidaten löschen, ist einfacher in der Programmlogik)
     *    - Ein link ist weak und einer strong, die Kandidaten sind verschieden
     *      -> Kandidat mit weak link kann in erster Zelle gelöscht werden
     * 
     * AICs:
     *    Bei einer Discontinuous Nice Loop mit zwei weak links kann weiter geprüft werden:
     *     - betrachtet werden der erste und der letzte strong link.
     *       wenn beide für den selben Kandidaten sind, können alle Kandidaten
     *       gelöscht werden, die beide Zellen sehen
     *    Ebenso bei einer weak, einer strong für verschiedene Kandidaten:
     *     - der strong link vor dem schließenden weak link wird gesucht; wenn er
     *       die Ausgangszelle sehen kann, haben wir AIC mit zwei strong links für verschiedene
     *       Kandidaten, die sich sehen: der erste Kandidat kann in der zweiten Zelle und
     *       der zweite Kandidat in der ersten Zelle gelöscht werden.
     *    Wenn einer dieser Fälle mehr als einen Kandidaten löscht, wird statt der Discontinuous Nice Loop
     *    die AIC eingeschrieben. AIC Loops werden immer als Continuous Nice Loops geschrieben.
     *
     *  Continuous Nice Loop:
     *    - Zwei weak links: Erste Zelle muss bivalue sein, Kandidaten müssen verschieden sein
     *    - Zwei strong links: Kandidaten müssen verschieden sein
     *    - Ein strong, ein weak link: Kandidaten müssen gleich sein
     *
     *    -> eine Zelle mit zwei strong links: alle anderen Kandidaten von dieser Zelle löschen
     *    -> weak link zwischen zwei Zellen: Kandidat des Links kann von allen Zellen gelöscht werden,
     *       die beide Zellen sehen
     *
     * Da nicht sicher gestellt werden kann, dass der erste Link aus der Zelle herausführt, wird in jedem
     * Fall zuerst die Chain gebildet, in der fertigen Chain wird der erste Link geprüft: bleibt er in der Zelle,
     * wird die Chain verworfen.
     *
     * @param entry TableEntry für den Start-Link
     * @param entryIndex Index auf den vorletzten Link des Nice Loops (ist letzter Eintrag, der in der Table noch enthalten ist).
     */
    private void checkNiceLoop(TableEntry entry, int entryIndex) {
        // Mindestlänge: 3 Links
        if (entry.getDistance(entryIndex) <= 2) {
            // Chain ist zu kurz -> keine Eliminierung möglich
            return;
        }

        // auf Looptyp prüfen
        globalStep.reset();
        globalStep.setType(SolutionType.DISCONTINUOUS_NICE_LOOP);
        resetTmpChains();
        addChain(entry, entry.getCellIndex(entryIndex), entry.getCandidate(entryIndex), entry.isStrong(entryIndex), true);
        if (globalStep.getChains().size() == 0) {
            // ungültige Chain -> bildet ein Lasso -> ignorieren
            return;
        }
        Chain localTmpChain = globalStep.getChains().get(0);
        // in AICs the first link can be within the same cell!
        if (localTmpChain.getCellIndex(0) == localTmpChain.getCellIndex(1)) {
            // da kommt noch eine andere
            return;
        }
        int[] nlChain = localTmpChain.chain;
        int nlChainIndex = localTmpChain.end;
        int nlChainLength = localTmpChain.getLength();

        boolean firstLinkStrong = entry.isStrong(1);
        boolean lastLinkStrong = entry.isStrong(entryIndex);
        int startCandidate = entry.getCandidate(0);
        int endCandidate = entry.getCandidate(entryIndex);
        int startIndex = entry.getCellIndex(0);

        if (!firstLinkStrong && !lastLinkStrong && startCandidate == endCandidate) {
            // Discontinuous -> startCandidate in erster Zelle löschen
            globalStep.addCandidateToDelete(startIndex, startCandidate);
            // auf mögliche AIC prüfen: die strong links müssen normale links sein
            if (Chain.getSNodeType(nlChain[1]) == Chain.NORMAL_NODE && Chain.getSNodeType(nlChain[nlChainIndex - 1]) == Chain.NORMAL_NODE) {
                tmpSet.set(Sudoku.buddies[Chain.getSCellIndex(nlChain[1])]);
                tmpSet.and(Sudoku.buddies[Chain.getSCellIndex(nlChain[nlChainIndex - 1])]);
                tmpSet.and(sudoku.getAllowedPositions()[startCandidate]);
                if (tmpSet.size() > 1) {
                    globalStep.setType(SolutionType.AIC);
                    for (int i = 0; i < tmpSet.size(); i++) {
                        if (tmpSet.get(i) != startIndex) {
                            globalStep.addCandidateToDelete(tmpSet.get(i), startCandidate);
                        }
                    }
                    localTmpChain.start++;
                    localTmpChain.end--;
                }
            }
        } else if (firstLinkStrong && lastLinkStrong && startCandidate == endCandidate) {
            // Discontinuous -> alle anderen Kandidaten löschen
            short[] cands = sudoku.getCell(startIndex).getAllCandidates(candType);
            for (int i = 0; i < cands.length; i++) {
                if (cands[i] != startCandidate) {
                    globalStep.addCandidateToDelete(startIndex, cands[i]);
                }
            }
        } else if (firstLinkStrong != lastLinkStrong && startCandidate != endCandidate) {
            // Discontinous -> weak link löschen
            if (!firstLinkStrong) {
                globalStep.addCandidateToDelete(startIndex, startCandidate);
                if (Chain.getSNodeType(nlChain[1]) == Chain.NORMAL_NODE &&
                        sudoku.getCell(Chain.getSCellIndex(nlChain[1])).isCandidate(candType, endCandidate)) {
                    globalStep.setType(SolutionType.AIC);
                    globalStep.addCandidateToDelete(Chain.getSCellIndex(nlChain[1]), endCandidate);
                    localTmpChain.start++;
                }
            } else {
                globalStep.addCandidateToDelete(startIndex, endCandidate);
                if (Chain.getSNodeType(nlChain[nlChainIndex - 1]) == Chain.NORMAL_NODE &&
                        sudoku.getCell(Chain.getSCellIndex(nlChain[nlChainIndex - 1])).isCandidate(candType, startCandidate)) {
                    globalStep.setType(SolutionType.AIC);
                    globalStep.addCandidateToDelete(Chain.getSCellIndex(nlChain[nlChainIndex - 1]), startCandidate);
                    localTmpChain.end--;
                }
            }
        } else if ((!firstLinkStrong && !lastLinkStrong && sudoku.getCell(startIndex).getAnzCandidates(candType) == 2 && startCandidate != endCandidate) ||
                (firstLinkStrong && lastLinkStrong && startCandidate != endCandidate) ||
                (firstLinkStrong != lastLinkStrong && startCandidate == endCandidate)) {
            // Continous -> auf Löschen prüfen
            globalStep.setType(SolutionType.CONTINUOUS_NICE_LOOP);
            // Zelle mit zwei strong links: strong link zwischen Zellen, weak link in der Zelle, strong link zu nächster Zelle
            // weak link zwischen Zellen: trivial
            // ACHTUNG: Erste Zelle mit berücksichtigen!
            for (int i = 0; i <= nlChainIndex; i++) {
                if ((i == 0 && (firstLinkStrong && lastLinkStrong)) ||
                        (i > 0 && (Chain.isSStrong(nlChain[i]) && i <= nlChainIndex - 2 &&
                        Chain.getSCellIndex(nlChain[i - 1]) != Chain.getSCellIndex(nlChain[i])))) {
                    // mögliche Zelle mit zwei strong links: nächster Link muss weak sein auf selbe Zelle, danach strong auf nächste Zelle
                    if (i == 0 || (!Chain.isSStrong(nlChain[i + 1]) && Chain.getSCellIndex(nlChain[i]) == Chain.getSCellIndex(nlChain[i + 1]) &&
                            Chain.isSStrong(nlChain[i + 2]) && Chain.getSCellIndex(nlChain[i + 1]) != Chain.getSCellIndex(nlChain[i + 2]))) {
                        // we are save here: group nodes and ALS cannot provide weak links in the cells through which they are reached
                        // in der Zelle nlChain[i] alle Kandidaten außer den beiden strong links löschen
                        int c1 = Chain.getSCandidate(nlChain[i]);
                        int c2 = Chain.getSCandidate(nlChain[i + 2]);
                        if (i == 0) {
                            c1 = startCandidate;
                            c2 = endCandidate;
                        }
                        short[] cands = sudoku.getCell(Chain.getSCellIndex(nlChain[i])).getAllCandidates(candType);
                        for (int j = 0; j < cands.length; j++) {
                            if (cands[j] != c1 && cands[j] != c2) {
                                globalStep.addCandidateToDelete(Chain.getSCellIndex(nlChain[i]), cands[j]);
                            }
                        }
                    }
                }
                // this condition is nonsens (I have no idea what I thought when I wrote it)
                // a weak link to the start cell will be the last item in the chain; a weak link to the second cell will be the second item
                // in the chain -> no special cases needed here
//                if ((i == 0 && (i == -1) ||
//                        (i > 0) && (!Chain.isSStrong(nlChain[i]) && Chain.getSCellIndex(nlChain[i - 1]) != Chain.getSCellIndex(nlChain[i])))) {
                if ((i > 0) && (!Chain.isSStrong(nlChain[i]) && Chain.getSCellIndex(nlChain[i - 1]) != Chain.getSCellIndex(nlChain[i]))) {
                    // weak link zwischen zwei Zellen
                    // CAUTION: If one of the cells is entry point for an ALS, nothing can be eliminated;
                    //          if one or both cells are group nodes, only candidates, that see all of the group node cells,
                    //          can be eliminated
                    // 20090224: entries to ALS can be treated like normal group nodes: all candidates in the
                    //          same house that dont belong to the node or the ALS can be eliminated
                    //          plus: all ALS candidates that are not entry/exit candidates eliminate all
                    //          candidates they can see
                    // 20100218: If an ALS node forces a digit (ALS left via more than one candidate -> all
                    //          candidates except one are eliminated in another cell) the leaving weak link is
                    //          missing (next link is strong to forced cell); in that case all other candidates
                    //          in the forced cell are exit candidates and may not be eliminated
//                    if (Chain.getSNodeType(nlChain[i]) != Chain.ALS_NODE && Chain.getSNodeType(nlChain[i - 1]) != Chain.ALS_NODE) {
//                        tmpSet.set(Sudoku.buddies[Chain.getSCellIndex(nlChain[i - 1])]);
//                        // check for group nodes
//                        if (Chain.getSNodeType(nlChain[i - 1]) == Chain.GROUP_NODE) {
//                            tmpSet.and(Sudoku.buddies[Chain.getSCellIndex2(nlChain[i - 1])]);
//                            if (Chain.getSCellIndex3(nlChain[i - 1]) != -1) {
//                                tmpSet.and(Sudoku.buddies[Chain.getSCellIndex3(nlChain[i - 1])]);
//                            }
//                        }
//                        tmpSet.and(Sudoku.buddies[Chain.getSCellIndex(nlChain[i])]);
//                        // check for group nodes
//                        if (Chain.getSNodeType(nlChain[i]) == Chain.GROUP_NODE) {
//                            tmpSet.and(Sudoku.buddies[Chain.getSCellIndex2(nlChain[i])]);
//                            if (Chain.getSCellIndex3(nlChain[i]) != -1) {
//                                tmpSet.and(Sudoku.buddies[Chain.getSCellIndex3(nlChain[i])]);
//                            }
//                        }
//                        tmpSet.andNot(tmpSetC);
//                        tmpSet.remove(startIndex);
//                        tmpSet.and(sudoku.getAllowedPositions()[Chain.getSCandidate(nlChain[i])]);
//                        if (!tmpSet.isEmpty()) {
//                            for (int j = 0; j < tmpSet.size(); j++) {
//                                globalStep.addCandidateToDelete(tmpSet.get(j), Chain.getSCandidate(nlChain[i]));
//                            }
//                        }
//                    }
                    int actCand = Chain.getSCandidate(nlChain[i]);
                    Chain.getSNodeBuddies(nlChain[i - 1], actCand, alses, tmpSet);
                    Chain.getSNodeBuddies(nlChain[i], actCand, alses, tmpSet1);
                    tmpSet.and(tmpSet1);
                    tmpSet.andNot(tmpSetC);
                    tmpSet.remove(startIndex);
//                    tmpSet.and(sudoku.getAllowedPositions()[Chain.getSCandidate(nlChain[i])]);
                    tmpSet.and(sudoku.getAllowedPositions()[actCand]);
                    if (!tmpSet.isEmpty()) {
                        for (int j = 0; j < tmpSet.size(); j++) {
                            globalStep.addCandidateToDelete(tmpSet.get(j), Chain.getSCandidate(nlChain[i]));
                        }
                    }
                    if (Chain.getSNodeType(nlChain[i]) == Chain.ALS_NODE) {
                        // there could be more than one exit candidate (the node following an ALS node
                        // must be weak; if it is strong, the weak link contains more than one
                        // candidate and was omitted
                        boolean isForceExit = i < nlChainIndex && Chain.isSStrong(nlChain[i + 1]);
                        int nextCellIndex = Chain.getSCellIndex(nlChain[i + 1]);
                        tmpSet2.clear();
                        if (isForceExit) {
                            // all candidates in the next cell (except the one providing the strong link)
                            // are exit candidates
                            int forceCand = Chain.getSCandidate(nlChain[i + 1]);
                            SudokuCell nextCell = sudoku.getCell(nextCellIndex);
                            nextCell.getCandidateSet(tmpSet2);
                            tmpSet2.remove(forceCand);
                        } else {
                            if (i < nlChainIndex) {
                                tmpSet2.add(Chain.getSCandidate(nlChain[i + 1]));
                            }
                        }
                        Als als = alses.get(Chain.getSAlsIndex(nlChain[i]));
                        for (int j = 1; j < als.buddiesPerCandidat.length; j++) {
                            if (j == actCand || tmpSet2.contains(j) || als.buddiesPerCandidat[j] == null) {
                                // RC -> handled from code above
                                // or exit candidate (handled by the next link or below)
                                // or candidate not in ALS
                                continue;
                            }
                            tmpSet.set(als.buddiesPerCandidat[j]);
                            //tmpSet.andNot(tmpSetC); not exactely sure, but I think cannibalism is allowed here
                            //tmpSet.remove(startIndex);
                            tmpSet.and(sudoku.getAllowedPositions()[j]);
                            if (!tmpSet.isEmpty()) {
                                for (int k = 0; k < tmpSet.size(); k++) {
                                    globalStep.addCandidateToDelete(tmpSet.get(k), j);
                                }
                            }
                        }
                        // special case forced next cell: exit candidates have to be handled here
                        if (isForceExit) {
                            // for all exit candidates: eliminate everything that sees all instances
                            // of that cand in the als and in the next cell
                            tmpSet1.set(Sudoku.buddies[nextCellIndex]);
                            for (int j = 0; j < tmpSet2.size(); j++) {
                                int actExitCand = tmpSet2.get(j);
                                tmpSet.set(als.buddiesPerCandidat[actExitCand]);
                                tmpSet.and(tmpSet1);
                                //tmpSet.andNot(tmpSetC);
                                //tmpSet.remove(startIndex);
                                tmpSet.and(sudoku.getAllowedPositions()[actExitCand]);
                                if (!tmpSet.isEmpty()) {
                                    for (int k = 0; k < tmpSet.size(); k++) {
                                        globalStep.addCandidateToDelete(tmpSet.get(k), actExitCand);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (globalStep.getCandidatesToDelete().size() > 0) {
            // ok, Loop ist nicht redundant -> einschreiben, wenn es die Kombination nicht schon gibt
            // zuerst auf grouped nodes prüfen
            // auf grouped nodes prüfen
            boolean grouped = false;
            Chain newChain = globalStep.getChains().get(0);
            for (int i = newChain.start; i <= newChain.end; i++) {
                if (Chain.getSNodeType(newChain.chain[i]) != Chain.NORMAL_NODE) {
                    grouped = true;
                    break;
                }
            }
            if (grouped) {
                if (globalStep.getType() == SolutionType.DISCONTINUOUS_NICE_LOOP) {
                    globalStep.setType(SolutionType.GROUPED_DISCONTINUOUS_NICE_LOOP);
                }
                if (globalStep.getType() == SolutionType.CONTINUOUS_NICE_LOOP) {
                    globalStep.setType(SolutionType.GROUPED_CONTINUOUS_NICE_LOOP);
                }
                if (globalStep.getType() == SolutionType.AIC) {
                    globalStep.setType(SolutionType.GROUPED_AIC);
                }
            }
            if (onlyGroupedNiceLoops && !grouped) {
                return;
            }
            // jetzt auf doppelte prüfen
            String del = globalStep.getCandidateString();
            Integer oldIndex = deletesMap.get(del);
            // ignores ALS penalty!
            //if (oldIndex != null && steps.get(oldIndex.intValue()).getChainLength() <= (nlChainIndex + 1)) {
            if (oldIndex != null && steps.get(oldIndex.intValue()).getChainLength() <= nlChainLength) {
                // Für diese Kandidaten gibt es schon eine Chain und sie ist kürzer als die neue
                return;
            }
            deletesMap.put(del, steps.size());
            try {
                // Die Chain muss kopiert werden
                newChain = (Chain) globalStep.getChains().get(0).clone();
                globalStep.getChains().clear();
                globalStep.getChains().add(newChain);
                adjustChains(globalStep);
                steps.add((SolutionStep) globalStep.clone());
            } catch (CloneNotSupportedException ex) {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
            }
        }
    }

    /**
     * Checks whether the AIC does make an elimination; if so builds the step and
     * adds it to steps.
     * 
     * @param entry The entry for the start cell
     * @param entryIndex index of the end cell of the AIC
     */
    private void checkAic(TableEntry entry, int entryIndex) {
        // Mindestlänge: 3 Links
        if (entry.getDistance(entryIndex) <= 2) {
            // Chain ist zu kurz -> keine Eliminierung möglich
            return;
        }

        globalStep.reset();
        globalStep.setType(SolutionType.AIC);
        
        // check whether eliminations are possible
        int startCandidate = entry.getCandidate(0);
        int endCandidate = entry.getCandidate(entryIndex);
        int startIndex = entry.getCellIndex(0);
        int endIndex = entry.getCellIndex(entryIndex);
        if (startCandidate == endCandidate) {
            // type 1 AIC: delete all candidates that can see both ends of the chain
            tmpSet.set(Sudoku.buddies[startIndex]);
            tmpSet.and(Sudoku.buddies[endIndex]);
            tmpSet.and(sudoku.getAllowedPositions()[startCandidate]);
            if (tmpSet.size() > 1) {
                for (int i = 0; i < tmpSet.size(); i++) {
                    if (tmpSet.get(i) != startIndex) {
                        globalStep.addCandidateToDelete(tmpSet.get(i), startCandidate);
                    }
                }
            }
        } else {
            // Type 2 AIC: Delete start candidate in end cell and vice versa
            if (sudoku.getCell(startIndex).isCandidate(candType, endCandidate)) {
                globalStep.addCandidateToDelete(startIndex, endCandidate);
            }
            if (sudoku.getCell(endIndex).isCandidate(candType, startCandidate)) {
                globalStep.addCandidateToDelete(endIndex, startCandidate);
            }
        }
        if (globalStep.getAnzCandidatesToDelete() == 0) {
            // nothing to do
            return;
        }
        // build the chain
        resetTmpChains();
        addChain(entry, entry.getCellIndex(entryIndex), entry.getCandidate(entryIndex), entry.isStrong(entryIndex), false, true);
        if (globalStep.getChains().size() == 0) {
            // something is wrong with that chain
            return;
        }
        // check for group nodes
        boolean grouped = false;
        Chain newChain = globalStep.getChains().get(0);
        for (int i = newChain.start; i <= newChain.end; i++) {
            if (Chain.getSNodeType(newChain.chain[i]) != Chain.NORMAL_NODE) {
                grouped = true;
                break;
            }
        }
        if (grouped) {
            if (globalStep.getType() == SolutionType.DISCONTINUOUS_NICE_LOOP) {
                globalStep.setType(SolutionType.GROUPED_DISCONTINUOUS_NICE_LOOP);
            }
            if (globalStep.getType() == SolutionType.CONTINUOUS_NICE_LOOP) {
                globalStep.setType(SolutionType.GROUPED_CONTINUOUS_NICE_LOOP);
            }
            if (globalStep.getType() == SolutionType.AIC) {
                globalStep.setType(SolutionType.GROUPED_AIC);
            }
        }
        if (onlyGroupedNiceLoops && !grouped) {
            return;
        }
        // jetzt auf doppelte prüfen
        String del = globalStep.getCandidateString();
        Integer oldIndex = deletesMap.get(del);
        if (oldIndex != null && steps.get(oldIndex.intValue()).getChainLength() <= globalStep.getChains().get(0).getLength()) {
            // Für diese Kandidaten gibt es schon eine Chain und sie ist kürzer als die neue
            return;
        }
        deletesMap.put(del, steps.size());
        try {
            // Die Chain muss kopiert werden
            newChain = (Chain) globalStep.getChains().get(0).clone();
            globalStep.getChains().clear();
            globalStep.getChains().add(newChain);
            adjustChains(globalStep);
            steps.add((SolutionStep) globalStep.clone());
        } catch (CloneNotSupportedException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
        }
    }

    private void fillTables() {
        // Tables initialisieren
        for (int i = 0; i < onTable.length; i++) {
            onTable[i].reset();
            offTable[i].reset();
        }
        extendedTableMap.clear();
        extendedTableIndex = 0;

        if (chainsOnly) {
            // nur direkte Links aufnehmen -> sollte dann eigentlich nur chains geben
            for (int i = 0; i < sudoku.getCells().length; i++) {
                if (sudoku.getCell(i).getValue() != 0) {
                    // Zelle gesetzt -> ignorieren
                    continue;
                }
                for (int j = 1; j <= 9; j++) {
                    if (!sudoku.getCell(i).isCandidate(candType, j)) {
                        continue;
                    }
                    int cand = j;
                    onTable[i * 10 + cand].addEntry(i, cand, true);
                    offTable[i * 10 + cand].addEntry(i, cand, false);
                    // Kandidat setzen löscht alle anderen Kandidaten der Zelle und der Häuser der Zelle
                    // Kandidat löschen setzt alle Singles (kann mit check auf andere Häuser kombiniert werden)
                    // da alle Aktionen auf das Startglied der Table zurückgeführt werden, ist der retIndex immer 0
                    for (int k = 1; k <= 9; k++) {
                        if (k == j || !sudoku.getCell(i).isCandidate(candType, k)) {
                            continue;
                        }
                        onTable[i * 10 + cand].addEntry(i, k, false);
                        if (sudoku.getCell(i).getAnzCandidates(candType) == 2) {
                            offTable[i * 10 + cand].addEntry(i, k, true);
                        }
                    }
                    tmpSet1.set(sudoku.getAllowedPositions()[cand]);
                    tmpSet1.remove(i);
                    getLinksForCandidate(i, cand, tmpSet1, Sudoku.lineTemplates[Sudoku.getLine(i)]);
                    getLinksForCandidate(i, cand, tmpSet1, Sudoku.colTemplates[Sudoku.getCol(i)]);
                    getLinksForCandidate(i, cand, tmpSet1, Sudoku.blockTemplates[Sudoku.getBlock(i)]);
                }
            }
        } else {
            // jetzt alle Zellen und alle Kandidaten durchgehen: Jeweils Wert setzen/Kandidat löschen
            // und die resultierenden Eliminierungen und Singles mitschreiben
            savedSudoku = sudoku.clone();
            for (int i = 0; i < savedSudoku.getCells().length; i++) {
//            if (i != 52) {
//                // nur zum Testen
//                continue;
//            }
                if (savedSudoku.getCell(i).getValue() != 0) {
                    // Zelle gesetzt -> ignorieren
                    continue;
                }
                short[] cands = savedSudoku.getCell(i).getAllCandidates(candType);
                for (int j = 0; j < cands.length; j++) {
                    int cand = cands[j];
                    sudoku.set(savedSudoku);
                    getTableEntry(onTable[i * 10 + cand], i, cand, true);
                    sudoku.set(savedSudoku);
                    getTableEntry(offTable[i * 10 + cand], i, cand, false);
                }
            }
            sudoku.set(savedSudoku);
        }
    }

    /**
     * CAUTION: Must be called AFTER fillTables() or the attributes extendedMap 
     * and extendedTableIndex will not be properly initialized; the initialization
     * cannot be moved here, because it must be possible to call fillTablesWithGroupNodes() 
     * and fillTablesWithAls() in arbitrary order.
     */
    private void fillTablesWithGroupNodes() {
        // get all the group nodes (group nodes are always handled as chains - only direct implications are stored)
        groupNodes = GroupNode.getGroupNodes(sudoku);
        // for every group node do: make a table for every group node (on and off);
        // write the index in extendedTable into extendedTableMap (together with the group node entry)
        //   for on-entries: every candidate that can see all group node cells is turned off
        //                   every other group node that can see (and doesnt overlap) the actual group node is turned off
        //   for off-entries: if a single candidate in one of the houses of the group node exists, it is turned on
        //                    if only one other non-overlapping group node (without extra non-group nodes) exists in one of the houses, it is turned on
        //
        // links to the group nodes have to be added in normal tables that trigger the group node:
        //   for on-entries: if only one additional candidate exists in one of the houses, the entry is added to that candidate's offTable
        //   for off-entries: the entry is added to the onTable of every candidate that sees the group node
        for (int i = 0; i < groupNodes.size(); i++) {
            GroupNode gn = groupNodes.get(i);
            // one table for on
            TableEntry onEntry = getNextExtendedTableEntry(extendedTableIndex);
            onEntry.addEntry(gn.index1, gn.index2, gn.index3, Chain.GROUP_NODE, gn.cand, true, 0, 0, 0, 0, 0);
            extendedTableMap.put(onEntry.entries[0], extendedTableIndex);
            extendedTableIndex++;
            // and one for off
            TableEntry offEntry = getNextExtendedTableEntry(extendedTableIndex);
            offEntry.addEntry(gn.index1, gn.index2, gn.index3, Chain.GROUP_NODE, gn.cand, false, 0, 0, 0, 0, 0);
            extendedTableMap.put(offEntry.entries[0], extendedTableIndex);
            extendedTableIndex++;

            // ok: collect candidates that can see the group node
            tmpSet.set(sudoku.getAllowedPositions()[gn.cand]);
            tmpSet.and(gn.buddies);
            if (!tmpSet.isEmpty()) {
                // every candidate that can see the group node is turned of by the on-entry
                // every candidate's onTable triggers the offEntry
                for (int j = 0; j < tmpSet.size(); j++) {
                    int index = tmpSet.get(j);
                    onEntry.addEntry(index, gn.cand, false);
                    TableEntry tmp = onTable[index * 10 + gn.cand];
                    tmp.addEntry(gn.index1, gn.index2, gn.index3, Chain.GROUP_NODE, gn.cand, false, 0, 0, 0, 0, 0);
                }
                // if in a given house only one additional candidate exists, it is turned on by the off-entry
                // the candidates offTable triggers the offEntry
                tmpSet1.set(tmpSet);
                tmpSet1.and(Sudoku.blockTemplates[gn.block]);
                if (!tmpSet1.isEmpty() && tmpSet1.size() == 1) {
                    offEntry.addEntry(tmpSet1.get(0), gn.cand, true);
                    TableEntry tmp = offTable[tmpSet1.get(0) * 10 + gn.cand];
                    tmp.addEntry(gn.index1, gn.index2, gn.index3, Chain.GROUP_NODE, gn.cand, true, 0, 0, 0, 0, 0);
                }
                tmpSet1.set(tmpSet);
                if (gn.line != -1) {
                    tmpSet1.and(Sudoku.lineTemplates[gn.line]);
                } else {
                    tmpSet1.and(Sudoku.colTemplates[gn.col]);
                }
                if (!tmpSet1.isEmpty() && tmpSet1.size() == 1) {
                    offEntry.addEntry(tmpSet1.get(0), gn.cand, true);
                    TableEntry tmp = offTable[tmpSet1.get(0) * 10 + gn.cand];
                    tmp.addEntry(gn.index1, gn.index2, gn.index3, Chain.GROUP_NODE, gn.cand, true, 0, 0, 0, 0, 0);
                }
            }

            // next: a group node can of course be connected to another group node
            // check all other group nodes for the same candidate: if they share one of
            // the houses but don't overlap, they are connected
            // NOTE: there cant be more than three group nodes in one house
            int lineAnz = 0;
            int line1Index = -1;
            int colAnz = 0;
            int col1Index = -1;
            int blockAnz = 0;
            int block1Index = -1;
            GroupNode gn2 = null;
            for (int j = 0; j < groupNodes.size(); j++) {
                gn2 = groupNodes.get(j);
                if (j == i) {
                    // thats us, skip
                    continue;
                }
                if (gn.cand != gn2.cand) {
                    // wrong candidate -> skip
                    continue;
                }
                // check for overlap
                tmpSet2.set(gn.indices);
                if (!tmpSet2.andEmpty(gn2.indices)) {
                    // group nodes do overlap -> skip
                    continue;
                }
                if (gn.line != -1 && gn.line == gn2.line) {
                    // store it for later use
                    lineAnz++;
                    if (lineAnz == 1) {
                        line1Index = j;
                    }
                    // group node is in the same line -> on-entry turns it off
                    onEntry.addEntry(gn2.index1, gn2.index2, gn2.index3, Chain.GROUP_NODE, gn.cand, false, 0, 0, 0, 0, 0);
                }
                if (gn.col != -1 && gn.col == gn2.col) {
                    // store it for later use
                    colAnz++;
                    if (colAnz == 1) {
                        col1Index = j;
                    }
                    // group node is in the same col -> on-entry turns it off
                    onEntry.addEntry(gn2.index1, gn2.index2, gn2.index3, Chain.GROUP_NODE, gn.cand, false, 0, 0, 0, 0, 0);
                }
                if (gn.block == gn2.block) {
                    // store it for later use
                    blockAnz++;
                    if (blockAnz == 1) {
                        block1Index = j;
                    }
                    // group node is in the same block -> on-entry turns it off
                    onEntry.addEntry(gn2.index1, gn2.index2, gn2.index3, Chain.GROUP_NODE, gn.cand, false, 0, 0, 0, 0, 0);
                }
            }
            // if in one house was only one additional group node and if there is no additional single candidate
            // in that same house -> group node is turned on by off-entry
            if (lineAnz == 1) {
                gn2 = groupNodes.get(line1Index);
                tmpSet.set(Sudoku.lineTemplates[gn.line]);
                tmpSet.and(sudoku.getAllowedPositions()[gn.cand]);
                tmpSet.andNot(gn.indices);
                tmpSet.andNot(gn2.indices);
                if (tmpSet.isEmpty()) {
                    // no additional candidates -> write it
                    offEntry.addEntry(gn2.index1, gn2.index2, gn2.index3, Chain.GROUP_NODE, gn.cand, true, 0, 0, 0, 0, 0);
                }
            }
            if (colAnz == 1) {
                gn2 = groupNodes.get(col1Index);
                tmpSet.set(Sudoku.colTemplates[gn.col]);
                tmpSet.and(sudoku.getAllowedPositions()[gn.cand]);
                tmpSet.andNot(gn.indices);
                tmpSet.andNot(gn2.indices);
                if (tmpSet.isEmpty()) {
                    // no additional candidates -> write it
                    offEntry.addEntry(gn2.index1, gn2.index2, gn2.index3, Chain.GROUP_NODE, gn.cand, true, 0, 0, 0, 0, 0);
                }
            }
            if (blockAnz == 1) {
                gn2 = groupNodes.get(block1Index);
                tmpSet.set(Sudoku.blockTemplates[gn.block]);
                tmpSet.and(sudoku.getAllowedPositions()[gn.cand]);
                tmpSet.andNot(gn.indices);
                tmpSet.andNot(gn2.indices);
                if (tmpSet.isEmpty()) {
                    // no additional candidates -> write it
                    offEntry.addEntry(gn2.index1, gn2.index2, gn2.index3, Chain.GROUP_NODE, gn.cand, true, 0, 0, 0, 0, 0);
                }
            }
        }
    }

    /**
     * CAUTION: Must be called AFTER fillTables() or the attributes extendedMap 
     * and extendedTableIndex will not be properly initialized; the initialization
     * cannot be moved here, because it must be possible to call fillTablesWithGroupNodes() 
     * and fillTablesWithAls() in arbitrary order.
     */
    private void fillTablesWithAls() {
        // Now ALS:
        // ALS can only be reached over weak links (single or multiple candidates), and they can
        // be left via weak or strong links. Turning the candidate(s)
        // off changes the ALS into a locked set that can provide eliminations or force
        // a cell to a certain value (the candidate eliminations that force the cell
        // are not stored in the chain, since we can't handle links with more than
        // one candidate).
        // Since every ALS can trigger different sets of eliminations depending on how it is reached, every ALS
        // can have more than one table entry. The weak link that provides the locked set is not stored in the
        // chain (it can affect multiple candidates, that don't form a group node, which we can't handle).
        // Eliminations caused by locked sets can trigger other ALSes.
        //
        // for every ALS do: check all possible entries; if an entry provides eliminations or forces
        //     cells make a table for that entry (only off);
        // write the index in extendedTable into extendedTableMap (together with the ALS entry)
        //   the ALS entry is added to the onTable of the candidate/group node/als that provides the entry
        //   every candidate/group node deleted by the resulting locked set is added to the ALS's table
        //   as is every newly triggered ALS
        // the ALS entry has the index of the first candidate that provides the entry set as index1, the
        // index in the ALS-array set as index2.
        //
        // more detailed:
        // for every ALS do
        //   - for every candidate of the als find all remaining candidates
        //        in the grid: they are all valid entries
        //   - if one of the entries from above is a member of a group node, that
        //        doesn't overlap the als, the group node is an additional entry
        //   - if the remaining locked set provides eliminations, record them and
        //        check for possible forcings; note that the eliminations could
        //        provide an entry for another als; also, the eliminations could
        //        form a group node
        //
        // 20090220: BUG - alsBuddies contains only cells, that can see all cells of the ALS
        //   its then used for finding possible entries and eliminations; this is incomplete:
        //   entries and eliminations only have to see all cells of the ALS that contain a 
        //   certain candidate!
        alses = Als.getAlses(sudoku, true);
        for (int i = 0; i < alses.size(); i++) {
            Als als = alses.get(i);
            if (als.indices.size() == 1) {
                // alses with size one (= nodes with two candidates) are ignored
                continue;
            }
//            als.getBuddies(alsBuddies);
            // for every candidate find all remaining candidates in the grid
            for (int j = 1; j <= 9; j++) {
                // first check, if there are possible eliminations (nothing to do if not):
                // for all other candidates get all cells, that contain that 
                // candidate and can see all cells of the ALS;
                // any such candidate can be eliminated
                // 20090220: a canddiate doesnt have to see all cells of the ALS, only the cells
                //    that contain that candidate
                if (als.indicesPerCandidat[j] == null || als.indicesPerCandidat[j].isEmpty()) {
                    // nothing to do -> next candidate
                    continue;
                }
                boolean eliminationsPresent = false;
                for (int k = 1; k <= 9; k++) {
                    alsEliminations[k].clear();
                    if (k == j) {
                        // that candidate is not in the als anymore
                        continue;
                    }
                    if (als.indicesPerCandidat[k] != null) {
                        alsEliminations[k].set(sudoku.getAllowedPositions()[k]);
                        // 20090220: use the correct buddies
                        //alsEliminations[k].and(alsBuddies);
                        alsEliminations[k].and(als.buddiesPerCandidat[k]);
                        if (!alsEliminations[k].isEmpty()) {
                            // possible eliminations found
                            eliminationsPresent = true;
                        }
                    }
                }
                if (!eliminationsPresent) {
                    // nothing to do -> next candidate
                    continue;
                }
                // Eliminations are possible, create a table for the als with that entry
                int entryIndex = als.indicesPerCandidat[j].get(0);
                TableEntry offEntry = null;
                if ((offEntry = getAlsTableEntry(entryIndex, i, j)) == null) {
                    offEntry = getNextExtendedTableEntry(extendedTableIndex);
                    offEntry.addEntry(entryIndex, i, Chain.ALS_NODE, j, false);
                    extendedTableMap.put(offEntry.entries[0], extendedTableIndex);
                    extendedTableIndex++;
                }
                // put the ALS into the onTables of all entry candidates:
                // find all candidates, that can provide an entry into the als
                tmpSet.set(sudoku.getAllowedPositions()[j]);
                // 20090220: use the correct buddies
                //tmpSet.and(alsBuddies);
                tmpSet.and(als.buddiesPerCandidat[j]);
                int alsEntry = Chain.makeSEntry(entryIndex, i, j, false, Chain.ALS_NODE);
                for (int k = 0; k < tmpSet.size(); k++) {
                    int actIndex = tmpSet.get(k);
                    TableEntry tmp = onTable[actIndex * 10 + j];
                    tmp.addEntry(entryIndex, i, Chain.ALS_NODE, j, false);
                    // every group node in which the candidate is a member and which doesn't overlap
                    // the als is a valid entry too; since we look for an on entry for the group
                    // node all group node cells have to see the appropriate cells of the als
                    for (int l = 0; l < groupNodes.size(); l++) {
                        GroupNode gAct = groupNodes.get(l);
                        if (gAct.cand == j && gAct.indices.contains(actIndex)) {
                            // first check overlapping
                            tmpSet1.set(als.indices);
                            if (!tmpSet1.andEmpty(gAct.indices)) {
                                // group node overlaps als -> ignore
                                continue;
                            }
                            // now check visibility: all group node cells have to be
                            // buddies of the als cells that hold the entry candidate
                            tmpSet1.set(als.indicesPerCandidat[j]);
                            if (!tmpSet1.andEquals(gAct.buddies)) {
                                // invalid
                                continue;
                            }
                            // the same group node could be found more than once
                            int entry = Chain.makeSEntry(gAct.index1, gAct.index2, gAct.index3, j, true, Chain.GROUP_NODE);
                            // if we had had that node already, it's onTable contained the als
                            TableEntry gTmp = extendedTable.get(extendedTableMap.get(entry));
                            if (gTmp.indices.containsKey(alsEntry)) {
                                // already present -> ignore
                                continue;
                            }
                            // new group node -> add the als
                            gTmp.addEntry(entryIndex, i, Chain.ALS_NODE, j, false);
                        }
                    }
                }
                // now for the eliminations: candidates and group nodes
                for (int k = 1; k <= 9; k++) {
                    if (alsEliminations[k].isEmpty()) {
                        // no eliminations
                        continue;
                    }
                    // every single elimination must be recorded
                    for (int l = 0; l < alsEliminations[k].size(); l++) {
                        // 20090213: add ALS penalty to distance
                        offEntry.addEntry(alsEliminations[k].get(l), k, als.getChainPenalty(), false);
//                        offEntry.addEntry(alsEliminations[k].get(l), k, false);
                    }
                    // if a group node is a subset of the eliminations, it is turned off as well
                    for (int l = 0; l < groupNodes.size(); l++) {
                        GroupNode gAct = groupNodes.get(l);
                        if (gAct.cand != k) {
                            // group node is for wrong candidate
                            continue;
                        }
                        tmpSet1.set(gAct.indices);
                        if (!tmpSet1.andEquals(alsEliminations[k])) {
                            // not all group node cells are eliminated
                            continue;
                        }
                        // 20090213: adjust penalty for ALS
                        offEntry.addEntry(gAct.index1, gAct.index2, gAct.index3, Chain.GROUP_NODE, 
                                k, false, 0, 0, 0, 0, 0, als.getChainPenalty());
//                        offEntry.addEntry(gAct.index1, gAct.index2, gAct.index3, Chain.GROUP_NODE, k, false, 0, 0, 0, 0, 0);
                    }
                }
                // now als: if the eliminations for one candidate cover all cells with
                // that candidate in another non-overlapping als, that als is triggered
                // we do that here for performance reasons
                for (int k = 0; k < alses.size(); k++) {
                    if (k == i) {
                        // not for ourself
                        continue;
                    }
                    Als tmpAls = alses.get(k);
                    tmpSet1.set(als.indices);
                    if (!tmpSet1.andEmpty(tmpAls.indices)) {
                        // overlapping -> ignore
                        continue;
                    }
                    for (int l = 1; l <= 9; l++) {
                        if (alsEliminations[l] == null || alsEliminations[l].isEmpty() ||
                                tmpAls.indicesPerCandidat[l] == null ||
                                tmpAls.indicesPerCandidat[l].isEmpty()) {
                            // nothing to do
                            continue;
                        }
                        // 20090220: tmpAls has not to be equal to alsEliminations, alsEliminations
                        //   must contain tmpAls!
                        //tmpSet1.set(tmpAls.indicesPerCandidat[l]);
                        //if (!tmpSet1.andEquals(alsEliminations[l])) {
                        tmpSet1.set(alsEliminations[l]);
                        if (!tmpSet1.contains(tmpAls.indicesPerCandidat[l])) {
                            // no entry
                            continue;
                        }
                        // create the table for the triggered als (if it does not produce
                        // valid eliminations it would be missing later on)
                        int tmpAlsIndex = tmpAls.indicesPerCandidat[l].get(0);
                        if (getAlsTableEntry(tmpAlsIndex, k, l) == null) {
                            TableEntry tmpAlsEntry = getNextExtendedTableEntry(extendedTableIndex);
                            tmpAlsEntry.addEntry(tmpAlsIndex, k, Chain.ALS_NODE, l, false);
                            extendedTableMap.put(tmpAlsEntry.entries[0], extendedTableIndex);
                            extendedTableIndex++;
                        }
                        // 20090213: adjust for ALS penalty
                        offEntry.addEntry(tmpAlsIndex, k, Chain.ALS_NODE, l, false, als.getChainPenalty());
//                        offEntry.addEntry(tmpAlsIndex, k, Chain.ALS_NODE, l, false);
                    }
                }
                // last but not least: forcings
                // if one of the als's buddies has only one candidate left
                // after the eliminations, it is forced
                // 20090220: use the correct buddies
                // only necessary, if the cell contains more than 2 candidates (its
                // handled correctly with only two candidates)
                for (int k = 0; k < als.buddies.size(); k++) {
                    int cellIndex = als.buddies.get(k);
                    SudokuCell cell = sudoku.getCell(cellIndex);
                    if (cell.getValue() != 0 || cell.getAnzCandidates(candType) == 2) {
                        // cell already set
                        continue;
                    }
                    cell.getCandidateSet(tmpSet1, candType);
                    for (int l = 1; l <= 9; l++) {
                        if (alsEliminations[l] != null && alsEliminations[l].contains(cellIndex)) {
                            // delete candidate
                            tmpSet1.remove(l);
                        }
                    }
                    if (tmpSet1.size() == 1) {
                        // forcing!
                        // 20090213: adjust for ALS penalty (plus the extra omitted link)
                        offEntry.addEntry(cellIndex, tmpSet1.get(0), als.getChainPenalty() + 1, true);
//                        offEntry.addEntry(cellIndex, tmpSet1.get(0), true);
                    }
                }
            }
        }
    }

    /**
     * Tries to find an extended table entry for a given als with the given
     * entry candidate; if none can be found, null is returned
     */
    private TableEntry getAlsTableEntry(int entryCellIndex, int alsIndex, int cand) {
        int entry = Chain.makeSEntry(entryCellIndex, alsIndex, cand, false, Chain.ALS_NODE);
        if (extendedTableMap.containsKey(entry)) {
            return extendedTable.get(extendedTableMap.get(entry));
        }
        return null;
    }

    /**
     * Returns the next free TableEntry from extendedTable (reuse of entries in
     * multiple search runs). If no entry is left, a new one is created
     * ans added to extendedTable.
     */
    private TableEntry getNextExtendedTableEntry(int tableIndex) {
        TableEntry entry = null;
        if (tableIndex >= extendedTable.size()) {
            entry = new TableEntry();
            extendedTable.add(entry);
        } else {
            entry = extendedTable.get(tableIndex);
            entry.reset();
        }
        return entry;
    }

    /**
     * Sammelt alle Links für ein Haus: tmpSet1 enthält alle noch möglichen Positionen für cand
     * (außer index), houseTemplate ist das zu index passende Template
     */
    private void getLinksForCandidate(int index, int cand, SudokuSet tmpSet1, SudokuSet houseTemplate) {
        tmpSet.set(tmpSet1);
        tmpSet.and(houseTemplate);
        for (int i = 0; i < tmpSet.size(); i++) {
            onTable[index * 10 + cand].addEntry(tmpSet.get(i), cand, false);
        }
        if (tmpSet.size() == 1) {
            offTable[index * 10 + cand].addEntry(tmpSet.get(0), cand, true);
        }
    }

    /**
     * Wir schauen eine Ebene voraus:
     *   - set == true: Wert setzen, anschließend alle Naked und alle Hidden Singles ermitteln
     *     und auch setzen (damit die gelöschten Kandidaten richtig eingeschrieben werden)
     *   - set == false: Kandidat löschen, dann schauen, ob in der Zelle nur noch ein Kandidat
     *     existiert. Wenn ja, sofort setzen, dann weiter wie oben.
     */
    private void getTableEntry(TableEntry entry, int cellIndex, int cand, boolean set) {
        // erster Eintrag, hier starten wir
        if (set) {
            setCell(cellIndex, cand, entry, false, false, 0);
        } else {
            sudoku.delCandidate(cellIndex, candType, cand);
            entry.addEntry(cellIndex, cand, false, 0);
            if (sudoku.getCell(cellIndex).getAnzCandidates(candType) == 1) {
                int setCand = sudoku.getCell(cellIndex).getAllCandidates(candType)[0];
                // getRetIndeices == false bewirkt retIndex == 0
                setCell(cellIndex, setCand, entry, false, false, 0);
            }
        }
        for (int j = 0; j < Options.getInstance().anzTableLookAhead; j++) {
            singleSteps.clear();
            simpleSolver.setSteps(singleSteps);
            simpleSolver.findAllNakedXle(sudoku, 1, false);
            simpleSolver.findAllHiddenXle(sudoku, 1, false);
            for (int i = 0; i < singleSteps.size(); i++) {
                SolutionStep step = singleSteps.get(i);
                int index = step.getIndices().get(0);
                setCell(index, step.getValues().get(0), entry, true,
                        step.getType() == SolutionType.NAKED_SINGLE,
                        step.getEntity());
            }
        }
    }

    /**
     * Es muss nicht nur gesetzt werden, sondern es müssen auch alle gelöschten Kandidaten mitgeschrieben werden
     */
    private void setCell(int cellIndex, int cand, TableEntry entry, boolean getRetIndices, boolean nakedSingle, int entityType) {
        // Alle Kandidaten bestimmen, die gelöscht werden müssen (auch alle Kandidaten
        // in der Zelle selbst). Als Grund für das Löschen wird auf den Setz-Eintrag
        // rückverwiesen
        tmpSet.set(sudoku.getAllowedPositions()[cand]);
        tmpSet.remove(cellIndex);
        tmpSet.and(Sudoku.buddies[cellIndex]);
        short[] cands = sudoku.getCell(cellIndex).getAllCandidates(candType);
        sudoku.setCell(cellIndex, cand);
        int retIndex = entry.index;
        if (getRetIndices) {
            // ermitteln, welche(r) Kandidat für das Setzen verantwortlich ist
            for (int i = 0; i < retIndices[0].length; i++) {
                retIndices[0][i] = 0;
            }
            if (nakedSingle) {
                // Kandidaten der eigenen Zelle prüfen
                short[] cellCands = savedSudoku.getCell(cellIndex).getAllCandidates(candType);
                if (cellCands.length > retIndices[0].length + 1) {
                    Logger.getLogger(getClass().getName()).log(Level.WARNING, "Zu viele Kandidaten (setCell() - Naked Single");
                }
                int ri = 0;
                for (int i = 0; i < cellCands.length && ri < retIndices[0].length; i++) {
                    if (cellCands[i] == cand) {
                        continue;
                    }
                    retIndices[0][ri++] = entry.getEntryIndex(cellIndex, false, cellCands[i]);
                }
            } else {
                // Kandidaten der eigenen Häuser prüfen
                if (entityType == SudokuCell.LINE) {
                    getRetIndicesForHouse(cellIndex, cand, Sudoku.lineTemplates[Sudoku.getLine(cellIndex)], entry);
                } else if (entityType == SudokuCell.COL) {
                    getRetIndicesForHouse(cellIndex, cand, Sudoku.colTemplates[Sudoku.getCol(cellIndex)], entry);
                } else {
                    getRetIndicesForHouse(cellIndex, cand, Sudoku.blockTemplates[Sudoku.getBlock(cellIndex)], entry);
                }
            }
            entry.addEntry(cellIndex, cand, true, retIndices[0][0], retIndices[0][1], retIndices[0][2],
                    retIndices[0][3], retIndices[0][4]);
        } else {
            entry.addEntry(cellIndex, cand, true);
        }
        for (int i = 0; i < tmpSet.size(); i++) {
            entry.addEntry(tmpSet.get(i), cand, false, retIndex);
        }
        for (int i = 0; i < cands.length; i++) {
            if (cands[i] != cand) {
                entry.addEntry(cellIndex, cands[i], false, retIndex);
            }
        }
    }

    private void getRetIndicesForHouse(int cellIndex, int cand, SudokuSet houseSet, TableEntry entry) {
        tmpSet1.set(savedSudoku.getAllowedPositions()[cand]);
        tmpSet1.remove(cellIndex);
        tmpSet1.and(houseSet);
        if (tmpSet1.size() > retIndices[0].length + 1) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Zu viele Kandidaten (setCell() - Hidden Single");
        }
        int ri = 0;
        for (int i = 0; i < tmpSet1.size() && ri < retIndices[0].length; i++) {
            retIndices[0][ri++] = entry.getEntryIndex(tmpSet1.get(i), false, cand);
        }
    }

    /**
     * Jeder einzelne Tabelleneintrag wird durchgegangen:
     * Für jeden einzelnen Eintrag in entry.entries werden alle neu dazukommenden
     * Folgerungen eingefügt. Das geht so lange, bis entweder keine Folgerungen mehr möglich sind
     * oder bis entry.entries voll ist.
     *
     * Für Einträge, die neu dazukommen, wird nur ein Querverweis auf die Ursprungstabelle gesetzt.
     * Bei Einträgen der neuen Tabelle, die schon vorhanden sind, wird geprüft, ob der neue Weg
     * kürzer ist als der alte. Wenn ja, wird er übernommen.
     *
     * Einfache Version mit Schleifen (wir werden sehen, ob das geht!)
     *
     * Group node table entries are never expanded (since we dont start or end with a group node,
     * that wouldnt make any sense). They are however used as possible implications.
     */
    private void expandTables(TableEntry[] table) {
        for (int i = 0; i < table.length; i++) {
//            if (i != 521) {
//                continue;
//            }
            if (table[i].index == 0) {
                // Zelle schon gesetzt -> keine Implikationen
                continue;
            }
            // Jeden Eintrag durchgehen (beginnend beim zweiten, der erste ist der Ausgangspunkt)
            TableEntry dest = table[i];   // table that should be expanded

            boolean isFromOnTable = false;
            boolean isFromExtendedTable = false;
            for (int j = 1; j < dest.entries.length; j++) {
                if (dest.entries[j] == 0) {
                    // ok -> fertig
                    break;
                }
                if (dest.isFull()) {
                    // nichts geht mehr!
                    Logger.getLogger(getClass().getName()).log(Level.WARNING, "TableEntry full!");
                    break;
                }
                TableEntry src = null;  // table for the current entry -> all entries in src have to be written into dest

                int srcTableIndex = dest.getCellIndex(j) * 10 + dest.getCandidate(j);
                isFromExtendedTable = false;
                isFromOnTable = false;
                if (Chain.getSNodeType(dest.entries[j]) != Chain.NORMAL_NODE) {
                    Integer tmpSI = extendedTableMap.get(dest.entries[j]);
                    if (tmpSI == null) {
                        Logger.getLogger(getClass().getName()).log(Level.WARNING, "Table for " + printTableEntry(dest.entries[j]) + " not found!");
                        continue;
                    }
                    srcTableIndex = tmpSI.intValue();
                    src = extendedTable.get(srcTableIndex);
                    isFromExtendedTable = true;
                } else {
                    if (dest.isStrong(j)) {
                        src = onTable[srcTableIndex];
                    } else {
                        src = offTable[srcTableIndex];
                    }
                    isFromOnTable = dest.isStrong(j);
                }
                if (src.index == 0) {
                    // sollte eigentlich nicht sein!
                    StringBuffer tmpBuffer = new StringBuffer();
                    tmpBuffer.append("TableEntry für " + dest.entries[j] + " nicht gefunden!\r\n");
                    tmpBuffer.append("i == " + i + ", j == " + j + ", dest.entries[j] == " + dest.entries[j] + ": ");
                    tmpBuffer.append(printTableEntry(dest.entries[j]));
                    Logger.getLogger(getClass().getName()).log(Level.WARNING, tmpBuffer.toString());
                    continue;
                }
                // ok -> expandieren
                int srcBaseDistance = dest.getDistance(j);
                // Alle Einträge durchgehen
                for (int k = 1; k < src.index; k++) {
                    // nur Originaleinträge!
                    if (src.isExpanded(k)) {
                        // selbst schon expandiert -> ignorieren
                        continue;
                    }
                    int srcDistance = src.getDistance(k);
                    if (dest.indices.containsKey(src.entries[k])) {
                        // Eintrag schon vorhanden -> auf Weglänge prüfen
                        int orgIndex = dest.getEntryIndex(src.entries[k]);
                        // 20090213: prefer normal nodes to group nodes or als
//                        if (dest.isExpanded(orgIndex) && dest.getDistance(orgIndex) > (srcBaseDistance + srcDistance)) {
                        if (dest.isExpanded(orgIndex) && 
                                (dest.getDistance(orgIndex) > (srcBaseDistance + srcDistance) ||
                                dest.getDistance(orgIndex) == (srcBaseDistance + srcDistance) &&
                                dest.getNodeType(orgIndex) > src.getNodeType(k))) {
                            // Alter Eintrag war länger oder komplizierter als neuer -> umschreiben
                            dest.retIndices[orgIndex] = TableEntry.makeSRetIndex(srcTableIndex, 0, 0, 0, 0);
                            // Expanded-Flag geht verloren -> neu setzen
                            dest.setExpanded(orgIndex);
                            if (isFromExtendedTable) {
                                dest.setExtendedTable(orgIndex);
                            } else if (isFromOnTable) {
                                dest.setOnTable(orgIndex);
                            }
                            dest.setDistance(orgIndex, srcBaseDistance + srcDistance);
                        }
                    } else {
                        // Neuer Eintrag
                        int srcCellIndex = src.getCellIndex(k);
                        int srcCand = src.getCandidate(k);
                        boolean srcStrong = src.isStrong(k);
                        if (Chain.getSNodeType(src.entries[k]) == Chain.NORMAL_NODE) {
                            dest.addEntry(srcCellIndex, srcCand, srcStrong, srcTableIndex);
                        } else {
                            int tmp = src.entries[k];
                            dest.addEntry(Chain.getSCellIndex(tmp), Chain.getSCellIndex2(tmp), Chain.getSCellIndex3(tmp),
                                    Chain.getSNodeType(tmp), srcCand, srcStrong, srcTableIndex, 0, 0, 0, 0);
                        }
                        dest.setExpanded(dest.index - 1);
                        if (isFromExtendedTable) {
                            dest.setExtendedTable(dest.index - 1);
                        } else if (isFromOnTable) {
                            dest.setOnTable(dest.index - 1);
                        }
                        dest.setDistance(dest.index - 1, srcBaseDistance + srcDistance);
                    }
                }
            }
        }
    }

    private void addChain(TableEntry entry, int cellIndex, int cand, boolean set) {
        addChain(entry, cellIndex, cand, set, false);
    }

    private void addChain(TableEntry entry, int cellIndex, int cand, boolean set, boolean isNiceLoop) {
        addChain(entry, cellIndex, cand, set, isNiceLoop, false);
    }
    
    /**
     * Passende Chain bilden und in Step schreiben: Beim Einschreiben muss die Reihenfolge
     * umgedreht werden, das Format muss an das Chain-Format in SolutionStep
     * angepasst werden.
     *
     *
     * @param entry TableEntry, für den die Chain gebildet werden soll
     * @param cellIndex Index der Zelle des letzten Gliedes der Chain
     * @param cand Kandidat des letzten Gliedes der Chain
     * @param set Letztes Glied der Chain ist strong oder weak
     * @param isNiceLoop Wie isAic, aber erster Link muss aus Zelle herausführen;
     *    es darf auf den Beginn der chain verwiesen werden, weil sie dann aus ist!
     * @param isAic Kein Glied der Kette darf auf die Mitte
     *    der Kette zurückverweisen (aber zwei aufeinanderfolgende Glieder dürfen in der
     *    selben Zelle liegen). Bei ungültigen Chains wird die Verarbeitung abgebrochen.
     *    Ein Verweis auf den Beginn der Chain ist für AICs ebenfalls ungültig!
     */
//    private void addChain(TableEntry entry, int cellIndex, int cand, boolean set, int lastEntry, boolean noLassos) {
    private void addChain(TableEntry entry, int cellIndex, int cand, boolean set, boolean isNiceLoop, boolean isAic) {
//        if (cellIndex != 79 || cand != 6 || entry.getCellIndex(0) != 73 || entry.getCandidate(0) != 1) {
//            return;
//        }
        buildChain(entry, cellIndex, cand, set);

        // neue Chain bilden
        int j = 0;
        if (isNiceLoop || isAic) {
            lassoSet.clear();
            // bei NiceLoops darf der letzte Link nicht innerhalb der Ausgangszelle liegen
            // (sonst gibt das doppelte chains, die falsch erkannt werden). Der letzte Link
            // führt zur Ausgangszelle zurück!
            if (isNiceLoop && Chain.getSCellIndex(chain[0]) == Chain.getSCellIndex(chain[1])) {
                // die kürzere Version kommt irgendwann extra
                return;
            }
        }
        int lastCellIndex = -1;
        int lastCellEntry = -1;
        int firstCellIndex = Chain.getSCellIndex(chain[chainIndex - 1]);
        for (int i = chainIndex - 1; i >= 0; i--) {
            int oldEntry = chain[i];
            int newCellIndex = Chain.getSCellIndex(oldEntry);
            if (isNiceLoop || isAic) {
                // bei NiceLoops darf die Chain nicht auf sich selbst zeigen
                // geprüft wird immer der vorletzte Eintrag, weil der letzte innerhalb der selben Zelle
                // liegen darf (3 hintereinander für dieselbe Zelle ist nicht erlaubt)
                if (lassoSet.contains(newCellIndex)) {
                    // verboten: Link zur Mitte der Chain selbst
                    return;
                }
                // for Nice Loops a reference to the first cell is valid, for AICs it is not!
                //if (lastCellIndex != -1 && lastCellIndex != firstCellIndex) {
                if (lastCellIndex != -1 && (lastCellIndex != firstCellIndex || isAic)) {
                    lassoSet.add(lastCellIndex);
                    // with group nodes: add all cells (nice loop may not cross a group node or als)
                    if (Chain.getSNodeType(lastCellEntry) == Chain.GROUP_NODE) {
                        int tmp = Chain.getSCellIndex2(lastCellEntry);
                        if (tmp != -1) {
                            lassoSet.add(tmp);
                        }
                        tmp = Chain.getSCellIndex3(lastCellEntry);
                        if (tmp != -1) {
                            lassoSet.add(tmp);
                        }
                    } else if (Chain.getSNodeType(lastCellEntry) == Chain.ALS_NODE) {
                        lassoSet.or(alses.get(Chain.getSAlsIndex(lastCellEntry)).indices);
                    }
                }
            }
            lastCellIndex = newCellIndex;
            lastCellEntry = oldEntry;
            tmpChain[j++] = oldEntry;
            // jetzt auf mögliche mins prüfen
            for (int k = 0; k < actMin; k++) {
                if (mins[k][minIndexes[k] - 1] == oldEntry) {
                    // ist min für aktuellen Eintrag -> hinzufügen (der
                    // erste Eintrag wird übersprungen, ist schon in chain)
                    for (int l = minIndexes[k] - 2; l >= 0; l--) {
                        tmpChain[j++] = -mins[k][l];
                    }
                    tmpChain[j++] = Integer.MIN_VALUE;
                }
            }
        }
        if (j > 0) {
            for (int i = 0; i < j; i++) {
                tmpChains[tmpChainsIndex].chain[i] = tmpChain[i];
            }
            tmpChains[tmpChainsIndex].start = 0;
            tmpChains[tmpChainsIndex].end = j - 1;
            tmpChains[tmpChainsIndex].resetLength();
            globalStep.addChain(tmpChains[tmpChainsIndex]);
            tmpChainsIndex++;
        }
    }

    /**
     * Chain für ein bestimmtes Ziel bilden: passenden entry im TableEntry
     * suchen und die Suche delegieren
     */
    private void buildChain(TableEntry entry, int cellIndex, int cand, boolean set) {
        chainIndex = 0;
        int chainEntry = Chain.makeSEntry(cellIndex, cand, set);
        int index = -1;
        for (int i = 0; i < entry.entries.length; i++) {
            if (entry.entries[i] == chainEntry) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Kein Chain-Entry für " + cellIndex + "/" + SolutionStep.getCellPrint(cellIndex) + "/" + cand + "/" + set);
            return;
        }
        // vorbereiten für multiple inferences
        actMin = 0;
        for (int i = 0; i < minIndexes.length; i++) {
            minIndexes[i] = 0;
        }
        // Haupt-Chain bilden
        tmpSetC.clear();
        chainIndex = buildChain(entry, index, chain, false, tmpSetC);
        // jetzt die multipleInferences checken
        int minIndex = 0;
        while (minIndex < actMin) {
            minIndexes[minIndex] = buildChain(entry, entry.getEntryIndex(mins[minIndex][0]), mins[minIndex], true, tmpSetC);
            minIndex++;
        }
    }

    /**
     * Konstruiert eine Chain für ein bestimmtes Ziel:
     *   - Startindex einschreiben
     *   - der retIndex1 zeigt auf das auslösende Glied bzw. auf die Table mit
     *     dem Originaleintrag (springen!)
     *   - wenn es mehr als einen retIndex gibt, den ersten normal behandeln,
     *     die anderen in mins/minIndices speichern, werden später ausgewertet
     *
     * Die Methode endet, wenn der erste Eintrag von entry erreicht ist.
     *
     * Alle Zellen der Haupt-Chain werden in chainSet geschrieben. Wird die Methode
     * für ein min aufgerufen, wird dieses chainSet mit übergeben, die Methode läuft
     * dann, bis die min auf die Haupt-Chain zurückführt.
     *
     * ACHTUNG: Die Chain steht in umgekehrter Reihenfolge im Array actChain[]
     */
    private int buildChain(TableEntry entry, int entryIndex, int[] actChain, boolean isMin, SudokuSet chainSet) {
        int actChainIndex = 0;
        actChain[actChainIndex++] = entry.entries[entryIndex];
        int firstEntryIndex = entryIndex;
        boolean expanded = false;
        TableEntry orgEntry = entry;
        while (firstEntryIndex != 0 && actChainIndex < actChain.length) {
            if (entry.isExpanded(firstEntryIndex)) {
                // Eintrag kommt aus einer anderen Table -> springen!
                if (entry.isExtendedTable(firstEntryIndex)) {
                    entry = extendedTable.get(orgEntry.getRetIndex(firstEntryIndex, 0));
                } else if (entry.isOnTable(firstEntryIndex)) {
                    entry = onTable[orgEntry.getRetIndex(firstEntryIndex, 0)];
                } else {
                    entry = offTable[orgEntry.getRetIndex(firstEntryIndex, 0)];
                }
                expanded = true;
                firstEntryIndex = entry.getEntryIndex(orgEntry.entries[firstEntryIndex]);
            }
            int tmpEntryIndex = firstEntryIndex;
            for (int i = 0; i < 5; i++) {
                entryIndex = entry.getRetIndex(tmpEntryIndex, i);
                if (i == 0) {
                    // erster retIndex verweist auf das nächste Element -> einschreiben und im
                    // chainSet vermerken, wenn es die Haupt-Chain ist
                    firstEntryIndex = entryIndex;
                    actChain[actChainIndex++] = entry.entries[entryIndex];
                    if (!isMin) {
                        // alle Zellen der Haupt-Chain werden mitgeschrieben
                        chainSet.add(entry.getCellIndex(entryIndex));
                        // group ndoes
                        if (Chain.getSNodeType(entry.entries[entryIndex]) == Chain.GROUP_NODE) {
                            int tmp = Chain.getSCellIndex2(entry.entries[entryIndex]);
                            if (tmp != -1) {
                                chainSet.add(tmp);
                            }
                            tmp = Chain.getSCellIndex3(entry.entries[entryIndex]);
                            if (tmp != -1) {
                                chainSet.add(tmp);
                            }
                        } else if (Chain.getSNodeType(entry.entries[entryIndex]) == Chain.ALS_NODE) {
                            if (Chain.getSAlsIndex(entry.entries[entryIndex]) == -1) {
                                Logger.getLogger(getClass().getName()).log(Level.WARNING, "INVALID ALS_NODE: " + Chain.toString(entry.entries[entryIndex]));
                            }
                            chainSet.or(alses.get(Chain.getSAlsIndex(entry.entries[entryIndex])).indices);
                        }
                    } else {
                        // ist die aktuelle chain eine min, feststellen, ob wir bei einem
                        // Ende angekommen sind (aktueller Entry ist Teil der Haupt-Chain)
                        if (chainSet.contains(entry.getCellIndex(entryIndex))) {
                            // Vorselektion: Die aktuelle Zelle ist zumindest Teil der Haupt-Chain -> jetzt durchsuchen
                            for (int j = 0; j < chainIndex; j++) {
                                if (chain[j] == entry.entries[entryIndex]) {
                                    // fertig!
                                    return actChainIndex;
                                }
                            }
                        }
                    }
                } else {
                    // ist multiple inference -> Startentry merken und später behandeln
                    // wenn wir schon die Netze auflösen, nicht mehr neue Netze bauen!
                    if (entryIndex != 0 && !isMin) {
                        // hier ist 0 nicht mehr erlaubt, das kann nur beim ersten retIndex sein!
                        mins[actMin][0] = entry.entries[entryIndex];
                        minIndexes[actMin++] = 1;
                    }
                }
            }
            if (expanded && firstEntryIndex == 0) {
                // wir waren in einem Verweis-Entry und sind am Anfang angekommen -> zum
                // Original zurückspringen
                int retEntry = entry.entries[0];
                entry = orgEntry;
                firstEntryIndex = entry.getEntryIndex(retEntry);
                expanded = false;
            }
        }
        return actChainIndex;
    }

    private void printTable(String title, TableEntry entry) {
        System.out.println(title + ": ");
        int anz = 0;
        StringBuffer tmp = new StringBuffer();
        for (int i = 0; i < entry.index; i++) {
            if (!entry.isStrong(i)) {
                //continue;
            }
            tmp.append(printTableEntry(entry.entries[i]));
            for (int j = 0; j < entry.getRetIndexAnz(i); j++) {
                int retIndex = entry.getRetIndex(i, j);
                tmp.append(" (");
                if (entry.isExpanded(i)) {
                    tmp.append("EX:" + retIndex + ":" + entry.isExtendedTable(i) + "/" + entry.isOnTable(i) + "/");
//                    TableEntry actEntry = entry.isOnTable(i) ? onTable[retIndex] : offTable[retIndex];
//                    int index1 = actEntry.getEntryIndex(entry.entries[i]);
//                    // eine Ebene zurück anzeigen
//                    for (int k = 0; k < actEntry.getRetIndexAnz(index1); k++) {
//                        int retIndex1 = actEntry.getRetIndex(index1, k);
//                        if (actEntry.isExpanded(index1)) {
//                            tmp.append("EEX/");
//                        }
//                        tmp.append(retIndex1 + "/" + printTableEntry(actEntry.entries[retIndex1]) + ")");
//                    }
                } else {
                    tmp.append(retIndex + "/" + printTableEntry(entry.entries[retIndex]) + ")");
                }
            }
            tmp.append(" ");
            anz++;
            if ((anz % 5) == 0) {
                tmp.append("\r\n");
            }
        }
        System.out.println(tmp.toString());
//        for (int i = 1; i < entry.onSets.length; i++) {
//            System.out.println(i + " on:  " + entry.onSets[i]);
//            System.out.println(i + " off: " + entry.offSets[i]);
//        }
    }

    private String printTableEntry(int entry) {
        int index = Chain.getSCellIndex(entry);
        int candidate = Chain.getSCandidate(entry);
        boolean set = Chain.isSStrong(entry);
        String cell = SolutionStep.getCellPrint(index, false);
        if (Chain.getSNodeType(entry) == Chain.GROUP_NODE) {
            cell = SolutionStep.getCompactCellPrint(index, Chain.getSCellIndex2(entry), Chain.getSCellIndex3(entry));
        } else if (Chain.getSNodeType(entry) == Chain.ALS_NODE) {
            cell = "ALS:" + SolutionStep.getAls(alses.get(Chain.getSAlsIndex(entry)));
        }
        if (set) {
            return cell + "=" + candidate;
        } else {
            return cell + "<>" + candidate;
        }
    }

    public void printTableAnz() {
        int onAnz = 0;
        int offAnz = 0;
        int entryAnz = 0;
        int maxEntryAnz = 0;
        for (int i = 0; i < onTable.length; i++) {
            if (onTable[i] != null) {
                onAnz++;
                entryAnz += onTable[i].index;
                if (onTable[i].index > maxEntryAnz) {
                    maxEntryAnz = onTable[i].index;
                }
            }
            if (offTable[i] != null) {
                offAnz++;
                entryAnz += offTable[i].index;
                if (offTable[i].index > maxEntryAnz) {
                    maxEntryAnz = offTable[i].index;
                }
            }
        }
        if (DEBUG) System.out.println("Tables: " + onAnz + " onTableEntries, " + offAnz + " offTableEntries, " +
                entryAnz + " Implikationen (" + maxEntryAnz + " max)");
    }

    class TablingComparator implements Comparator<SolutionStep> {

        /**
         * - Setzen vor...
         *     + Nach Anzahl zu setzender Kandidaten
         *     + Nach Äquivalenz (gleiche zu setzende Kandidaten)
         *     + Nach Indexsumme der zu setzenden Kandidaten
         *     + Nach Anzahl Steps in allen Chains
         * - ...Löschen
         *     + Nach Anzahl zu löschender Kandidaten
         *     + Nach Äquivalenz (gleiche zu löschende Kandidaten)
         *     + Nach Indexsumme der zu löschenden Kandidaten
         *     + Nach Anzahl Steps in allen Chains
         */
        @Override
        public int compare(SolutionStep o1, SolutionStep o2) {
            int sum1 = 0, sum2 = 0;

            // zuerst Setzen oder Löschen
            if (o1.getIndices().size() > 0 && o2.getIndices().size() == 0) {
                return -1;
            }
            if (o1.getIndices().size() == 0 && o2.getIndices().size() > 0) {
                return +1;
            }
            // Jetzt Setzen und Löschen getrennt behandeln
            if (o1.getIndices().size() > 0) {
                // Setzen
                // zuerst nach Anzahl zu setzende Kandidaten (absteigend!)
                int result = o2.getIndices().size() - o1.getIndices().size();
                if (result != 0) {
                    return result;
                // nach Äquivalenz (gleiche zu setzende Kandidaten)
                }
                if (!o1.isEquivalent(o2)) {
                    // nicht äquivalent: nach Indexsumme der zu löschenden Kandidaten
                    sum1 = o1.getSumme(o1.getIndices());
                    sum2 = o1.getSumme(o2.getIndices());
                    return sum1 == sum2 ? 1 : sum1 - sum2;
                }

                // Nach Anzahl Steps in allen Chains (absteigend)
                result = o1.getChainLength() - o2.getChainLength();
                if (result != 0) {
                    return result;
                }
            } else {
                // Löschen
                // zuerst nach Anzahl zu löschende Kandidaten (absteigend!)
                int result = o2.getCandidatesToDelete().size() - o1.getCandidatesToDelete().size();
                if (result != 0) {
                    return result;
                // nach Äquivalenz (gleiche zu löschende Kandidaten)
                }
                if (!o1.isEquivalent(o2)) {
                    // nicht äquivalent: nach Indexsumme der zu löschenden Kandidaten
                    // SCHLECHT!!!! Alle Kandidaten einzeln vergleichen
                    result = o1.compareCandidatesToDelete(o2);
                    if (result != 0) {
                        return result;
                    }
                }

                // Nach Anzahl Steps in allen Chains (absteigend)
                result = o1.getChainLength() - o2.getChainLength();
                if (result != 0) {
                    return result;
                }
            }
            return 0;
        }
    }

    public static void main(String[] args) {
        TablingSolver ts = new TablingSolver(null);
        TablingSolver.DEBUG = true;
        Sudoku sudoku = new Sudoku();
        //sudoku.setSudoku(":0100:1:....7.94..7..9...53....5.7..874..1..463.8.........7.8.8..7.....7......28.5.268...:::");
        //sudoku.setSudoku(":0000:x:....7.94..7..9...53....5.7..874..1..463.8.........7.8.8..7.....7......28.5.268...:613 623 233 633 164 165 267 269 973 377 378 379 983 387::");
        // Originalbeispiel
        //sudoku.setSudoku(":0000:x:2.4.857...15.2..4..98..425.8.2..61.79...7.5.257.2....4.29..147..5.....2..87.326..:618 358 867 368 968 381 681 183::");
        // #39462
        //sudoku.setSudoku(":0000:x:.4..1..........5.6......3.15.38.2...7......2..........6..5.7....2.....1....3.14..:211 213 214 225 235 448 465 366 566 468 469::");
        // Another puzzle for your consideration
        //sudoku.setSudoku(":0000:x:61.......9.37.62..27..3.6.9.......85....1....79.......8.769..32..62.879..29....6.:517 419 819 138 141 854 756 459 863 169 469 391::");
        //sudoku.setSudoku(":0702:9:.62143.5.1..5.8.3.5..7.9....28.154..4.56.2..3.16.8.52.6.9851..225...6..1..123.695:711 817 919 422 727 729 929 837 438 838 639 757 957 758 961 772 787 788 792:944 964 985:");
        // Nice Loop tutorial:
        // group node example 1
        //sudoku.setSudoku(":0000:x:..1.5794...961.8.5....8.1.3..279...8..3.....77...3.6..4.7.2....52...17...3.57.2..:632 633 651 863 469 672 872 691 891 699::");
        // example 2
        //sudoku.setSudoku(":0000:x:2....9.5..358...6...42....1.6.....7.5....2..4.8...3.96721...64..4...1.2..5.42...9:713 931 735 736 337 837 752 155 881 984 985 693 398::");
        // bug in Grouped Continuous Nice Loop
        // r7c3<>1 -> falsch! (Group Node)
        //sudoku.setSudoku(":0000:x:.....1.2...4+29.......576+4.8+735.+2.6.1..87....+44......7.56.......3......49.+49.325+6+7:912 814 122 233 555 162 263 874 875 876 182 282 885 887::");
        // r7c378<>1 -> falsch! (ALS)
        //sudoku.setSudoku(":0000:x:.....1.2...4+29.......576+4.8+735.+2.6.1..87....+44......7.56.......3..+6...49.+49.325+6+7:812 912 814 122 233 555 162 263 874 875 876 182 282 885 887::");
        // Beispiel daj
        //sudoku.setSudoku(":0000:x:4..1..8.9....3.54.8....46.1..34.1..8.74....5.98.5.34..749.....5.6..4....3.8..9..4:512 715 735 648 668 378 388 795::");
        // Grouped AIC with 4 eliminations
        //sudoku.setSudoku(":0000:x:....6.+83..36.8..94.2.+3496.....2..5..95.7...8.....+583.......1....65........4..+57.8:164 664 979 286 786 989::");
        // Grouped AIC that touches the beginning of the loop (-> lasso!)
        // Grouped AIC 5- r6c9 -6- r6c4 =6= r13c4 -6- r2c56 =6= r2c9 -6- r6c9 -5- r5c789 =5= r5c4 -5 => r5c789,r6c456<>5
        //sudoku.setSudoku(":0711:5:4+8..+12.391+953..28......+9+4...+1.4..9.886+4.+9+1....79...1+4..+5123.+8.4..+89........1.8...:248 269 369:557 558 559 564 565 566:");
        // Continuous Nice Loop 2- r4c4 =2= r5c6 -2- ALS:r156c7(2|78|4) -4- ALS:r3c49(4|7|2) -2 => r2c4 <> 2, r2c789<>4, r1c9<> 4, r3c8<>4, r3c128<>7, r289c7<>7, r28c7<>8
        sudoku.setSudoku(":0000:x:9...6..2............1.893.......65..41.8...96..24.......352.1..1.........8..1...5:316 716 221 521 621 721 325 725 326 726 741 344 744 944 345 348 748 848 349 749 849 361 861 362 365 366 384 784 985 394 794::");
        // Wrong elminations in grouped continuous nice loop (issue 2795464)
        // 1/2/3/4/6/7/9 3= r2c4 =5= r2c9 -5- ALS:r13c7,r3c9 =7= r6c7 -7- ALS:r4c3,r56c2 -3- r4c4 =3= r2c4 =5 => r2c28,r3456c1,r46c7<>1, r12c9<>2, r4c18<>3, r456c1<>4, r2c4<>6, r6c19<>7, r1c9,r468c7<>9
        // r1c9<>9, r6c7<>9 are invalid
        sudoku.setSudoku(":0709:1234679:5.81...6.....9.4...39.8..7..6...5.....27.95....58...2..8..5134..51.3.....9...8651:221 224 231 743 445 349 666 793:122 128 131 141 147 151 161 167 219 229 341 348 441 451 461 624 761 769 919 947 967 987::11");
        ts.setSudoku(sudoku);
        List<SolutionStep> steps = null;
        long ticks = System.currentTimeMillis();
        int anzLoops = 1;
        for (int i = 0; i < anzLoops; i++) {
            //steps = ts.getAllForcingChains(sudoku);
            //steps = ts.getAllForcingNets(sudoku);
            //steps = ts.getAllNiceLoops(sudoku);
            steps = ts.getAllGroupedNiceLoops(sudoku);
        }
        ticks = System.currentTimeMillis() - ticks;
        System.out.println("Dauer: " + (ticks / anzLoops) + "ms");
        System.out.println("Anzahl Steps: " + steps.size());
        for (int i = 0; i < steps.size(); i++) {
            System.out.println(steps.get(i).getCandidateString());
            System.out.println(steps.get(i).toString(2));
        }
    }
}
