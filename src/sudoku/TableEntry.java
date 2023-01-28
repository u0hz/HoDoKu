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

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The format of an entry is described in class Chain.
 *
 * In retIndices references are stored to the chain elements that made
 * the current chain entry possible index in the current table). If more 
 * than one retIndex is present, the chain is necessarily a net. Each retIndex 
 * can hold a maximum of five references to predecessors thus limiting
 * the complexity of networks.
 * Format of entries in retIndices:
 *
 *   bit  0 .. 11: Index of first predecessor (indices in extendedTable can be longer than 10 bits)
 *   bit 12 .. 21: Index of second predecessor (only for nets)
 *   bit 22 .. 31: Index of third predecessor (only for nets)
 *   bit 32 .. 41: Index of fourth predecessor (only for nets)
 *   bit 42 .. 51: Index of fifth predecessor (only for nets)
 *   bit 52 .. 60: Distance to the root element of the chain
 *   bit 61: set if entry is expanded (comes from another table)
 *   bit 62: source table is on- or off-table
 *   bit 63: source table is extended-table
 *
 * Please note:
 *   - "distance" is used in finding the shortest possible chain
 *     (number of links used to get to the current entry)
 *   - if more than one index is present, index 1 is always the largest
 *     numerical value (should make the shortest chain the "main"
 *     chain in networks)
 *   - if "expanded" (61) is set, the index1 is an index in the table
 *     from where the entry was expanded. Which table this is depends on
 *     the flags "onTable" and "extended":
 *        onTable 0 extended 0: offTable
 *        onTable 1 extended 0: onTable
 *        onTable 0 extended 1: extendedTable
 *        onTable 1 extended 1: invalid
 *
 * @author Bernhard Hobiger
 */
public class TableEntry {
    private static long[] rIndices = new long[5];
    private static final long EXPANDED =       0x2000000000000000L;
    private static final long ON_TABLE =       0x4000000000000000L;
    private static final long EXTENDED_TABLE = 0x8000000000000000L;
    private static final long RAW_ENTRY      = 0x1fffffffffffffffL;
    
    int index = 0;
    // ii s/w c
    // index strong/weak candidate
    int[] entries = new int[Options.getInstance().maxTableEntryLength];
    // enthält bis zu 5 reverse indices plus die Entfernung zum
    // ersten Eintrag der Tabelle
    long[] retIndices = new long[Options.getInstance().maxTableEntryLength];
    SudokuSet[] onSets = new SudokuSet[10];
    SudokuSet[] offSets = new SudokuSet[10];
    SortedMap<Integer,Integer> indices = new TreeMap<Integer,Integer>();
    
    TableEntry() {
        for (int i = 0; i < onSets.length; i++) {
            onSets[i] = new SudokuSet();
            offSets[i] = new SudokuSet();
        }
    }
    
    void reset() {
        index = 0;
        entries[0] = 0;
        retIndices[0] = 0;
        indices.clear();
        for (int i = 0; i < onSets.length; i++) {
            onSets[i].clear();
            offSets[i].clear();
        }
        for (int i = 0; i < entries.length; i++) {
            entries[i] = 0;
            retIndices[i] = 0;
        }
    }
    
    void addEntry(int cellIndex, int cand, int penalty, boolean set) {
        addEntry(cellIndex, -1, -1, Chain.NORMAL_NODE, cand, set, 0, 0, 0, 0, 0, penalty);
    }
    
    void addEntry(int cellIndex, int cand, boolean set) {
        addEntry(cellIndex, cand, set, 0, 0, 0, 0, 0);
    }
    
    void addEntry(int cellIndex, int cand, boolean set, int reverseIndex) {
        addEntry(cellIndex, cand, set, reverseIndex, 0, 0, 0, 0);
    }
    
    void addEntry(int cellIndex, int cand, boolean set, int ri1,
            int ri2, int ri3, int ri4, int ri5) {
        addEntry(cellIndex, -1, -1, Chain.NORMAL_NODE, cand, set, ri1, ri2, ri3, ri4, ri5);
    }
    
    void addEntry(int cellIndex1, int alsIndex, int nodeType, int cand, boolean set, int penalty) {
        addEntry(cellIndex1, Chain.getSLowerAlsIndex(alsIndex), Chain.getSHigherAlsIndex(alsIndex),
                nodeType, cand, set, 0, 0, 0, 0, 0, penalty);
    }
    
    void addEntry(int cellIndex1, int alsIndex, int nodeType, int cand, boolean set) {
        addEntry(cellIndex1, Chain.getSLowerAlsIndex(alsIndex), Chain.getSHigherAlsIndex(alsIndex),
                nodeType, cand, set, 0, 0, 0, 0, 0);
    }
    
    void addEntry(int cellIndex1, int cellIndex2, int cellIndex3, int nodeType, int cand, boolean set, int ri1,
            int ri2, int ri3, int ri4, int ri5) {
        addEntry(cellIndex1, cellIndex2, cellIndex3, nodeType, cand, set,
                ri1, ri2, ri3, ri4, ri5, 0);
    }
    
    /**
     * Einträge werden nur hinzugefügt, wenn sie nicht schon existieren
     */
    void addEntry(int cellIndex1, int cellIndex2, int cellIndex3, int nodeType, int cand, boolean set, int ri1,
            int ri2, int ri3, int ri4, int ri5, int penalty) {
        if (index >= entries.length) {
            // leider schon voll...
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "addEntry(): TableEntry is already full!");
            return;
        }
        // check only for single cells -> group nodes, ALS etc. can not be start or end point
        // of a chain (in this implementation)
        if (nodeType == Chain.NORMAL_NODE) {
            if ((set && onSets[cand].contains(cellIndex1)) || (! set && offSets[cand].contains(cellIndex1))) {
                // haben wir schon!
                return;
            }
        }
        int entry = Chain.makeSEntry(cellIndex1, cellIndex2, cellIndex3, cand, set, nodeType);
        entries[index] = entry;
        retIndices[index] = makeSRetIndex(ri1, ri2, ri3, ri4, ri5);
        // when expanding ri1 is the index of the original table for the entry;
        // setting the distance doesn't make any sense in this context (distance
        // is set by the expansion routine). Since we don't know here, whether we
        // are expanding or not, we just try to avoid exceptions
        if (ri1 < retIndices.length) {
            setDistance(index, getDistance(ri1) + 1);
        }
        
        if (nodeType == Chain.NORMAL_NODE) {
            if (set) {
                onSets[cand].add(cellIndex1);
            } else {
                offSets[cand].add(cellIndex1);
            }
        }
        
        // 20090213: Adjust chain penalty for ALS
        int distance = getDistance(index);
        distance += penalty;
        setDistance(index, distance);
        
        indices.put(entry, index);
        index++;
    }
    
    int getEntry(int index) {
        return entries[index];
    }
    
    int getEntryIndex(int cellIndex, boolean set, int cand) {
        return indices.get(Chain.makeSEntry(cellIndex, cand, set));
    }
    
    int getEntryIndex(int entry) {
        Integer tmp = indices.get(entry);
        if (tmp == null) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "tmp == null: " + entry);
        }
        return indices.get(entry);
    }
    
    boolean isFull() {
        return index == entries.length;
    }
    
    public int getCellIndex(int index) {
        return Chain.getSCellIndex(entries[index]);
    }
    
    public boolean isStrong(int index) {
        return Chain.isSStrong(entries[index]);
    }
    
    public int getCandidate(int index) {
        return Chain.getSCandidate(entries[index]);
    }
    
    /**
     * Erstellt einen retIndex-Eintrag: Es werden maximal fünf retIndexe in einem int
     * gespeichert (jeweils um 10 bit nach links geschoben -> maximaler Wert für
     * jeden retIndex ist 1023)
     *
     * Der größte Index wird index 0 (für Chain-Rekonstruktion ohne Net)
     */
    public static long makeSRetIndex(int index1, int index2, int index3,
            int index4, int index5) {
        rIndices[0] = index1;
        rIndices[1] = index2;
        rIndices[2] = index3;
        rIndices[3] = index4;
        rIndices[4] = index5;
        for (int i = rIndices.length - 1; i > 0; i--) {
            for (int j = 0; j < i; j++) {
                if (rIndices[j] < rIndices[j + 1]) {
                    long tmp = rIndices[j + 1];
                    rIndices[j + 1] = rIndices[j];
                    rIndices[j] = tmp;
                }
            }
        }
        return (rIndices[4] << 42) + (rIndices[3] << 32) + (rIndices[2] << 22) +
                (rIndices[1] << 12) + rIndices[0];
    }
    
    /**
     * Ermittelt die Anzahl der in diesem retIndex gespeicherten Indexe
     * Der erste ist immer gesetzt (auch wenn er 0 ist)
     */
    public static int getSRetIndexAnz(long retIndex) {
        int anz = 1;
        long tmp = retIndex;
        tmp >>= 12;
        for (int i = 0; i < 4; i++) {
            if ((tmp & 0x3ff) != 0) {
                anz++;
            }
            tmp >>= 10;
        }
        return anz;
    }
    
    public int getRetIndexAnz(int index) {
        return getSRetIndexAnz(retIndices[index]);
    }
    
    /**
     * Rechnet den richtigen index aus retIndex heraus
     * which geht von 0 bis 4, 5 liefert die Entfernung
     */
    public static int getSRetIndex(long retIndex, int which) {
        if (which == 0) {
            return (int)(retIndex & 0xfff);
        } else {
            return (int)((retIndex >> (which * 10 + 2)) & 0x3ff);
        }
    }
    
    public int getRetIndex(int index, int which) {
        return getSRetIndex(retIndices[index], which);
    }
    
    public void setDistance(int index, int distance) {
        // alte Entfernung löschen (50 mal 1)
        long tmp = distance & 0x1ff;
        retIndices[index] &= 0xE00FFFFFFFFFFFFFL;
        retIndices[index] |= (tmp << 52);
    }
    
    public int getDistance(int index) {
        return getSRetIndex(retIndices[index], 5) & 0x1ff;
    }
    
    /**
     * Prüft, ob der Eintrag aus einer anderen Tabelle stammt. Wenn ja,
     * ist getRetIndex( index, 0) der Index der Tabelle und ON_TABLE bestimmt,
     * ob es aus onTables oder aus offTables kommt
     */
    public boolean isExpanded(int index) {
        return (retIndices[index] & EXPANDED) != 0;
    }
    
    public void setExpanded(int index) {
        retIndices[index] |= EXPANDED;
    }
    
    /**
     * Prüft, ob der Eintrag onTables oder aus offTables kommt
     */
    public boolean isOnTable(int index) {
        return (retIndices[index] & ON_TABLE) != 0;
    }
    
    public void setOnTable(int index) {
        retIndices[index] |= ON_TABLE;
    }
    
    /**
     * Prüft, ob der Eintrag aus extendedTables kommt
     */
    public boolean isExtendedTable(int index) {
        return (retIndices[index] & EXTENDED_TABLE) != 0;
    }
    
    public void setExtendedTable(int index) {
        retIndices[index] |= EXTENDED_TABLE;
    }
    
    public void setExtendedTable() {
        retIndices[index - 1] |= EXTENDED_TABLE;
    }
    
    /**
     * Retrieves the node type of the entry
     */
    public int getNodeType(int index) {
        return Chain.getSNodeType(entries[index]);
    }
    
}
