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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author zhobigbe
 */
public class ChainSolver extends AbstractSolver {

    private static final int MAX_CHAIN_LENGTH = 200;
    //private static final int RESTRICT_CHAIN_LENGTH = 20;
    //private static final int RESTRICT_NICE_LOOP_LENGTH = 10;
    //private boolean restrictChainSize = true;
    private static final int X_CHAIN = 0;
    private static final int XY_CHAIN = 1;
    private static final int REMOTE_PAIR = 2;
    private static final int NICE_LOOP = 3;
    private static ChainComparator chainComparator = null;

    private int[] links = new int[20000];       // Array mit allen Links
    private int[] startIndices = new int[810];  // index des ersten links
    private int[] endIndices = new int[810];    // index des letzten Links + 1
    private int[] chain = new int[MAX_CHAIN_LENGTH];  // eine Chain für alles!
    private SudokuSet chainSet = new SudokuSet();     // ein Set mit allen Zellen der aktuellen Chain (für Loop-Check)
    private SudokuSet endCells = new SudokuSet();     // enthält für eine Startzelle alle Endzellen, zu denen bereits chains gefunden wurden
    private int startIndex = 0;                       // Index der ersten zelle der aktuellen Chain
    private int startCandidate = 0;                   // Kandidat, mit dem die aktuelle Chain beginnt
    private int startCandidate2 = 0;                  // 2. Kandidat für die aktuelle Chain (Remote Pairs)
    private int rpCandMask = 0;                       // für Remote Pair (Kandidaten, für die gesucht werden soll
    private int recDepth = 0;
    private int maxRecDepth = 0;
    private int anzAufrufe = 0;
    private SudokuSet checkBuddies = new SudokuSet(); // für Prüfungen auf zu löschende Kandidaten
    private SortedMap<String, Integer> deletesMap = new TreeMap<String, Integer>();  // alle bisher gefundenen Chains: Eliminierungen und Chainlänge
    private SolutionStep globalStep = new SolutionStep(SolutionType.FULL_HOUSE);
    private List<SolutionStep> steps;

    /** Creates a new instance of ChainSolver */
    public ChainSolver() {
        if (chainComparator == null) {
            chainComparator = new ChainComparator();
        }
    }

    @Override
    protected SolutionStep getStep(SolutionType type) {
        SolutionStep result = null;
        switch (type) {
            case X_CHAIN:
                result = getXChains();
                break;
            case XY_CHAIN:
                result = getXYChains();
                break;
            case REMOTE_PAIR:
                result = getRemotePairs();
                break;
//            case NICE_LOOP:
//            case CONTINUOUS_NICE_LOOP:
//            case DISCONTINUOUS_NICE_LOOP:
//                result = getNiceLoops();
//                break;
        }
        return result;
    }

    @Override
    protected boolean doStep(SolutionStep step) {
        boolean handled = true;
        switch (step.getType()) {
            case X_CHAIN:
            case XY_CHAIN:
            case REMOTE_PAIR:
            case NICE_LOOP:
//            case CONTINUOUS_NICE_LOOP:
//            case DISCONTINUOUS_NICE_LOOP:
                for (Candidate cand : step.getCandidatesToDelete()) {
                    sudoku.delCandidate(cand.getIndex(), candType, cand.getValue());
                }
                break;
            default:
                handled = false;
        }
        return handled;
    }

    private SolutionStep getXChains() {
        steps = new ArrayList<SolutionStep>();
        getChains(X_CHAIN);
        if (steps.size() > 0) {
            Collections.sort(steps);
            return steps.get(0);
        }
        return null;
    }

    private SolutionStep getXYChains() {
        steps = new ArrayList<SolutionStep>();
        getChains(XY_CHAIN);
        if (steps.size() > 0) {
            Collections.sort(steps);
            return steps.get(0);
        }
        return null;
    }

    private SolutionStep getRemotePairs() {
        steps = new ArrayList<SolutionStep>();
        getChains(REMOTE_PAIR);
        if (steps.size() > 0) {
            Collections.sort(steps);
            return steps.get(0);
        }
        return null;
    }
//    private SolutionStep getNiceLoops() {
//        steps = new ArrayList<SolutionStep>();
//        getChains(NICE_LOOP);
//        if (steps.size() > 0) {
//            Collections.sort(steps);
//            return steps.get(0);
//        }
//        return null;
//    }
    public List<SolutionStep> getAllChains(Sudoku sudoku) {
        Sudoku save = getSudoku();
        setSudoku(sudoku);
        List<SolutionStep> tmpSteps = new ArrayList<SolutionStep>();
        tmpSteps = getAllChains(tmpSteps);
        Collections.sort(tmpSteps, chainComparator);
        if (save != null) {
            setSudoku(save);
        }
        return tmpSteps;
    }

    public List<SolutionStep> getAllChains(List<SolutionStep> steps) {
        // initialisieren
        long ticks = System.currentTimeMillis();
        this.steps = new ArrayList<SolutionStep>();
        getChains(X_CHAIN);
        Collections.sort(this.steps);
        steps.addAll(this.steps);

        steps.clear();
        getChains(XY_CHAIN);
        Collections.sort(this.steps);
        steps.addAll(this.steps);

        steps.clear();
        getChains(REMOTE_PAIR);
        Collections.sort(this.steps);
        steps.addAll(this.steps);

//        steps.clear();
//        getChains(NICE_LOOP);
//        Collections.sort(this.steps);
//        steps.addAll(this.steps);
        ticks = System.currentTimeMillis() - ticks;
        Logger.getLogger(getClass().getName()).log(Level.FINE, "getAllChains() gesamt: " + ticks + "ms");

        return steps;
    }

    /**
     * Alle Zellen und für jede Zelle alle Kandidaten durchgehen:
     * Für jeden strong und jeden weak link eine neue chain anlegen und
     * rekursiv weitermachen
     *
     * typ: 0 .. nur X-Chains (alle Links müssen für denselben Kandidaten sein
     * typ: 1 .. nur XY-Chains/Remote Pairs (alle Links müssen bivalue cells sein, der
     *           strong link muss innerhalb der Zelle sein)
     * typ: 2 .. nur Remote Pairs (alle Einschränkungen für XY-Chains plus alle
     *           Kandidaten gleich)
     * typ: 3 .. Nice Loops (keine Einschränkungen, aber muss Loop zum Beginn sein)
     */
    private void getChains(int typ) {
        long ticks = System.currentTimeMillis();
        getAllLinks();
        ticks = System.currentTimeMillis() - ticks;
        Logger.getLogger(getClass().getName()).log(Level.FINE, "getAllLinks(): " + ticks + "ms");

        recDepth = 0;
        maxRecDepth = 0;
        anzAufrufe = 0;
        deletesMap.clear();
        //checkLoopSetsIndex = 0;
        boolean onlyOne = false; // zum testen: nur für einen Kandidaten in einer Zelle
        for (int i = 0; i < sudoku.getCells().length; i++) {
            if (onlyOne && i != 64) {
                continue;
            }
            for (int j = 1; j <= 9; j++) {
//                if (onlyOne && j != 6) {
//                    continue;
//                }
                int tmp = i * 10 + j;
                // zuerst die Strong Links, dann die weak links
                for (int l = 0; l < 2; l++) {
                    if ((typ == X_CHAIN || typ == XY_CHAIN || typ == REMOTE_PAIR) && l == 1) {
                        ///*K*/if (typ == 1 && l == 1) {
                        // Kein Start mit weak link
                        continue;
                    }
                    for (int k = startIndices[tmp]; k < endIndices[tmp]; k++) {
                        // Nach Typ unterscheiden
                        if (typ == X_CHAIN && Chain.getSCandidate(links[k]) != j) {
                            // link ist für anderen Kandidaten -> keine X-Chain möglich
                            continue;
                        }
                        if ((typ == XY_CHAIN || typ == REMOTE_PAIR) && sudoku.getCell(Chain.getSCellIndex(links[k])).getAnzCandidates(candType) != 2) {
                            // keine bivalue cell -> keine XY-Chain möglich
                            continue;
                        }
                        if ((typ == XY_CHAIN || typ == REMOTE_PAIR) && Chain.getSCellIndex(links[k]) != i) {
                            // der erste strong link muss innerhalb der Zelle bleiben
                            continue;
                        }
//                        if (typ == NICE_LOOP && (links[k] / 10) % 100 == i) {
//                            // bei NICE_LOOPS muss der erste Link aus der Zelle herausgehen, sonst gibt es
//                            // Doppeldeutigkeiten
//                            continue;
//                        }
                        if (typ == REMOTE_PAIR) {
                            rpCandMask = sudoku.getCell(Chain.getSCellIndex(links[k])).getCandidateMask(candType);
                        }
                        if ((l == 0 && Chain.isSStrong(links[k])) || (l == 1 && !Chain.isSStrong(links[k]))) {
                            chain[0] = Chain.makeSEntry(i, j, false);
                            chain[1] = links[k];
                            chainSet.clear();
                            chainSet.add(i);
                            endCells.clear();
                            startIndex = i;
                            startCandidate = j;
                            if (typ == REMOTE_PAIR) {
                                startCandidate2 = startCandidate;
                                //short[] cands = sudoku.getCell((links[k] / 10) % 100).getAllCandidates(candType);
                                short[] cands = sudoku.getCell(Chain.getSCellIndex(links[k])).getAllCandidates(candType);
                                for (int m = 0; m < cands.length; m++) {
                                    if (cands[m] != startCandidate2) {
                                        startCandidate2 = cands[m];
                                        break;
                                    }
                                }
                            }
                            getChainRecursive(1, typ);
                        }
                    }
                }
            }
        }
    }

    /**
     * Hier passierts:
     *
     * Alle Links prüfen: Jeder Link, der die Chain verlängern kann, wird eingeschrieben,
     * dann wird rekursiv weitergesucht.
     *
     * Prüfungen:
     *   - Ein Link darf nicht auf sich selbst zeigen (manchmal gibt es weak und strong Link
     *     für die selbe Zelle und den selben Kandidaten -> nur einer ist erlaubt)
     *   - Ein Link, der auf eine bereits in der Chain enthaltene Zelle zurückverweist, ist
     *     ungültig (außer die Zelle war die letzte in der Chain und die vorletzte war eine
     *     andere Zelle)
     *   - Ein Link, der auf den Beginn der Chain zurückführt, ist für Nice Loops gültig,
     *     es gibt danach aber keine Rekursion mehr
     *   - Alle anderen Links verlängern die Chain
     */
    private void getChainRecursive(int chainIndex, int typ) {
        // chain[chainIndex] anthält das letzte Glied der Kette: Neue aktuelle
        // Zelle und neuen Kandidaten ermitteln und weitermachen
        anzAufrufe++;
        recDepth++;
        if (recDepth > maxRecDepth) {
            maxRecDepth = recDepth;
        }
        if (recDepth % 100 == 0) {
            Logger.getLogger(getClass().getName()).log(Level.FINER, "Recursion depth: " + recDepth);
        }
        int link = chain[chainIndex];
        int linkIndex = Chain.getSCellIndex(link);
        int newStart = startIndices[Chain.getSCellIndex(link) * 10 + Chain.getSCandidate(link)]; // In link ist strong/weak codiert
        int newEnd = endIndices[Chain.getSCellIndex(link) * 10 + Chain.getSCandidate(link)];
        // wenn der erste Link strong ist, müssen alle ungeraden links strong sein;
        // ist der erste link weak, müssen alle geraden links weak sein; chainIndex
        // ist jetzt allerdings noch um eins kleiner, daher Logik umdrehen
        boolean firstStrong = Chain.isSStrong(chain[1]); // erster link weak
        boolean strongOnly = firstStrong ? chainIndex % 2 == 0 : chainIndex % 2 != 0;
        for (int i = newStart; i < newEnd; i++) {
            int newLink = links[i];
            int newLinkIndex = Chain.getSCellIndex(newLink);
            // prüfen, ob der Link zum Typ des vorherigen Links passt
            if (strongOnly && !Chain.isSStrong(newLink)) {
                continue;
            }
            // chain darf nicht auf sich selbst verweisen
            if (Chain.getSCellIndex(link) == Chain.getSCellIndex(newLink) &&
                    Chain.getSCandidate(link) == Chain.getSCandidate(newLink)) {
                continue;
            }
            // Nach Typ unterscheiden
            if (typ == X_CHAIN && Chain.getSCandidate(newLink) != startCandidate) {
                // link ist für anderen Kandidaten -> keine X-Chain möglich
                continue;
            }
            if ((typ == XY_CHAIN || typ == REMOTE_PAIR) && sudoku.getCell(newLinkIndex).getAnzCandidates(candType) != 2) {
                // keine bivalue cell -> keine XY-Chain möglich
                continue;
            }
            if ((typ == XY_CHAIN || typ == REMOTE_PAIR) && strongOnly && newLinkIndex != linkIndex) {
                // nicht innerhalb derselben Zelle
                continue;
            }
            if (typ == REMOTE_PAIR) {
                short cands = sudoku.getCell(newLinkIndex).getCandidateMask(candType);
                if (cands != rpCandMask) {
                    // Remote Pair: Alle Zellen müssen dieselben 2 Kandidaten haben
                    continue;
                }
            }
            // der neue Link darf nicht auf die Mitte der Kette zurückverweisen; auf den Anfang darf er schon, aber
            // dann ist die Kette fertig (wird später behandelt)
            int loopIndex = -1;
            if (chainSet.contains(newLinkIndex)) {
                if (startIndex == newLinkIndex) {
                    // Loop zum Anfang
                    loopIndex = 0;
                } else {
                    // Loop in die Mitte der Kette
                    loopIndex = 1;
                }
            }
            if (loopIndex > 0) {
                continue;
            }

            // ok: neuer Link -> einschreiben, prüfen und neue Rekursion starten
            // in chainSet wird immer der vorletzte eingeschrieben! (sonst funktioniert
            // die Loop-Suche nicht: der letzte darf doppelt sein, aber nicht dreifach)
            chainSet.add(Chain.getSCellIndex(chain[chainIndex]));
            if (!strongOnly) {
                newLink = Chain.setSStrong(newLink, false);
            }
            chain[++chainIndex] = newLink;

            // Jetzt könnte es eine gültige Chain mit Eliminierungen sein
            switch (typ) {
                case X_CHAIN:
                    checkXChain(newLink, chainIndex);
                    break;
                case XY_CHAIN:
                    checkXYChain(newLink, chainIndex);
                    break;
                case REMOTE_PAIR:
                    checkRemotePairs(newLink, chainIndex);
                    break;
                case NICE_LOOP:
                    checkNiceLoop(newLink, chainIndex);
                    break;
            }

            // nur maximal MAX_CHAIN_LENGTH Glieder lange chains
            // wenn restrictChainSize gesetzt ist, nur options.restrictChainLength Glieder
            // Ist die Chain ein Loop, darf nicht rekursiv weitergesucht werden
            if (chainIndex < MAX_CHAIN_LENGTH - 1 &&
                    !(typ != NICE_LOOP && Options.getInstance().restrictChainSize &&
                    chainIndex >= Options.getInstance().restrictChainLength) &&
                    !(typ == NICE_LOOP && Options.getInstance().restrictChainSize &&
                    chainIndex >= Options.getInstance().restrictNiceLoopLength) &&
                    loopIndex != 0) {
                // Rekursion
                getChainRecursive(chainIndex, typ);
            }

            // letztes Element wieder weg und weiterschauen
            chainIndex--;
            chainSet.remove(Chain.getSCellIndex(chain[chainIndex]));

        }
        recDepth--;
    }

    private void checkXChain(int lastLink, int chainIndex) {
        // Chain muss mindestens 3 Glieder haben, erster und letzter Link
        // müssen strong sein (erster ist Strong, sonst wird die Chain
        // gar nicht erst gefunden)
        if (chainIndex <= 1 || (chainIndex % 2) == 0) {
            // Chain zu kurz oder Ende mit weak link -> geht nicht
            return;
        }
        //int endIndex = (lastLink / 10) % 100;
        int endIndex = Chain.getSCellIndex(lastLink);
        if (endCells.contains(endIndex)) {
            // die Chain hatten wir schon!
            return;
        }
        // wenn es die Chain schon gibt, nichts tun (da die chains bidirektional sind,
        // wird jede Chain zumindest 2 mal gefunden)
//        if (!isNewChain(startIndex, endIndex)) {
//            // gibts schon!
//            return;
//        }

        // ok, könnte eine neue Chain sein
        globalStep.reset();
        checkBuddies.set(Sudoku.buddies[startIndex]);
        checkBuddies.and(Sudoku.buddies[endIndex]);
        checkBuddies.and(sudoku.getAllowedPositions()[startCandidate]);
        checkBuddies.andNot(chainSet);  // nicht in der Chain selbst löschen!
        checkBuddies.remove(endIndex);  // das letzte Glied der Chain ist u.U. nicht in chainSet
        if (checkBuddies.isEmpty()) {
            // es kann leider nichts gelöscht werden!
            return;
        }
        // es gibt was zu löschen -> Step zusammenbauen
        globalStep.setType(SolutionType.X_CHAIN);
        globalStep.addValue(startCandidate);
        for (int i = 0; i < checkBuddies.size(); i++) {
            globalStep.addCandidateToDelete(checkBuddies.get(i), startCandidate);
        }
        
        // check if the chain has already been found
        String del = globalStep.getCandidateString();
        Integer oldLength = deletesMap.get(del);
        if (oldLength != null && oldLength.intValue() <= chainIndex) {
            // Für diese Kandidaten gibt es schon eine Chain und sie ist kürzer als die neue
            return;
        }
        deletesMap.put(del, chainIndex);

        // Die Chain muss kopiert werden
        int[] newChain = new int[chainIndex + 1];
        for (int i = 0; i < newChain.length; i++) {
            newChain[i] = chain[i];
        }
        globalStep.addChain(0, chainIndex, newChain);
        try {
            steps.add((SolutionStep) globalStep.clone());
        } catch (CloneNotSupportedException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
        }
        // Neue Endzelle merken!
        endCells.add(endIndex);
    }

    private void checkXYChain(int lastLink, int chainIndex) {
        // Chain muss mindestens 3 Glieder haben, erster und letzter Link
        // müssen strong sein (erster ist Strong, sonst wird die Chain
        // gar nicht erst gefunden) und für denselben Kandidaten bestimmt sein
        //if (chainIndex <= 1 || (chainIndex % 2) == 0 || (lastLink % 10) != startCandidate) {
        if (chainIndex <= 1 || (chainIndex % 2) == 0 || Chain.getSCandidate(lastLink) != startCandidate) {
            // Chain zu kurz oder Ende mit weak link -> geht nicht
            return;
        }
        //int endIndex = (lastLink / 10) % 100;
        int endIndex = Chain.getSCellIndex(lastLink);
        if (endCells.contains(endIndex)) {
            // die Chain hatten wir schon!
            return;
        }
        // wenn es die Chain schon gibt, nichts tun (da die chains bidirektional sind,
        // wird jede Chain zumindest 2 mal gefunden)
        // bad way of checking for duplicates: possibly shorter chains are not found!
//        if (!isNewChain(startIndex, endIndex)) {
//            // gibts schon!
//            return;
//        }

        // ok, könnte eine neue Chain sein
        globalStep.reset();
        checkBuddies.set(Sudoku.buddies[startIndex]);
        checkBuddies.and(Sudoku.buddies[endIndex]);
        checkBuddies.and(sudoku.getAllowedPositions()[startCandidate]);
        checkBuddies.andNot(chainSet);  // nicht in der Chain selbst löschen!
        checkBuddies.remove(endIndex);  // das letzte Glied der Chain ist u.U. nicht in chainSet
        if (checkBuddies.isEmpty()) {
            // es kann leider nichts gelöscht werden!
            return;
        }
        // es gibt was zu löschen -> Step zusammenbauen
        globalStep.setType(SolutionType.XY_CHAIN);
        globalStep.addValue(startCandidate);
        for (int i = 0; i < checkBuddies.size(); i++) {
            globalStep.addCandidateToDelete(checkBuddies.get(i), startCandidate);
        }
        
        // check if the chain has already been found
        String del = globalStep.getCandidateString();
        Integer oldLength = deletesMap.get(del);
        if (oldLength != null && oldLength.intValue() <= chainIndex) {
            // Für diese Kandidaten gibt es schon eine Chain und sie ist kürzer als die neue
            return;
        }
        deletesMap.put(del, chainIndex);

        // Die Chain muss kopiert werden
        int[] newChain = new int[chainIndex + 1];
        for (int i = 0; i < newChain.length; i++) {
            newChain[i] = chain[i];
        }
        globalStep.addChain(0, chainIndex, newChain);
        try {
            steps.add((SolutionStep) globalStep.clone());
        } catch (CloneNotSupportedException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
        }
        // Neue Endzelle merken!
        endCells.add(endIndex);
    }

    private void checkRemotePairs(int lastLink, int chainIndex) {
        // Chain muss mindestens 4 zellen umfassen (Chain-Länge mindestens 8), erster und letzter Link
        // müssen strong sein (erster ist Strong, sonst wird die Chain
        // gar nicht erst gefunden) und für denselben Kandidaten bestimmt sein
        // last item doesnt have to be for the same candidate!
        //if (chainIndex < 7 || (chainIndex % 2) == 0 || (lastLink % 10) != startCandidate) {
        //if (chainIndex < 7 || (chainIndex % 2) == 0 || Chain.getSCandidate(lastLink) != startCandidate) {
        if (chainIndex < 7 || (chainIndex % 2) == 0) {
            // Chain zu kurz oder Ende mit weak link -> geht nicht
            return;
        }
        //int endIndex = (lastLink / 10) % 100;
        int endIndex = Chain.getSCellIndex(lastLink);
        if (endCells.contains(endIndex)) {
            // die Chain hatten wir schon!
            return;
        }
        // wenn es die Chain schon gibt, nichts tun (da die chains bidirektional sind,
        // wird jede Chain zumindest 2 mal gefunden)
//        if (!isNewChain(startIndex, endIndex)) {
//            // gibts schon!
//            return;
//        }

        // ok, könnte eine neue Chain sein
        globalStep.reset();
        globalStep.setType(SolutionType.REMOTE_PAIR);
        // Für alle Kombinationen aus verschieden gepolten Zellen die löschbaren Kandidaten ermitteln
        // the first cell must give at least one elimination, or there exists a shorter Remote Pair
        SudokuSet tmp = new SudokuSet();
        SudokuSet cdd1 = new SudokuSet();
        SudokuSet cdd2 = new SudokuSet();
        boolean firstCellWithoutElimination = true;
        for (int i = 0; i <= chainIndex; i += 2) {
            // die erste anders gepolte Zelle ist 6 weg, alle anderen
            // jeweils weitere 4
            for (int j = i + 6; j <= chainIndex; j += 4) {
                //tmp.set(sudoku.buddies[(chain[i] / 10) % 100]);
                tmp.set(Sudoku.buddies[Chain.getSCellIndex(chain[i])]);
                //tmp.and(sudoku.buddies[(chain[j] / 10) % 100]);
                tmp.and(Sudoku.buddies[Chain.getSCellIndex(chain[j])]);
                tmp.andNot(chainSet);  // nicht in der Chain selbst löschen!
                tmp.remove(endIndex);  // das letzte Glied der Chain ist u.U. nicht in chainSet
                checkBuddies.set(tmp);
                checkBuddies.and(sudoku.getAllowedPositions()[startCandidate]);
                cdd1.or(checkBuddies);
                checkBuddies.set(tmp);
                checkBuddies.and(sudoku.getAllowedPositions()[startCandidate2]);
                cdd2.or(checkBuddies);
            }
            if (i == 0 && (!cdd1.isEmpty() || ! cdd2.isEmpty())) {
                firstCellWithoutElimination = false;
            }
        }
        if (firstCellWithoutElimination || (cdd1.isEmpty() && cdd2.isEmpty())) {
            // nichts zu löschen
            return;
        }
        globalStep.addValue(startCandidate);
        globalStep.addValue(startCandidate2);
        for (int i = 0; i < cdd1.size(); i++) {
            globalStep.addCandidateToDelete(cdd1.get(i), startCandidate);
        }
        for (int i = 0; i < cdd2.size(); i++) {
            globalStep.addCandidateToDelete(cdd2.get(i), startCandidate2);
        }

        // check if the chain has already been found
        String del = globalStep.getCandidateString();
        Integer oldLength = deletesMap.get(del);
        if (oldLength != null && oldLength.intValue() <= chainIndex) {
            // Für diese Kandidaten gibt es schon eine Chain und sie ist kürzer als die neue
            return;
        }
        deletesMap.put(del, chainIndex);

        // Die Chain muss kopiert werden
        int[] newChain = new int[chainIndex + 1];
        for (int i = 0; i < newChain.length; i++) {
            newChain[i] = chain[i];
        }
        globalStep.addChain(0, chainIndex, newChain);
        try {
            steps.add((SolutionStep) globalStep.clone());
        } catch (CloneNotSupportedException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
        }
    }

    /**
     * Wenn die erste und die letzte Zelle der Chain identisch sind, ist es ein
     * Nice Loop.
     *
     *  Discontinous Nice Loop:
     *    - Erster und letzter Link sind weak für den selben Kandidaten
     *      -> Kandidat kann in erster Zelle gelöscht werden
     *    - Erster und letzter Link sind strong für den selben Kandidaten
     *      -> Kandidat kann in erster Zelle gesetzt werden (alle anderen Kandidaten löschen, ist einfacher in der Programmlogik)
     *    - Ein link ist weak und einer strong, die Kandidaten sind verschieden
     *      -> Kandidat mit weak link kann in erster Zelle gelöscht werden
     *
     *  Continuous Nice Loop:
     *    - Zwei weak links: Erste Zelle muss bivalue sein, Kandidaten müssen verschieden sein
     *    - Zwei strong links: Kandidaten müssen verschieden sein
     *    - Ein strong, ein weak link: Kandidaten müssen gleich sein
     *
     *    -> eine Zelle mit zwei strong links: alle anderen Kandidaten von dieser Zelle löschen
     *    -> weak link zwischen zwei Zellen: Kandidat des Links kann von allen Zellen gelöscht werden,
     *       die beide Zellen sehen
     */
    private void checkNiceLoop(int lastLink, int chainIndex) {
        //int endIndex = (lastLink / 10) % 100;
        int endIndex = Chain.getSCellIndex(lastLink);
        // Mindestlänge: 3 Links
        if (endIndex != startIndex) {
            // kein Loop
            return;
        }
        // auf Looptyp prüfen
        globalStep.reset();
        globalStep.setType(SolutionType.DISCONTINUOUS_NICE_LOOP);
        //boolean firstLinkStrong = chain[1] / 1000 > 0;
        boolean firstLinkStrong = Chain.isSStrong(chain[1]);
        //boolean lastLinkStrong = lastLink / 1000 > 0;
        boolean lastLinkStrong = Chain.isSStrong(lastLink);
        //int endCandidate = lastLink % 10;
        int endCandidate = Chain.getSCandidate(lastLink);
        if (!firstLinkStrong && !lastLinkStrong && startCandidate == endCandidate) {
            // Discontinous -> startCandidate in erster Zelle löschen
            globalStep.addCandidateToDelete(startIndex, startCandidate);
        } else if (firstLinkStrong && lastLinkStrong && startCandidate == endCandidate) {
            // Discontinous -> alle anderen Kandidaten löschen
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
            } else {
                globalStep.addCandidateToDelete(startIndex, endCandidate);
            }
        } else if ((!firstLinkStrong && !lastLinkStrong && sudoku.getCell(startIndex).getAnzCandidates(candType) == 2 && startCandidate != endCandidate) ||
                (firstLinkStrong && lastLinkStrong && startCandidate != endCandidate) ||
                (firstLinkStrong != lastLinkStrong && startCandidate == endCandidate)) {
            // Continous -> auf Löschen prüfen
            globalStep.setType(SolutionType.CONTINUOUS_NICE_LOOP);
            // Zelle mit zwei strong links: strong link zwischen Zellen, weak link in der Zelle, strong link zu nächster Zelle
            // weak link zwischen Zellen: trivial
            for (int i = 1; i <= chainIndex; i++) {
                //if (chain[i] / 1000 > 0 && i <= chainIndex - 2 && (chain[i - 1] / 10) % 100 != (chain[i] / 10) % 100) {
                if (Chain.isSStrong(chain[i]) && i <= chainIndex - 2 && Chain.getSCellIndex(chain[i - 1]) != Chain.getSCellIndex(chain[i])) {
                    // mögliche Zelle mit zwei strong links: nächster Link muss weak sein auf selbe Zelle, danach strong auf nächste Zelle
                    //if (chain[i + 1] / 1000 == 0 && (chain[i] / 10) % 100 == (chain[i + 1] / 10) % 100 &&
                    //        chain[i + 2] / 1000 > 0 && (chain[i + 1] / 10) % 100 != (chain[i + 2] / 10) % 100) {
                    if (!Chain.isSStrong(chain[i + 1]) && Chain.getSCellIndex(chain[i]) == Chain.getSCellIndex(chain[i + 1]) &&
                            Chain.isSStrong(chain[i + 2]) && Chain.getSCellIndex(chain[i + 1]) != Chain.getSCellIndex(chain[i + 2])) {
                        // in der Zelle chain[i] alle kandidaten außer den beiden strong links löschen
                        //int c1 = chain[i] % 10;
                        int c1 = Chain.getSCandidate(chain[i]);
                        //int c2 = chain[i + 2] % 10;
                        int c2 = Chain.getSCandidate(chain[i + 2]);
                        //short[] cands = sudoku.getCell((chain[i] / 10) % 100).getAllCandidates(candType);
                        short[] cands = sudoku.getCell(Chain.getSCellIndex(chain[i])).getAllCandidates(candType);
                        for (int j = 0; j < cands.length; j++) {
                            if (cands[j] != c1 && cands[j] != c2) {
                                //globalStep.addCandidateToDelete((chain[i] / 10) % 100, cands[j]);
                                globalStep.addCandidateToDelete(Chain.getSCellIndex(chain[i]), cands[j]);
                            }
                        }
                    }
                }
                //if (chain[i] / 1000 == 0 && (chain[i - 1] / 10) % 100 != (chain[i] / 10) % 100) {
                if (!Chain.isSStrong(chain[i]) && Chain.getSCellIndex(chain[i - 1]) != Chain.getSCellIndex(chain[i])) {
                    // weak link zwischen zwei Zellen
                    //checkBuddies.set(sudoku.buddies[(chain[i - 1] / 10) % 100]);
                    checkBuddies.set(Sudoku.buddies[Chain.getSCellIndex(chain[i - 1])]);
                    //checkBuddies.and(sudoku.buddies[(chain[i] / 10) % 100]);
                    checkBuddies.and(Sudoku.buddies[Chain.getSCellIndex(chain[i])]);
                    checkBuddies.andNot(chainSet);
                    checkBuddies.remove(endIndex);
                    //checkBuddies.and(sudoku.getAllowedPositions()[chain[i] % 10]);
                    checkBuddies.and(sudoku.getAllowedPositions()[Chain.getSCandidate(chain[i])]);
                    if (!checkBuddies.isEmpty()) {
                        for (int j = 0; j < checkBuddies.size(); j++) {
                            //globalStep.addCandidateToDelete(checkBuddies.get(j), chain[i] % 10);
                            globalStep.addCandidateToDelete(checkBuddies.get(j), Chain.getSCandidate(chain[i]));
                        }
                    }
                }
            }
        }

        if (globalStep.getCandidatesToDelete().size() > 0) {
            // ok, Loop ist nicht redundant -> einschreiben, wenn es die Kombination nicht schon gibt
            String del = globalStep.getCandidateString();
            Integer oldLength = deletesMap.get(del);
            if (oldLength != null && oldLength.intValue() <= chainIndex) {
                // Für diese Kandidaten gibt es schon eine Chain und sie ist kürzer als die neue
                return;
            }
            deletesMap.put(del, chainIndex);
            // Die Chain muss kopiert werden
            int[] newChain = new int[chainIndex + 1];
            for (int i = 0; i < newChain.length; i++) {
                newChain[i] = chain[i];
            }
            globalStep.addChain(0, chainIndex, newChain);
            try {
                steps.add((SolutionStep) globalStep.clone());
            } catch (CloneNotSupportedException ex) {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
            }
        }
    }

    private boolean isNewChain(int startIndex, int endIndex) {
        boolean isNew = true;
        for (int i = 0; i < steps.size(); i++) {
            Chain tmpChain = steps.get(i).getChains().get(0);
            //if ((chain.getChain()[chain.getStart()] / 10) % 100 == endIndex && (chain.getChain()[chain.getEnd()] / 10) % 100 == startIndex) {
            //if (Chain.getSCellIndex(chain.getChain()[chain.getStart()]) == endIndex && Chain.getSCellIndex(chain.getChain()[chain.getEnd()]) == startIndex) {
            if (tmpChain.getCellIndex(tmpChain.getStart()) == endIndex && tmpChain.getCellIndex(tmpChain.getEnd()) == startIndex) {
                isNew = false;
                break;
            }
        }
        return isNew;
    }

    private void getAllLinks() {
        int index = 0;
        int startEndIndex = 0;
        for (int i = 0; i < sudoku.getCells().length; i++) {
            SudokuCell cell = sudoku.getCell(i);
            for (int j = 1; j <= 9; j++) {
                startEndIndex = i * 10 + j;
                if (cell.getValue() != 0 || !cell.isCandidate(candType, j)) {
                    startIndices[startEndIndex] = index;
                    endIndices[startEndIndex] = index;
                    continue;
                }
                startIndices[startEndIndex] = index;
                // Innerhalb der Zelle gibt es zwei Möglichkeiten: sind nur noch
                // zwei Kandidaten vorhanden, besteht ein strong link zwischen ihnen;
                // gibt es mehr als zwei Kandidaten, gibt es einen weak link zu jedem
                for (int k = 1; k <= 9; k++) {
                    if (k == j) {
                        continue;
                    }
                    if (cell.isCandidate(candType, k)) {
                        if (cell.getAnzCandidates(candType) == 2) {
                            //links[index++] = 1000 + i * 10 + k;
                            links[index++] = Chain.makeSEntry(i, k, true);
                        } else {
                            //links[index++] = i * 10 + k;
                            links[index++] = Chain.makeSEntry(i, k, false);
                        }
                    }
                }
                // Jetzt alle Häuser prüfen
                index = getAllLinksInHouse(cell, index, i, j, Sudoku.LINES[Sudoku.getLine(i)], false);
                index = getAllLinksInHouse(cell, index, i, j, Sudoku.COLS[Sudoku.getCol(i)], false);
                index = getAllLinksInHouse(cell, index, i, j, Sudoku.BLOCKS[Sudoku.getBlock(i)], true);
                endIndices[startEndIndex] = index;
            }
        }
    }

    /**
     * Gibt es den Kandidaten nur noch ein Mal zusätzlich im Haus, besteht ein strong link
     * Gibt es den Kandidaten mehrmals, besteht jeweils ein weak link
     */
    private int getAllLinksInHouse(SudokuCell cell, int index, int cellIndex, int cand, int[] unit, boolean isBlock) {
        // zuerst zählen
        int anz = 0;
        for (int i = 0; i < unit.length; i++) {
            if (sudoku.getCell(unit[i]).getValue() == 0 && sudoku.getCell(unit[i]).isCandidate(candType, cand)) {
                anz++;
            }
        }
        // jetzt alle durchschauen und links setzen
        for (int i = 0; i < unit.length; i++) {
            int newIndex = unit[i];
            if (newIndex == cellIndex) {
                continue;
            }
            if (isBlock && (Sudoku.getLine(newIndex) == Sudoku.getLine(cellIndex) || Sudoku.getCol(newIndex) == Sudoku.getCol(cellIndex))) {
                // hatten wir schon
                continue;
            }
            SudokuCell cell1 = sudoku.getCell(newIndex);
            if (cell1.getValue() == 0 && cell1.isCandidate(candType, cand)) {
                if (anz == 2) {
                    //links[index++] = 1000 + newIndex * 10 + cand;
                    links[index++] = Chain.makeSEntry(newIndex, cand, true);
                } else {
                    //links[index++] = newIndex * 10 + cand;
                    links[index++] = Chain.makeSEntry(newIndex, cand, false);
                }
            }
        }
        return index;
    }

    class ChainComparator implements Comparator<SolutionStep> {

        /**
         * getAllChains() should be sorted by type first
         */
        @Override
        public int compare(SolutionStep o1, SolutionStep o2) {
            if (o1.getType().ordinal() != o2.getType().ordinal()) {
                return o1.getType().ordinal() - o2.getType().ordinal();
            }
            return o1.compareTo(o2);
        }
    }

    public static void main(String[] args) {
        Sudoku sudoku = new Sudoku(true);
        //sudoku.setSudoku("000002540009080000004006071000000234200070006846000000680900300000050100037600000");
        //sudoku.setSudoku(":0702:x:..2..85.4571643.....45.27..25.361.4..164..2..94.2851.67698543..4..12.6..125.364.8:918 332 838 938 939 758 958 759 959:318 338 359:");
        // ACHTUNG: Fehlhafte Candidaten: 9 muss in r1c3 gesetzt werden!
        //sudoku.setSudoku(":0702:x:..2..85.4571643.....45.27..25.361.4..164..2..94.2851.67698543..4..12.6..125.364.8:913 918 332 838 938 939 758 958 759 959:318 338 359:");
        //sudoku.setSudoku("36.859..45194723864.861395.1467382959..541.....59264.1.54387..9.931645....1295.43");
        //sudoku.setSudoku(":0000:x:4.963582.5.842...33268.945..3.7..28.8.23...74794582316.8.9.3.4...31.87..9..26.138:145 146 152 573 582 792::");
        //sudoku.setSudoku(":0702:9:.62143.5.1..5.8.3.5..7.9....28.154..4.56.2..3.16.8.52.6.9851..225...6..1..123.695:711 817 919 422 727 729 929 837 438 838 639 757 957 758 961 772 787 788 792:944 964 985:");
        //sudoku.setSudoku(":0000:x:61.......9.37.62..27..3.6.9.......85....1....79.......8.769..32..62.879..29....6.:517 419 819 138 141 854 756 459 863 169 469 391::");

        //sudoku.setSudoku(":0000:x:6.752.4..54..6..7..2.497.5..7524...1..41895.78....5.4.48...2.9575..1...4..6.547.8:647 364 374 684 288 391::");
        //sudoku.setSudoku(":0706::6.752.4..54..6..7..2.497.5..7524...1..41895.78....5.4.48...2.9575..1...4..6.547.8:112 316 318 123 323 327 329 137 647 364 374 684 288 391:384 826 827 963:");
        //sudoku.setSudoku(":0706::6.752.4..54..6..7..2.497.5..7524...1..41895.78....5.4.48...2.9575..1...4..6.547.8:112 316 318 123 323 826 327 827 329 137 647 963 364 374 384 684 288 391:362 662 962:");
        // BUG: shorter chain available
        //sudoku.setSudoku(":0702:6:.84..53.7..1493.....3.8..412.73.61..3.8...4..1.98....3.1..7.23...2.3....83.2..71.:522 622 731 931 532 632 732 548 549 255 958 959 571 671 581 681 981 482 582 682 984 986 988 989:614 615 631:");
        // BUG: Longest RemotePair is too long - fixed
        //sudoku.setSudoku(":0703:8:45.132..63.1657.4.2768493516.2415.37.1472356.73598612416.594.8..2.3614.554.27861.::817 829 917 929:");
        // BUG: No Remote Pair found (found but not displayed correctly - fixed
        sudoku.setSudoku(":0703:4:5.91673.8.63548.191..23956.952413..6316782495...9561328.53916..637824951.91675..3:432 462 298:498:");
        ChainSolver cs = new ChainSolver();
        cs.setSudoku(sudoku);
        cs.steps = new ArrayList<SolutionStep>();
        long millis = System.currentTimeMillis();
        List<SolutionStep> sumSteps = new ArrayList<SolutionStep>();
        int i = 0;
        for (i = 0; i < 1; i++) {
            //cs.getXChains();
            //cs.getRemotePairs();
            //cs.getXYChains();
            sumSteps = cs.getAllChains(sudoku);
        //cs.getNiceLoops();
        }
        millis = System.currentTimeMillis() - millis;
        System.out.println("Time: " + (millis / i) + "ms");
        System.out.println("Anzahl Aufrufe: " + (cs.anzAufrufe / i) + ", RecDepth: " + (cs.maxRecDepth / i));

        Collections.sort(cs.steps);
        for (i = 0; i < cs.steps.size(); i++) {
            System.out.println(cs.steps.get(i).toString(2));
        }
        System.out.println("Gesamt: " + cs.steps.size() + " chains!");
        
        Collections.sort(sumSteps);
        for (i = 0; i < sumSteps.size(); i++) {
            System.out.println(sumSteps.get(i).toString(2));
        }
        
    }
}
