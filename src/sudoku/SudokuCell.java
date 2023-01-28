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

import java.util.Arrays;

/**
 *
 * @author Bernhard Hobiger
 */
public class SudokuCell implements Cloneable {
    public static final int USER = 0;
    public static final int PLAY = 1;
    public static final int ALL = 2;
    
    public static final int BLOCK = 0;
    public static final int LINE = 1;
    public static final int COL = 2;
    public static final int CELL = 3;
    
    private static final short M_1 = 0x0001;
    private static final short M_2 = 0x0002;
    private static final short M_3 = 0x0004;
    private static final short M_4 = 0x0008;
    private static final short M_5 = 0x0010;
    private static final short M_6 = 0x0020;
    private static final short M_7 = 0x0040;
    private static final short M_8 = 0x0080;
    private static final short M_9 = 0x0100;
    private static final short M_ALL = 0x01FF;
    private static final short[] masks = { M_1, M_2, M_3, M_4, M_5, M_6, M_7, M_8, M_9 };
    private static final int[] anzCandsArray = new int[512];
    
    private byte value = 0;
    private boolean isFixed = false; // vorgegebene Zahl, kann nicht verändert werden!
    private short[] candidates = new short[3];
    
    private short[] tmpCand = new short[9];
    
    /** Creates a new instance of SudokuCell */
    public SudokuCell() {
        if (anzCandsArray[M_1] == 0) {
            // Für jede mögliche Kandidatenmaske die Anzahl an Kandidaten
            fillAnzCandsArrayRecursive(0, 0, (short)0);
        }
        setValue(0);
        // alle Zahlen möglich
        candidates[ALL] = M_ALL;
        candidates[PLAY] = M_ALL;
    }
    
    public SudokuCell(byte value) {
        this();
        this.value = value;
    }
    
    @Override
    public SudokuCell clone() throws CloneNotSupportedException {
        SudokuCell newCell = (SudokuCell) super.clone();
        newCell.candidates = new short[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            newCell.candidates[i] = candidates[i];
        }
        return newCell;
    }

    public void set(SudokuCell src) {
        value = src.value;
        isFixed = src.isFixed;
        for (int i = 0; i < candidates.length; i++) {
            candidates[i] = src.candidates[i];
        }
    }
    
    /**
     * Im Array anzCandsMask steht für jede mögliche Kombination aus Kandidaten (Bitmask)
     * die Anzahl der Kandidaten.
     */
    private void fillAnzCandsArrayRecursive(int start, int level, short baseMask) {
        level++;
        for (int i = start; i < masks.length; i++) {
            short newMask = (short)(baseMask | masks[i]);
            anzCandsArray[newMask] = level;
            fillAnzCandsArrayRecursive(i + 1, level, newMask);
        }
    }
    
    public byte getValue() {
        return value;
    }
    
    public void setValue(int value) {
        this.value = (byte) value;
    }
    
    public void setValue(int value, boolean isFixed) {
        this.value = (byte) value;
        this.isFixed = isFixed;
    }
    
    public void setCandidate(int type, int value) {
        short mask = masks[value - 1];
        candidates[type] |= mask;
    }
    
    public void delCandidate(int type, int value) {
        short mask = (short) ((~masks[value - 1]) & 0xFFFF);
        candidates[type] &= mask;
    }
    
    /** Wert in allen Kandidaten löschen */
    public void delCandidate(int value) {
        short mask = (short) ((~masks[value - 1]) & 0xFFFF);
        for (int i = 0; i < candidates.length; i++) {
            candidates[i] &= mask;
        }
    }
    
    public void setAllCandidates(int type) {
        candidates[type] |= M_ALL;
    }
    
    public void delAllCandidates(int type) {
        candidates[type] = 0;
    }
    
    public void resetPlayCandidates() {
        candidates[PLAY] = candidates[ALL];
    }
    
    public boolean isCandidate(int type, int value) {
        short mask = masks[value - 1];
        return (candidates[type] & mask) != 0 ? true : false;
    }
    
    public boolean isCandidateValid(int type, int value) {
        if (isCandidate(type, value) && isCandidate(ALL, value)) {
            return true;
        }
        return false;
    }
    
    public String getCandidateString(int type) {
        StringBuffer tmp = new StringBuffer();
        for (int i = 1; i <= 9; i++) {
            if (isCandidate(type, i)) {
                tmp.append(i);
            }
        }
        return tmp.toString();
    }
    
    public short[] getAllCandidates() {
        return getAllCandidates(ALL);
    }
    
    public short[] getAllCandidates(int type) {
        // wird von der Java-Runtime mit 0 initialisiert
        int index = 0;
        for (short i = 1; i <= 9; i++) {
            if (isCandidate(type, i)) {
                tmpCand[index++] = i;
            }
        }
        return Arrays.copyOf(tmpCand, index);
    }
    
    public short getCandidateMask(int type) {
        return candidates[type];
    }
    
    public void getCandidateSet(SudokuSet candSet) {
        getCandidateSet(candSet, PLAY);
    }
    
    public void getCandidateSet(SudokuSet candSet, int type) {
        candSet.set(candidates[type] << 1);
    }
    
    public boolean isFixed() {
        return isIsFixed();
    }
    
    public void setIsFixed(boolean isFixed) {
        this.isFixed = isFixed;
    }
    
    public int getAnzCandidates(int type) {
        return anzCandsArray[candidates[type]];
    }

    public void setValue(byte value) {
        this.value = value;
    }

    public boolean isIsFixed() {
        return isFixed;
    }

    public short[] getCandidates() {
        return candidates;
    }

    public void setCandidates(short[] candidates) {
        this.candidates = candidates;
    }
    
    @Override
    public String toString() {
        StringBuffer tmp = new StringBuffer();
        tmp.append("SudokuCell value=" + value +
                ", fixed=" + isFixed + ", cands:");
        short[] cands = getAllCandidates(PLAY);
        for (int i = 0; i < cands.length; i++) {
            tmp.append(" " + cands[i]);
        }
        return tmp.toString();
    }
}
