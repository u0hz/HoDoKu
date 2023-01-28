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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author zhobigbe
 */
public class BacktrackingSolver {
    private static BacktrackingSolver instance = null;
    
    // je eine Bitmask für jede Zeile, Spalte und für jeden Block; für jede
    // Zelle, die im jeweiligen Haus gesetzt ist, wird das entsprechende Bit auf 0 gesetzt
    // rcb enthält daher für jedes Haus eine bitmap mit noch möglichen Werten
    private short[] rcb = new short[27];
    // eine Bitmask für jede Zelle: 1 Bit für jeden möglichen Kandidaten
    // unterscheidet nicht zwischen "gesetzt" und "Naked Single", muss aus rcb gelesen werden
    private short[] cell = new short[81];
    
    class Try {
        int candidates = 0;
        int cell = 0; 
        int free = 0;  // Index der im Original ungesetzten Zelle
        int guess = 0; 
        int index = 0;
    }
    
    // enthält für jede Zelle den index in die richtigen Häuser (rcb)
    private int[][] constraint = new int[81][3];
    
    private char[] name = { '.', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
    
    // enthält für die Bitmasken der Ziffern 1-9 die zugehörige Ziffer
    private short[] ident = new short[1 << 9];
    // enthält für jede mögliche Kombination von Bits die Anzahl an Bits
    private short[] count = new short[1 << 9];
    
    // enthält am Index i eine Bitmask mit dem niederwertigsten Bit von i gesetzt
    private short[] next = new short[1 << 9];
    // enthält die Bitmasks für die Ziffern 1-9 an den Character-Codes dieser Ziffern
    // Die Zeichen '.', '0' und '_' werden mit 0x1ff belegt (alle Ziffern gesetzt)
    private short[] token = new short[1 << 8];
    
    // aktueller Versuch für jede Zelle:
    // Ein Eintrag für jede ursprünglich nicht gesetzte Zelle
    private Try[] tries = new Try[81];
    
    // Anzahl Lösungen für das Sudoku
    private int solutionCount = 0;
    private String solution = null;
    
    /** Creates a new instance of BacktrackingSolver */
    private BacktrackingSolver() {
        int i, j, k, n;
        
        for (i = 0; i < tries.length; i++) {
            tries[i] = new Try();
        }
        
        // Arrays für Konvertierung Ziffer<->Bitmask und retour
        for (i = 1; i <= 9; i++) {
            short mask = (short)(1<<(i-1));
            token[(int)name[i]] = mask;
            ident[mask] = (short)i;
        }
        token['.'] = token['0'] = token['_'] = 0x1ff;
        
        // Anzahl Bits pro möglicher Bitmask ermitteln
        for (i = 0; i < 1<<9; i++) {
            k = 0;
            for (j = 1; j < 1<<9; j <<= 1) {
                // Anzahl Bits in i zählen
                if ((i & j) != 0 && k++ == 0) {
                    next[i] = (short)j;
                }
            }
            count[i] = (short)k;
        }
        
        // Indexe für die Häuser setzen
        for (k = i = 0; i < 9; i++) {
            n = 18 + ((i / 3) * 3);
            for (j = 9; j < 18; j++) {
                constraint[k][0] = i; // Zeilen von 0 bis 8
                constraint[k][1] = j; // Spalten von 9 bis 17
                constraint[k][2] = n + ((j / 3) % 3); // Blöcke von 18 bis 26
                k++;
            }
        }
    }
    
    public static BacktrackingSolver getInstance() {
        if (instance == null) {
            instance = new BacktrackingSolver();
        }
        return instance;
    }
    
    public void solve(String sudokuString) {
        int j, k, m, n, x;
        int level;
        solution = "";
        
        // Häuser initialisieren: Alle Ziffern in allen Häusern erlaubt
        for (int i = 0; i < 27; i++) {
            rcb[i] = (short)0x1ff;
        }

        int depth = 0; // Anzahl ungesetzter Zellen (entspr. Rekursionstiefe)
        for (int i = 0; i < sudokuString.length() && i < 81; i++) {
            x = sudokuString.charAt(i);
            if ((j = token[x]) != 0) {
                if (j == 0x1ff)
                    tries[depth++].free = i;
                else
                    move(i, j);
            } else {
                Logger.getLogger(getClass().getName()).log(Level.WARNING, "invalid character (" + x + ")");
            }
        }
        depth--;
        level = 0;
        solutionCount = 0;
        for (;;) {
            m = 10;
            for (k = level; k <= depth; k++) {
                // nächste ungesetzte Zelle suchen
                int index = tries[k].free;
                // Bitmask auf alle noch möglichen Werte setzen
                cell[index] = mask(index);
                if (cell[index] == 0) {
                    // keine Lösung mehr möglich (kein möglicher Wert für Zelle)
                    m = 11;
                    break;
                }
                if (m > (n = count[cell[index]])) {
                    tries[level + 1].index = k;
                    tries[level + 1].candidates = cell[index];
                    if ((m = n) == 1)
                        break;
                }
            }
            if (m < 10) {
                k = tries[level+1].index;
                tries[level+1].cell = tries[k].free;
                if (k != level) {
                    j = tries[level].free;
                    tries[level].free = tries[k].free;
                    tries[k].free = j;
                }
                level++;
            } else {
                if (m == 10) {
                    if (solutionCount++ != 0) {
                        Logger.getLogger(getClass().getName()).log(Level.FINER, "multiple solutions");
                        break;
                    }
                    StringBuffer tmpBuffer = new StringBuffer();
                    for (int i = 0; i < 81; i++) {
                        tmpBuffer.append(name[ident[cell[i]]]);
                    }
                    solution = tmpBuffer.toString();
                }
                undo(tries[level].cell, tries[level].guess);
            }
            while ((x = next[tries[level].candidates]) == 0) {
                if (--level == 0) {
                    if (solutionCount == 0) {
                        Logger.getLogger(getClass().getName()).log(Level.FINER, "no solution");
                    }
                    return;
                }
                undo(tries[level].cell, tries[level].guess);
            }
            tries[level].candidates ^= x;
            tries[level].guess = x;
            move(tries[level].cell, x);
        }
    }
    
    public int getSolutionCount() {
        return solutionCount;
    }
    
    public String getSolution() {
        return solution;
    }
    
    /**
     * Liefert für die Zelle index eine Bitmap mit allen Werten, die diese Zelle noch annehmen kann
     */
    private short mask(int index) {
        return (short)(rcb[constraint[index][0]] & rcb[constraint[index][1]] & rcb[constraint[index][2]]);
    }
    
    /**
     * "Setzt" bzw. "löscht" value in index: in jedem der drei Häuser der Zelle wird das
     * entsprechende Bit getoggled
     * value ist kein Wert, sondern eine Bitmap
     */
    private void propagate(int index, int value) {
        rcb[constraint[index][0]] ^= value;
        rcb[constraint[index][1]] ^= value;
        rcb[constraint[index][2]] ^= value;
    }
    
    /**
     * Setzt Wert value in Zelle index (value ist kein Wert, sondern eine Bitmap)
     */
    private void move(int index, int value) {
        cell[index] = (short)value;
        propagate(index, value);
    }
    
    /**
     * Löscht Wert value in Zelle index (value ist kein Wert, sondern eine Bitmap)
     */
    private void undo(int index, int value) {
        cell[index] = (short)0;
        propagate(index, value);
    }
    
    public static void main(String[] args) {
//        ..15............32...............2.9.5...3......7..8..27.....4.3...9.......6..5..
//        .1.....2....8..6.......3........43....2.1....8......9.4...7.5.3...2...........4..
//        ...87..3.52.......4..........3.9..7......54...8.......2.....5.....3....9...1.....
//        .51..........2.4...........64....2.....5.1..7...3..6..4...3.......8...5.2........
//        17.....4....62....5...3....84....1.....3....6......9....6.....3.....1..........5.
//        ...7...1...6.......4.......7..5.1.....8...4..2...........24.6...3..8....1.......9
//        3.....7.....1..4.....2.........5.61..82...........6....1.....287...3...........3.
//        64.7............53.......1.7.86........4.9...5.........6....4......5.2......1....
        BacktrackingSolver bs = BacktrackingSolver.getInstance();
//        bs.solve("..15............32...............2.9.5...3......7..8..27.....4.3...9.......6..5..");
//        System.out.println(bs.getSolution() + " (" + bs.getSolutionCount() + ")");
//        bs.solve(".1.....2....8..6.......3........43....2.1....8......9.4...7.5.3...2...........4..");
//        System.out.println(bs.getSolution() + " (" + bs.getSolutionCount() + ")");
//        bs.solve("...87..3.52.......4..........3.9..7......54...8.......2.....5.....3....9...1.....");
//        System.out.println(bs.getSolution() + " (" + bs.getSolutionCount() + ")");
//        bs.solve(".51..........2.4...........64....2.....5.1..7...3..6..4...3.......8...5.2........");
//        System.out.println(bs.getSolution() + " (" + bs.getSolutionCount() + ")");
//        bs.solve("17.....4....62....5...3....84....1.....3....6......9....6.....3.....1..........5.");
//        System.out.println(bs.getSolution() + " (" + bs.getSolutionCount() + ")");
//        bs.solve("...7...1...6.......4.......7..5.1.....8...4..2...........24.6...3..8....1.......9");
//        System.out.println(bs.getSolution() + " (" + bs.getSolutionCount() + ")");
//        bs.solve("3.....7.....1..4.....2.........5.61..82...........6....1.....287...3...........3.");
//        System.out.println(bs.getSolution() + " (" + bs.getSolutionCount() + ")");
//        bs.solve("64.7............53.......1.7.86........4.9...5.........6....4......5.2......1....");
//        System.out.println(bs.getSolution() + " (" + bs.getSolutionCount() + ")");
//        bs.solve("64.7............53.......1.7.86........4.9...5.........6....4......5.2......1....");
//        System.out.println(bs.getSolution() + " (" + bs.getSolutionCount() + ")");
        //bs.solve("1..2..3...2.1.........3...1....1.2.3..1...4....2.4...........1.2.........1.......");
        //System.out.println(bs.getSolution() + " (" + bs.getSolutionCount() + ")");
        
        int anzRuns = 100;
        long ticks = System.currentTimeMillis();
        for (int i = 0; i < anzRuns; i++) {
            //bs.solve("..15............32...............2.9.5...3......7..8..27.....4.3...9.......6..5..");
            //bs.solve(".1.756.........2..4...9.57...95...21.........76...14...91.7...3..7.........635.9.");
            //  2 Easter Monster bs.solve("1.......2.9.4...5...6...7...5.9.3.......7.......85..4.7.....6...3...9.8...2.....1");
            //  2 bs.solve("..............3.85..1.2.......5.7.....4...1...9.......5......73..2.1........4...9");
            // 95 bs.solve(".1.....2....8..6.......3........43....2.1....8......9.4...7.5.3...2...........4..");
            //  1 bs.solve("...87..3.52.......4..........3.9..7......54...8.......2.....5.....3....9...1.....");
            // 73 bs.solve("..15............32...............2.9.5...3......7..8..27.....4.3...9.......6..5..");
            //  0 bs.solve("7.....4...2..7..8...3..8..9...5..3...6..2..9...1..7..6...3..9...3..4..6...9..1..5");
            //  1 bs.solve("...7..8......4..3......9..16..5......1..3..4...5..1..75..2..6...3..8..9...7.....2");
            //bs.solve("536020900008000000000000000600285009000903000800761004000000000004000000201000007");
            //bs.solve("000000490020100000000000500800400300609000000000200000000069070040050000000000001");
            //bs.solve(".1.....2....8..6.......3........43....2.1....8......9.4...7.5.3...2...........4..");
            //bs.solve("...87..3.52.......4..........3.9..7......54...8.......2.....5.....3....9...1.....");
            //bs.solve("..1..4.......6.3.5...9.....8.....7.3.......285...7.6..3...8...6..92......4...1...");
            //bs.solve("7..4......5....3........1..368..........2..5....7........5...7213...8...6........");
        }
        ticks = System.currentTimeMillis() - ticks;
        System.out.println(bs.getSolution() + " (" + bs.getSolutionCount() + ")");
        System.out.println("Time: " + (ticks / anzRuns) + "ms");
    }
}
