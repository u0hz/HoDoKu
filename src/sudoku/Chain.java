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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A chain consists of links, every link can be weak or strong, it can be a candidat in a cell,
 * a group node, an ALS, AUR...
 *
 * Since more than one chain can share the same array, the chain starts with chain[start]
 * and it ends with chain[end].
 *
 * Every entry is a 32-bit Integer. Format of one entry:
 * 
 * |       |       |       |       |       |       |       |       |
 * |x x|x x x x|x x x x x x x|x x x x x x x|x x x x x x x|x|x x x x|
 * | 7 |   6   |     5       |      4      |      3      |2|   1   |
 *
 *  1: candidate (entry candidate if node is ALS, AUR...)
 *  2: Strong (1 ... candidat is set) or weak (0 ... candidate is not set)
 *  3: normal node: cell index
 *     group node:  index of first cell
 *     als, aur...: index of the first cell that provides the entry (e.g. turns an ALS into a LS)
 *  4: normal node: not used
 *     group node:  index of second cell or 0x7f if not used
 *     als, aur...: lower 7 bits of the index in appropriate array (stored outside the chain, normally in SolutionStep)
 *  5: normal node: not used
 *     group node:  third cell or 0x7f if not used
 *     als, aur...: higher 7 bits of the index in appropriate array (stored outside the chain, normally in SolutionStep)
 *  6: type of node:
 *       0 ... normal node
 *       1 ... group node
 *       2 ... ALS node
 *  7: reserved
 *
 * This format is used in chains and in table entries (see TablingSolver)
 *
 * @author Bernhard Hobiger
 */
public class Chain implements Cloneable {

    private static final int EQUALS_MASK = 0x3fffffef;
    private static final int CAND_MASK = 0xf;
    private static final int STRONG_MASK = 0x10;
    private static final int INDEX_MASK = 0x7f;
    private static final int INDEX1_MASK = 0xfe0;
    private static final int INDEX1_OFFSET = 5;
    private static final int INDEX2_MASK = 0x7f000;
    private static final int INDEX2_OFFSET = 12;
    private static final int INDEX3_MASK = 0x3f80000;
    private static final int INDEX3_OFFSET = 19;
    private static final int ALS_INDEX_MASK = 0x3fff000;
    private static final int ALS_INDEX_OFFSET = 12;
    private static final int NO_INDEX = 0x7f; // all bits set: index not used (only valid for third index in grouped node)
    private static final int MODE_MASK = 0x3c000000;
    private static final int MODE_DEL_MASK = 0xc3ffffff;
    private static final int MODE_OFFSET = 26;
    private static final int NORMAL_NODE_MASK = 0x0;
    private static final int GROUP_NODE_MASK = 0x4000000;
    private static final int ALS_NODE_MASK = 0x8000000;
    public static final int NORMAL_NODE = 0;
    public static final int GROUP_NODE = 1;
    public static final int ALS_NODE = 2;
    public static final String[] TYPE_NAMES = new String[]{"NORMAL_NODE", "GROUP_NODE", "ALS_NODE"};
    public int start;
    public int end;
    private int length;
    public int[] chain;

    public Chain() {
    }

    public Chain(int start, int end, int[] chain) {
        this.start = start;
        this.end = end;
        this.chain = chain;
        this.length = -1;
    }

    @Override
    public Object clone()
            throws CloneNotSupportedException {
        Chain newChain = (Chain) super.clone();
        newChain.start = start;
        newChain.end = end;
//        newChain.chain = chain.clone();
        newChain.chain = Arrays.copyOf(chain, end + 1);
        return newChain;
    }

    public void reset() {
        start = 0;
        end = 0;
        length = -1;
    }
    
    public void resetLength() {
        length = -1;
    }
    
    public int getLength() {
        return getLength(null);
    }

    public int getLength(List<AlsInSolutionStep> alses) {
        if (length == -1) {
            length = calculateLength(alses);
        }
        return length;
    }

    private int calculateLength() {
        return calculateLength(null);
    }

    private int calculateLength(List<AlsInSolutionStep> alses) {
        double tmpLength = 0;
        for (int i = start; i <= end; i++) {
            tmpLength++;
            if (getSNodeType(chain[i]) == Chain.ALS_NODE) {
                if (alses != null) {
                    int alsIndex = getSAlsIndex(chain[i]);
                    if (alses.size() > alsIndex) {
                        tmpLength += alses.get(alsIndex).getChainPenalty();
                    } else {
                        tmpLength += 5;
                    }
                } else {
                    tmpLength += 2.5;
                }
            }
        }
        return (int) tmpLength;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public int[] getChain() {
        return chain;
    }

    public void setChain(int[] chain) {
        this.chain = chain;
    }

    public static int makeSEntry(int cellIndex, int candidate, boolean isStrong) {
        return makeSEntry(cellIndex, 0, 0, candidate, isStrong, NORMAL_NODE);
    }

    public static int makeSEntry(int cellIndex, int candidate, boolean isStrong, int nodeType) {
        return makeSEntry(cellIndex, 0, 0, candidate, isStrong, nodeType);
    }

    public static int getSHigherAlsIndex(int alsIndex) {
        return (alsIndex >> 7) & INDEX_MASK;
    }

    public static int getSLowerAlsIndex(int alsIndex) {
        return alsIndex &= INDEX_MASK;
    }

    public static int makeSEntry(int cellIndex, int alsIndex, int candidate, boolean isStrong, int nodeType) {
        int tmpIndex = getSHigherAlsIndex(alsIndex);
        alsIndex = getSLowerAlsIndex(alsIndex);
        return makeSEntry(cellIndex, alsIndex, tmpIndex, candidate, isStrong, nodeType);
    }

    public static int makeSEntry(int cellIndex1, int cellIndex2, int cellIndex3, int candidate, boolean isStrong, int nodeType) {
        int entry = (cellIndex1 << INDEX1_OFFSET) | candidate;
        if (isStrong) {
            entry |= STRONG_MASK;
        }

        if (nodeType != NORMAL_NODE) {
            switch (nodeType) {
                case GROUP_NODE:
                    entry |= GROUP_NODE_MASK;
                    break;
                case ALS_NODE:
                    entry |= ALS_NODE_MASK;
                    break;
            }
        }

        if (cellIndex2 == -1) {
            if (nodeType == NORMAL_NODE) {
                cellIndex2 = 0;
            } else {
                cellIndex2 = NO_INDEX;
            }
        }
        if (cellIndex3 == -1) {
            if (nodeType == NORMAL_NODE) {
                cellIndex3 = 0;
            } else {
                cellIndex3 = NO_INDEX;
            }
        }
        entry |= (cellIndex2 << INDEX2_OFFSET);
        entry |= (cellIndex3 << INDEX3_OFFSET);
        return entry;
    }

    public void setEntry(int index, int entry) {
        chain[index] = entry;
    }

    public void setEntry(int index, int cellIndex, int candidate, boolean isStrong) {
        setEntry(index, makeSEntry(cellIndex, candidate, isStrong));
    }

    public static int getSCellIndex(int entry) {
        if (entry > 0) {
            return (entry >> INDEX1_OFFSET) & INDEX_MASK;
        } else {
            return ((-entry) >> INDEX1_OFFSET) & INDEX_MASK;
        }
    }

    public static int getSCellIndex2(int entry) {
        int result = -1;
        if (entry > 0) {
            result = (entry >> INDEX2_OFFSET) & INDEX_MASK;
        } else {
            result = ((-entry) >> INDEX2_OFFSET) & INDEX_MASK;
        }
        if (result == INDEX_MASK) {
            result = -1;
        }
        return result;
    }

    public static int getSCellIndex3(int entry) {
        int result = -1;
        if (entry > 0) {
            result = (entry >> INDEX3_OFFSET) & INDEX_MASK;
        } else {
            result = ((-entry) >> INDEX3_OFFSET) & INDEX_MASK;
        }
        if (result == INDEX_MASK) {
            result = -1;
        }
        return result;
    }

    public static int getSAlsIndex(int entry) {
        int result = -1;
        if (entry < 0) {
            entry = -entry;
        }
        result = (entry & ALS_INDEX_MASK) >> ALS_INDEX_OFFSET;
        return result;
    }

    public static int replaceSAlsIndex(int entry, int newAlsIndex) {
        entry &= ~ALS_INDEX_MASK;
        newAlsIndex <<= ALS_INDEX_OFFSET;
        newAlsIndex &= ALS_INDEX_MASK;
        entry |= newAlsIndex;
        return entry;
    }

    public void replaceAlsIndex(int entryIndex, int newAlsIndex) {
        chain[entryIndex] = replaceSAlsIndex(chain[entryIndex], newAlsIndex);
    }

    public int getCellIndex(int index) {
        return getSCellIndex(chain[index]);
    }

    public static int getSCandidate(int entry) {
        if (entry > 0) {
            return entry & CAND_MASK;
        } else {
            return (-entry) & CAND_MASK;
        }
    }

    public int getCandidate(int index) {
        return getSCandidate(chain[index]);
    }

    public static boolean isSStrong(int entry) {
        if (entry > 0) {
            return (entry & STRONG_MASK) != 0;
        } else {
            return ((-entry) & STRONG_MASK) != 0;
        }
    }

    public boolean isStrong(int index) {
        return isSStrong(chain[index]);
    }

    public static int getSNodeType(int entry) {
        if (entry > 0) {
            return (entry & MODE_MASK) >> MODE_OFFSET;
        } else {
            return ((-entry) & MODE_MASK) >> MODE_OFFSET;
        }
    }

    public int getNodeType(int index) {
        return getSNodeType(chain[index]);
    }

    public static int setSStrong(int entry, boolean strong) {
        if (strong) {
            entry |= STRONG_MASK;
        } else {
            entry &= ~STRONG_MASK;
        }
        return entry;
    }

    public void getNodeBuddies(int index, SudokuSetBase set, List<Als> alses) {
        getSNodeBuddies(chain[index], getCandidate(index), alses, set);
    }

    /**
     * - for normal nodes just takes the buddies of the node
     * - for group nodes takes the anded buddies of all node cells
     * - for ALS takes the anded buddies of all ALS cells, that contain that candidate
     * 
     * @param entry The entry
     * @param candidate Only valid for als entries: the candidate for which the buddies should be
     *        calculated
     * @param alses A list with all alses for that chain (only valid for ALS nodes)
     * @param set The set containing the buddies
     */
    public static void getSNodeBuddies(int entry, int candidate, List<Als> alses, SudokuSetBase set) {
        if (getSNodeType(entry) == NORMAL_NODE) {
            set.set(Sudoku.buddies[getSCellIndex(entry)]);
        } else if (getSNodeType(entry) == Chain.GROUP_NODE) {
            set.set(Sudoku.buddies[getSCellIndex(entry)]);
            set.and(Sudoku.buddies[getSCellIndex2(entry)]);
            if (getSCellIndex3(entry) != -1) {
                set.and(Sudoku.buddies[getSCellIndex3(entry)]);
            }
        } else if (getSNodeType(entry) == Chain.ALS_NODE) {
            Als als = alses.get(getSAlsIndex(entry));
            set.set(als.buddiesPerCandidat[candidate]);
        } else {
            set.clear();
            Logger.getLogger(Chain.class.getName()).log(Level.SEVERE, "getSNodeBuddies() gesamt: invalid node type (" + 
                    getSNodeType(entry) + ")");
        }
    }

    public static boolean equalsIndexCandidate(int entry1, int entry2) {
        return (entry1 & EQUALS_MASK) == (entry2 & EQUALS_MASK);
    }

    public static String toString(int entry) {
        if (getSNodeType(entry) == ALS_NODE) {
            return TYPE_NAMES[getSNodeType(entry)] + " - " +
                    getSAlsIndex(entry) + " - " +
                    getSCellIndex(entry) + " - " + isSStrong(entry) + " - " + getSCandidate(entry);
        } else {
            return TYPE_NAMES[getSNodeType(entry)] + " - " +
                    getSCellIndex3(entry) + " - " + getSCellIndex2(entry) + " - " +
                    getSCellIndex(entry) + " - " + isSStrong(entry) + " - " + getSCandidate(entry);
        }
    }
    
    @Override
    public String toString() {
        StringBuffer tmp = new StringBuffer();
        for (int i = start; i <= end; i++) {
            tmp.append(toString(chain[i]) + " ");
        }
        return tmp.toString();
    }

    public static void main(String[] args) {
        int entry = makeSEntry(0, 1, true);
        System.out.println("Entry: " + getSCellIndex(entry) + "/" + getSCandidate(entry) + "/" + isSStrong(entry));
    }
}
