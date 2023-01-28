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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Es gelten die Definitionen aus dem Ultimate Fish Guide: http://www.sudoku.com/boards/viewtopic.php?t=4993
 *
 * Zusätze:
 *   - Ein Base-Candidate ist eine Potential Elimination, wenn er in mindestens zwei Cover-Units enthalten ist
 *   - Ein Basic-Fish ist Sashimi, wenn die Base-unit, die die Fins enthält, ohne Fins nur noch einen
 *     Kandidaten hat.
 *
 * ToDo:
 *
 *   - Endo-Fins
 *   - Elimination von Base-Kandidaten
 *   - Neue Sashimi-Definition
 *   - Optimieren! Sehr langsam für größere Franken- und Mutant-Fische
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
    private static final int BLOCK_MASK = 0x4;    // Array mit Sets, in jedem Set stehen für die entsprechende Unit alle Indexe,
    // an denen ein bestimmter Kandidat vorkommt
    private SudokuSet[] baseCandidates = null;
    private SudokuSet[] coverCandidates = null;    // Unit-Arrays für die aktuelle Suche
    private int[][] baseUnits = null;
    private int[][] coverUnits = null;
    private SudokuSet[] cInt = new SudokuSet[7]; // die Cover-Sets für den Vergleich
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
    private SudokuSet possibleCoverUnits = new SudokuSet();  // Set mit allen Cover-Units, die für den aktuellen Versuch gültig sind
    private SudokuSet coverUnitsIncluded = new SudokuSet(); // Indexe aller aktuellen Cover-Units
    private SudokuSet coverCandSet = new SudokuSet(); // alle aktuellen Cover-Candidates
    private SudokuSet[] coverCandidatesMasks = new SudokuSet[8]; // 1 Maske pro Rekursionsebene: Kandidaten, die in coverCandSet neu hinzugefügt wurden
    private SudokuSet deleteCandSet = new SudokuSet(); // Set mit allen Kandidaten, die gelöscht werden können
    private SudokuSet finBuddies = new SudokuSet();    // Set mit allen Kandidaten, die von allen fins gesehen werden können (exkl. Base-Kandidaten)
    private SudokuSet checkSashimiSet = new SudokuSet(); // für Sashimi-Check
    private boolean withoutFins; // true, wenn finnless Fische gesucht werden sollen
    private boolean withFins; // true, wenn Finned-Fische gesucht werden sollen
    private boolean withEndoFins; // Auch Fische mit EndoFins
    private boolean sashimi; // true, wenn Sashimi-Fische gesucht werden sollen (withFins muss ebenfalls true sein)
    private int minSize;     // minimale Anzahl Base-Units für Suche
    private int maxSize;     // maximale Anzahl Base-Units für Suche
    private int maxBaseCombinations = 0; // Anzahl möglicher Kombinationen aus base-units
    private FindAllStepsProgressDialog dlg = null;
    private SudokuSet templateSet = new SudokuSet();
    private int baseGesamt = 0;
    private int baseShowGesamt = 0;
    private int coverGesamt = 0;
    private int versucheFisch = 0;
    private int versucheFins = 0;
    private int[] anzFins = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    /** Creates a new instance of FishSolver */
    public FishSolver() {
        for (int i = 0; i < coverCandidatesMasks.length; i++) {
            coverCandidatesMasks[i] = new SudokuSet();
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
                result = getAnyFish(size, lineUnits, colUnits, true, false, false, false);
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
                result = getAnyFish(size, lineUnits, colUnits, false, true, false, false);
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
                result = getAnyFish(size, lineUnits, colUnits, false, true, true, false);
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
                result = getAnyFish(size, lineBlockUnits, colBlockUnits, true, false, false, true);
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
                result = getAnyFish(size, lineBlockUnits, colBlockUnits, false, true, false, true);
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
                result = getAnyFish(size, allUnits, allUnits, true, false, false, true);
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
                result = getAnyFish(size, allUnits, allUnits, false, true, false, true);
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
            int maxFins, int maxEndoFins, FindAllStepsProgressDialog dlg, int forCandidate) {
        this.dlg = dlg;
        Sudoku save = getSudoku();
        setSudoku(sudoku);
        int oldMaxFins = Options.getInstance().maxFins;
        int oldEndoFins = Options.getInstance().maxEndoFins;
        Options.getInstance().maxFins = maxFins;
        Options.getInstance().maxEndoFins = maxEndoFins;
        List<SolutionStep> oldSteps = steps;
        steps = new ArrayList<SolutionStep>();
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
            getFishes(i, minSize, maxSize, allUnits, allUnits, true, true, false, true);
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
            boolean withFins, boolean sashimi, boolean withEndoFins) {
        anzCheckBaseUnitsRecursive = 0;
        anzCheckCoverUnitsRecursive = 0;
        baseGesamt = 0;
        baseShowGesamt = 0;
        steps = new ArrayList<SolutionStep>();
        // Templates initialisieren (für Optimierung)
        initCandTemplates();
        for (int i = 1; i <= 9; i++) {
            getFishes(i, size, size, units1, units2, withoutFins, withFins, sashimi, withEndoFins);
            if (steps.size() > 0) {
                break;
            }
            if (units1 != allUnits && units2 != allUnits) {
                getFishes(i, size, size, units2, units1, withoutFins, withFins, sashimi, withEndoFins);
                if (steps.size() > 0) {
                    break;
                }
            }
        }
        if (steps.size() > 0) {
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

        this.baseUnits = baseUnits;
        this.coverUnits = coverUnits;
        this.candidate = candidate;
        this.withoutFins = withoutFins;
        this.withFins = withFins;
        this.withEndoFins = withEndoFins;
        this.sashimi = sashimi;
        this.minSize = minSize;
        this.maxSize = maxSize;
        initForCandidat(candidate, baseUnits, coverUnits);

        // Set mit den Indexen der im aktuellen Versuch inkludierten Base-Units (Index in baseUnits)
        SudokuSet baseUnitsIncluded = new SudokuSet();
        for (int i = 0; i < baseUnits.length; i++) {
            if (!baseCandidates[i].isEmpty()) {
                // aktuelle Base-Unit enthält mindestens einen Kandidaten -> versuchen
                baseUnitsIncluded.add(i);
                endoFins.clear();
                getFishesRecursive(maxSize, 1, baseCandidates[i], i + 1, baseUnitsIncluded, endoFins);
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
     * aktuellen Base-Units eine nicht leer Schnittmenge haben) und alle Kombinationen werden durchprobiert.
     *
     * @param maxSize Maximale Anzahl an Base-Units pro Kombination.
     * @param aktSize Derzeitige Anzahl Base-Units
     * @param baseCandSet Alle Kandidaten der bisher aufgenommenen Base-Units.
     * @param startIndex Index in <code>baseUnits</code>, ab dem neue Units hinzugefügt werden sollen.
     * @param baseUnitsIncluded Set mit den Indexen der aktuell verwendeten Base-Units.
     * @param endoFinSet Set mit allen Endo-Fins der aktuellen BaseUnits
     */
    private void getFishesRecursive(int maxSize, int aktSize, SudokuSet baseCandSet, int startIndex,
            SudokuSet baseUnitsIncluded, SudokuSet endoFinSet) {
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
            // die neue Unit darf nur probiert werden, wenn sie keine gemeinsamen Kandidaten mit dem
            // bestehenden Base-Set hat
            // VORSICHT: Spezialfall Fin als Kandidat in zwei Base-Units wird ignoriert -- FEHLT NOCH!
            endoFinCheck.clear();
            if (baseCandSet.intersects(baseCandidates[i], endoFinCheck)) {
                if (!withFins || !withEndoFins || (endoFinSet.size() + endoFinCheck.size()) > Options.getInstance().maxEndoFins) {
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
            // Vereinigung der bisherigen Sets mit der neuen Base-Units bilden (Kopie!)
            // und die Base-Unit speichern
            SudokuSet aktBaseCandSet = baseCandSet.clone();
            SudokuSet aktEndoFinSet = endoFinSet.clone();
            aktBaseCandSet.merge(baseCandidates[i]);
            aktEndoFinSet.merge(endoFinCheck);
            baseUnitsIncluded.add(i);
            // prüfen, ob es mit diesen Endofins überhaupt Eliminierungen geben kann (zahlt sich hier aus,
            // weil man sich die gesamte Prüfung auf Cover-Sets sparen kann
            finBuddies.setAll();
            if (Options.getInstance().isCheckTemplates()) {
                // alle Kandidaten, die die Endofins sehen können
                for (int j = 0; j < aktEndoFinSet.size(); j++) {
                    finBuddies.and(Sudoku.buddies[aktEndoFinSet.get(j)]);
                }
                // jetzt nur noch die, die es auch wirklich gibt
                finBuddies.andNot(sudoku.getAllowedPositions()[candidate]);
                // und von denen nur die, die eliminiert werden können
                finBuddies.and(delCandTemplates[candidate]);
            }
            // wenn aktSize im Bereich von this.minSize bis this.maxSize liegt, Cover-Units prüfen
            if (aktSize >= this.minSize && aktSize <= this.maxSize && !finBuddies.isEmpty()) {
                // prüfen, ob ein Fisch vorliegt: Ein Set mit allen Cover-Units erstellen,
                // die eine Schnittmenge mit einer der Base-Units bilden und mindestens
                // einen Kandidaten aus aktBaseCandSet enthalten
                possibleCoverUnits = getPossibleCoverUnits(aktBaseCandSet, baseUnitsIncluded);
                coverUnitsIncluded.clear();
                for (int j = 0; j < possibleCoverUnits.size(); j++) {
                    coverUnitsIncluded.add(possibleCoverUnits.get(j));
                    // hier brauchen wir noch nicht mit Masken zu arbeiten
                    coverCandSet.set(coverCandidates[possibleCoverUnits.get(j)]);
                    cInt[0] = coverCandidates[possibleCoverUnits.get(j)];
                    // Für die Suche ist aktuelle Anzahl Base-Units wichtig, die Cover-Sets beginnen wieder bei 1
                    checkCoverUnitsRecursive(aktSize, 1, aktBaseCandSet, baseUnitsIncluded,
                            coverUnitsIncluded, j + 1, possibleCoverUnits, aktEndoFinSet);
                    coverUnitsIncluded.remove(possibleCoverUnits.get(j));
                    cInt[0] = SudokuSet.EMPTY_SET;
                }
            }
            // Eine Base-Unit mehr
            getFishesRecursive(maxSize, aktSize, aktBaseCandSet, i + 1, baseUnitsIncluded, aktEndoFinSet);
            baseUnitsIncluded.remove(i);
        }
    }

    /**
     * Bildet rekursiv alle möglichen Kombinationen aus möglichen Cover-Sets. Wenn die Anzahl der
     * aktuell betrachteten Cover-Sets gleich oder bis maximal 2 größer ist als die Anzahl der aktuell
     * enthaltenen Base-Sets, kann geprüft werden, ob ein Fisch vorliegt.
     *
     * Die im aktuellen Versuch enthaltenen Cover-Candidaten stehen in <code>coverCandSet</code>
     * (aus Performancegründen Eigenschaft der Klasse)
     *
     * @param steps Liste mit allen gefundenen Fischen, neue Fische werden hier hinzugefügt.
     * @param anzBaseUnits Anzahl der Base-Units in diesem Versuch.
     * @param aktSize Anzahl der Cover-units in diesem Versuch.
     * @param baseCandSet Alle Kandidaten der aktuellen Base-Units.
     * @param baseUnitsIncluded Alle Indexe der Base-Units, die im derzeitigen Versuch enthalten sind.
     * @param coverUnitsIncluded Alle Indexe der Cover-Units, die im derzeitigen Versuch enthalten sind.
     * @param startIndex Index in possibleCoverUnits, ab dem neue Units hinzugefügt werden sollen.
     * @param possibleCoverUnits Set mit allen Cover-Units, die prinzipiell in Frage kommen (Indexe in coverUnits)
     * @param endoFinSet Set mit allen Endo-Fins der aktuellen Base-Units
     */
    private void checkCoverUnitsRecursive(int anzBaseUnits, int aktSize,
            SudokuSet baseCandSet, SudokuSet baseUnitsIncluded, SudokuSet coverUnitsIncluded, int startIndex,
            SudokuSet possibleCoverUnits, SudokuSet endoFinSet) {
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
                if (!endoFinSet.isEmpty() && !withFins) {
                    continue;
                }
                versucheFisch++;
                // jetzt kann es ein Fisch sein (mit oder ohne Flossen) -> prüfen
                fins.clear();
                boolean isCovered = baseCandSet.isCovered(coverCandSet, fins);
                fins.merge(endoFinSet);
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

                    // Kannibalismus (optimieren?)
                    for (int j = 0; j < baseCandSet.size(); j++) {
                        int aktCand = baseCandSet.get(j);
                        int anz = 0;
                        for (int k = 0; k < cInt.length; k++) {
                            if (cInt[k].contains(aktCand)) {
                                anz++;
                            }
                        }
                        if (anz >= 2) {
                            // kann gelöscht werden
                            addCandidateToDelete(aktCand, candidate);
                            addCannibalistic(aktCand, candidate);
                        }
                    }

                    // Wenn es zu löschende Kandidaten gibt, wird der SolutionStep in steps aufgenommen
                    if (candidatesToDelete.size() > 0) {
                        initSolutionStep(globalStep, aktSize, candidate, baseCandSet,
                                baseUnitsIncluded, coverUnitsIncluded, false, false, fins, endoFinSet,
                                candidatesToDelete, cannibalistic);
                        try {
                            steps.add((SolutionStep) globalStep.clone());
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
                        deleteCandSet.set(baseCandSet);
                        deleteCandSet.and(finBuddies);
                        for (int j = 0; j < deleteCandSet.size(); j++) {
                            int aktCand = deleteCandSet.get(j);
                            int anz = 0;
                            for (int k = 0; k < cInt.length; k++) {
                                if (cInt[k].contains(aktCand)) {
                                    anz++;
                                }
                            }
                            if (anz >= 2) {
                                // kann gelöscht werden
                                addCandidateToDelete(aktCand, candidate);
                                addCannibalistic(aktCand, candidate);
                            }
                        }
                    }
                    // Wenn es zu löschende Kandidaten gibt, wird der SolutionStep in steps aufgenommen
                    if (candidatesToDelete.size() > 0) {
                        boolean isSashimi = checkSashimi(baseCandSet, baseUnitsIncluded, coverUnitsIncluded);
                        if (!sashimi || isSashimi) {
                            initSolutionStep(globalStep, aktSize, candidate, baseCandSet,
                                    baseUnitsIncluded, coverUnitsIncluded, withFins, isSashimi, fins, endoFinSet,
                                    candidatesToDelete, cannibalistic);
                            try {
                                steps.add((SolutionStep) globalStep.clone());
                            } catch (CloneNotSupportedException ex) {
                                Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
                            }
                        }
                    }
                }
            }
            // eine neue Unit hinzufügen und wieder probieren.
            checkCoverUnitsRecursive(anzBaseUnits, aktSize, baseCandSet, baseUnitsIncluded,
                    coverUnitsIncluded, i + 1, possibleCoverUnits, endoFinSet);
            cInt[aktSize - 1] = SudokuSet.EMPTY_SET;
            // in diesem Schritt dazugefügte Kandidaten wieder entfernen
            coverCandSet.andNot(coverCandidatesMasks[aktSize]);
            coverUnitsIncluded.remove(possibleCoverUnits.get(i));
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
            SudokuSet fins, SudokuSet endoFins, List<Candidate> candidatesToDelete, List<Candidate> cannibalistic) {
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
     * prüfen, ob sie mit keiner der baseUnits identisch ist; wenn beides zutrifft,
     * dem neuen Set hinzufügen.
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
//        double fakAnzBaseUnits = 1;
//        for (int i = 2; i <= anzBaseUnits; i++) {
//            fakAnzBaseUnits *= i;
//        }
        maxBaseCombinations = 0;
        // we have only maxSize combinations (the smaller fishes are automatically
        // included
        //for (int i = minSize; i <= maxSize; i++) {
        for (int i = 1; i <= maxSize; i++) {
//            double fakNMinusK = 1;
//            for (int j = 2; j <= anzBaseUnits - i; j++) {
//                fakNMinusK *= j;
//            }
//            double fakK = 1;
//            for (int j = 2; j <= i; j++) {
//                fakK *= j;
//            }
            //maxBaseCombinations += (int) (fakAnzBaseUnits / (fakNMinusK * fakK));
            maxBaseCombinations += combinations((int) anzBaseUnits, i);
        }
        if (dlg != null) {
            dlg.resetFishProgressBar(maxBaseCombinations);
        }
    }

    /**
     * Calculates n over k
     * 
     * @param n
     * @param k
     * @return
     */
    private int combinations(int n, int k) {
        double fakN = 1;
        for (int i = 2; i <= n; i++) {
            fakN *= i;
        }
        double fakNMinusK = 1;
        for (int i = 2; i <= n - k; i++) {
            fakNMinusK *= i;
        }
        double fakK = 1;
        for (int i = 2; i <= k; i++) {
            fakK *= i;
        }
        return (int) (fakN / (fakNMinusK * fakK));
    }

    /**
     * Führt die tatsächlichen Berechnungen für {@link initForCandidat(int,int[][],int[][])} durch.
     * @param candidate Kandidat, für den der Fisch gesucht werden soll.
     * @param sets Array vom Typ <CODE>SudokuSet</CODE>, in dem die Indexpositionen des Kandidatens abgelegt werden sollen.
     * @param units Arrays mit den Indexpositionen der entsprechenden Units.
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

    public static void main(String[] args) {
        Sudoku sudoku = new Sudoku(true);
        //sudoku.setSudoku(":0361:4:..5.132673268..14917...2835..8..1.262.1.96758.6..283...12....83693184572..723.6..:434 441 442 461 961 464 974:411:r7c39 r6c1b9 fr3c3");
        //sudoku.setSudoku(":0300:4:135792..4.9.8315278725461...8.917.5....3.4.783.72.89.1...673.1....1297..7..485..6:653 472 473 277 483 683 388:481:c28 r68");
        //sudoku.setSudoku(":0000:x:7.2.34.8.........2.8..51.74.......51..63.27..29.......14.76..2.8.........2.51.8.7:::");
        // 7, 7, 20, 20: 18297 / 23422
        // 4, 4, 20, 20: 1329  / 1609
        sudoku.setSudoku(":0000:x:1.......2.9.4...5...6...7...5.9.3......+67+4......85..4.7.....6...3...9.8...2.....1:126 226 826 148 261 167::");
        // 7, 7, 20, 20: 327.453 / 457.859
        // 4, 4, 20, 20: 3328    / 3672
        //sudoku.setSudoku(":0000:x:9.7..5...1..7..9..86..9.57..8...61.9316.59..72.91..65.....2..96.9...4..8...9..3.5:214 314 414 315 615 815 217 118 218 222 325 228 328 428 234 339 439 448 854 458 772 473 374 384 185 493 795::");
        FishSolver fs = new FishSolver();
        long millis = System.currentTimeMillis();
        List<SolutionStep> steps = fs.getAllFishes(sudoku, 7, 7, 20, 20, null, -1);
        millis = System.currentTimeMillis() - millis;
        System.out.println("Zeit: " + millis + "ms");
    }
}
