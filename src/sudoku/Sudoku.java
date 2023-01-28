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

import java.io.ObjectInputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Bernhard Hobiger
 */
public class Sudoku implements Cloneable {
    // Indizes für Blöcke, Zeilen und Spalten
    public static final int[][] BLOCKS = {
        {0, 1, 2, 9, 10, 11, 18, 19, 20},
        {3, 4, 5, 12, 13, 14, 21, 22, 23},
        {6, 7, 8, 15, 16, 17, 24, 25, 26},
        {27, 28, 29, 36, 37, 38, 45, 46, 47},
        {30, 31, 32, 39, 40, 41, 48, 49, 50},
        {33, 34, 35, 42, 43, 44, 51, 52, 53},
        {54, 55, 56, 63, 64, 65, 72, 73, 74},
        {57, 58, 59, 66, 67, 68, 75, 76, 77},
        {60, 61, 62, 69, 70, 71, 78, 79, 80}
    };
    public static final int[][] LINES = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8},
        {9, 10, 11, 12, 13, 14, 15, 16, 17},
        {18, 19, 20, 21, 22, 23, 24, 25, 26},
        {27, 28, 29, 30, 31, 32, 33, 34, 35},
        {36, 37, 38, 39, 40, 41, 42, 43, 44},
        {45, 46, 47, 48, 49, 50, 51, 52, 53},
        {54, 55, 56, 57, 58, 59, 60, 61, 62},
        {63, 64, 65, 66, 67, 68, 69, 70, 71},
        {72, 73, 74, 75, 76, 77, 78, 79, 80}
    };
    public static final int[][] COLS = {
        {0, 9, 18, 27, 36, 45, 54, 63, 72},
        {1, 10, 19, 28, 37, 46, 55, 64, 73},
        {2, 11, 20, 29, 38, 47, 56, 65, 74},
        {3, 12, 21, 30, 39, 48, 57, 66, 75},
        {4, 13, 22, 31, 40, 49, 58, 67, 76},
        {5, 14, 23, 32, 41, 50, 59, 68, 77},
        {6, 15, 24, 33, 42, 51, 60, 69, 78},
        {7, 16, 25, 34, 43, 52, 61, 70, 79},
        {8, 17, 26, 35, 44, 53, 62, 71, 80}
    };    // Enthält zu jedem Index im Sudoku den zugehörigen Block (Index in BLOCKS)
    private static final int[] BLOCK_FROM_INDEX = {
        0, 0, 0, 1, 1, 1, 2, 2, 2,
        0, 0, 0, 1, 1, 1, 2, 2, 2,
        0, 0, 0, 1, 1, 1, 2, 2, 2,
        3, 3, 3, 4, 4, 4, 5, 5, 5,
        3, 3, 3, 4, 4, 4, 5, 5, 5,
        3, 3, 3, 4, 4, 4, 5, 5, 5,
        6, 6, 6, 7, 7, 7, 8, 8, 8,
        6, 6, 6, 7, 7, 7, 8, 8, 8,
        6, 6, 6, 7, 7, 7, 8, 8, 8
    };
    private static final int CPL = 9;    //
    // Templates
    //
    // Ein Template pro möglicher Kombination aus 9 gleichen Ziffern im Grid
    protected static SudokuSetBase[] templates = new SudokuSetBase[46656];
    // Ein Template mit allen möglichen Buddies für jede Zelle im Grid
    protected static SudokuSet[] buddies = new SudokuSet[81];
    // For every group of 8 cells (denoted by a byte in a SudokuSetBase) all possible buddies
    protected static SudokuSetBase[][] groupedBuddies = new SudokuSetBase[11][256];
    // Ein Template mit allen Zellen für jedes Haus
    protected static SudokuSet[] lineTemplates = new SudokuSet[9];
    protected static SudokuSet[] colTemplates = new SudokuSet[9];
    protected static SudokuSet[] blockTemplates = new SudokuSet[9];    // Ein Template pro Kandidat mit allen prinzipiell noch möglichen Positionen
    // (ohne Berücksichtigung der gesetzten Zellen)
    private SudokuSetBase[] possiblePositions = new SudokuSetBase[10];
    // Ein Template pro Kandidat mit allen noch möglichen Positionen
    private SudokuSetBase[] allowedPositions = new SudokuSetBase[10];
    // Ein Template pro Kandidat mit allen gesetzten Positionen
    private SudokuSet[] positions = new SudokuSet[10];    // 9x9 Sudoku, linearer Zugriff (ist leichter)
    private SudokuCell[] cells = new SudokuCell[81];    // Schwierigkeits-Level dieses Sudokus
    private DifficultyLevel level;
    private int score;    // Ausgangspunkt für dieses Sudoku (für reset)
    private String initialState = null;

    static {
        // Buddies und Unit-Sets initialisieren
        long ticks = System.currentTimeMillis();
        //System.out.println("Init...");
        initBuddies();
        ticks = System.currentTimeMillis() - ticks;
        //System.out.println("Init buddies: " + ticks + "ms");

        // Templates initialisieren
        ticks = System.currentTimeMillis();
        initTemplates();
        ticks = System.currentTimeMillis() - ticks;
        //System.out.println("Init templates: " + ticks + "ms");
        
        // Grouped buddies
        ticks = System.currentTimeMillis();
        initGroupedBuddies();
        ticks = System.currentTimeMillis() - ticks;
        //System.out.println("Init grouped buddies: " + ticks + "ms");
    }
    
    /** Creates a new instance of Sudoku */
    public Sudoku() {
        // allowed positions
        for (int i = 0; i <= 9; i++) {
            allowedPositions[i] = new SudokuSet(true);
            allowedPositions[i].setAll();
            possiblePositions[i] = new SudokuSet(true);
            possiblePositions[i].setAll();
            positions[i] = new SudokuSet();
        }

        setSudoku(null);
    }
    
    @Override
    public Sudoku clone() {
        Sudoku newSudoku = null;
        try {
            newSudoku = (Sudoku) super.clone();
            newSudoku.setCells(new SudokuCell[cells.length]);
            for (int i = 0; i < cells.length; i++) {
                newSudoku.cells[i] = cells[i].clone();
            }
            newSudoku.allowedPositions = new SudokuSetBase[allowedPositions.length];
            for (int i = 0; i < allowedPositions.length; i++) {
                newSudoku.allowedPositions[i] = allowedPositions[i].clone();
            }
            newSudoku.possiblePositions = new SudokuSetBase[possiblePositions.length];
            for (int i = 0; i < possiblePositions.length; i++) {
                newSudoku.possiblePositions[i] = possiblePositions[i].clone();
            }
            newSudoku.positions = new SudokuSet[positions.length];
            for (int i = 0; i < positions.length; i++) {
                newSudoku.positions[i] = positions[i].clone();
            }
            synchronizeSets();
        } catch (CloneNotSupportedException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
        }
        return newSudoku;
    }

    public void set(Sudoku src) {
        for (int i = 0; i < cells.length; i++) {
            cells[i].set(src.cells[i]);
        }
        for (int i = 0; i < allowedPositions.length; i++) {
            allowedPositions[i].set(src.allowedPositions[i]);
        }
        for (int i = 0; i < possiblePositions.length; i++) {
            possiblePositions[i].set(src.possiblePositions[i]);
        }
        for (int i = 0; i < positions.length; i++) {
            positions[i].set(src.positions[i]);
        }
        synchronizeSets();
    }

    /**
     * Alle Zellen außer den Givens auf 0 setzen
     */
    public void resetSudoku() {
        if (initialState != null) {
            setSudoku(initialState, false);
        }
    }

    /**
     * Sometimes the sets allowedPositions, possiblePositions and positions get
     * out of synch with the data in cells[]. This method resynchs them.
     */
    public void synchronizeSets() {
        for (int i = 0; i < allowedPositions.length; i++) {
            allowedPositions[i].clear();
            possiblePositions[i].setAll();
            positions[i].clear();
        }
        for (int i = 0; i < cells.length; i++) {
            int value = cells[i].getValue();
            if (value != 0) {
                positions[value].add(i);
                possiblePositions[value].andNot(buddies[i]);
            } else {
                for (int j = 1; j <= 9; j++) {
                    if (cells[i].isCandidateValid(SudokuCell.PLAY, j)) {
                        allowedPositions[j].add(i);
                    }
                }
            }
        }
    }

    public void setSudoku(String init) {
        setSudoku(init, true);
    }

    public void setSudoku(String init, boolean saveInitialState) {
        for (int i = 0; i < cells.length; i++) {
            cells[i] = new SudokuCell();
        }
        for (int i = 0; i < allowedPositions.length; i++) {
            allowedPositions[i].setAll();
        }
        for (int i = 0; i < possiblePositions.length; i++) {
            possiblePositions[i].setAll();
        }
        for (int i = 0; i < positions.length; i++) {
            positions[i].clear();
        }
        resetCandidates();
        if (init == null) {
            return;
        }

        //
        // Mögliche Formate: ...1.32.4..1.
        //
        //                   0001032040010
        //
        //                   +-------+-------+-------+
        //                   | . . . | . . . | . . . | (auch mit '0')
        //                   | 2 4 . | 3 5 . | . . . | (auch mit '0')
        //                   | . . . | . . . | . . . | (auch mit '0')
        //                   +-------+-------+-------+
        //                   | . . . | . 7 . | 3 . 1 |
        //                   | . . . | . . . | . . . |
        //                   | . . . | . . . | . . . |
        //                   +-------+-------+-------+
        //                   | . . . | . . . | . . . |
        //                   | . . . | . . . | . . . |
        //                   | . . . | . . . | . . . |
        //                   +-------+-------+-------+
        //
        //                   *-----------------------------------------------------------*
        //                   | 6     19    2     | 3     19    5     | 4     8     7     |
        //                   | 578   19    4     | 28    67    1679  | 3     159   1259  |
        //                   | 578   3     57    | 28    4     179   | 19    6     1259  |
        //                   |-------------------+-------------------+-------------------|
        //                   | 3    *678   568   |*17    2     179   | 1679  159   4     |
        //                   | 57    4     1     | 6     379   8     | 2     3579  359   |
        //                   | 9     2     67    | 4     5     137   | 167   137   8     |
        //                   |-------------------+-------------------+-------------------|
        //                   | 2     5   #367    |*17    8     1367  | 179   4     139   |
        //                   | 4    *67   9      | 5     1367  2     | 8     137   13    |
        //                   | 1    *78   378    | 9     37    4     | 5     2     6     |
        //                   *-----------------------------------------------------------*
        //
        // ACHTUNG: Beim Import eines PM-Grids können einzelne Werte auch für Naked Singles stehen.
        // Entscheidung: Wenn eines der Häuser eines Singles noch den selben Kandidaten enthält, ist es ein
        //               Naked Single, sonst ein gesetztes Zeichen
        //

        // Eingabe in einzelne Zeilen zerlegen, alle Rahmenzeilen und Sonderzeichen
        // wegwerfen (SudoCue verwendet "." im Rahmen, gibt Fehler);
        // eine Rahmenzeile ist eine Zeile, die mindestens ein Mal "---" enthält.
        String lineEnd = null;
        int[][] candidates = new int[9][9];
        if (init.contains("\r\n")) {
            lineEnd = "\r\n";
        } else if (init.contains("\r")) {
            lineEnd = "\r";
        } else if (init.contains("\n")) {
            lineEnd = "\n";
        }
        String[] lines = null;
        if (lineEnd != null) {
            lines = init.split(lineEnd);
            StringBuffer tmpBuffer = new StringBuffer();
            tmpBuffer.append("lines org\r\n");
            for (int i = 0; i < lines.length; i++) {
                tmpBuffer.append("lines[" + i + "]: " + lines[i] + "\r\n");
            }
            Logger.getLogger(getClass().getName()).log(Level.FINE, tmpBuffer.toString());
        } else {
            lines = new String[1];
            lines[0] = init;
            Logger.getLogger(getClass().getName()).log(Level.FINE, "Einzeiler: <" + lines[0] + ">");
        }
        int anzLines = lines.length;

        // Auf Library-Format prüfen: erkennbar an 1-Zeiler + 6 Doppelpunkte
        // wenn es Library-Format ist, werden die zu löschenden Kandidaten extra
        // gespeichert (libraryCandStr), lines[0] erhält nur die zu setzenden Zellen
        boolean libraryFormat = false;
        String libraryCandStr = null;
        if (anzLines == 1) {
            int anzDoppelpunkt = getAnzPatternInString(init, ":");
//            int anzDoppelpunkt = 0;
//            int index = -1;
//            while ((index = init.indexOf(":", index + 1)) >= 0) {
//                anzDoppelpunkt++;
//            }
            if (anzDoppelpunkt == 6 || anzDoppelpunkt == 7) {
                libraryFormat = true;
                String[] libLines = init.split(":");
                lines[0] = libLines[3];
                if (libLines.length >= 5) {
                    libraryCandStr = libLines[4];
                } else {
                    libraryCandStr = "";
                }
                Logger.getLogger(getClass().getName()).log(Level.FINE, "LF - lines[0]: " + lines[0]);
                Logger.getLogger(getClass().getName()).log(Level.FINE, "LF - libraryCandStr: " + libraryCandStr);
            }
        }

        // many formats append additional info after a '#', but are one liners -> check
        if (anzLines == 1) {
            if (lines[0].contains("#")) {
                String tmpStr = lines[0].substring(0, lines[0].indexOf("#")).trim();
                if (tmpStr.length() >= 81) {
                    // was comment at end of line
                    lines[0] = tmpStr;
                }
            }
        }

        // gsf's q1-taxonomy: more than 6 ',' in one line
        // 99480,99408,114,1895,100000002090400050006000700050903000000070000000850040700000600030009080002000001,Easter-Monster,20.00s,0,C21.m/F350642.604765/N984167.2160032/B207128.704971.243458.236078/P9.16.467645.2.20.1697341.25.5.2.445049253/M3.566.938,C21.m/F10.28/N8.25/B3.10.10/H2.4.2/W1.1.1/X1.8/Y1.13/K1.8.18.0.0.0.6.2/O3.3/G4.0.1/M1.4.29
        if (anzLines == 1) {
            if (getAnzPatternInString(init, ",") >= 6) {
                String[] gsfLines = init.split(",");
                lines[0] = gsfLines[4];
            }
        }

        // In the library format solved cells, that are not givens, can be marked with '+'
        // solvedButNotGivens is initialized with 'false'
        boolean[] solvedButNotGivens = new boolean[81];
        if (libraryFormat) {
            // in library format the sudoku itself is always in the form
            // "81.37.6..4..18....53....8.1.73...51..65...98..84...36..5.....29...561....48792156"
            StringBuffer tmp = new StringBuffer(lines[0]);
            for (int i = 0; i < tmp.length(); i++) {
                char ch = tmp.charAt(i);
                if (ch == '+') {
                    // cell is not a given!
                    solvedButNotGivens[i] = true;
                    tmp.deleteCharAt(i);
                    if (i >= 0) {
                        i--;
                    }
                }
            }
        }

        // alle überflüssigen Zeichen entfernen, Kandidaten
        // ermitteln und Sudoku setzen
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] != null) {
                // alle überflüssigen Zeichen entfernen; übrigbleiben dürfen nur
                // Ziffern, Punkte und Leerzeichen (immer nur 1 Leerzeichen!)
                // '|' ist fast immer vertikales Trennzeichen -> durch ' ' ersetzen
                StringBuffer tmp = new StringBuffer(lines[i].trim());
                // Trennzeilen sind Zeilen, die "---" enthalten
                // Trennzeilen enthalten manchmal Punkte; wenn der Inputstring von der
                // Kommandozeile kommt, macht das Probleme!
                int tmpIndex = -1;
                while ((tmpIndex = tmp.indexOf("---")) >= 0) {
                    if (tmpIndex > 0) {
                        char ch = tmp.charAt(tmpIndex - 1);
                        if (! Character.isDigit(ch) && ch != ' ' && ch != '|') {
                            tmpIndex--;
                        }
                    }
                    int endIndex = tmpIndex + 1;
                    while (endIndex < tmp.length() && tmp.charAt(endIndex) == '-') {
                        endIndex++;
                    }
                    if (endIndex < tmp.length() - 1) {
                        char ch = tmp.charAt(endIndex + 1);
                        if (! Character.isDigit(ch) && ch != ' ' && ch != '|') {
                            endIndex++;
                        }
                    }
                    //tmp.delete(0, tmp.length());
                    tmp.delete(tmpIndex, endIndex + 1);
                }
                // Müll entfernen
                for (int j = 0; j < tmp.length(); j++) {
                    char ch = tmp.charAt(j);
                    if (ch == '|') {
                        tmp.setCharAt(j, ' ');
                    } else if (!Character.isDigit(ch) && ch != '.' && ch != ' ') {
                        tmp.deleteCharAt(j);
                        if (j >= 0) {
                            j--;
                        }
                    }
                }
                // doppelte Leerzeichen entfernen
                int index = 0;
                while ((index = tmp.indexOf("  ")) != -1) {
                    tmp.deleteCharAt(index);
                }
                lines[i] = tmp.toString().trim();
                // wenn lines[i] jetzt leer ist, wird die Zeile entfernt
                if (lines[i].length() == 0) {
                    for (int j = i; j < lines.length - 1; j++) {
                        lines[j] = lines[j + 1];
                    }
                    lines[lines.length - 1] = null;
                    anzLines--;
                    i--;
                }
            }
        }
        StringBuffer tmpBuffer = new StringBuffer();
        tmpBuffer.append("lines trimmed\r\n");
        for (int i = 0; i < lines.length; i++) {
            tmpBuffer.append("lines[" + i + "]: " + lines[i] + "\r\n");
        }
        tmpBuffer.append("anzLines: " + anzLines);
        Logger.getLogger(getClass().getName()).log(Level.FINE, tmpBuffer.toString());

        // Spezialfall SimpleSudoku: enthält PM-Grid und Einzelstring -> Einzelstring ignorieren ?? gilt das noch???
        if (anzLines == 10) {
            anzLines--;
        }
        // SimpleSudoku: kann 3 Grids enthalten: Originale Givens, gelöste Zellen und PM
        boolean logAgain = false;
        while (anzLines > 9 && anzLines % 9 == 0) {
            logAgain = true;
            for (int i = 9; i < anzLines; i++) {
                lines[i - 9] = lines[i];
                if (i >= anzLines - 9) {
                    lines[i] = null;
                }
            }
            anzLines -= 9;
        }
        if (logAgain) {
            tmpBuffer = new StringBuffer();
            tmpBuffer.append("lines after SimpleSudoku\r\n");
            for (int i = 0; i < lines.length; i++) {
                tmpBuffer.append("lines[" + i + "]: " + lines[i] + "\r\n");
            }
            tmpBuffer.append("anzLines: " + anzLines);
            Logger.getLogger(getClass().getName()).log(Level.FINE, tmpBuffer.toString());
        }

        // wenn ein PM-Grid vorliegt, werden die Kandidaten nach candidates gelesen; bei Einzeilern werden
        // die Zellen direkt gesetzt
        int sRow = 0;
        int sCol = 0;
        int sIndex = 0;
        boolean singleDigits = true;
        boolean isPmGrid = false;
        String sInit = lines[0];
        for (int i = 1; i < anzLines; i++) {
            sInit += " " + lines[i];
        }
        if (sInit.length() > 81) {
            singleDigits = false;
        }
        if (sInit.length() > 2 * 81) {
            isPmGrid = true;
        }
        Logger.getLogger(getClass().getName()).log(Level.FINE, singleDigits + "/" + isPmGrid + "/" + sInit);
        while (sIndex < sInit.length()) {
            // bis zum nächsten Ziffernblock springen
            char ch = sInit.charAt(sIndex);
            while (sIndex < sInit.length() && !(Character.isDigit(ch) || ch == '.')) {
                sIndex++;
                ch = sInit.charAt(sIndex);
            }
            if (sIndex >= sInit.length()) {
                break;
            }
            if (isPmGrid) {
                if (ch == '.' || ch == '0') {
                    Logger.getLogger(getClass().getName()).log(Level.WARNING, "Invalid character: " + ch);
                    candidates[sRow][sCol] = 0;
                    sIndex++;
                } else {
                    if (singleDigits) {
                        candidates[sRow][sCol] = Integer.parseInt(sInit.substring(sIndex, sIndex + 1));
                        sIndex++;
                    } else {
                        int endIndex = sInit.indexOf(" ", sIndex);
                        if (endIndex < 0) {
                            endIndex = sInit.length();
                        }
                        candidates[sRow][sCol] = Integer.parseInt(sInit.substring(sIndex, endIndex));
                        Logger.getLogger(getClass().getName()).log(Level.FINE, "candidates[" + sRow + "][" + sCol + "] = " + candidates[sRow][sCol]);
                        sIndex = endIndex;
                    }
                }
            } else {
                if (Character.isDigit(ch) && Character.digit(ch, 10) > 0) {
                    boolean given = true;
                    if (libraryFormat) {
                        given = !solvedButNotGivens[sRow * 9 + sCol];
                    }
                    //System.out.println("sRow="+sRow+", sCol="+sCol+", digit="+Character.digit(ch,10)+", given="+given);
                    setCell(sRow, sCol, Character.digit(ch, 10), given);
                }
                sIndex++;
            }
            sCol++;
            if (sCol == 9) {
                sCol = 0;
                sRow++;
            }
        }

        int type = SudokuCell.PLAY;
        if (isPmGrid) {
            // Sudoku setzen: zuerst alle Kandidaten setzen, dann durchgehen und bei jeder Zelle
            // mit nur einem Kandidaten prüfen, ob der Kandidat in einem Buddy gesetzt ist; wenn
            // nein, kann die Zelle gesetzt werden
            int[] cands = new int[10];
            for (int row = 0; row < candidates.length; row++) {
                for (int col = 0; col < candidates[row].length; col++) {
                    // Es dürfen nur die angegebenen Kandidaten gesetzt sein
                    Arrays.fill(cands, 0);
                    int sum = candidates[row][col];
                    while (sum > 0) {
                        cands[sum % 10] = 1;
                        sum /= 10;
                    }
                    SudokuCell cell = getCell(row, col);
                    for (int i = 1; i < cands.length; i++) {
                        if (cands[i] == 0 && cell.isCandidate(type, i)) {
                            setCandidate(row, col, type, i, false);
                        } else if (cands[i] == 1 && !cell.isCandidate(type, i)) {
                            setCandidate(row, col, type, i, true);
                        }
                    }
                }
            }
            // Jetzt eventuelle Zellen setzen
            for (int i = 0; i < cells.length; i++) {
                SudokuCell cell = cells[i];
                if (cell.getAnzCandidates(type) == 1) {
                    // prüfen, ob einer der Buddies den Kandidaten auch hat
                    int cand = cell.getAllCandidates(type)[0];
                    boolean otherCandidate = false;
                    for (int j = 0; j < buddies[i].size(); j++) {
                        int i2 = buddies[i].get(j);
                        if (i != i2 && cells[i2].isCandidate(type, cand)) {
                            otherCandidate = true;
                            break;
                        }
                    }
                    if (!otherCandidate) {
                        setCell(i, cand, true);
                    //cell.setValue(cand);
                    }
                }
            }
        }

        // bei Library-Format müssen eventuell noch Kandidaten gelöscht werden
        if (libraryFormat && libraryCandStr.length() > 0) {
            String[] candArr = libraryCandStr.split(" ");
            tmpBuffer = new StringBuffer();
            tmpBuffer.append("libraryCandStr: <" + libraryCandStr + ">\r\n");
            for (int i = 0; i < candArr.length; i++) {
                tmpBuffer.append("candArr[" + i + "]: <" + candArr[i] + ">\r\n");
                if (candArr[i].length() == 0) {
                    continue;
                }
                int candPos = Integer.parseInt(candArr[i]);
                int col = candPos % 10;
                candPos /= 10;
                int row = candPos % 10;
                candPos /= 10;
                setCandidate(row - 1, col - 1, type, candPos, false);
            }
            Logger.getLogger(getClass().getName()).log(Level.FINE, tmpBuffer.toString());
        }

        if (saveInitialState) {
            //setInitialState(getSudoku(ClipboardMode.PM_GRID));
            setInitialState(getSudoku(ClipboardMode.LIBRARY));
        }
    }

    private int getAnzPatternInString(String str, String pattern) {
        int anzPattern = 0;
        int index = -1;
        while ((index = str.indexOf(pattern, index + 1)) >= 0) {
            anzPattern++;
        }
        return anzPattern;
    }

    public boolean checkSudoku(Sudoku solvedSudoku) {
        for (int i = 0; i < cells.length; i++) {
            SudokuCell cell = cells[i];
            if (cell.getValue() != 0) {
                if (!checkIsValidValue(i, cell.getValue())) {
                    return false;
                }
                if (solvedSudoku != null && solvedSudoku.getCell(i).getValue() != cell.getValue()) {
                    return false;
                }
            } else {
                for (int j = 1; j <= 9; j++) {
                    if (!cell.isCandidate(SudokuCell.PLAY, j)) {
                        continue;
                    }
                    if (!cell.isCandidateValid(SudokuCell.PLAY, j)) {
                        return false;
                    }
                }
                if (solvedSudoku != null) {
                    int solvedValue = solvedSudoku.getCell(i).getValue();
                    if (solvedValue != 0 && !cell.isCandidate(SudokuCell.PLAY, solvedValue)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public String getSudoku() {
        return getSudoku(ClipboardMode.PM_GRID, null);
    }

    public String getSudoku(ClipboardMode mode) {
        return getSudoku(mode, null);
    }

    public String getSudoku(ClipboardMode mode, SolutionStep step) {
        String dot = Options.getInstance().useZeroInsteadOfDot ? "0" : ".";
        StringBuffer out = new StringBuffer();
        if (mode == ClipboardMode.LIBRARY) {
            if (step == null) {
                out.append(":0000:x:");
            } else {
                String type = step.getType().getLibraryType();
                if (step.getType().isFish() && step.isIsSiamese()) {
                    type += "1";
                }
                out.append(":" + type + ":");
//                for (int i = 0; i < step.getValues().size(); i++) {
//                    out.append(step.getValues().get(i));
//                }
                // append the candidates, that can be deleted
                SortedSet<Integer> candToDeleteSet = new TreeSet<Integer>();
                if (step.getType().useCandToDelInLibraryFormat()) {
                    for (Candidate cand : step.getCandidatesToDelete()) {
                        candToDeleteSet.add(cand.value);
                    }
                }
                // if nothing can be deleted, append the cells, that can be set
                if (candToDeleteSet.isEmpty()) {
                    for (int i = 0; i < step.getValues().size(); i++) {
                        candToDeleteSet.add(step.getValues().get(i));
                    }
                }
                for (int cand : candToDeleteSet) {
                    out.append(cand);
                }
                out.append(":");
            }
        }
        if (mode == ClipboardMode.CLUES_ONLY || mode == ClipboardMode.VALUES_ONLY ||
                mode == ClipboardMode.LIBRARY) {
            for (SudokuCell cell : cells) {
                if (cell.getValue() == 0 || (mode == ClipboardMode.CLUES_ONLY && !cell.isFixed())) {
                    //out.append(".");
                    out.append(dot);
                } else {
                    if (mode == ClipboardMode.LIBRARY && !cell.isFixed()) {
                        out.append("+");
                    }
                    out.append(Integer.toString(cell.getValue()));
                }
            }
        }
        if (mode == ClipboardMode.PM_GRID || mode == ClipboardMode.PM_GRID_WITH_STEP) {
            // new: create one StringBuffer per cell with all candidates/values; add
            // special characters for step if necessary; if a '*' is added to a cell, 
            // insert a blank in all other cells of that col that don't have a '*';
            // calculate fieldLength an write it
            StringBuffer[] cellBuffers = new StringBuffer[cells.length];
            for (int i = 0; i < cells.length; i++) {
                cellBuffers[i] = new StringBuffer();
                if (cells[i].getValue() != 0) {
                    cellBuffers[i].append(String.valueOf(cells[i].getValue()));
                } else {
                    //cellBuffers[i].append(String.valueOf(cells[i].getCandidateString(SudokuCell.PLAY)));
                    String candString = cells[i].getCandidateString(SudokuCell.PLAY);
                    if (candString.isEmpty()) {
                        candString = dot;
                    }
                    cellBuffers[i].append(candString);
                }
            }

            // now add markings for step
            if (mode == ClipboardMode.PM_GRID_WITH_STEP && step != null) {
                boolean[] cellsWithExtraChar = new boolean[cells.length];
                // indices
                for (int index : step.getIndices()) {
                    insertOrReplaceChar(cellBuffers[index], '*');
                    cellsWithExtraChar[index] = true;
                }
                // fins and endo-fins
                if (SolutionType.isFish(step.getType()) ||
                        step.getType() == SolutionType.W_WING) {
                    for (Candidate cand : step.getFins()) {
                        int index = cand.getIndex();
                        insertOrReplaceChar(cellBuffers[index], '#');
                        cellsWithExtraChar[index] = true;
                    }
                }
                if (SolutionType.isFish(step.getType())) {
                    for (Candidate cand : step.getEndoFins()) {
                        int index = cand.getIndex();
                        insertOrReplaceChar(cellBuffers[index], '@');
                        cellsWithExtraChar[index] = true;
                    }
                }
                // chains
                for (Chain chain : step.getChains()) {
                    for (int i = chain.start; i <= chain.end; i++) {
                        if (chain.getNodeType(i) == Chain.ALS_NODE) {
                            // ALS are handled separately
                            continue;
                        }
                        int index = chain.getCellIndex(i);
                        insertOrReplaceChar(cellBuffers[index], '*');
                        cellsWithExtraChar[index] = true;
                        if (chain.getNodeType(i) == Chain.GROUP_NODE) {
                            index = Chain.getSCellIndex2(chain.chain[i]);
                            if (index != -1) {
                                insertOrReplaceChar(cellBuffers[index], '*');
                                cellsWithExtraChar[index] = true;
                            }
                            index = Chain.getSCellIndex3(chain.chain[i]);
                            if (index != -1) {
                                insertOrReplaceChar(cellBuffers[index], '*');
                                cellsWithExtraChar[index] = true;
                            }
                        }
                    }
                }

                // ALS
                char alsChar = 'A';
                for (AlsInSolutionStep als : step.getAlses()) {
                    for (int index : als.getIndices()) {
                        insertOrReplaceChar(cellBuffers[index], alsChar);
                        cellsWithExtraChar[index] = true;
                    }
                    alsChar++;
                }

                // candidates to delete
                for (Candidate cand : step.getCandidatesToDelete()) {
                    int index = cand.index;
                    char candidate = Character.forDigit(cand.value, 10);
                    for (int i = 0; i < cellBuffers[index].length(); i++) {
                        if (cellBuffers[index].charAt(i) == candidate && (i == 0 || (i > 0 && cellBuffers[index].charAt(i - 1) != '-'))) {
                            cellBuffers[index].insert(i, '-');
                            if (i == 0) {
                                cellsWithExtraChar[index] = true;
                            }
                        }
                    }
                }

                // now adjust columns, where a character was added
                for (int i = 0; i < cellsWithExtraChar.length; i++) {
                    if (cellsWithExtraChar[i]) {
                        int[] indices = Sudoku.COLS[Sudoku.getCol(i)];
                        for (int j = 0; j < indices.length; j++) {
                            if (Character.isDigit(cellBuffers[indices[j]].charAt(0))) {
                                cellBuffers[indices[j]].insert(0, ' ');
                            }
                        }
                    }
                }
            }

            int[] fieldLengths = new int[COLS.length];
            for (int i = 0; i < cellBuffers.length; i++) {
                int col = getCol(i);
                if (cellBuffers[i].length() > fieldLengths[col]) {
                    fieldLengths[col] = cellBuffers[i].length();
                }
            }
            for (int i = 0; i < fieldLengths.length; i++) {
                fieldLengths[i] += 2;
            }
            for (int i = 0; i < 9; i++) {
                if ((i % 3) == 0) {
                    writeLine(out, i, fieldLengths, null, true);
                }
                writeLine(out, i, fieldLengths, cellBuffers, false);
            }
            writeLine(out, 9, fieldLengths, null, true);

            if (mode == ClipboardMode.PM_GRID_WITH_STEP && step != null) {
                out.append("\r\n");
                out.append(step.toString(2));
            }
        }
        if (mode == ClipboardMode.LIBRARY) {
            // gelöschte Kandidaten anhängen
            int type = SudokuCell.PLAY;
            boolean first = true;
            out.append(":");
            for (int i = 0; i < cells.length; i++) {
                SudokuCell cell = cells[i];
                if (cell.getValue() == 0) {
                    for (int j = 1; j <= 9; j++) {
                        if (cell.isCandidate(SudokuCell.ALL, j) && !cell.isCandidate(type, j)) {
                            if (first) {
                                first = false;
                            } else {
                                out.append(" ");
                            }
                            out.append(Integer.toString(j) + Integer.toString((i / 9) + 1) + Integer.toString((i % 9) + 1));
                        }
                    }
                }
            }
            if (step == null) {
                out.append("::");
            } else {
                String candString = step.getCandidateString(true);
                out.append(":" + candString + ":");
//                if (SolutionType.isFish(step.getType())) {
//                    step.getEntities(out, step.getBaseEntities(), true);
//                    out.append(" ");
//                    step.getEntities(out, step.getCoverEntities(), true);
//                    if (step.getFins().size() > 0) {
//                        out.append(" ");
//                        step.getFins(out, false, true);
//                    }
//                    if (step.getEndoFins().size() > 0) {
//                        out.append(" ");
//                        step.getFins(out, true, true);
//                    }
//                }
                if (candString.isEmpty()) {
                    out.append(step.getValueIndexString());
                }
                out.append(":");
                if (step.getType().isSimpleChainOrLoop()) {
                    out.append((step.getChainLength() - 1));
                }
            }
        }
        return out.toString();
    }

    private void insertOrReplaceChar(StringBuffer buffer, char ch) {
        if (Character.isDigit(buffer.charAt(0))) {
            buffer.insert(0, ch);
        } else {
            buffer.replace(0, 1, Character.toString(ch));
        }
    }

    private void writeLine(StringBuffer out, int line, int[] fieldLengths,
            StringBuffer[] cellBuffers, boolean drawOutline) {
        if (drawOutline) {
            char leftRight = '.';
            char middle = '.';
            if (line == 3 || line == 6) {
                leftRight = ':';
                middle = '+';
            } else if (line == 9) {
                leftRight = '\'';
                middle = '\'';
            }
            out.append(leftRight);
            //for (int i = 0; i < 3 * fieldLength; i++) {
            for (int i = 0; i < fieldLengths[0] + fieldLengths[1] + fieldLengths[2]; i++) {
                out.append('-');
            }
            out.append(middle);
            for (int i = 0; i < fieldLengths[3] + fieldLengths[4] + fieldLengths[5]; i++) {
                out.append('-');
            }
            out.append(middle);
            for (int i = 0; i < fieldLengths[6] + fieldLengths[7] + fieldLengths[8]; i++) {
                out.append('-');
            }
            out.append(leftRight);
        } else {
            for (int i = line * 9; i < (line + 1) * 9; i++) {
                if ((i % 3) == 0) {
                    out.append("|");
                    if ((i % 9) != 8) {
                        out.append(' ');
                    }
                } else {
                    out.append(' ');
                }
                int tmp = fieldLengths[getCol(i)];
                out.append(cellBuffers[i]);
                tmp -= cellBuffers[i].length();
                for (int j = 0; j < tmp - 1; j++) {
                    out.append(' ');
                }
            }
            out.append('|');
        }
        out.append("\r\n");
    }

    public void resetCandidates() {
        for (SudokuCell cell : cells) {
            if (cell != null) {
                cell.delAllCandidates(SudokuCell.USER);
                cell.resetPlayCandidates();
            }
        }
    }

    /**
     * Setzen eines Kandidaten in hoDrawMode löscht alle Kandidaten in SudokuCell.PLAY des
     * entsprechenden Blocks/Zeile/Spalte, die keine Entsprechung in SudokuCell.USER,
     * SudokuCell.LINE oder SudokuCell.COL haben. Wird in USER gesetzt, muss zusätzlich noch
     * geprüft werden, ob zwei Kandidaten in der selben Zeile oder Spalte stehen, in diesem
     * Fall muss die gesamte Zeile bzw. Spalte gelöscht werden (inklusive eventueller
     * Zeilen-/Spalten-Kandidaten in anderen Blöcken - in PLAY löschen, werden dann rot angezeigt).
     *
     * Löschen eines Kandidaten ist komplizierter: Gibt es noch einen anderen Kandidaten
     * in Block, Zeile oder Spalte, bleibt er in PLAY gelöscht. War es der letzte Kandidat
     * in Block, Zeile oder Spalte, wird in allen Zellen der entsprechenden Einheit wieder
     * hinzugefügt. Dabei muss auf Sonderfälle geachtet werden: Wird beispielsweise aus Block
     * gelöscht, muss für jede Zelle noch auf Zeile oder Spalte geprüft werden usw.
     *
     * ACHTUNG: Ist mir doch zu kompliziert! USER, LINE und COL werden einfach gesetzt/gelöscht,
     * PLAY spiegelt immer ALL wieder (außer showCandidates ist gesetzt, dann wird nur mit PLAY gearbeitet).
     * Daraus folgt, dass PLAY immer der Zustand der Lösung ist, alle Solver müssen sich auf PLAY beziehen,
     * SolverSteps sind nur möglich, wenn showCandidates gesetzt ist.
     */
    public void delCandidate(int index, int type, int value) {
        setCandidate(index, type, value, false);
    }

    public void setCandidate(int index, int type, int value) {
        setCandidate(index, type, value, true);
    }

    public void setCandidate(int line, int col, int type, int value, boolean set) {
        setCandidate(getIndex(line, col), type, value, set);
    }

    public void setCandidate(int index, int type, int value, boolean set) {
        SudokuCell cell = cells[index];
        if (set) {
            cell.setCandidate(type, value);
            if (type != SudokuCell.PLAY) {
                cell.setCandidate(SudokuCell.PLAY, value);
            }
            allowedPositions[value].add(index);
        } else {
            cell.delCandidate(type, value);
            if (type != SudokuCell.PLAY) {
                cell.delCandidate(SudokuCell.PLAY, value);
            }
            allowedPositions[value].remove(index);
        }
    }

    public void setCell(int line, int col, int value) {
        setCell(getIndex(line, col), value);
    }

    public void setCell(int index, int value) {
        setCell(index, value, false);
    }

    public void setCell(int line, int col, int value, boolean isFixed) {
        setCell(getIndex(line, col), value, isFixed);
    }

    public void setCell(int index, int value, boolean isFixed) {
        setCell(index, value, isFixed, true);
    }

    public void setCell(int line, int col, int value, boolean isFixed, boolean adjustTemplates) {
        setCell(getIndex(line, col), value, isFixed, adjustTemplates);
    }

    /**
     * Setzt oder löscht einen Wert in einer Sudoku-Zelle. Die internen Kandidaten
     * werden automatisch angepasst:
     * <ul>
     *    <li>Beim Setzen werden alle Kandidaten in Block, Zeile und Spalte gelöscht</li>
     *    <li>Beim Löschen werden alle Kandidaten in Block, Zeile und Spalte gesetzt,
     *        bei denen es keinen Ausschließungsgrund gibt</li>
     * </ul>
     * @param index Index der Zelle (0 bis 80)
     * @param value Zu setzender Wert (0 für "löschen")
     * @param isFixed <code>true</code>, wenn der Wert ein Clue ist
     * @param adjustTemplates <code>true</code>, wenn die Listen mit möglichen Templates angepasst werden sollen
     *          (Anpassung entfällt für Clues)
     */
    public void setCell(int index, int value, boolean isFixed, boolean adjustTemplates) {
        SudokuCell cell = getCell(index);
        int oldValue = cell.getValue(); // brauchen wir beim Löschen
        cell.setValue(value, isFixed);
        if (value != 0) {
            // Zelle setzen -> Kandidaten löschen
            for (int i : BLOCKS[getBlock(index)]) {
                cells[i].delCandidate(value);
            }
            for (int i : LINES[getLine(index)]) {
                cells[i].delCandidate(value);
            }
            for (int i : COLS[getCol(index)]) {
                cells[i].delCandidate(value);
            }
            // in positions setzen
            positions[value].add(index);
            // in allowedPositions werden alle Buddies gestrichen, der index selbst
            // wird für alle Ziffern gestrichen (in possibleValues wird der index nur
            // für die aktuelle Zahl gestrichen)
            allowedPositions[value].andNot(buddies[index]);
            possiblePositions[value].andNot(buddies[index]);
            for (int i = 1; i <= 9; i++) {
                allowedPositions[i].remove(index);
            }
            possiblePositions[value].remove(index);
        } else {
            // zuerst die Bitmasken anpassen, sonst wird nichts gesetzt
            // in positions entfernen
            positions[oldValue].remove(index);
            // in allowedPositions ist das mühsam: zuerst wird possiblePositions neu berechnet;
            // alle positionen im Grid minus die Buddies aller gesetzten Zahlen minus die Zahlen selbst
            // allowedPositions[oldValue] zusätzliche minus alle überhaupt gesetzten Zellen
            // In allowedPositions[x] kann die aktuelle Zelle gesetzt werden, wenn sie in possiblePositions[x] gesetzt ist
            possiblePositions[oldValue].setAll();
            for (int i = 0; i < positions[oldValue].size(); i++) {
                possiblePositions[oldValue].andNot(buddies[positions[oldValue].get(i)]);
            //allowedPositions[oldValue].remove(positions[oldValue].get(i));
            }
            possiblePositions[oldValue].andNot(positions[oldValue]);
            // in allowedPositions sind alle gesetzten Positionen prinzipiell nicht erlaubt
            allowedPositions[oldValue].set(possiblePositions[oldValue]);
            for (int i = 1; i <= 9; i++) {
                allowedPositions[oldValue].andNot(positions[i]);
            }
            // der Index selbst ist wieder überall erlaubt, wo er prinzipiell möglich ist
            for (int i = 1; i <= 9; i++) {
                if (possiblePositions[i].contains(index)) {
                    allowedPositions[i].add(index);
                }
            }
            // Zelle löschen -> Kandidaten dazufügen, wenn es keinen Hinderungsgrund gibt
            for (int i : BLOCKS[getBlock(index)]) {
                setCandidate(i, oldValue);
            }
            for (int i : LINES[getLine(index)]) {
                setCandidate(i, oldValue);
            }
            for (int i : COLS[getCol(index)]) {
                setCandidate(i, oldValue);
            }
            // In der Zelle selbst sind jetzt möglicherweise andere Kandidaten dazugekommen
            for (int i = 1; i <= 9; i++) {
                setCandidate(index, i);
            }
        }
    }

    private void setCandidate(int index, int oldValue) {
        if (isValidValue(index, oldValue)) {
            cells[index].setCandidate(SudokuCell.ALL, oldValue);
            cells[index].setCandidate(SudokuCell.PLAY, oldValue);
            allowedPositions[oldValue].add(index);
        }
    }

    /**
     * Sucht die passende Zelle aus {@link cells} und gibt sie zurück.
     * @param line Zeile (0 bis 8)
     * @param col Spalte (0 bis 8)
     * @return Referenz auf die entsprechende Zelle
     */
    public SudokuCell getCell(int line, int col) {
        return cells[line * CPL + col];
    }

    public SudokuCell getCell(int index) {
        return cells[index];
    }

    public boolean isValidValue(int line, int col, int value) {
        return isValidValue(getIndex(line, col), value);
    }

    /**
     * Prüft, ob ein bestimmter Wert für einen bestimmten Index gültig ist (verwendet Bitmap).
     * @param index Index in {@link cells}
     * @param value Gewünschter Wert
     * @return <CODE>true</CODE>: Wert ist regelkonform.<br>
     * <CODE>false</CODE>: Wert ist nicht regelkomform.
     */
    public boolean isValidValue(int index, int value) {
        return allowedPositions[value].contains(index);
    }

    public boolean checkIsValidValue(int line, int col, int value) {
        return checkIsValidValue(getIndex(line, col), value);
    }

    /**
     * Prüft, ob ein bestimmter Wert für einen bestimmten Index gültig ist. Dazu wird einfach
     * geschaut, ob dieser Wert im Block, in der Zeile oder in der Spalte schon einmal vorkommt.
     * @param index Index in {@link cells}
     * @param value Gewünschter Wert
     * @return <CODE>true</CODE>: Wert ist regelkonform.<br>
     * <CODE>false</CODE>: Wert ist nicht regelkomform.
     */
    public boolean checkIsValidValue(int index, int value) {
        for (int i : BLOCKS[getBlock(index)]) {
            if (i != index && cells[i].getValue() == value) {
                return false;
            }
        }
        for (int i : LINES[getLine(index)]) {
            if (i != index && cells[i].getValue() == value) {
                return false;
            }
        }
        for (int i : COLS[getCol(index)]) {
            if (i != index && cells[i].getValue() == value) {
                return false;
            }
        }
        return true;
    }

    /**
     * Errechnet den Zeilenindex aus dem linearen Index.
     * @param index Index in {@link cells}
     * @return Zeilenindex der entsprechenden Zelle.
     */
    public static int getLine(int index) {
        return index / CPL;
    }

    /**
     * Errechnet den Spaltenindex aus dem linearen Index.
     * @param index Index in {@link cells}
     * @return Spaltenindex der entsprechenden Zelle.
     */
    public static int getCol(int index) {
        return index % CPL;
    }

    /**
     * Errechnet den Blockindex aus dem linearen Index.
     * @param index Index in {@link cells}
     * @return Blockindex des entsprechenden Blocks.
     */
    public static int getBlock(int index) {
        return BLOCK_FROM_INDEX[index];
    }

    public static int getIndex(int line, int col) {
        return line * 9 + col;
    }

    public boolean isSolved() {
        boolean solved = true;
        for (SudokuCell cell : cells) {
            if (cell.getValue() == 0) {
                solved = false;
                break;
            }
        }
        return solved;
    }

    public int anzFilledCells() {
        int anz = 0;
        for (SudokuCell cell : cells) {
            if (cell.getValue() != 0) {
                anz++;
            }
        }
        return anz;
    }

    public SudokuCell[] getCells() {
        return cells;
    }

    private static void initBuddies() {
        if (buddies[0] != null) {
            // ist schon initialisiert
            return;
        }

        // ein Template mit allen Buddies pro Zelle
        for (int i = 0; i < 81; i++) {
            buddies[i] = new SudokuSet();
            for (int j = 0; j < 81; j++) {
                if (i != j && (Sudoku.getLine(i) == Sudoku.getLine(j) ||
                        Sudoku.getCol(i) == Sudoku.getCol(j) ||
                        Sudoku.getBlock(i) == Sudoku.getBlock(j))) {
                    // Zelle ist Buddy
                    buddies[i].add(j);
                }
            }
        }

        // Ein Set für jedes Haus mit allen Zellen des Hauses
        for (int i = 0; i < lineTemplates.length; i++) {
            lineTemplates[i] = new SudokuSet();
            for (int j = 0; j < LINES[i].length; j++) {
                lineTemplates[i].add(LINES[i][j]);
            }
            colTemplates[i] = new SudokuSet();
            for (int j = 0; j < COLS[i].length; j++) {
                colTemplates[i].add(COLS[i][j]);
            }
            blockTemplates[i] = new SudokuSet();
            for (int j = 0; j < BLOCKS[i].length; j++) {
                blockTemplates[i].add(BLOCKS[i][j]);
            }
        }
    }

    /**
     * Optimization: For every group of 8 cells all possible buddies -> 11 * 256 combinations.
     * These group buddies are used by SudokuSetBase: might speed up the search for
     * all possible buddies of multiple units (mainly in fish and ALS search)
     */
    private static void initGroupedBuddies() {
        for (int i = 0; i < 11; i++) {
            initGroupForGroupedBuddies(i * 8, groupedBuddies[i]);
        }
    }

    /**
     * First compute all possible combinations of 8 cells starting with
     * groupOffset, then get a set with all budies for every combination.
     * 
     * @param groupOffset The first index in the group of 8 cells
     * @param groupArray The array that stores all possible buddy sets
     */
    private static void initGroupForGroupedBuddies(int groupOffset, SudokuSetBase[] groupArray) {
        SudokuSet groupSet = new SudokuSet();
        for (int i = 0; i < 256; i++) {
            groupSet.clear();
            int mask = 0x01;
            for (int j = 0; j < 8; j++) {
                //System.out.print("i: " + i + ", j: " + j + " (mask: " + Integer.toHexString(mask) + ") ");
                if ((i & mask) != 0 && (groupOffset + j + 1) <= 81) {
                    groupSet.add(groupOffset + j);
                    //System.out.print("  ADD: " + (groupOffset + j + 1));
                }
                //System.out.println();
                mask <<= 1;
            }
            SudokuSetBase buddiesSet = new SudokuSetBase(true);
            for (int j = 0; j < groupSet.size(); j++) {
                buddiesSet.and(buddies[groupSet.get(j)]);
            }
            groupArray[i] = buddiesSet;
            //System.out.println("Grouped Buddies " + groupOffset + "/" + i + ": " + groupSet + ", " + buddiesSet);
        }
    }

    /**
     * Calculates all common buddies of all cells in <code>cells</code> and
     * returns them in <code>buddies</code>. groupedBuddies is used
     * for calculations.
     * 
     * @param cells The cells for which the buddies should be calculated
     * @param buddiesOut The resulting buddies
     */
    public static void getBuddies(SudokuSetBase cells, SudokuSetBase buddiesOut) {
        buddiesOut.setAll();
        if (cells.mask1 != 0) {
            for (int i = 0, j = 0; i < 8; i++, j += 8) {
                int mIndex = (int) ((cells.mask1 >> j) & 0xFF);
                buddiesOut.and(groupedBuddies[i][mIndex]);
            }
        }
        if (cells.mask2 != 0) {
            for (int i = 8, j = 0; i < 11; i++, j += 8) {
                int mIndex = (int) ((cells.mask2 >> j) & 0xFF);
                buddiesOut.and(groupedBuddies[i][mIndex]);
            }
        }
    }
    
    /**
     * Calculates all common buddies of all cells in <code>cells</code> and
     * returns them in <code>buddies</code>. Calculations are done the
     * traditional way.
     * 
     * @param cells The cells for which the buddies should be calculated
     * @param buddiesOut The resulting buddies
     */
    public static void getBuddiesWG(SudokuSet cells, SudokuSetBase buddiesOut) {
        buddiesOut.setAll();
        for (int i = 0; i < cells.size(); i++) {
            buddiesOut.and(buddies[cells.get(i)]);
        }
    }
    
    private static void initTemplates() {
        // alle 46656 möglichen Templates anlegen
        try {
            //System.out.println("Start Templates lesen...");
            long ticks = System.currentTimeMillis();
            ObjectInputStream in = new ObjectInputStream(Sudoku.class.getResourceAsStream("/templates.dat"));
            templates = (SudokuSetBase[])in.readObject();
            in.close();
            ticks = System.currentTimeMillis() - ticks;
            //System.out.println("Templates lesen: " + ticks + "ms");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
//        //Sudoku sudoku = new Sudoku(false);
//        Sudoku sudoku = new Sudoku();
//        SudokuSetBase set = new SudokuSetBase();
//        initTemplatesRecursive(sudoku, 0, 0, 1, set);
//        try {
////            PrintWriter out = new PrintWriter(new FileWriter("templates.txt"));
////            for (int i = 0; i < templates.length; i++) {
////                if ((i % 2) == 0) {
////                    out.print("        new SudokuSetBase(" + templates[i].mask1 + "L, " + templates[i].mask2 + "L),");
////                } else {
////                    out.println(" new SudokuSetBase(" + templates[i].mask1 + "L, " + templates[i].mask2 + "L),");
////                }
////            }
////            out.close();
//            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("templates.dat"));
//            out.writeObject(templates);
//            out.close();
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }

        // jetzt noch die Templates für die Häuser
        for (int i = 0; i < LINES.length; i++) {
            for (int j = 0; j < LINES[i].length; j++) {
                lineTemplates[i].add(LINES[i][j]);
                colTemplates[i].add(COLS[i][j]);
                blockTemplates[i].add(BLOCKS[i][j]);
            }
        }
    }

    private static int initTemplatesRecursive(Sudoku sudoku, int line, int index, int cand, SudokuSetBase set) {
        if (line >= Sudoku.LINES.length) {
            templates[index++] = set.clone();
            return index;
        }
        for (int i = 0; i < Sudoku.LINES[line].length; i++) {
            int lIndex = Sudoku.LINES[line][i];
            SudokuCell cell = sudoku.getCell(lIndex);
            if (cell.getValue() != 0 || !cell.isCandidate(SudokuCell.ALL, cand)) {
                // Zelle ist schon belegt (darf eigentlich nicht sein) oder der Kandidat ist hier nicht mehr möglich
                continue;
            }
            // Kandidat in dieser Zeile setzen
            sudoku.setCell(lIndex, cand);
            set.add(lIndex);
            // weiter in nächste Zeile
            index = initTemplatesRecursive(sudoku, line + 1, index, cand, set);
            // und Zelle wieder löschen
            sudoku.setCell(lIndex, 0);
            set.remove(lIndex);
        }
        return index;
    }

    public int getAnzFilled() {
        int sum = 0;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i].getValue() != 0) {
                sum++;
            }
        }
        return sum;
    }

    public void setNoClues() {
        for (int i = 0; i < cells.length; i++) {
            cells[i].setIsFixed(false);
        }
    }

    public static void main(String[] args) {
//        long ticks = System.currentTimeMillis();
//        //Sudoku newSudoku = new Sudoku(true);
//        Sudoku newSudoku = new Sudoku();
//        ticks = System.currentTimeMillis() - ticks;
//        System.out.println("Templates angelegt: " + ticks + "ms");
//        for (int i = 0; i < templates.length; i += 10000) {
//            if (templates[i] != null) {
//                System.out.println(templates[i].toString());
//            }
//        }
//
//        ticks = System.currentTimeMillis();
//        List<SudokuSetBase> testList = new LinkedList<SudokuSetBase>();
//        for (int i = 0; i < templates.length; i++) {
//            if (templates[i] != null) {
//                testList.add(templates[i]);
//            }
//        }
//        ticks = System.currentTimeMillis() - ticks;
//        System.out.println("Liste angelegt: " + ticks + "ms");
//        System.out.println("size: " + testList.size());
        // test grouped buddies
        SudokuSet cells = new SudokuSet();
        BigInteger count = new BigInteger("0");
        BigInteger zero = new BigInteger("0");
        BigInteger one = new BigInteger("1");
        BigInteger mill = new BigInteger("1000000");
        SudokuSet buddies1 = new SudokuSet();
        SudokuSetBase buddies2 = new SudokuSetBase();
        boolean b1 = true;
        boolean b2 = true;
        long ticks = System.currentTimeMillis();
        for (long m1 = 0xfff45678fffl; m1 <= Long.MAX_VALUE; m1++) {
            for (long m2 = 0; m2 <= 0x3ffffl; m2++) {
                cells.clear();
                long mask = 0x1;
                for (int i = 0; i < 63; i++) {
                    if ((m1 & mask) != 0) {
                        cells.add(i);
                    }
                    mask <<= 1;
                }
                mask = 0x1;
                for (int i = 0; i < 18; i++) {
                    if ((m2 & mask) != 0) {
                        cells.add(i + 63);
                    }
                    mask <<= 1;
                }
                
                if (b1) {
                    getBuddiesWG(cells, buddies1);
                }
                if (b2) {
                    getBuddies(cells, buddies2);
                }
                if (b1 && b2) {
                    if (!buddies1.equals(buddies2)) {
                        System.out.println("Error! cells: " + cells + ", buddies1: " + buddies1 + ", buddies2: " + buddies2);
                        return;
//                    } else {
//                        System.out.println("Ok! cells: " + cells + ", buddies1: " + buddies1 + ", buddies2: " + buddies2);
                    }
                }

                count = count.add(one);
                if (count.mod(mill).equals(zero)) {
                    double t2 = System.currentTimeMillis() - ticks;
                    double d = t2 / (count.doubleValue() / mill.doubleValue());
                    System.out.println(count + ": " + ((long)d) + "ms per " + mill + " buddies");
                }
            }
        }
        System.out.println("Grouped buddies: " + count + " combinations!");
    }

    public DifficultyLevel getLevel() {
        return level;
    }

    public void setLevel(DifficultyLevel level) {
        this.level = level;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public SudokuSetBase[] getPossiblePositions() {
        return possiblePositions;
    }

    public void setPossiblePositions(SudokuSetBase[] possiblePositions) {
        this.possiblePositions = possiblePositions;
    }

    public SudokuSetBase[] getAllowedPositions() {
        return allowedPositions;
    }

    public void setAllowedPositions(SudokuSetBase[] allowedPositions) {
        this.allowedPositions = allowedPositions;
    }

    public SudokuSet[] getPositions() {
        return positions;
    }

    public void setPositions(SudokuSet[] positions) {
        this.positions = positions;
    }

    public void setCells(SudokuCell[] cells) {
        this.cells = cells;
    }

    public String getInitialState() {
        return initialState;
    }

    public void setInitialState(String initialState) {
        this.initialState = initialState;
    }

    public int getUnsolvedCandidatesAnz() {
        int anz = 0;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i].getValue() == 0) {
                anz += cells[i].getAnzCandidates(SudokuCell.PLAY);
            }
        }
        return anz;
    }

    public int getUnsolvedCellsAnz() {
        int anz = 0;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i].getValue() == 0) {
                anz++;
            }
        }
        return anz;
    }

    public void printAllowedPositions(String txt) {
        System.out.println(txt + ":");
        for (int i = 1; i < allowedPositions.length; i++) {
            System.out.println("   allowedPositions[" + i + "]: " + allowedPositions[i]);
        }
    }
}
