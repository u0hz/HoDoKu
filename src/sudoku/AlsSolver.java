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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author user
 */
public class AlsSolver extends AbstractSolver {
    private static AlsComparator alsComparator = null;
    
    private List<SolutionStep> steps; // gefundene Lösungsschritte
    private SolutionStep globalStep = new SolutionStep(SolutionType.HIDDEN_SINGLE);
    
    private SudokuSet indexSet = new SudokuSet();
    //private SudokuSet candSet = new SudokuSet();
    //private SudokuSet[] candAddSets = new SudokuSet[10];
    private List<Als> alses = new ArrayList<Als>();
    private int[] startIndices;
    private int[] endIndices;
    private List<RestrictedCommon> restrictedCommons = new ArrayList<RestrictedCommon>();
    private List<RestrictedCommon> chain = new ArrayList<RestrictedCommon>(); // gespeichert werden für die Chain nur die Links
    private SudokuSet chainSet = new SudokuSet(); // Alle Indices der Chain bisher
    private Als startAls;  // erstes ALS in der Chain (für Prüfung auf Eliminierungen)
    private int recDepth = 0; // aktuelle Tiefe der Rekursion (Chain-Suche)
    private int maxRecDepth = 0; // maximale Tiefe der Rekursion (Chain-Suche)
    
    private SudokuSet possibleRestrictedCommonsSet = new SudokuSet(); // alle Kandidaten, die in beiden ALS vorkommen
    private SudokuSet restrictedCommonSet = new SudokuSet(); // zum Prüfen auf restricted commons (Buddies aufsummieren)
    private SudokuSet restrictedCommonTmpSet = new SudokuSet(); // Position mit Buddies vereinen
    private SudokuSet restrictedCommonIndexSet = new SudokuSet(); // Ale Positionen eines Kandidaten in beiden ALS
    private SudokuSet forbiddenIndexSet = new SudokuSet();  // Alle Positionen in allen ALS -> kommen nicht in Frage!
    private SudokuSet intersectionSet = new SudokuSet();  // für Prüfung auf Überlappen
    
    /** Creates a new instance of AlsSolver */
    public AlsSolver() {
        if (alsComparator == null) {
            alsComparator = new AlsComparator();
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
//                getTemplateSet(true);
//                if (steps.size() > 0) {
//                    result = steps.get(0);
//                }
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
        collectAllRestrictedCommons(true);
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
        Logger.getLogger(AlsSolver.class.getName()).log(Level.FINE, "getAllAlses() total: " + millis1 + "ms");
        steps = oldSteps;
        if (save != null) {
            setSudoku(save);
        }
        return resultSteps;
    }
    
    private void getAlsXYChain() {
        steps = new ArrayList<SolutionStep>();
        collectAllAlses();
        collectAllRestrictedCommons(true);
        getAlsXYChainInt();
    }
    
    private void getAlsXYWing() {
        steps = new ArrayList<SolutionStep>();
        collectAllAlses();
        collectAllRestrictedCommons(false);
        getAlsXYWingInt();
    }
    
    private void getAlsXZ() {
        steps = new ArrayList<SolutionStep>();
        collectAllAlses();
        collectAllRestrictedCommons(false);
        getAlsXZInt();
    }
    
    /**
     * Alle restricted commons durchgehen. Für jeden alle in beiden ALS enthaltene Kandidaten durchgehen
     * (außer restricted common selbst) und schauen, ob es Buddies außerhalb der ALS gibt. Wenn ja,
     * können sie gelöscht werden
     *
     * Wenn es mehr als einen restricted common zwischen den selben beiden ALS gibt, können beide für
     * Eliminierungen verwendet werden
     */
    private void getAlsXZInt() {
        globalStep.reset();
        int lastAls1 = -1;
        int lastAls2 = -1;
        for (int i = 0; i < restrictedCommons.size(); i++) {
            RestrictedCommon rc = restrictedCommons.get(i);
            if (lastAls1 != rc.als1 || lastAls2 != rc.als2) {
                if (globalStep.getCandidatesToDelete().size() > 0) {
                    // Step zusammenbauen
                    globalStep.setType(SolutionType.ALS_XZ);
                    globalStep.addAls(alses.get(lastAls1).indices, alses.get(lastAls1).candidates);
                    globalStep.addAls(alses.get(lastAls2).indices, alses.get(lastAls2).candidates);
                    try {
                        steps.add((SolutionStep)globalStep.clone());
                    } catch (CloneNotSupportedException ex) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                    }
                    globalStep.reset();
                }
                lastAls1 = rc.als1;
                lastAls2 = rc.als2;
            }
            Als als1 = alses.get(rc.als1);
            Als als2 = alses.get(rc.als2);
            checkCandidatesToDelete(als1, als2, null, rc.cand, -1);
        }
    }
    
    /**
     * Alle Kombinationen aus zwei restricted commons durchgehen und schauen, ob sich ein XY-Wing
     * konstruieren lässt (die Kandidaten für die zwei restricted commons müssen verschieden sein,
     * es müssen drei ALS beteiligt sein, die sich nicht überschneiden).
     *
     * Wenn eine gültige Kombination gefunden wurde, ALS C identifizieren, in ALS A und B die gemeinsamen
     * Kandidaten (!= restricted commons) feststellen und auf mögliche Eliminationen prüfen.
     */
    private void getAlsXYWingInt() {
        globalStep.reset();
        for (int i = 0; i < restrictedCommons.size(); i++) {
            RestrictedCommon rc1 = restrictedCommons.get(i);
            for (int j = i + 1; j < restrictedCommons.size(); j++) {
                RestrictedCommon rc2 = restrictedCommons.get(j);
                // die beiden restricted commons müssen verschieden sein!
                if (rc1.cand == rc2.cand) {
                    // kann kein XY-Wing sein!
                    continue;
                }
                // die beiden restricted commons müssen insgesamt 3 verschiedene ALS verbinden, die
                // sich nicht überschneiden dürfen
                // da rc1.als1 != rc1.als2 && rc2.als1 != rc2.als2 fällt der Check kurz aus
                if (! ((rc1.als1 == rc2.als1 && rc1.als2 != rc2.als2) ||
                        (rc1.als2 == rc2.als1 && rc1.als1 != rc2.als2) ||
                        (rc1.als1 == rc2.als2 && rc1.als2 != rc2.als1) ||
                        (rc1.als2 == rc2.als2 && rc1.als1 != rc2.als1))) {
                    // ist sicher kein XY-Wing!
                    continue;
                }
                // Feststellen, wer C ist, damit der Überschneidungscheck gemacht werden kann
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
                // Jetzt noch Überschneidungen prüfen (für die restricted commons allein wurde das schon gemacht
                // Da sich rc1.als1 nicht mit rc1.als2 (und rc2.als1 nicht mit rc2.als2) überlappt, können die
                // gemeinsamen Insices von rc1 und rc2 miteinander geprüft werden
                // Das Ergebnis muss jedenfalls gleich C sein (da C in beiden Restricted commons enthalten ist)
                intersectionSet.set(alses.get(rc1.als1).indices);
                intersectionSet.or(alses.get(rc1.als2).indices);
                indexSet.set(alses.get(rc2.als1).indices);
                indexSet.or(alses.get(rc2.als2).indices);
                intersectionSet.and(indexSet);
                if (! intersectionSet.equals(c.indices)) {
                    // Überlappung -> darf nicht sein
                    continue;
                }
                // jetzt geht es wie bei XZ um die gemeinsamen Kandidaten von A und B
                checkCandidatesToDelete(a, b, c, rc1.cand, rc2.cand);
                if (globalStep.getCandidatesToDelete().size() > 0) {
                    // Step zusammenbauen
                    globalStep.setType(SolutionType.ALS_XY_WING);
                    globalStep.addAls(a.indices, a.candidates);
                    globalStep.addAls(b.indices, b.candidates);
                    globalStep.addAls(c.indices, c.candidates);
                    try {
                        steps.add((SolutionStep)globalStep.clone());
                    } catch (CloneNotSupportedException ex) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                    }
                    globalStep.reset();
                }
            }
        }
    }
    
    /**
     * Ausgehend von jedem ALS alle möglichen Kombinationen durchgehen:
     *
     *  - die ALS einer Chain dürfen sich nicht überlappen
     *  - zwei in der Chain aufeinanderfolgende restricted commons dürfen nicht gleich sein
     *  - jede Chain muss mindestens 4 ALS lang sein
     *  - Anfangs- und End-ALS müssen einen gemeinsamen Kandidaten haben, für den es außerhalb
     *    der Chain noch andere Kandidaten gibt -> können gelöscht werden
     *
     * Da die Chains prinzipiell direktional sind, muss nur in eine Richtung gesucht werden
     * (hoffe ich jedenfalls).
     *
     * Der Check, ob ein neues ALS schon enthalten ist, muss nicht durchgeführt werden (erstens
     * gehen die restricted commons nur in eine Richtung, zweitens deckt sich der Check mit der
     * Prüfung auf Überlappung).
     *
     * Eventuell muss doch in beide Richtungen gesucht werden, muss ich noch prüfen (restricted Commons-
     * Struktur ändern?)
     */
    private void getAlsXYChainInt() {
        recDepth = 0;
        maxRecDepth = 0;
        for (int i = 0; i < alses.size(); i++) {
            startAls = alses.get(i);
            chain.clear();
            chainSet.set(startAls.indices);
            getAlsXYChainRecursive(i, -1, startAls);
        }
        Logger.getLogger(AlsSolver.class.getName()).log(Level.FINER, steps.size() + " (maxRecDepth: " + maxRecDepth + ")");
    }
    
    /**
     * Hier passierts: Für das ALS mit Index alsIndex alle restricted commons durchgehen.
     * Wenn das ALS, auf das der restricted common verweist, sich nicht mit den
     * bisherigen ALSs überschneidet und der neue restricted common verschieden ist
     * vom letzten, neues ALS hinzufügen und rekursiv weitermachen. Sobald die
     * Chain 4 Glieder lang ist, bei jedem Schritt auf mögliche Eliminierungen testen.
     */
    private void getAlsXYChainRecursive(int alsIndex, int firstRestrictedCommon, Als lastAls) {
        // Abbruch der Rekursion?
        if (alsIndex >= alses.size()) {
            return;
        }
        recDepth++;
        if (recDepth > maxRecDepth) {
            maxRecDepth = recDepth;
        }
        if (recDepth % 100 == 0) {
            Logger.getLogger(AlsSolver.class.getName()).log(Level.FINER, "Recursion depth: " + recDepth);
        }
        // wenn es keinen restricted common mehr gibt, wird die Schleife übersprungen
        for (int i = startIndices[alsIndex]; i < endIndices[alsIndex]; i++) {
            RestrictedCommon rc = restrictedCommons.get(i);
            if (chain.size() > 0 && rc.cand == chain.get(chain.size() - 1).cand) {
                // nicht erlaubt, restricted commons müssen sich bei jedem Glied ändern
                continue;
            }
            Als aktAls = alses.get(rc.als2);
            if (! chainSet.andEmpty(aktAls.indices)) {
                // neues ALS überschneidet sich mit dem Rest -> darf nicht sein
                continue;
            }
            
            // ok, nächstes ALS darf hinzugefügt werden
            if (firstRestrictedCommon == -1) {
                firstRestrictedCommon = rc.cand;
            }
            chain.add(rc);
            chainSet.or(aktAls.indices);
            // wenn die Chain mindestens 4 Glieder lang ist, auf zu löschende Kandidaten prüfen
            if (chain.size() >= 3) {
                globalStep.reset();
                checkCandidatesToDelete(startAls, aktAls, lastAls, firstRestrictedCommon, rc.cand, true, chainSet);
                if (globalStep.getCandidatesToDelete().size() > 0) {
                    // Chain gefunden -> Step zusammenbauen und einschreiben
                    globalStep.setType(SolutionType.ALS_XY_CHAIN);
                    globalStep.addAls(startAls.indices, startAls.candidates);
                    Als tmpAls = startAls;
                    for (int j = 0; j < chain.size(); j++) {
                        Als tmp = alses.get(chain.get(j).als2);
                        globalStep.addAls(tmp.indices, tmp.candidates);

                        // die restricted commons müssen noch für die gesamte Chain geschrieben werden
                        addRestrictedCommonToStep(tmpAls, tmp, chain.get(j).cand, true);
                        tmpAls = tmp;
                    }
                    try {
                        steps.add((SolutionStep)globalStep.clone());
                    } catch (CloneNotSupportedException ex) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                    }
                }
            }
            
            // und weiter geht die wilde Hatz...
            getAlsXYChainRecursive(rc.als2, firstRestrictedCommon, aktAls);
            
            // und wieder retour
            chainSet.andNot(aktAls.indices);
            chain.remove(rc);
            if (chain.size() == 0) {
                firstRestrictedCommon = -1;
            }
        }
        recDepth--;
    }
    
    /**
     * Kann für XZ und XY-Wing verwendet werden: Die gemeinsamen Kandidaten (!= restricted common) zweier ALS prüfen,
     * schauen, ob es Kandidaten gibt, die außerhalb der ALS liegen und alle Kandidaten sehen -> können gelöscht werden
     */
    private void checkCandidatesToDelete(Als als1, Als als2, Als als3, int restr1, int restr2) {
        checkCandidatesToDelete(als1, als2, als3, restr1, restr2, false, null);
    }
    
    private void checkCandidatesToDelete(Als als1, Als als2, Als als3, int restr1, int restr2, boolean forChain, SudokuSet forbiddenIndices) {
        boolean rcWritten = false;
        possibleRestrictedCommonsSet.set(als1.candidates);
        possibleRestrictedCommonsSet.and(als2.candidates);
        if (restr1 != -1) {
            possibleRestrictedCommonsSet.remove(restr1);
        }
        if (restr2 != -1) {
            possibleRestrictedCommonsSet.remove(restr2);
        }
        // possibleRestrictedCommons enthält jetzt alle Kandidaten, die in beiden ALS vorkommen,
        // außer dem restricted common selbst
        if (possibleRestrictedCommonsSet.isEmpty()) {
            // nichts zu tun
            return;
        }
        // Alle Positionen aller ALS sammeln (als3 kann null sein!)
        if (forChain) {
            forbiddenIndexSet.set(forbiddenIndices);
        } else {
            forbiddenIndexSet.set(als1.indices);
            forbiddenIndexSet.or(als2.indices);
            if (als3 != null) {
                forbiddenIndexSet.or(als3.indices);
            }
        }
        // jetzt alle gemeinsamen Kandidaten prüfen
        for (int j = 0; j < possibleRestrictedCommonsSet.size(); j++) {
            int cand = possibleRestrictedCommonsSet.get(j);
            // Alle Positionen sammeln, in denen Kandidat cand in beiden ALS vorkommt
            restrictedCommonIndexSet.set(als1.indicesPerCandidat[cand]);
            restrictedCommonIndexSet.or(als2.indicesPerCandidat[cand]);
            // jetzt prüfen, ob es Buddies für alle diese Kandidaten gibt
            restrictedCommonSet.setAll();
            for (int k = 0; k < restrictedCommonIndexSet.size(); k++) {
                int rIndex = restrictedCommonIndexSet.get(k);
                // die Position des Kandidatens selbst ist hier egal
                restrictedCommonTmpSet.set(sudoku.buddies[rIndex]);
                restrictedCommonSet.and(restrictedCommonTmpSet);
            }
            // die Positionen in den ALS werden explizit ausgenommen (für XY-Wing/XY-Chain nötig!)
            restrictedCommonSet.andNot(forbiddenIndexSet);
            // jetzt alle noch offenen Kandidaten checken, die nicht Teil der ALS sind
            indexSet.set(restrictedCommonIndexSet);
            restrictedCommonIndexSet.not();
            restrictedCommonIndexSet.and(sudoku.getAllowedPositions()[cand]);
            restrictedCommonSet.and(restrictedCommonIndexSet);
            if (! restrictedCommonSet.isEmpty()) {
                // gefunden -> können alle gelöscht werden
                for (int l = 0; l < restrictedCommonSet.size(); l++) {
                    globalStep.addCandidateToDelete(restrictedCommonSet.get(l), cand);
                }
                // die gemeinsamen Kandidaten selbst werden fins
                for (int l = 0; l < indexSet.size(); l++) {
                    globalStep.addFin(indexSet.get(l), cand);
                }
                if (! rcWritten) {
                    // endoFins mit restricted common belegen und chain einschreiben
                    if (! forChain) {
                        if (als3 != null) {
                            addRestrictedCommonToStep(als1, als3, restr1, false);
                        } else {
                            addRestrictedCommonToStep(als1, als2, restr1, false);
                        }
                        if (restr2 != -1) {
                            addRestrictedCommonToStep(als2, als3, restr2, false);
                        }
                    }
                    rcWritten = true;
                }
            }
        }
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
                    int dx = sudoku.getLine(index1) - Sudoku.getLine(index2);
                    int dy = sudoku.getCol(index1) - Sudoku.getCol(index2);
                    int dist = dx * dx + dy * dy;
                    if (dist < minDist) {
                        minDist = dist;
                        minIndex1 = index1;
                        minIndex2 = index2;
                    }
                }
            }
            int[] chain = new int[2];
//            chain[0] = minIndex1 * 10 + cand;
//            chain[1] = minIndex2 * 10 + cand;
            chain[0] = Chain.makeSEntry(minIndex1, cand, false);
            chain[1] = Chain.makeSEntry(minIndex2, cand, false);
            globalStep.addChain(0, 1, chain);
        }
    }
    
    /**
     * Alle Kombinationen von jeweils 2 ALS durchgehen und prüfen, ob es einen restricted common gibt
     * (Kandidat, der in beiden ALS vorkommt, und wo alle Vorkommen im einem ALS alle Vorkommen
     * im anderen ALS sehen)
     */
    private void collectAllRestrictedCommons(boolean writeIndices) {
        Logger.getLogger(AlsSolver.class.getName()).entering(getClass().getName(), "collectAllRestrictedCommons()");
        long ticks = System.currentTimeMillis();
        restrictedCommons.clear();
        if (writeIndices) {
            startIndices = new int[alses.size()];
            endIndices = new int[alses.size()];
        }
        // Alle Kombinationen durchgehen
        for (int i = 0; i < alses.size(); i++) {
            Als als1 = alses.get(i);
            if (writeIndices) {
                startIndices[i] = restrictedCommons.size();
            }
            Logger.getLogger(AlsSolver.class.getName()).log(Level.FINER, "als1: " + SolutionStep.getAls(als1));
            for (int j = i + 1; j < alses.size(); j++) {
                Als als2 = alses.get(j);
                // Kombination nur prüfen, wenn sich die ALS nicht überlappen
                intersectionSet.set(als1.indices);
                intersectionSet.and(als2.indices);
                if (! intersectionSet.isEmpty()) {
                    // Überlappung -> nichts checken
                    continue;
                }
                Logger.getLogger(AlsSolver.class.getName()).log(Level.FINER, "als2: " + SolutionStep.getAls(als2));
                // restricted common: alle Buddies + die Positionen der Kandidaten selbst verundet
                // muss mindestens alle Positionen dieses Kandidaten ergeben
                possibleRestrictedCommonsSet.set(als1.candidates);
                possibleRestrictedCommonsSet.and(als2.candidates);
                // possibleRestrictedCommons enthält jetzt alle Kandidaten, die in beiden ALS vorkommen
                if (possibleRestrictedCommonsSet.isEmpty()) {
                    // nichts zu tun
                    continue;
                }
                int rcAnz = 0;
                for (int k = 0; k < possibleRestrictedCommonsSet.size(); k++) {
                    int cand = possibleRestrictedCommonsSet.get(k);
                    // Alle Positionen sammeln, in denn Kandidat cand in beiden ALS vorkommt
                    restrictedCommonIndexSet.set(als1.indicesPerCandidat[cand]);
                    restrictedCommonIndexSet.or(als2.indicesPerCandidat[cand]);
                    // jetzt prüfen, ob sich alle diese Kandidaten sehen
                    restrictedCommonSet.setAll();
                    for (int l = 0; l < restrictedCommonIndexSet.size(); l++) {
                        int rIndex = restrictedCommonIndexSet.get(l);
                        // die Position des Kandidatens selbst mit prüfen
                        restrictedCommonTmpSet.set(Sudoku.buddies[rIndex]);
                        restrictedCommonTmpSet.add(rIndex);
                        restrictedCommonSet.and(restrictedCommonTmpSet);
                    }
                    // jetzt sind alle gemeinsamen Buddies bekannt, hier müssen noch alle
                    // gemeinsamen Kandidaten drinnen sein
                    if (restrictedCommonIndexSet.andEquals(restrictedCommonSet)) {
                        // gefunden -> cand ist restricted common
                        restrictedCommons.add(new RestrictedCommon(i, j, cand));
                        rcAnz++;
                    }
                }
                if (rcAnz > 1) {
                    Logger.getLogger(AlsSolver.class.getName()).log(Level.FINER, rcAnz + " RCs for ALS " + SolutionStep.getAls(als1) + "/" + SolutionStep.getAls(als2));
                }
            }
            if (writeIndices) {
                endIndices[i] = restrictedCommons.size();
            }
        }
        ticks = System.currentTimeMillis() - ticks;
        Logger.getLogger(AlsSolver.class.getName()).log(Level.FINE, "collectAllRestrictedCommons(): " + ticks + "ms; restrictedCommon size: " + restrictedCommons.size());
    }
    
    private void collectAllAlses() {
        alses = Als.getAlses(sudoku);
    }
    
    class RestrictedCommon implements Comparable<RestrictedCommon> {
        int als1;
        int als2;
        int cand;
        
        RestrictedCommon(int als1, int als2, int cand) {
            this.als1 = als1;
            this.als2 = als2;
            this.cand = cand;
        }
        
        @Override
        public int compareTo(AlsSolver.RestrictedCommon r) {
            int result = als1 - r.als1;
            if (result == 0) {
                result = als2 - r.als2;
                if (result == 0) {
                    result = cand - r.cand;
                }
            }
            return result;
        }
    }
    
    public static void main(String[] args) {
        Sudoku sudoku = new Sudoku(true);
        //sudoku.setSudoku(":0361:4:..5.132673268..14917...2835..8..1.262.1.96758.6..283...12....83693184572..723.6..:434 441 442 461 961 464 974:411:r7c39 r6c1b9 fr3c3");
        //sudoku.setSudoku(":0300:4:135792..4.9.8315278725461...8.917.5....3.4.783.72.89.1...673.1....1297..7..485..6:653 472 473 277 483 683 388:481:c28 r68");
        //sudoku.setSudoku(":0000:x:7.2.34.8.........2.8..51.74.......51..63.27..29.......14.76..2.8.........2.51.8.7:::");
        sudoku.setSudoku(":0000:x:5837..4.2.1.............1....9...63.........47.1.45928..52.38....6..427.12..6.3..:::");
//        sudoku.setSudoku(":0000:x:..65.849..15.4.7.2..9...65.9..867315681.5.279..7.9.864.63...5..1...3.94..9.7..1..:324 326 331 332 339 261 262 364 366 871 891::");        
//        sudoku.setSudoku(":0000:x:.78.6519393..1..7.516739842.9..76.1..6539.28..4..2..69657142938.2.983.5.389657421:::");        
//        sudoku.setSudoku(":0000:x:65.17....382469.5...18..6...36.4...5.27...46.845.1....2.3.845.6...5...825.8.21.34:917 931 738 739 246 147 747 947 355 356 959 266 366 767 967 978 181 981::");        
//        sudoku.setSudoku(":0000:x:8...742.5.248.57...3.621.9...94.2....1...8.2.2....63...5.263.7...214965....587..2:541 847 849 653 469 869 391 491 497::");        
//        sudoku.setSudoku(":0000:x:1.7.5.....8.17..3.3...98...7628394..8.1245.67..471682....58...6.1..67.9....92.5..:927 237 637 438 569 372 277 377 281 389 392 199::");        
//        sudoku.setSudoku(":0000:x:8..7...4...43....667.1248.9.6.2.9...4..871625...6.3.9.3.6.12.871....73.2.2..3...4:112 512 513 913 515 922 525 128 343 543 743 167 972 982 485 585 985 596::");        
        AlsSolver as = new AlsSolver();
        as.setSudoku(sudoku);
        long millis = System.currentTimeMillis();
//        as.getAlsXYChain();
        as.getAlsXZ();
        millis = System.currentTimeMillis() - millis;
        System.out.println("Find all ALS-XX: " + millis + "ms");
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
        int sum1 = 0, sum2 = 0;
        
        // zuerst nach Anzahl zu löschende Kandidaten (absteigend!)
        int result = o2.getCandidatesToDelete().size() - o1.getCandidatesToDelete().size();
        if (result != 0) return result;
        
        // nach Äquivalenz (gleiche zu löschende Kandidaten)
        if (! o1.isEquivalent(o2)) {
            // nicht äquivalent: nach Indexsumme der zu löschenden Kandidaten
            sum1 = o1.getIndexSumme(o1.getCandidatesToDelete());
            sum2 = o1.getIndexSumme(o2.getCandidatesToDelete());
            return sum1 == sum2 ? 1 : sum1 - sum2;
        }
        
        // Nach Anzahl ALS
        result = o1.getAlses().size() - o2.getAlses().size();
        if (result != 0) return result;
        
        // Nach Anzahl Kandidaten in allen ALS
        result = o1.getAlsesIndexCount() - o2.getAlsesIndexCount();
        if (result != 0) return result;
        
        // zuletzt nach Typ
        return o1.getType().ordinal() - o2.getType().ordinal();
    }
}
