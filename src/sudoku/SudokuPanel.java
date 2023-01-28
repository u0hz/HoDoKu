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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.Stack;
import java.util.TreeMap;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import javax.swing.JOptionPane;
import org.w3c.dom.Node;

/**
 *
 * @author  Bernhard Hobiger
 */
public class SudokuPanel extends javax.swing.JPanel implements Printable {
    // Konstante
    private static final int DELTA = 5; // Abstand zwischen den Quadraten in Pixel
    private static final int DELTA_RAND = 5; // Abstand zu den Rändern
    // Konfigurationseigenschaften
    private boolean showCandidates = Options.getInstance().isShowCandidates(); // Alle möglichen Kandidaten anzeigen
    private int candidateMode = SudokuCell.USER; // welche Kandidaten sollen gesetzt werden
    private boolean showWrongValues = Options.getInstance().isShowWrongValues();    // falsche Werte mit anderer Farbe
    private boolean showDeviations = Options.getInstance().isShowDeviations();  // Werte und Kandidaten, die von der Lösung abweichen
    private boolean invalidCells = true; // true: ungültige Zellen, false: mögliche Zellen
    private boolean showInvalidOrPossibleCells = false;  // Ungültige/Mögliche Zellen für showHintCellValue mit anderem Hintergrund
    private int showHintCellValue = 0;
    private boolean showAllCandidatesAkt = false; // bei alle Kandidaten anzeigen (nur aktive Zelle)
    private boolean showAllCandidates = false; // bei alle Kandidaten anzeigen (alle Zellen)
    private int delta = DELTA; // Zwischenraum zwischen Blöcken
    private int deltaRand = DELTA_RAND; // Zwischenraum zu den Rändern
    private Font valueFont;    // Font für die Zellenwerte
    private Font candidateFont; // Font für die Kandidaten
    // interne Variable
    private Sudoku sudoku; // Daten für das Sudoku
    private Sudoku solvedSudoku; // Lösung für Anzeige von Fehlern
    private SudokuSolver solver; // Lösung für das Sudoku
    private SudokuCreator creator; // Lösung mit BruteForce (Dancing Links)
    private MainFrame mainFrame; // für Oberfläche
    private SolutionStep step;   // für Anzeige der Hinweise
    private int chainIndex = -1; // if != -1, only the chain with the right index is shown
    private List<Integer> alsToShow = new ArrayList<Integer>(); // if chainIndex is != -1, alsToShow contains the indices of the ALS, that are part of the chain
    private int oldWidth; // Breite des Panels, als das letzte Mal Fonts erzeugt wurden
    private int width;    // Breite des Panels, auf Quadrat normiert
    private int height;   // Höhe des Panels, auf Quadrat normiert
    private int cellSize; // Kantenlänge einer Zelle
    private int startSX;  // x-Koordinate des linken oberen Punktes des Sudoku
    private int startSY;  // y-Koordinate des linken oberen Punktes des Sudoku
    private Graphics2D g2; // zum Zeichnen, spart eine Menge Parameter
    private CubicCurve2D.Double cubicCurve = new CubicCurve2D.Double(); // für Chain-Pfeile
    private Polygon arrow = new Polygon(); // Pfeilspitze
    private Stroke arrowStroke = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); // Pfeilspitzen abrunden
    private Stroke strongLinkStroke = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); // strong links durchziehen
    private Stroke weakLinkStroke = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            10.0f, new float[]{5.0f}, 0.0f); // weak links punkten
    private List<Point> points = new ArrayList<Point>(200);
    private double arrowLengthFactor = 1.0 / 6.0;
    private double arrowHeightFactor = 1.0 / 3.0;
    private int aktLine = 4; // aktuell markiertes Feld (Zeile)
    private int aktCol = 4;  // aktuell markiertes Feld (Spalte)
    private int aktNumber = 0; // zu setzende Nummer bei Mausklick (0: nichts setzen oder löschen)

    // Undo/Redo
    private Stack<Sudoku> undoStack = new Stack<Sudoku>();
    private Stack<Sudoku> redoStack = new Stack<Sudoku>();
    private SortedMap<Integer, Integer> coloringMap = new TreeMap<Integer, Integer>(); // coloring: contains cell index + index in coloringColors[]

    /** Creates new form SudokuPanel */
    public SudokuPanel(MainFrame mf) {
        mainFrame = mf;
        sudoku = new Sudoku();
        sudoku.resetCandidates();
        setShowCandidates(Options.getInstance().isShowCandidates());
        creator = new SudokuCreator();
        solver = SudokuSolver.getInstance();
        solver.setSudoku(sudoku.clone());
        solver.solve();

        initComponents();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {

        setBackground(new java.awt.Color(255, 255, 255));
        setMinimumSize(new java.awt.Dimension(300, 300));
        setPreferredSize(new java.awt.Dimension(600, 600));
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                formMouseClicked(evt);
            }
        });
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
            public void keyReleased(java.awt.event.KeyEvent evt) {
                formKeyReleased(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 600, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 600, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void formKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyReleased
        handleKeysReleased(evt);
        mainFrame.fixFocus();
    }//GEN-LAST:event_formKeyReleased

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        int keyCode = evt.getKeyCode();
        switch (keyCode) {
            case KeyEvent.VK_ESCAPE:
                mainFrame.setStatusLabel(0);
                break;
            default:
                handleKeys(evt);
        }
        mainFrame.fixFocus();
    }//GEN-LAST:event_formKeyPressed

    private void formMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseClicked
        // undo/Redo siehe handleKeys()
        boolean changed = false;
        undoStack.push(sudoku.clone());

        int line = getLine(evt.getPoint());
        int col = getCol(evt.getPoint());
        boolean ctrlPressed = (evt.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0;
        if (line >= 0 && line <= 8 && col >= 0 && col <= 8) {
            aktLine = line;
            aktCol = col;
            if (aktNumber != 0) {
                // Zelle setzen, wenn sie nicht fix ist; wenn die Zelle bereits
                // einen Wert enthält, muss dieser erst gelöscht werden, damit
                // die Kandidaten richtig angepasst werden
                // linke Maustaste setzt, rechte löscht
                SudokuCell cell = sudoku.getCell(line, col);
                if (evt.getButton() == MouseEvent.BUTTON1) {
                    // setzen
                    if (!ctrlPressed) {
                        // Zelle setzen
                        setCell(line, col, aktNumber);
                        changed = true;
                    } else {
                        // Kandidaten setzen: nur wenn kein Wert gesetzt ist
                        if (cell.getValue() == 0) {
                            sudoku.setCandidate(line, col, candidateMode, aktNumber, true);
                            changed = true;
                        }
                    }
                } else if (evt.getButton() == MouseEvent.BUTTON3) {
                    if (!ctrlPressed) {
                        if (cell.getValue() != 0) {
                            sudoku.setCell(line, col, 0);
                            changed = true;
                        }
                    } else {
                        // Kandidaten löschen: nur wenn kein Wert gesetzt ist
                        if (cell.getValue() == 0) {
                            sudoku.setCandidate(line, col, candidateMode, aktNumber, false);
                            changed = true;
                        }
                    }
                }
            }
            if (changed) {
                redoStack.clear();
            } else {
                undoStack.pop();
            }
            mainFrame.check();
            repaint();
        }
    }//GEN-LAST:event_formMouseClicked

    public void loadFromFile(Sudoku sudoku, Sudoku solvedSudoku) {
        this.sudoku = sudoku;
        this.solvedSudoku = solvedSudoku;
        redoStack.clear();
        undoStack.clear();
        coloringMap.clear();
        step = null;
        setChainInStep(-1);
        mainFrame.check();
        repaint();
    }

    private void checkShowAllCandidates(int modifiers, int keyCode) {
        // wenn <Shift> und <Ctrl> gedrückt sind, soll showAllCandidatesAkt true sein, sonst false
        boolean oldShowAllCandidatesAkt = showAllCandidatesAkt;
        showAllCandidatesAkt = false;
        if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 && (modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
            showAllCandidatesAkt = true;
        }
        // wenn <Shift> und <Alt> gedrückt sind, soll showAllCandidates true sein, sonst false
        boolean oldShowAllCandidates = showAllCandidates;
        showAllCandidates = false;
        if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 && (modifiers & KeyEvent.ALT_DOWN_MASK) != 0) {
            showAllCandidates = true;
        }
        if (oldShowAllCandidatesAkt != showAllCandidatesAkt || oldShowAllCandidates != showAllCandidates) {
            repaint();
        }
    }

    public void handleKeysReleased(KeyEvent evt) {
        // wenn <Left-Shift> und <Left-Ctrl> gedrückt sind, soll showAllCandidatesAkt true sein, sonst false
        int modifiers = evt.getModifiersEx();
        int keyCode = 0; // getKeyCode() liefert immer noch die zuletzt gedrückte Taste

        checkShowAllCandidates(modifiers, keyCode);
    }

    public void handleKeys(KeyEvent evt) {
        // Undo/Redo: alten Zustand speichern, wenn nichts geändert wurde, wieder entfernen
        boolean changed = false;
        undoStack.push(sudoku.clone());

        int keyCode = evt.getKeyCode();
        int modifiers = evt.getModifiersEx();

        // wenn <Shift> und <Ctrl> gedrückt sind, soll showAllCandidatesAkt true sein, sonst false
        // wenn <Shift> und <Alt> gedrückt sind, soll showAllCandidates true sein, sonst false
        checkShowAllCandidates(modifiers, keyCode);

        // "normale" Tastaturbehandlung
        SudokuCell cell = sudoku.getCell(aktLine, aktCol);
        // bei keyPressed funktioniert getKeyChar() nicht zuverläsig, daher die Zahl selbst ermitteln
        int number = 0;
        switch (keyCode) {
            case KeyEvent.VK_DOWN:
                if (aktLine < 8) {
                    aktLine++;
                }
                break;
            case KeyEvent.VK_UP:
                if (aktLine > 0) {
                    aktLine--;
                }
                break;
            case KeyEvent.VK_RIGHT:
                if (aktCol < 8) {
                    aktCol++;
                }
                break;
            case KeyEvent.VK_LEFT:
                if (aktCol > 0) {
                    aktCol--;
                }
                break;
            case KeyEvent.VK_HOME:
                aktCol = 0;
                break;
            case KeyEvent.VK_END:
                aktCol = 8;
                break;
            case KeyEvent.VK_9:
                number++;
            case KeyEvent.VK_8:
                number++;
            case KeyEvent.VK_7:
                number++;
            case KeyEvent.VK_6:
                number++;
            case KeyEvent.VK_5:
                number++;
            case KeyEvent.VK_4:
                number++;
            case KeyEvent.VK_3:
                number++;
            case KeyEvent.VK_2:
                number++;
            case KeyEvent.VK_1:
                number++;
                //int number = Character.digit(evt.getKeyChar(), 10);
                if ((modifiers & KeyEvent.CTRL_DOWN_MASK) == 0) {
                    // Zelle setzen
                    setCell(aktLine, aktCol, number);
                    changed = true;
                } else {
                    if (cell.getValue() == 0) {
                        if (cell.isCandidate(candidateMode, number)) {
                            sudoku.setCandidate(aktLine, aktCol, candidateMode, number, false);
                            changed = true;
                        } else {
                            sudoku.setCandidate(aktLine, aktCol, candidateMode, number, true);
                            changed = true;
                        }
                    }
                }
                break;
            case KeyEvent.VK_BACK_SPACE:
            case KeyEvent.VK_DELETE:
            case KeyEvent.VK_0:
                if ((modifiers & KeyEvent.CTRL_DOWN_MASK) == 0) {
                    // Zelle löschen
                    if (cell.getValue() != 0 && !cell.isFixed()) {
                        sudoku.setCell(aktLine, aktCol, 0);
                        changed = true;
                    }
                }
                break;
            case KeyEvent.VK_F9:
                number++;
            case KeyEvent.VK_F8:
                number++;
            case KeyEvent.VK_F7:
                number++;
            case KeyEvent.VK_F6:
                number++;
            case KeyEvent.VK_F5:
                number++;
            case KeyEvent.VK_F4:
                number++;
            case KeyEvent.VK_F3:
                number++;
            case KeyEvent.VK_F2:
                number++;
            case KeyEvent.VK_F1:
                number++;
                if (getShowHintCellValue() == number && isShowInvalidOrPossibleCells()) {
                    if ((modifiers & KeyEvent.CTRL_DOWN_MASK) == 0) {
                        setShowHintCellValue(0);
                        setShowInvalidOrPossibleCells(false);
                    } else {
                        invalidCells = !invalidCells;
                    }
                } else {
                    setShowHintCellValue(number);
                    setShowInvalidOrPossibleCells(true);
                    if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
                        invalidCells = !invalidCells;
                    }
                }
                break;
            case KeyEvent.VK_SPACE:
                if (isShowInvalidOrPossibleCells()) {
                    int candidate = getShowHintCellValue();
                    if (cell.getValue() == 0) {
                        if (cell.isCandidate(candidateMode, candidate)) {
                            sudoku.setCandidate(aktLine, aktCol, candidateMode, candidate, false);
                            changed = true;
                        } else {
                            sudoku.setCandidate(aktLine, aktCol, candidateMode, candidate, true);
                            changed = true;
                        }
                    }
                }
                break;
            case KeyEvent.VK_GREATER:
            case KeyEvent.VK_LESS:
                boolean isUp = evt.getKeyChar() == '>';
                if (isShowInvalidOrPossibleCells()) {
                    if (isUp) {
                        showHintCellValue++;
                        if (showHintCellValue > 9) {
                            showHintCellValue = 1;
                        }
                    } else {
                        showHintCellValue--;
                        if (showHintCellValue < 1) {
                            showHintCellValue = 9;
                        }
                    }
                }
                break;
            case KeyEvent.VK_E:
                number++;
            case KeyEvent.VK_D:
                number++;
            case KeyEvent.VK_C:
                number++;
            case KeyEvent.VK_B:
                number++;
            case KeyEvent.VK_A:
                // if ctrl or alt or meta is pressed, it's a shortcut
                if ((modifiers & KeyEvent.ALT_DOWN_MASK) != 0 ||
                        (modifiers & KeyEvent.CTRL_DOWN_MASK) != 0 ||
                        (modifiers & KeyEvent.META_DOWN_MASK) != 0) {
                    // do nothing!
                    break;
                }
                // calculate index in coloringColors[]
                number *= 2;
                if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
                    number++;
                }
                int index = Sudoku.getIndex(aktLine, aktCol);
                if (coloringMap.containsKey(index) && coloringMap.get(index) == number) {
                    // pressing the same key on the same cell twice removes the coloring
                    coloringMap.remove(index);
                } else {
                    // either newly colored cell or change of cell color
                    coloringMap.put(index, number);
                }
                break;
            case KeyEvent.VK_R:
                coloringMap.clear();
                break;
        }
        if (changed) {
            // Undo wurde schon behandelt, Redo ist nicht mehr möglich
            redoStack.clear();
        } else {
            // kein Undo nötig -> wieder entfernen
            undoStack.pop();
        }
        mainFrame.check();
        repaint();
    }

    private void setCell(int line, int col, int number) {
        SudokuCell cell = sudoku.getCell(line, col);
        if (!cell.isFixed() && cell.getValue() != number) {
            // Setzen ist möglich, auf löschen prüfen
            if (cell.getValue() != 0) {
                sudoku.setCell(line, col, 0);
            }
            sudoku.setCell(line, col, number);
        }
    }

    /**
     * Speichert die aktuelle Ansicht des Baums in eine PNG-Datei. Dabei wird
     * die Auflösung des Bildes auf 300dpi erhöht. Auf Systemen mit 96dpi
     * sollte die entstehende PNG-Datei exakt gleich groß dargestellt werden
     * wie der Panel am Bildschirm.
     * @param fileName Pfad und Name der neuen Bilddatei.
     */
    public void saveSudokuAsPNG(File file, int size, int dpi) {
        BufferedImage fileImage = new BufferedImage(size, size, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = fileImage.createGraphics();
        this.g2 = g;
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, size, size);
        drawPage(size, size, true, false);
        writePNG(fileImage, dpi, file);
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        if (pageIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        }

        // Graphics2D-Objekt herrichten
        Graphics2D printG2 = (Graphics2D) graphics;
        printG2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
        int printWidth = (int) pageFormat.getImageableWidth();
        int printHeight = (int) pageFormat.getImageableHeight();

        // Überschrift
        printG2.setFont(Options.getInstance().getBigFont());
        String title = MainFrame.VERSION;
        FontMetrics metrics = printG2.getFontMetrics();
        int textWidth = metrics.stringWidth(title);
        int textHeight = metrics.getHeight();
        int y = 2 * textHeight;
        printG2.drawString(title, (printWidth - textWidth) / 2, textHeight);

        // Level
        printG2.setFont(Options.getInstance().getSmallFont());
        if (sudoku != null && sudoku.getLevel() != null) {
            title = sudoku.getLevel().getName() + " (" + sudoku.getScore() + ")";
            metrics = printG2.getFontMetrics();
            textWidth = metrics.stringWidth(title);
            textHeight = metrics.getHeight();
            printG2.drawString(title, (printWidth - textWidth) / 2, y);
            y += textHeight;
        }

        printG2.translate(0, y);
        this.g2 = printG2;
        drawPage(printWidth, printHeight, true);
        return Printable.PAGE_EXISTS;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g2 = (Graphics2D) g;
        drawPage(getBounds().width, getBounds().height);
    }

    private void drawPage(int totalWidth, int totalHeight) {
        drawPage(totalWidth, totalHeight, false, true);
    }

    private void drawPage(int totalWidth, int totalHeight, boolean isPrint) {
        drawPage(totalWidth, totalHeight, isPrint, true);
    }

    private void drawPage(int totalWidth, int totalHeight, boolean isPrint, boolean mitRand) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Größe bestimmen und quadratisch machen
        width = totalWidth;
        height = totalHeight;
        width = (height < width) ? height : width;
        height = (width < height) ? width : height;

        // Zwischenräume nicht mehr konstant
        delta = DELTA;
        deltaRand = DELTA_RAND;

        if (Options.getInstance().getDrawMode() == 1) {
            delta = 0;
        }

        // Größe der einzelnen Zellen bestimmen und Maße anpassen (Rundungsfehler!)
        if (mitRand) {
            cellSize = (width - 4 * delta - 2 * deltaRand) / 9;
        } else {
            cellSize = (width - 4 * delta) / 9;
        }
        width = height = cellSize * 9 + 4 * delta;
        startSX = (totalWidth - width) / 2;
        if (isPrint && mitRand) {
            startSY = 0;
        } else {
            startSY = (totalHeight - height) / 2;
        }

        // Fonts festlegen
        // ACHTUNG: Bei jeder Änderung Fonts neu setzen!
        Font tmpFont = Options.getInstance().getDefaultValueFont();
        if (valueFont != null) {
            if (!valueFont.getName().equals(tmpFont.getName()) ||
                    valueFont.getStyle() != tmpFont.getStyle() ||
                    valueFont.getSize() != ((int) (cellSize * Options.getInstance().getValueFontFactor()))) {
                valueFont = new Font(tmpFont.getName(), tmpFont.getStyle(),
                        (int) (cellSize * Options.getInstance().getValueFontFactor()));
            }
        }
        tmpFont = Options.getInstance().getDefaultCandidateFont();
        if (candidateFont != null) {
            if (!candidateFont.getName().equals(tmpFont.getName()) ||
                    candidateFont.getStyle() != tmpFont.getStyle() ||
                    candidateFont.getSize() != ((int) (cellSize * Options.getInstance().getCandidateFontFactor()))) {
                candidateFont = new Font(tmpFont.getName(), tmpFont.getStyle(),
                        (int) (cellSize * Options.getInstance().getCandidateFontFactor()));
            }
        }
        if (oldWidth != width) {
            oldWidth = width;
            valueFont = new Font(Options.getInstance().getDefaultValueFont().getName(),
                    Options.getInstance().getDefaultValueFont().getStyle(),
                    (int) (cellSize * Options.getInstance().getValueFontFactor()));
            candidateFont = new Font(Options.getInstance().getDefaultCandidateFont().getName(),
                    Options.getInstance().getDefaultCandidateFont().getStyle(),
                    (int) (cellSize * Options.getInstance().getCandidateFontFactor()));
        }

        // Zellen zeichnen
        int dx = 0, dy = 0, dcx = 0, dcy = 0, ddx = 0, ddy = 0;
        for (int line = 0; line < 9; line++) {
            for (int col = 0; col < 9; col++) {
                // Zelle holen
                SudokuCell cell = sudoku.getCell(line, col);

                // Hintergrund zeichnen
                g2.setColor(Options.getInstance().getDefaultCellColor()); // normal ist weiß

                int cellIndex = Sudoku.getIndex(line, col);
                if (line == aktLine && col == aktCol && !isPrint) {
                    g2.setColor(Options.getInstance().getAktCellColor());
                }
                if (isShowInvalidOrPossibleCells() && isInvalidCells() &&
                        (cell.getValue() != 0 || (getShowHintCellValue() != 0 && !cell.isCandidateValid(SudokuCell.PLAY, getShowHintCellValue())))) {
                    g2.setColor(Options.getInstance().getInvalidCellColor());
                }
                if (isShowInvalidOrPossibleCells() && !isInvalidCells() && cell.getValue() == 0 &&
                        getShowHintCellValue() != 0 && cell.isCandidateValid(SudokuCell.PLAY, getShowHintCellValue())) {
                    g2.setColor(Options.getInstance().getPossibleCellColor());
                }
                if (cell.getValue() == 0 && coloringMap.containsKey(cellIndex)) {
                    // coloring
                    g2.setColor(Options.getInstance().getColoringColors()[coloringMap.get(cellIndex)]);
                }
                g2.fillRect(getX(line, col), getY(line, col), cellSize, cellSize);
                if (line == aktLine && col == aktCol && !isPrint && g2.getColor() != Options.getInstance().getAktCellColor()) {
                    g2.setColor(Options.getInstance().getAktCellColor());
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                    g2.fillRect(getX(line, col), getY(line, col), cellSize, cellSize);
                    g2.setPaintMode();
                }


                // Wert zeichnen
                int startX = getX(line, col);
                int startY = getY(line, col);
                if (cell.getValue() != 0) {
                    // Wert vorhanden: zeichnen
                    g2.setColor(Options.getInstance().getCellValueColor());
                    if (cell.isFixed()) {
                        g2.setColor(Options.getInstance().getCellFixedValueColor());
                    } else if (isShowWrongValues() == true && !sudoku.checkIsValidValue(line, col, cell.getValue())) {
                        g2.setColor(Options.getInstance().getWrongValueColor());
                    } else if (isShowDeviations() && solvedSudoku != null && cell.getValue() != solvedSudoku.getCell(line, col).getValue()) {
                        g2.setColor(Options.getInstance().getDeviationColor());
                    }
                    g2.setFont(valueFont);
                    dx = (cellSize - g2.getFontMetrics().stringWidth("8")) / 2;
                    dy = (cellSize + g2.getFontMetrics().getAscent()) / 2;
                    g2.drawString(Integer.toString(cell.getValue()), startX + dx, startY + dy);
                } else {
                    g2.setFont(candidateFont);
                    // alle vorhandenen Kandidaten gleichmäßig über die Zelle verteilen
                    // wird auch aufgerufen, wenn hoDrawMode gesetzt ist und <Shift>+<Ctrl> gedrückt
                    // ist; muss in diesem Fall alle Kandidaten anzeigen
                    int type = candidateMode;
                    if (showAllCandidates || showAllCandidatesAkt && line == aktLine && col == aktCol) {
                        type = SudokuCell.ALL;
                    }
                    if (showCandidates) {
                        type = SudokuCell.PLAY;
                    }
                    int third = cellSize / 3;
                    dcx = (third - g2.getFontMetrics().stringWidth("8")) / 2;
                    dcy = (third + g2.getFontMetrics().getAscent()) / 2;
                    ddx = (int) (g2.getFontMetrics().stringWidth("8") * Options.getInstance().getHintBackFactor());
                    ddy = (int) (g2.getFontMetrics().getAscent() * Options.getInstance().getHintBackFactor());
                    for (int i = 1; i <= 9; i++) {
                        if (cell.isCandidate(type, i) ||
                                (isShowCandidates() && isShowDeviations() && solvedSudoku != null && i == solvedSudoku.getCell(line, col).getValue())) {
                            g2.setColor(Options.getInstance().getCandidateColor());
                            if (isShowWrongValues() == true && !cell.isCandidateValid(type, i)) {
                                g2.setColor(Options.getInstance().getWrongValueColor());
                            }
                            if (!cell.isCandidate(type, i) && isShowDeviations() && solvedSudoku != null &&
                                    i == solvedSudoku.getCell(line, col).getValue()) {
                                g2.setColor(Options.getInstance().getDeviationColor());
                            }
                            int shiftX = ((i - 1) % 3) * third;
                            int shiftY = ((i - 1) / 3) * third;
                            Color hintColor = null;
                            Color candColor = null;
                            if (step != null) {
                                int index = Sudoku.getIndex(line, col);
                                if (step.getIndices().indexOf(index) >= 0 && step.getValues().indexOf(i) >= 0) {
                                    hintColor = Options.getInstance().getHintCandidateBackColor();
                                    candColor = Options.getInstance().getHintCandidateColor();
                                }
                                int alsIndex = step.getAlsIndex(index);
                                if (alsIndex != -1 && (chainIndex == -1 || alsToShow.contains(alsIndex))) {
                                    hintColor = Options.getInstance().getHintCandidateAlsBackColors()[alsIndex % Options.getInstance().getHintCandidateAlsBackColors().length];
                                    candColor = Options.getInstance().getHintCandidateAlsColors()[alsIndex % Options.getInstance().getHintCandidateAlsColors().length];
                                }
                                for (int k = 0; k < step.getChains().size(); k++) {
                                    if (chainIndex != -1 && k != chainIndex) {
                                        // show only one chain in Forcing Chains/Nets
                                        continue;
                                    }
                                    Chain chain = step.getChains().get(k);
                                    for (int j = chain.start; j <= chain.end; j++) {
                                        if (chain.chain[j] == Integer.MIN_VALUE) {
                                            // Trennmarker für mins -> ignorieren
                                            continue;
                                        }
                                        int chainEntry = Math.abs(chain.chain[j]);
                                        int index1 = -1, index2 = -1, index3 = -1;
                                        if (Chain.getSNodeType(chainEntry) == Chain.NORMAL_NODE) {
                                            index1 = Chain.getSCellIndex(chainEntry);
                                        }
                                        if (Chain.getSNodeType(chainEntry) == Chain.GROUP_NODE) {
                                            index1 = Chain.getSCellIndex(chainEntry);
                                            index2 = Chain.getSCellIndex2(chainEntry);
                                            index3 = Chain.getSCellIndex3(chainEntry);
                                        }
                                        if ((index == index1 || index == index2 || index == index3) && Chain.getSCandidate(chainEntry) == i) {
                                            if (Chain.isSStrong(chainEntry)) {
                                                // strong link
                                                hintColor = Options.getInstance().getHintCandidateBackColor();
                                                candColor = Options.getInstance().getHintCandidateColor();
                                            } else {
                                                hintColor = Options.getInstance().getHintCandidateFinBackColor();
                                                candColor = Options.getInstance().getHintCandidateFinColor();
                                            }
                                        }
                                    }
                                }
                                for (Candidate cand : step.getFins()) {
                                    if (cand.getIndex() == index && cand.getValue() == i) {
                                        hintColor = Options.getInstance().getHintCandidateFinBackColor();
                                        candColor = Options.getInstance().getHintCandidateFinColor();
                                    }
                                }
                                for (Candidate cand : step.getEndoFins()) {
                                    if (cand.getIndex() == index && cand.getValue() == i) {
                                        hintColor = Options.getInstance().getHintCandidateEndoFinBackColor();
                                        candColor = Options.getInstance().getHintCandidateEndoFinColor();
                                    }
                                }
                                if (step.getValues().contains(i) && step.getColorCandidates().containsKey(index)) {
                                    hintColor = Options.getInstance().getColoringColors()[step.getColorCandidates().get(index)];
                                    candColor = Options.getInstance().getCandidateColor();
                                }
                                for (Candidate cand : step.getCandidatesToDelete()) {
                                    if (cand.getIndex() == index && cand.getValue() == i) {
                                        hintColor = Options.getInstance().getHintCandidateDeleteBackColor();
                                        candColor = Options.getInstance().getHintCandidateDeleteColor();
                                    }
                                }
                                for (Candidate cand : step.getCannibalistic()) {
                                    if (cand.getIndex() == index && cand.getValue() == i) {
                                        hintColor = Options.getInstance().getHintCandidateCannibalisticBackColor();
                                        candColor = Options.getInstance().getHintCandidateCannibalisticColor();
                                    }
                                }
                            }
                            if (hintColor != null) {
                                g2.setColor(candColor);
                                Color dummy = g2.getColor();
                                g2.setColor(hintColor);
                                g2.fillOval(startX + shiftX + dcx - 2 * (ddy - ddx) / 3, startY + shiftY + dcy - 4 * ddy / 5 - 1, ddy, ddy);
                                g2.setColor(dummy);
                            }
                            g2.drawString(Integer.toString(i), startX + dcx + shiftX, startY + dcy + shiftY);
                        }
                    }
                }
            }
        }

        // Rahmen zeichnen: muss am Schluss sein, wegen der Hintergründe
        switch (Options.getInstance().getDrawMode()) {
            case 0:
                g2.setStroke(new BasicStroke(2));
                g2.setColor(Options.getInstance().getGridColor());
                g2.drawRect(startSX, startSY, width, height);
                drawBlockLine(delta + startSX, 1 * delta + startSY, true);
                drawBlockLine(delta + startSX, 2 * delta + startSY + 3 * cellSize, true);
                drawBlockLine(delta + startSX, 3 * delta + startSY + 6 * cellSize, true);
                break;
            case 1:
                g2.setStroke(new BasicStroke(2));
                if (width > 1000) {
                    g2.setStroke(new BasicStroke(4));
                }
                g2.setColor(Options.getInstance().getInnerGridColor());
                drawBlockLine(delta + startSX, 1 * delta + startSY, false);
                drawBlockLine(delta + startSX, 2 * delta + startSY + 3 * cellSize, false);
                drawBlockLine(delta + startSX, 3 * delta + startSY + 6 * cellSize, false);
                g2.setColor(Options.getInstance().getGridColor());
                g2.drawRect(startSX, startSY, width, height);
                for (int i = 0; i < 3; i++) {
                    g2.drawLine(startSX, startSY + i * 3 * cellSize, startSX + 9 * cellSize, startSY + i * 3 * cellSize);
                    g2.drawLine(startSX + i * 3 * cellSize, startSY, startSX + i * 3 * cellSize, startSY + 9 * cellSize);
                }
                break;
        }

        // Chains zeichnen, wenn vorhanden
        if (step != null && step.getChains().size() != 0) {
            // es gibt mindestens eine Chain
            // zuerst alle Punkte sammeln (auch zu löschende Kandidaten und ALS)
            points.clear();
            //for (Chain chain : step.getChains()) {
            for (int ci = 0; ci < step.getChainAnz(); ci++) {
                if (chainIndex != -1 && chainIndex != ci) {
                    continue;
                }
                Chain chain = step.getChains().get(ci);
                for (int i = chain.start; i <= chain.end; i++) {
                    int che = Math.abs(chain.chain[i]);
                    points.add(getChainPoint(Chain.getSCellIndex(che), Chain.getSCandidate(che), cellSize, dcx, dcy, ddx, ddy));
                    if (Chain.getSNodeType(che) == Chain.GROUP_NODE) {
                        int indexC = Chain.getSCellIndex2(che);
                        if (indexC != -1) {
                            points.add(getChainPoint(indexC, Chain.getSCandidate(che), cellSize, dcx, dcy, ddx, ddy));
                        }
                        indexC = Chain.getSCellIndex3(che);
                        if (indexC != -1) {
                            points.add(getChainPoint(indexC, Chain.getSCandidate(che), cellSize, dcx, dcy, ddx, ddy));
                        }
                    }
                }
            }
            for (Candidate cand : step.getCandidatesToDelete()) {
                points.add(getChainPoint(cand.index, cand.value, cellSize, dcx, dcy, ddx, ddy));
            }
            //for (AlsInSolutionStep als : step.getAlses()) {
            for (int ai = 0; ai < step.getAlses().size(); ai++) {
                if (chainIndex != -1 && ! alsToShow.contains(ai)) {
                    continue;
                }
                AlsInSolutionStep als = step.getAlses().get(ai);
                for (int i = 0; i < als.indices.size(); i++) {
                    int index = als.indices.get(i);
                    short[] cands = sudoku.getCell(index).getAllCandidates();
                    for (int j = 0; j < cands.length; j++) {
                        points.add(getChainPoint(index, cands[j], cellSize, dcx, dcy, ddx, ddy));
                    }
                }
            }
            // dann zeichnen
            //for (Chain chain : step.getChains()) {
            for (int ci = 0; ci < step.getChainAnz(); ci++) {
                if (chainIndex != -1 && ci != chainIndex) {
                    continue;
                }
                Chain chain = step.getChains().get(ci);
                drawChain(g2, chain, cellSize, dcx, dcy, ddx, ddy);
            }
        }
    }

    private Point getChainPoint(int index, int cand, int cellSize, int dcx, int dcy, int ddx, int ddy) {
        return new Point(getCandKoord(index, cand, cellSize, dcx, dcy, ddx, ddy, true),
                getCandKoord(index, cand, cellSize, dcx, dcy, ddx, ddy, false));
    }

    /**
     * Zuerst eine Liste mit den Koordinaten aller Knoten der Chain erstellen. Dann wird
     * gezeichnet:
     *   - Koordinaten der Endpunkte berechnen
     *   - Prüfen, ob ein anderer Knoten auf der Strecke liegt
     *   - Wenn ja, mit Bezier-Kurve zeichnen (Tangenten 45 Grad zur Gerade)
     *       - Richtung zunächst einmal immer oben/links
     *   - Zu kurze Strecken nicht berücksichtigen
     */
    private void drawChain(Graphics2D g2, Chain chain, int cellSize,
            int dcx, int dcy, int ddx, int ddy) {
        //System.out.println("Chain: " + chain.start + "/" + chain.end + "/" + chain.chain);
        int[] ch = chain.chain;
        //List<Point> points1 = new ArrayList<Point>(chain.end - chain.start + 1);
        List<Point> points1 = new ArrayList<Point>(chain.end + 1);
        //for (int i = chain.start; i <= chain.end; i++) {
        for (int i = 0; i <= chain.end; i++) {
            if (i < chain.start) {
                points1.add(null);
                continue;
            }
            int che = Math.abs(ch[i]);
            points1.add(new Point(getCandKoord(Chain.getSCellIndex(che), Chain.getSCandidate(che), cellSize, dcx, dcy, ddx, ddy, true),
                    getCandKoord(Chain.getSCellIndex(che), Chain.getSCandidate(che), cellSize, dcx, dcy, ddx, ddy, false)));
        }
        Stroke oldStroke = g2.getStroke();
        int oldChe = 0;
        int oldIndex = 0;
        int index = 0;
        for (int i = chain.start; i < chain.end; i++) {
            // nur zeichnen, wenn zwischen zwei verschiedenen Zellen
            if (ch[i + 1] == Integer.MIN_VALUE) {
                // Trennmarker -> ignorieren
                continue;
            }
            index = i;
            int che = Math.abs(ch[i]);
            int che1 = Math.abs(ch[i + 1]);
            if (ch[i] > 0 && ch[i + 1] < 0) {
                oldChe = che;
                oldIndex = i;
            }
            if (ch[i] == Integer.MIN_VALUE && ch[i + 1] < 0) {
                che = oldChe;
                index = oldIndex;
            }
            if (ch[i] < 0 && ch[i + 1] > 0) {
                che = oldChe;
                index = oldIndex;
            }
            if (Chain.getSCellIndex(che) == Chain.getSCellIndex(che1)) {
                continue;
            }
            g2.setColor(Options.getInstance().getArrowColor());
            if (Chain.isSStrong(che1)) {
                g2.setStroke(strongLinkStroke);
            } else {
                g2.setStroke(weakLinkStroke);
            }
            drawArrow(g2, index, Chain.getSCandidate(che), i + 1, Chain.getSCandidate(che1), cellSize, dcx, dcy, ddx, ddy, points1);
        }
        g2.setStroke(oldStroke);

    }

    private void drawArrow(Graphics2D g2, int index1, int cand1, int index2, int cand2, int cellSize,
            int dcx, int dcy, int ddx, int ddy, List<Point> points1) {
        // Start- und Endpunkte des Pfeils berechnen
        Point p1 = new Point(points1.get(index1));
        Point p2 = new Point(points1.get(index2));
        int length = (int) Math.sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y));
        double deltaX = p2.x - p1.x;
        double deltaY = p2.y - p1.y;
        // Quadranten-Anpassung nicht vergessen
        int quadrant = getQuadrant(deltaX, deltaY);
        double alpha = getAlpha(deltaX, deltaY, quadrant);
        adjustEndPoints(p1, p2, alpha, quadrant, ddy);

        // Prüfen, ob ein anderer Kandidat auf der direkten Linie liegt
        double epsilon = 0.1;
        double dx1 = p2.x - p1.x;
        double dy1 = p2.y - p1.y;
        boolean doesIntersect = false;
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).equals(points1.get(index1)) || points.get(i).equals(points1.get(index2))) {
                continue;
            }
            Point point = points.get(i);
            double dx2 = point.x - p1.x;
            double dy2 = point.y - p1.y;
            // Kontrolle mit Ähnlichkeitssatz
            if (Math.signum(dx1) == Math.signum(dx2) && Math.signum(dy1) == Math.signum(dy2) &&
                    Math.abs(dx2) <= Math.abs(dx1) && Math.abs(dy2) <= Math.abs(dy1)) {
                // Punkt könnte auf der Geraden liegen
                if (dx1 == 0.0 || dy1 == 0.0 || Math.abs(dx1 / dy1 - dx2 / dy2) < epsilon) {
                    // Punkt liegt auf der Geraden
                    doesIntersect = true;
                    break;
                }
            }
        }
        if (length < 2 * ddy) {
            doesIntersect = true;
        }

        // Werte für Pfeilspitze vorbereiten
        double aAlpha = alpha;
        int aQuadrant = quadrant;

        // Grundlinie zeichnen
        if (doesIntersect) {
            int bezierLength = 20;
            // Wenn die Punkte zu nahe beieinander sind, geht die Ausgangspunktberechnung schief
            if (length < 2 * ddy) {
                bezierLength = length / 4;
            }
            // Die Endpunkte müssen um 45 Grad gedreht werden: beim Startpunkt gegen den
            // Uhrzeigersinn, beim Endpunkt im Uhrzeigersinn
            rotatePoint(points1.get(index1), p1, -Math.PI / 4.0);
            rotatePoint(points1.get(index2), p2, Math.PI / 4.0);

            aAlpha = alpha - Math.PI / 4.0;
            if (quadrant == 2) {
                aAlpha = alpha + Math.PI / 4.0;
            }
            int bX1 = (int) (p1.x + bezierLength * Math.cos(aAlpha));
            int bY1 = (int) (p1.y + bezierLength * Math.sin(aAlpha));
            aAlpha = alpha + Math.PI / 4.0;
            if (quadrant == 2) {
                aAlpha = 5 * Math.PI / 4.0 - alpha;
            }
            int bX2 = (int) (p2.x - bezierLength * Math.cos(aAlpha));
            int bY2 = (int) (p2.y - bezierLength * Math.sin(aAlpha));
            if (quadrant == 2) {
                bY2 = (int) (p2.y + bezierLength * Math.sin(aAlpha));
            }
            cubicCurve.setCurve(p1.x, p1.y, bX1, bY1, bX2, bY2, p2.x, p2.y);
            g2.draw(cubicCurve);

        } else {
            g2.drawLine(p1.x, p1.y, p2.x, p2.y);
        }

        // Pfeilspitzen zeichnen
        g2.setStroke(arrowStroke);
        double arrowLength = cellSize * arrowLengthFactor;
        double arrowHeight = arrowLength * arrowHeightFactor;
        if (length > (arrowLength * 2 + ddy)) {
            if (doesIntersect) {
                double angleKorr = (length - 40.0) / 30.0 * 0.2;
                if (angleKorr > 0.3) {
                    angleKorr = 0.3;
                }
                if (quadrant == 2) {
                    aAlpha += angleKorr;
                } else {
                    aAlpha -= angleKorr;
                }
            }
            // Pfeilspitzen zeichnen
            double sin = Math.sin(aAlpha);
            double cos = Math.cos(aAlpha);
            if (aQuadrant == 2) {
                sin = -sin;
            }
            int aX = p2.x - (int) (cos * arrowLength);
            int aY = p2.y - (int) (sin * arrowLength);
            int daX = (int) (sin * arrowHeight);
            int daY = (int) (cos * arrowHeight);
            arrow.reset();
            arrow.addPoint(aX - daX, aY + daY);
            arrow.addPoint(p2.x, p2.y);
            arrow.addPoint(aX + daX, aY - daY);
            g2.fill(arrow);
            g2.draw(arrow);
        }
    }

    /**
     * p2 wird um angle Grad um p1 gedreht
     */
    private void rotatePoint(Point p1, Point p2, double angle) {
        // in den Nullpunkt verschieben
        p2.x -= p1.x;
        p2.y -= p1.y;

        // um angle rotieren
        double sinAngle = Math.sin(angle);
        double cosAngle = Math.cos(angle);
        double xact = p2.x;
        double yact = p2.y;
        p2.x = (int) (xact * cosAngle - yact * sinAngle);
        p2.y = (int) (xact * sinAngle + yact * cosAngle);

        // und zurückschieben
        p2.x += p1.x;
        p2.y += p1.y;
    }

    private void adjustEndPoints(Point p1, Point p2, double alpha, int quadrant, int ddy) {
        double tmpDelta = ddy / 2 + 4;
        int pX = (int) (tmpDelta * Math.cos(alpha));
        int pY = (int) (tmpDelta * Math.sin(alpha));
        if (quadrant == 2) {
            pY = -pY;
        }
        p1.x += pX;
        p1.y += pY;
        p2.x -= pX;
        p2.y -= pY;
    }

    private double getAlpha(double deltaX, double deltaY, int quadrant) {
        double alpha = Math.atan(deltaY / deltaX);
        // Quadranten-Anpassung nicht vergessen
        if (quadrant == 2) {
            alpha = Math.PI - alpha;
        } else if (quadrant == 3) {
            alpha += Math.PI;
        }
        return alpha;
    }

    private int getQuadrant(double deltaX, double deltaY) {
        int quadrant = 1;
        if (deltaX < 0.0) {
            if (deltaY < 0.0) {
                quadrant = 2;
            } else {
                quadrant = 3;
            }
        } else {
            if (deltaY > 0.0) {
                quadrant = 4;
            }
        }
        return quadrant;
    }

    private int getCandKoord(int index, int cand, int cellSize, int dcx, int dcy, int ddx, int ddy, boolean isX) {
        int third = cellSize / 3;
        int startX = getX(Sudoku.getLine(index), Sudoku.getCol(index));
        int startY = getY(Sudoku.getLine(index), Sudoku.getCol(index));
        int shiftX = ((cand - 1) % 3) * third;
        int shiftY = ((cand - 1) / 3) * third;
        if (isX) {
            return startX + shiftX + dcx - 2 * (ddy - ddx) / 3 + ddy / 2;
        } else {
            return startY + shiftY + dcy - 4 * ddy / 5 - 1 + ddy / 2;
        }
    }

    private int getX(int line, int col) {
        int x = col * cellSize + delta + startSX;
        if (col > 2) {
            x += delta;
        }
        if (col > 5) {
            x += delta;
        }
        return x;
    }

    private int getY(int line, int col) {
        int y = line * cellSize + delta + startSY;
        if (line > 2) {
            y += delta;
        }
        if (line > 5) {
            y += delta;
        }
        return y;
    }

    private int getLine(Point p) {
        double tmp = p.y - startSY - delta;
        if ((tmp >= 3 * cellSize && tmp <= 3 * cellSize + delta) ||
                (tmp >= 6 * cellSize + delta && tmp <= 6 * cellSize + 2 * delta)) {
            return -1;
        }
        if (tmp > 3 * cellSize) {
            tmp -= delta;
        }
        if (tmp > 6 * cellSize) {
            tmp -= delta;
        }
        return (int) Math.ceil((tmp / cellSize) - 1);
    }

    private int getCol(Point p) {
        double tmp = p.x - startSX - delta;
        if ((tmp >= 3 * cellSize && tmp <= 3 * cellSize + delta) ||
                (tmp >= 6 * cellSize + delta && tmp <= 6 * cellSize + 2 * delta)) {
            return -1;
        }
        if (tmp > 3 * cellSize) {
            tmp -= delta;
        }
        if (tmp > 6 * cellSize) {
            tmp -= delta;
        }
        return (int) Math.ceil((tmp / cellSize) - 1);
    }

    private void drawBlockLine(int x, int y, boolean withRect) {
        drawBlock(x, y, withRect);
        drawBlock(x + 3 * cellSize + delta, y, withRect);
        drawBlock(x + 6 * cellSize + 2 * delta, y, withRect);
    }

    private void drawBlock(int x, int y, boolean withRect) {
        if (withRect) {
            g2.drawRect(x, y, 3 * cellSize, 3 * cellSize);
        }
        g2.drawLine(x, y + 1 * cellSize, x + 3 * cellSize, y + 1 * cellSize);
        g2.drawLine(x, y + 2 * cellSize, x + 3 * cellSize, y + 2 * cellSize);
        g2.drawLine(x + 1 * cellSize, y, x + 1 * cellSize, y + 3 * cellSize);
        g2.drawLine(x + 2 * cellSize, y, x + 2 * cellSize, y + 3 * cellSize);
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    public void setAktNumber(int aktNumber) {
        this.aktNumber = aktNumber;
    }

    public Sudoku getSudoku() {
        return sudoku;
    }

    public Sudoku getSolvedSudoku() {
        return solvedSudoku;
    }

    public int getCandidateMode() {
        return candidateMode;
    }

    public boolean isShowCandidates() {
        return showCandidates;
    }

    public void setShowCandidates(boolean showCandidates) {
        this.showCandidates = showCandidates;
        if (showCandidates) {
            candidateMode = SudokuCell.PLAY;
        } else {
            candidateMode = SudokuCell.USER;
        }
        repaint();
    }

    public boolean isShowWrongValues() {
        return showWrongValues;
    }

    public void setShowWrongValues(boolean showWrongValues) {
        this.showWrongValues = showWrongValues;
        repaint();
    }

    public boolean undoPossible() {
        return undoStack.size() > 0;
    }

    public boolean redoPossible() {
        return redoStack.size() > 0;
    }

    public void undo() {
        if (undoPossible()) {
            redoStack.push(sudoku);
            sudoku = undoStack.pop();
            mainFrame.check();
            repaint();
        }
    }

    public void redo() {
        if (redoPossible()) {
            undoStack.push(sudoku);
            sudoku = redoStack.pop();
            mainFrame.check();
            repaint();
        }
    }

    public void setSudoku(Sudoku newSudoku) {
        setSudoku(newSudoku.getSudoku(), false);
    }

    public void setSudoku(Sudoku newSudoku, boolean alreadySolved) {
        setSudoku(newSudoku.getSudoku(), alreadySolved);
    }

    public void setSudoku(String init) {
        setSudoku(init, false);
    }

    public void setSudoku(String init, boolean alreadySolved) {
        step = null;
        setChainInStep(-1);
        undoStack.clear();
        redoStack.clear();
        coloringMap.clear();
        if (init == null || init.length() == 0) {
            sudoku = new Sudoku();
            solvedSudoku = new Sudoku();
        } else {
            sudoku.setSudoku(init);
            solvedSudoku = sudoku.clone();
            boolean unique = creator.validSolution(solvedSudoku);
            if (!unique) {
                JOptionPane.showMessageDialog(this,
                        java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.multiple_solutions"),
                        java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.invalid_puzzle"),
                        JOptionPane.ERROR_MESSAGE);
            } else {
                solvedSudoku = creator.getSolvedSudoku().clone();
                if (!sudoku.checkSudoku(solvedSudoku)) {
                    JOptionPane.showMessageDialog(this,
                            java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.wrong_values"),
                            java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.invalid_puzzle"),
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    if (!alreadySolved) {
                        Sudoku tmpSudoku = sudoku.clone();
                        getSolver().setSudoku(tmpSudoku);
                        getSolver().solve(true);
                    }
//                sudoku.setLevel(tmpSudoku.getLevel());
//                sudoku.setScore(tmpSudoku.getScore());
                    sudoku.setLevel(getSolver().getSudoku().getLevel());
                    sudoku.setScore(getSolver().getSudoku().getScore());
                }
            }
        }
        mainFrame.check();
        repaint();
    }

    public String getSudokuString(ClipboardMode mode) {
        return sudoku.getSudoku(mode, step);
    }

    public SudokuSolver getSolver() {
        return solver;
    }

    public SolutionStep getNextStep(boolean singlesOnly) {
        step = getSolver().getHint(sudoku, singlesOnly);
        setChainInStep(-1);
        repaint();
        return step;
    }

    public void setStep(SolutionStep step) {
        this.step = step;
        setChainInStep(-1);
        repaint();
    }

    public SolutionStep getStep() {
        return step;
    }

    public void setChainInStep(int chainIndex) {
        if (step == null) {
            chainIndex = -1;
        }
        if (chainIndex >= 0 && chainIndex > step.getChainAnz() - 1) {
            chainIndex = -1;
        }
        this.chainIndex = chainIndex;
        alsToShow.clear();
        if (chainIndex != -1) {
            Chain chain = step.getChains().get(chainIndex);
            for (int i = chain.start; i <= chain.end; i++) {
                if (chain.getNodeType(i) == Chain.ALS_NODE) {
                    alsToShow.add(Chain.getSAlsIndex(chain.chain[i]));
                }
            }
        }
//        StringBuffer tmp = new StringBuffer();
//        tmp.append("setChainInStep(" + chainIndex + "): ");
//        for (int i = 0; i < alsToShow.size(); i++) {
//            tmp.append(alsToShow.get(i) + " ");
//        }
//        System.out.println(tmp);
        repaint();
    }

    public void doStep() {
        if (step != null) {
            undoStack.push(sudoku.clone());
            redoStack.clear();
            getSolver().doStep(sudoku, step);
            step = null;
            setChainInStep(-1);
            mainFrame.check();
            repaint();
        }
    }

    public void abortStep() {
        step = null;
        setChainInStep(-1);
        repaint();
    }

    public int getAnzFilled() {
        return sudoku.getAnzFilled();
    }

    public void setNoClues() {
        sudoku.setNoClues();
        repaint();
    }

    public boolean isInvalidCells() {
        return invalidCells;
    }

    public void setInvalidCells(boolean invalidCells) {
        this.invalidCells = invalidCells;
    }

    public boolean isShowInvalidOrPossibleCells() {
        return showInvalidOrPossibleCells;
    }

    public void setShowInvalidOrPossibleCells(boolean showInvalidOrPossibleCells) {
        this.showInvalidOrPossibleCells = showInvalidOrPossibleCells;
    }

    public int getShowHintCellValue() {
        return showHintCellValue;
    }

    public void setShowHintCellValue(int showHintCellValue) {
        this.showHintCellValue = showHintCellValue;
    }

    public boolean isShowDeviations() {
        return showDeviations;
    }

    public void setShowDeviations(boolean showDeviations) {
        this.showDeviations = showDeviations;
        mainFrame.check();
        repaint();
    }

    /**
     * Schreibt ein BufferedImage in eine PNG-Datei. Dabei wird die Auflösung
     * in die Metadaten der Datei geschrieben, was alles etwas kompliziert
     * macht.
     * @param bi Zu zeichnendes Bild
     * @param dpi Auflösung in dots per inch
     * @param fileName Pfad und Name der neuen Bilddatei
     */
    private void writePNG(BufferedImage bi, int dpi, File file) {
        Iterator i = ImageIO.getImageWritersByFormatName("png");
        //are there any jpeg encoders available?

        if (i.hasNext()) //there's at least one ImageWriter, just use the first one
        {
            ImageWriter imageWriter = (ImageWriter) i.next();
            //get the param
            ImageWriteParam param = imageWriter.getDefaultWriteParam();
            ImageTypeSpecifier its = new ImageTypeSpecifier(bi.getColorModel(), bi.getSampleModel());

            //get metadata
            IIOMetadata iomd = imageWriter.getDefaultImageMetadata(its, param);

            String formatName = "javax_imageio_png_1.0";//this is the DOCTYPE of the metadata we need

            Node node = iomd.getAsTree(formatName);

            // standardmäßig ist nur IHDR gesetzt, pHYs dazufügen
            int dpiRes = (int) (dpi / 2.54 * 100);
            IIOMetadataNode res = new IIOMetadataNode("pHYs");
            res.setAttribute("pixelsPerUnitXAxis", String.valueOf(dpiRes));
            res.setAttribute("pixelsPerUnitYAxis", String.valueOf(dpiRes));
            res.setAttribute("unitSpecifier", "meter");
            node.appendChild(res);

            try {
                iomd.setFromTree(formatName, node);
            } catch (IIOInvalidTreeException e) {
                JOptionPane.showMessageDialog(this, e.getLocalizedMessage(),
                        java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.error"),
                        JOptionPane.ERROR_MESSAGE);
            }
            //attach the metadata to an image
            IIOImage iioimage = new IIOImage(bi, null, iomd);
            try {
                FileImageOutputStream out = new FileImageOutputStream(file);
                imageWriter.setOutput(out);
                imageWriter.write(iioimage);
                out.close();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, e.getLocalizedMessage(),
                        java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.error"),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
