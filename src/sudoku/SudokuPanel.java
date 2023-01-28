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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
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
    private boolean invalidCells = Options.getInstance().invalidCells; // true: ungültige Zellen, false: mögliche Zellen
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
    private CellZoomPanel cellZoomPanel; // active cell display and color chooser
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
    private int shiftLine = -1; // second cell for creating regions with the keyboard (shift pressed)
    private int shiftCol = -1; // second cell for creating regions with the keyboard (shift pressed)

    // Undo/Redo
    private Stack<Sudoku> undoStack = new Stack<Sudoku>();
    private Stack<Sudoku> redoStack = new Stack<Sudoku>();
    // coloring: contains cell index + index in coloringColors[]
    private SortedMap<Integer, Integer> coloringMap = new TreeMap<Integer, Integer>();
    // coloring canddiates: contains cell index * 10 + candidate + index in coloringColors[]
    private SortedMap<Integer, Integer> coloringCandidateMap = new TreeMap<Integer, Integer>();
    // indicates wether coloring is active (-1 means "not active"
    private int aktColorIndex = -1;
    // coloring is meant for cells or candidates
    private boolean colorCells = true;
    // Cursor for coloring: shows the strong color
    private Cursor colorCursor = null;
    // Cursor for coloring: shows the weak color
    private Cursor colorCursorShift = null;
    // old cursor for reset
    private Cursor oldCursor = null;
    // if more than one cell is selected, the indices of all selected cells are stored here
    private SortedSet<Integer> selectedCells = new TreeSet<Integer>();
    // Array containing all "Make x" menu items from the popup menu
    private JMenuItem[] makeItems = null;
    // Array containing all "Exclude x" menu items from the popup menu
    private JMenuItem[] excludeItems = null;
    // Array containing all "Toggle color x" menu items from the popup menu
    private JMenuItem[] toggleColorItems = null;

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

        makeItems = new JMenuItem[] { 
            make1MenuItem, make2MenuItem, make3MenuItem, make4MenuItem, make5MenuItem,
            make6MenuItem, make7MenuItem, make8MenuItem, make9MenuItem
        };
        excludeItems = new JMenuItem[] {
            exclude1MenuItem, exclude2MenuItem, exclude3MenuItem,
            exclude4MenuItem, exclude5MenuItem, exclude6MenuItem,
            exclude7MenuItem, exclude8MenuItem, exclude9MenuItem
        };
        toggleColorItems = new JMenuItem[] {
            color1aMenuItem, color1bMenuItem, color2aMenuItem, color2bMenuItem,
            color3aMenuItem, color3bMenuItem, color4aMenuItem, color4bMenuItem,
            color5aMenuItem, color5bMenuItem
        };
        setColorIconsInPopupMenu();
        updateCellZoomPanel();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        cellPopupMenu = new javax.swing.JPopupMenu();
        make1MenuItem = new javax.swing.JMenuItem();
        make2MenuItem = new javax.swing.JMenuItem();
        make3MenuItem = new javax.swing.JMenuItem();
        make4MenuItem = new javax.swing.JMenuItem();
        make5MenuItem = new javax.swing.JMenuItem();
        make6MenuItem = new javax.swing.JMenuItem();
        make7MenuItem = new javax.swing.JMenuItem();
        make8MenuItem = new javax.swing.JMenuItem();
        make9MenuItem = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JSeparator();
        exclude1MenuItem = new javax.swing.JMenuItem();
        exclude2MenuItem = new javax.swing.JMenuItem();
        exclude3MenuItem = new javax.swing.JMenuItem();
        exclude4MenuItem = new javax.swing.JMenuItem();
        exclude5MenuItem = new javax.swing.JMenuItem();
        exclude6MenuItem = new javax.swing.JMenuItem();
        exclude7MenuItem = new javax.swing.JMenuItem();
        exclude8MenuItem = new javax.swing.JMenuItem();
        exclude9MenuItem = new javax.swing.JMenuItem();
        excludeSeveralMenuItem = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JSeparator();
        color1aMenuItem = new javax.swing.JMenuItem();
        color1bMenuItem = new javax.swing.JMenuItem();
        color2aMenuItem = new javax.swing.JMenuItem();
        color2bMenuItem = new javax.swing.JMenuItem();
        color3aMenuItem = new javax.swing.JMenuItem();
        color3bMenuItem = new javax.swing.JMenuItem();
        color4aMenuItem = new javax.swing.JMenuItem();
        color4bMenuItem = new javax.swing.JMenuItem();
        color5aMenuItem = new javax.swing.JMenuItem();
        color5bMenuItem = new javax.swing.JMenuItem();

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("intl/SudokuPanel"); // NOI18N
        make1MenuItem.setText(bundle.getString("SudokuPanel.popup.make1")); // NOI18N
        make1MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                make1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(make1MenuItem);

        make2MenuItem.setText(bundle.getString("SudokuPanel.popup.make2")); // NOI18N
        make2MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                make1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(make2MenuItem);

        make3MenuItem.setText(bundle.getString("SudokuPanel.popup.make3")); // NOI18N
        make3MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                make1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(make3MenuItem);

        make4MenuItem.setText(bundle.getString("SudokuPanel.popup.make4")); // NOI18N
        make4MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                make1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(make4MenuItem);

        make5MenuItem.setText(bundle.getString("SudokuPanel.popup.make5")); // NOI18N
        make5MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                make1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(make5MenuItem);

        make6MenuItem.setText(bundle.getString("SudokuPanel.popup.make6")); // NOI18N
        make6MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                make1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(make6MenuItem);

        make7MenuItem.setText(bundle.getString("SudokuPanel.popup.make7")); // NOI18N
        make7MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                make1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(make7MenuItem);

        make8MenuItem.setText(bundle.getString("SudokuPanel.popup.make8")); // NOI18N
        make8MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                make1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(make8MenuItem);

        make9MenuItem.setText(bundle.getString("SudokuPanel.popup.make9")); // NOI18N
        make9MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                make1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(make9MenuItem);
        cellPopupMenu.add(jSeparator1);

        exclude1MenuItem.setText(bundle.getString("SudokuPanel.popup.exclude1")); // NOI18N
        exclude1MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exclude1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(exclude1MenuItem);

        exclude2MenuItem.setText(bundle.getString("SudokuPanel.popup.exclude2")); // NOI18N
        exclude2MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exclude1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(exclude2MenuItem);

        exclude3MenuItem.setText(bundle.getString("SudokuPanel.popup.exclude3")); // NOI18N
        exclude3MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exclude1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(exclude3MenuItem);

        exclude4MenuItem.setText(bundle.getString("SudokuPanel.popup.exclude4")); // NOI18N
        exclude4MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exclude1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(exclude4MenuItem);

        exclude5MenuItem.setText(bundle.getString("SudokuPanel.popup.exclude5")); // NOI18N
        exclude5MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exclude1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(exclude5MenuItem);

        exclude6MenuItem.setText(bundle.getString("SudokuPanel.popup.exclude6")); // NOI18N
        exclude6MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exclude1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(exclude6MenuItem);

        exclude7MenuItem.setText(bundle.getString("SudokuPanel.popup.exclude7")); // NOI18N
        exclude7MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exclude1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(exclude7MenuItem);

        exclude8MenuItem.setText(bundle.getString("SudokuPanel.popup.exclude8")); // NOI18N
        exclude8MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exclude1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(exclude8MenuItem);

        exclude9MenuItem.setText(bundle.getString("SudokuPanel.popup.exclude9")); // NOI18N
        exclude9MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exclude1MenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(exclude9MenuItem);

        excludeSeveralMenuItem.setText(bundle.getString("SudokuPanel.popup.several")); // NOI18N
        excludeSeveralMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                excludeSeveralMenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(excludeSeveralMenuItem);
        cellPopupMenu.add(jSeparator2);

        color1aMenuItem.setText(bundle.getString("SudokuPanel.popup.color1a")); // NOI18N
        color1aMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                color1aMenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(color1aMenuItem);

        color1bMenuItem.setText(bundle.getString("SudokuPanel.popup.color1b")); // NOI18N
        color1bMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                color1aMenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(color1bMenuItem);

        color2aMenuItem.setText(bundle.getString("SudokuPanel.popup.color2a")); // NOI18N
        color2aMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                color1aMenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(color2aMenuItem);

        color2bMenuItem.setText(bundle.getString("SudokuPanel.popup.color2b")); // NOI18N
        color2bMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                color1aMenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(color2bMenuItem);

        color3aMenuItem.setText(bundle.getString("SudokuPanel.popup.color3a")); // NOI18N
        color3aMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                color1aMenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(color3aMenuItem);

        color3bMenuItem.setText(bundle.getString("SudokuPanel.popup.color3b")); // NOI18N
        color3bMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                color1aMenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(color3bMenuItem);

        color4aMenuItem.setText(bundle.getString("SudokuPanel.popup.color4a")); // NOI18N
        color4aMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                color1aMenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(color4aMenuItem);

        color4bMenuItem.setText(bundle.getString("SudokuPanel.popup.color4b")); // NOI18N
        color4bMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                color1aMenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(color4bMenuItem);

        color5aMenuItem.setText(bundle.getString("SudokuPanel.popup.color5a")); // NOI18N
        color5aMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                color1aMenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(color5aMenuItem);

        color5bMenuItem.setText(bundle.getString("SudokuPanel.popup.color5b")); // NOI18N
        color5bMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                color1aMenuItemActionPerformed(evt);
            }
        });
        cellPopupMenu.add(color5bMenuItem);

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
        updateCellZoomPanel();
        mainFrame.fixFocus();
    }//GEN-LAST:event_formKeyReleased

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        int keyCode = evt.getKeyCode();
        switch (keyCode) {
            case KeyEvent.VK_ESCAPE:
                mainFrame.coloringPanelClicked(-1);
                clearRegion();
                break;
            default:
                handleKeys(evt);
        }
        updateCellZoomPanel();
        mainFrame.fixFocus();
    }//GEN-LAST:event_formKeyPressed

    /**
     * New mouse control for version 2.0:
     * <ul>
     * <li>clicking a cell sets the cursor to the cell (not in coloring mode)</li>
     * <li>holding shift or ctrl down while clicking selects a region of cells</li>
     * <li>double clicking a cell with only one candidate left sets that candidate in the cell</li>
     * <li>double clicking a cell containing a Hidden Single sets that cell if filters are applied for
     *     the candidate</li>
     * <li>double clicking a candidate with ctrl pressed toggles the candidate</li>
     * <li>right click on a cell activates the context menu</li>
     * </ul>
     * If {@link #aktColorIndex} is set (ne -1), coloring mode is in effect and the mouse
     * behaviour changes completely (whether a cell or a candidate should be colored
     * is decided by {@link #colorCells}):
     * <ul>
     * <li>left click on a cell/candidate toggles the color on the cell/candidate</li>
     * <li>left click on a cell/candidate with shift pressed toggles the alternate color on the cell/candidate</li>
     * </ul>
     * Context menu:<br>
     * The context menu for a single cell shows entries to set the cell to all remaining candidates, entries
     * to remove all remaining candidates (including one entry to remove multiple candidates in one move)
     * and entries for coloring. If a region of cells is selected, setting cells is not possible.
     * 
     * @param evt
     */
    private void formMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseClicked
        // undo/Redo siehe handleKeys()
        undoStack.push(sudoku.clone());
        boolean changed = false;
        //undoStack.push(sudoku.clone()); - nach unten geschoben

        int line = getLine(evt.getPoint());
        int col = getCol(evt.getPoint());
        int cand = getCandidate(evt.getPoint(), line, col);
        //System.out.println("line/col/cand " + line + "/" + col + "/" + cand);
        boolean ctrlPressed = (evt.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0;
        boolean shiftPressed = (evt.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0;
        if (line >= 0 && line <= 8 && col >= 0 && col <= 8) {
            //System.out.println((evt.getButton() == MouseEvent.BUTTON1) + "/" + (evt.getButton() == MouseEvent.BUTTON2) + "/" + (evt.getButton() == MouseEvent.BUTTON3));
            if (evt.getButton() == MouseEvent.BUTTON3) {
                // bring up popup menu
                showPopupMenu(line, col);
            } else {
                if (aktColorIndex != -1) {
                    // coloring is active
                    int colorNumber = aktColorIndex;
                    if (shiftPressed || evt.getButton() == MouseEvent.BUTTON2) {
                        if (colorNumber % 2 == 0) {
                            colorNumber++;
                        } else {
                            colorNumber--;
                        }
                    }
                    //System.out.println(line + "/" + col + "/" + cand + "/" + colorNumber + "/" + colorCells);
                    if (colorCells) {
                        // coloring for cells
                        handleColoring(line, col, -1, colorNumber);
                    } else {
                        // coloring for candidates
                        if (cand != -1) {
                            handleColoring(line, col, cand, colorNumber);
                        }
                    }
                    // we do adjust the selected cell (ranges are not allowed in coloring)
                    aktLine = line;
                    aktCol = col;
                } else if (evt.getButton() == MouseEvent.BUTTON1) {
                    // in normal mode we only react to the left mouse button
                    //System.out.println("BUTTON1/" + evt.getClickCount() + "/" + ctrlPressed + "/" + cand);
                    SudokuCell cell = sudoku.getCell(line, col);
                    if (evt.getClickCount() == 2) {
                        if (ctrlPressed) {
                            if (cand != -1) {
                                // toggle candidate
                                if (cell.isCandidate(candidateMode, cand)) {
                                    sudoku.setCandidate(line, col, candidateMode, cand, false);
                                } else {
                                    sudoku.setCandidate(line, col, candidateMode, cand, true);
                                }
                                clearRegion();
                                changed = true;
                            }
                        } else {
                            if (cell.getValue() == 0) {
                                if (cell.getAnzCandidates(candidateMode) == 1) {
                                    // Naked single -> set it!
                                    int actCand = cell.getAllCandidates(candidateMode)[0];
                                    setCell(line, col, actCand);
//                                // if filters are applied, change digit to act digit
//                                if (showHintCellValue != 0) {
//                                    showHintCellValue = actCand;
//                                }
                                    changed = true;
                                } else if (showHintCellValue != 0 && isHiddenSingle(showHintCellValue, line, col)) {
                                    // Hidden Single -> it
                                    setCell(line, col, showHintCellValue);
                                    changed = true;
                                } else if (cand != -1) {
                                    // candidate double clicked -> set it
                                    // (only if that candidate is still set in the cell)
                                    if (cell.isCandidate(candidateMode, cand)) {
                                        setCell(line, col, cand);
                                    }
                                    changed = true;
                                }
                            }
                        }
                    } else if (evt.getClickCount() == 1) {
                        if (ctrlPressed) {
                            // select additional cell
                            if (selectedCells.size() == 0) {
                                // the last selected cell is not yet in the set
                                selectedCells.add(Sudoku.getIndex(aktLine, aktCol));
                                selectedCells.add(Sudoku.getIndex(line, col));
                                aktLine = line;
                                aktCol = col;
                            } else {
                                int index = Sudoku.getIndex(line, col);
                                if (selectedCells.contains(index)) {
                                    selectedCells.remove(index);
                                } else {
                                    selectedCells.add(Sudoku.getIndex(line, col));
                                }
                                aktLine = line;
                                aktCol = col;
                            }
                        } else if (shiftPressed) {
                            if (Options.getInstance().useShiftForRegionSelect) {
                                // select range of cells
                                selectRegion(line, col);
                            } else {
                                if (cand != -1) {
                                    // toggle candidate
                                    if (cell.isCandidate(candidateMode, cand)) {
                                        sudoku.setCandidate(line, col, candidateMode, cand, false);
                                    } else {
                                        sudoku.setCandidate(line, col, candidateMode, cand, true);
                                    }
                                    clearRegion();
                                    changed = true;
                                }
                            }
                        } else {
                            // select single cell, delete old markings if available
                            aktLine = line;
                            aktCol = col;
                            clearRegion();
                        }
                    }
                }
            }
            if (changed) {
                redoStack.clear();
            } else {
                undoStack.pop();
            }
            updateCellZoomPanel();
            mainFrame.check();
            repaint();
        }
    }//GEN-LAST:event_formMouseClicked

    private void make1MenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_make1MenuItemActionPerformed
        popupSetCell((JMenuItem)evt.getSource());
    }//GEN-LAST:event_make1MenuItemActionPerformed

    private void exclude1MenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exclude1MenuItemActionPerformed
        popupExcludeCandidate((JMenuItem)evt.getSource());
    }//GEN-LAST:event_exclude1MenuItemActionPerformed

    private void excludeSeveralMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_excludeSeveralMenuItemActionPerformed
        String input = JOptionPane.showInputDialog(this, ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.cmessage"),
                ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.ctitle"), JOptionPane.QUESTION_MESSAGE);
        if (input != null) {
            undoStack.push(sudoku.clone());
            boolean changed = false;
            for (int i = 0; i < input.length(); i++) {
                char digit = input.charAt(i);
                if (Character.isDigit(digit)) {
                    if (removeCandidateFromActiveCells(Character.getNumericValue(digit))) {
                        changed = true;
                    }
                }
            }
            if (changed) {
                redoStack.clear();
            } else {
                undoStack.pop();
            }
            updateCellZoomPanel();
            mainFrame.check();
            repaint();
        }
    }//GEN-LAST:event_excludeSeveralMenuItemActionPerformed

    private void color1aMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_color1aMenuItemActionPerformed
        popupToggleColor((JMenuItem)evt.getSource());
    }//GEN-LAST:event_color1aMenuItemActionPerformed

    /**
     * Loads all relevant objects into <code>state</code>. If <code>copy</code> is true,
     * all objects are copied.<br>
     * Some objects have to be copied regardless of parameter <code>copy</code>.
     * @param state
     * @param copy
     */
    public void getState(GuiState state, boolean copy) {
        // items that dont have to be copied
        state.chainIndex = chainIndex;
        // items that must be copied anyway
        state.undoStack = (Stack<Sudoku>) undoStack.clone();
        state.redoStack = (Stack<Sudoku>) redoStack.clone();
        state.coloringMap = (SortedMap<Integer, Integer>) ((TreeMap) coloringMap).clone();
        state.coloringCandidateMap = (SortedMap<Integer, Integer>) ((TreeMap) coloringCandidateMap).clone();
        // items that might be null (and therefore wont be copied)
        state.sudoku = sudoku;
        state.solvedSudoku = solvedSudoku;
        state.step = step;
        if (copy) {
            state.sudoku = (Sudoku) sudoku.clone();
            if (solvedSudoku != null) {
                state.solvedSudoku = (Sudoku) solvedSudoku.clone();
            }
            if (step != null) {
                state.step = (SolutionStep) step.clone();
            }
        }
    }

    /**
     * Loads back a saved state. Whether the objects had been copied
     * before is irrelevant here.<br>
     * The optional objects {@link GuiState#undoStack} and {@link GuiState#redoStack}
     * can be null. If this is the case they are cleared.
     * @param state
     */
    public void setState(GuiState state) {
        chainIndex = state.chainIndex;
        if (state.undoStack != null) {
            undoStack = state.undoStack;
        } else {
            undoStack.clear();
        }
        if (state.redoStack != null) {
            redoStack = state.redoStack;
        } else {
            redoStack.clear();
        }
        if (state.coloringMap != null) {
            coloringMap = state.coloringMap;
        } else {
            coloringMap.clear();
        }
        if (state.coloringCandidateMap != null) {
            coloringCandidateMap = state.coloringCandidateMap;
        } else {
            coloringCandidateMap.clear();
        }
        sudoku = state.sudoku;
        sudoku.synchronizeSets();
        solvedSudoku = state.solvedSudoku;
        step = state.step;
        updateCellZoomPanel();
        mainFrame.check();
        repaint();
    }

//    public void loadFromFile(Sudoku sudoku, Sudoku solvedSudoku) {
//        this.sudoku = sudoku;
//        this.solvedSudoku = solvedSudoku;
//        redoStack.clear();
//        undoStack.clear();
//        coloringMap.clear();
//        coloringCandidateMap.clear();
//        step = null;
//        setChainInStep(-1);
//        updateCellZoomPanel();
//        mainFrame.check();
//        repaint();
//    }

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

        if (aktColorIndex >= 0) {
            if (getCursor() == colorCursorShift) {
                setCursor(colorCursor);
                //System.out.println("normal cursor set");
            }
        }
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

        // if only <shift> is pressed and coloring is active, the cursor should change to complementary color
        if (aktColorIndex >= 0) {
            if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
                if (getCursor() == colorCursor) {
                    setCursor(colorCursorShift);
                    //System.out.println("cursor shift set");
                }
            }
        }

        // "normale" Tastaturbehandlung
        SudokuCell cell = sudoku.getCell(aktLine, aktCol);
        // bei keyPressed funktioniert getKeyChar() nicht zuverlässig, daher die Zahl selbst ermitteln
        int number = 0;
        boolean clearSelectedRegion = true;
        switch (keyCode) {
            case KeyEvent.VK_DOWN:
                if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0 &&
                        (modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 &&
                        showHintCellValue != 0) {
                    // go to next filtered candidate
                    int index = findNextHintCandidate(aktLine, aktCol, keyCode);
                    aktLine = Sudoku.getLine(index);
                    aktCol = Sudoku.getCol(index);
                } else if (aktLine < 8) {
                    // go to the next line
                    aktLine++;
                    if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
                        // go to the next unset cell
                        while (aktLine < 8 && sudoku.getCell(aktLine, aktCol).getValue() != 0) {
                            aktLine++;
                        }
                    } else if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
                        // expand the selected region
                        aktLine--;
                        setShift();
                        shiftLine++;
                        selectRegion(shiftLine, shiftCol);
                        clearSelectedRegion = false;
                    }
                }
                if (clearSelectedRegion) {
                    clearRegion();
                }
                break;
            case KeyEvent.VK_UP:
                if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0 &&
                        (modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 &&
                        showHintCellValue != 0) {
                    // go to next filtered candidate
                    int index = findNextHintCandidate(aktLine, aktCol, keyCode);
                    aktLine = Sudoku.getLine(index);
                    aktCol = Sudoku.getCol(index);
                } else if (aktLine > 0) {
                    // go to the next line
                    aktLine--;
                    if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
                        // go to the next unset cell
                        while (aktLine > 0 && sudoku.getCell(aktLine, aktCol).getValue() != 0) {
                            aktLine--;
                        }
                    } else if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
                        // expand the selected region
                        aktLine++;
                        setShift();
                        shiftLine--;
                        selectRegion(shiftLine, shiftCol);
                        clearSelectedRegion = false;
                    }
                }
                if (clearSelectedRegion) {
                    clearRegion();
                }
                break;
            case KeyEvent.VK_RIGHT:
                if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0 &&
                        (modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 &&
                        showHintCellValue != 0) {
                    // go to next filtered candidate
                    int index = findNextHintCandidate(aktLine, aktCol, keyCode);
                    aktLine = Sudoku.getLine(index);
                    aktCol = Sudoku.getCol(index);
                } else if (aktCol < 8) {
                    // go to the next line
                    aktCol++;
                    if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
                        // go to the next unset cell
                        while (aktCol < 8 && sudoku.getCell(aktLine, aktCol).getValue() != 0) {
                            aktCol++;
                        }
                    } else if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
                        // expand the selected region
                        aktCol--;
                        setShift();
                        shiftCol++;
                        selectRegion(shiftLine, shiftCol);
                        clearSelectedRegion = false;
                    }
                }
                if (clearSelectedRegion) {
                    clearRegion();
                }
                break;
            case KeyEvent.VK_LEFT:
                if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0 &&
                        (modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 &&
                        showHintCellValue != 0) {
                    // go to next filtered candidate
                    int index = findNextHintCandidate(aktLine, aktCol, keyCode);
                    aktLine = Sudoku.getLine(index);
                    aktCol = Sudoku.getCol(index);
                } else if (aktCol > 0) {
                    // go to the next col
                    aktCol--;
                    if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
                        // go to the next unset cell
                        while (aktCol > 0 && sudoku.getCell(aktLine, aktCol).getValue() != 0) {
                            aktCol--;
                        }
                    } else if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
                        // expand the selected region
                        aktCol++;
                        setShift();
                        shiftCol--;
                        selectRegion(shiftLine, shiftCol);
                        clearSelectedRegion = false;
                    }
                }
                if (clearSelectedRegion) {
                    clearRegion();
                }
                break;
            case KeyEvent.VK_HOME:
                if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
                    setShift();
                    if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
                        shiftLine = 0;
                    } else {
                        shiftCol = 0;
                    }
                    selectRegion(shiftLine, shiftCol);
                    clearSelectedRegion = false;
                } else {
                    if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
                        aktLine = 0;
                    } else {
                        aktCol = 0;
                    }
                }
                if (clearSelectedRegion) {
                    clearRegion();
                }
                break;
            case KeyEvent.VK_END:
                if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
                    setShift();
                    if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
                        shiftLine = 8;
                    } else {
                        shiftCol = 8;
                    }
                    selectRegion(shiftLine, shiftCol);
                    clearSelectedRegion = false;
                } else {
                    if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
                        aktLine = 8;
                    } else {
                        aktCol = 8;
                    }
                }
                if (clearSelectedRegion) {
                    clearRegion();
                }
                break;
            case KeyEvent.VK_9:
            case KeyEvent.VK_NUMPAD9:
                number++;
            case KeyEvent.VK_8:
            case KeyEvent.VK_NUMPAD8:
                number++;
            case KeyEvent.VK_7:
            case KeyEvent.VK_NUMPAD7:
                number++;
            case KeyEvent.VK_6:
            case KeyEvent.VK_NUMPAD6:
                number++;
            case KeyEvent.VK_5:
            case KeyEvent.VK_NUMPAD5:
                number++;
            case KeyEvent.VK_4:
            case KeyEvent.VK_NUMPAD4:
                number++;
            case KeyEvent.VK_3:
            case KeyEvent.VK_NUMPAD3:
                number++;
            case KeyEvent.VK_2:
            case KeyEvent.VK_NUMPAD2:
                number++;
            case KeyEvent.VK_1:
            case KeyEvent.VK_NUMPAD1:
                number++;
                //int number = Character.digit(evt.getKeyChar(), 10);
                if (selectedCells.isEmpty()) {
                    if ((modifiers & KeyEvent.CTRL_DOWN_MASK) == 0) {
                        // Zelle setzen
                        setCell(aktLine, aktCol, number);
                        changed = true;
                    } else if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) == 0) {
                        // only when shift is NOT pressed (if pressed its a menu accelerator)
                        toggleCandidateInCell(aktLine, aktCol, number);
                        changed = true;
                    }
                }
                break;
            case KeyEvent.VK_BACK_SPACE:
            case KeyEvent.VK_DELETE:
            case KeyEvent.VK_0:
            case KeyEvent.VK_NUMPAD0:
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
                if ((modifiers & KeyEvent.ALT_DOWN_MASK) == 0) {
                    // pressing <Alt><F4> changes the selection ... not good
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
                }
                break;
            case KeyEvent.VK_SPACE:
                if (isShowInvalidOrPossibleCells() && selectedCells.isEmpty()) {
                    int candidate = getShowHintCellValue();
                    toggleCandidateInCell(aktLine, aktCol, candidate);
                    changed = true;
//                    if (cell.getValue() == 0) {
//                        if (cell.isCandidate(candidateMode, candidate)) {
//                            sudoku.setCandidate(aktLine, aktCol, candidateMode, candidate, false);
//                            changed = true;
//                        } else {
//                            sudoku.setCandidate(aktLine, aktCol, candidateMode, candidate, true);
//                            changed = true;
//                        }
//                    }
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
                        (modifiers & KeyEvent.ALT_GRAPH_DOWN_MASK) != 0 ||
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
                handleColoring(-1, number);
//                int index = Sudoku.getIndex(aktLine, aktCol);
//                if (coloringMap.containsKey(index) && coloringMap.get(index) == number) {
//                    // pressing the same key on the same cell twice removes the coloring
//                    coloringMap.remove(index);
//                } else {
//                    // either newly colored cell or change of cell color
//                    coloringMap.put(index, number);
//                }
                break;
            case KeyEvent.VK_R:
                clearColoring();
                break;
            case KeyEvent.VK_GREATER:
            case KeyEvent.VK_LESS:
            default:
                // doesnt work on all keyboards :-(
                // more precisely: doesnt work, if the keyboard layout in the OS
                // doesnt match the physical layout of the keyboard
                char ch = evt.getKeyChar();
                if (ch == '<' || ch == '>') {
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
                }
                break;
        }
        if (changed) {
            // Undo wurde schon behandelt, Redo ist nicht mehr möglich
            redoStack.clear();
        } else {
            // kein Undo nötig -> wieder entfernen
            undoStack.pop();
        }
        updateCellZoomPanel();
        mainFrame.check();
        repaint();
    }

    /**
     * Clears a selected region of cells
     */
    private void clearRegion() {
        selectedCells.clear();
        shiftLine = -1;
        shiftCol = -1;
    }

    /**
     * Initializes {@link #shiftLine}/{@link #shiftCol} for
     * selecting regions of cells using the keyboard
     */
    private void setShift() {
        if (shiftLine == -1) {
            shiftLine = aktLine;
            shiftCol = aktCol;
        }
    }

    /**
     * Select all cells in the rectangle defined by
     * {@link #aktLine}/{@link #aktCol} and line/col
     * @param line
     * @param col
     */
    private void selectRegion(int line, int col) {
        selectedCells.clear();
        if (line == aktLine && col == aktCol) {
            // same cell clicked twice -> no region selected -> do nothing
        } else {
            // every cell in the region gets selected, aktLine and aktCol are not changed
            int cStart = col < aktCol ? col : aktCol;
            int lStart = line < aktLine ? line : aktLine;
            for (int i = cStart; i <= cStart + Math.abs(col - aktCol); i++) {
                for (int j = lStart; j <= lStart + Math.abs(line - aktLine); j++) {
                    selectedCells.add(Sudoku.getIndex(j, i));
                }
            }
        }
    }

    /**
     * Finds the next colored cell, if filters are applied. mode gives the
     * direction in which to search (as KeyEvent). The search wraps at
     * sudoku boundaries.
     * @param line
     * @param col
     * @param mode
     * @return
     */
    private int findNextHintCandidate(int line, int col, int mode) {
        int index = Sudoku.getIndex(line, col);
        if (showHintCellValue == 0) {
            return index;
        }
        switch (mode) {
            case KeyEvent.VK_DOWN:
                // let's start with the next line
                line++;
                if (line == 9) {
                    line = 0;
                    col++;
                    if (col == 9) {
                        return index;
                    }
                }
                for (int i = col; i < 9; i++) {
                    int j = i == col ? line : 0;
                    for (; j < 9; j++) {
                        if (sudoku.getCell(j, i).getValue() == 0 &&
                                sudoku.getCell(j, i).isCandidate(candidateMode, showHintCellValue)) {
                            return Sudoku.getIndex(j, i);
                        }
                    }
                }
                break;
            case KeyEvent.VK_UP:
                // let's start with the previous line
                line--;
                if (line < 0) {
                    line = 8;
                    col--;
                    if (col < 0) {
                        return index;
                    }
                }
                for (int i = col; i >= 0; i--) {
                    int j = i == col ? line : 8;
                    for (; j >= 0; j--) {
                        if (sudoku.getCell(j, i).getValue() == 0 &&
                                sudoku.getCell(j, i).isCandidate(candidateMode, showHintCellValue)) {
                            return Sudoku.getIndex(j, i);
                        }
                    }
                }
                break;
            case KeyEvent.VK_LEFT:
                // lets start left
                index--;
                if (index < 0) {
                    return index + 1;
                }
                while (index >= 0) {
                    if (sudoku.getCell(index).getValue() == 0 &&
                            sudoku.getCell(index).isCandidate(candidateMode, showHintCellValue)) {
                        return index;
                    }
                    index--;
                }
                if (index < 0) {
                    index = Sudoku.getIndex(line, col);
                }
                break;
            case KeyEvent.VK_RIGHT:
                // lets start right
                index++;
                if (index >= sudoku.getCells().length) {
                    return index - 1;
                }
                while (index < sudoku.getCells().length) {
                    if (sudoku.getCell(index).getValue() == 0 &&
                            sudoku.getCell(index).isCandidate(candidateMode, showHintCellValue)) {
                        return index;
                    }
                    index++;
                }
                if (index >= sudoku.getCells().length) {
                    index = Sudoku.getIndex(line, col);
                }
                break;
        }
        return index;
    }
    
    /**
     * Removes all coloring info
     */
    public void clearColoring() {
        coloringMap.clear();
        coloringCandidateMap.clear();
        setActiveColor(-1);
        updateCellZoomPanel();
        mainFrame.check();
    }

    /**
     * Handles coloring for all selected cells, delegates to {@link #handleColoring(int, int, int, int)}
     * (see description there).
     * @param candidate
     * @param colorNumber
     */
    private void handleColoring(int candidate, int colorNumber) {
        if (selectedCells.isEmpty()) {
            handleColoring(aktLine, aktCol, candidate, colorNumber);
        } else {
            for (int index : selectedCells) {
                handleColoring(Sudoku.getLine(index), Sudoku.getCol(index), candidate, colorNumber);
            }
        }
    }

    /**
     * Toggles Color for candidate in active cell; only called from
     * {@link CellZoomPanel}.
     * 
     * @param candidate
     */
    public void handleColoring(int candidate) {
        handleColoring(aktLine, aktCol, candidate, aktColorIndex);
        repaint();
        updateCellZoomPanel();
        mainFrame.fixFocus();
    }
    
    /**
     * Handles the coloring of a cell or a candidate. If candidate equals -1, a cell
     * is to be coloured, else a candidate. If the target is already colored and the
     * new color matches the old one, coloring is removed, else it is set
     * to the new color.
     * @param line
     * @param col
     * @param candidate
     * @param colorNumber
     */
    private void handleColoring(int line, int col, int candidate, int colorNumber) {
        SortedMap<Integer,Integer> map = coloringMap;
        int key = Sudoku.getIndex(line, col);
        if (candidate != -1) {
            key = key * 10 + candidate;
            map = coloringCandidateMap;
        }
        if (map.containsKey(key) && map.get(key) == colorNumber) {
            // pressing the same key on the same cell twice removes the coloring
            map.remove(key);
        } else {
            // either newly colored cell or change of cell color
            map.put(key, colorNumber);
        }
        updateCellZoomPanel();
    }

    /**
     * Handles "set value" done in {@link CellZoomPanel}. Should not be
     * used otherwise.
     * @param number
     */
    public void setCellFromCellZoomPanel(int number) {
        undoStack.push(sudoku.clone());
        setCell(aktLine, aktCol, number);
        updateCellZoomPanel();
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
     * Toggles candidate in all active cells (all cells in {@link #selectedCells} or
     * cell denoted by {@link #aktLine}/{@link #aktCol} if {@link #selectedCells} is
     * empty).<br>
     *
     * Uses {@link #candidateMode} to determine the right type of candidate.
     * @param candidate
     */
    private void toggleCandidateInAktCells(int candidate) {
        if (selectedCells.isEmpty()) {
            toggleCandidateInCell(aktLine, aktCol, candidate);
        } else {
            for (int index : selectedCells) {
                toggleCandidateInCell(Sudoku.getLine(index), Sudoku.getCol(index), candidate);
            }
        }
    }

    /**
     * Toggles candidate in the cell denoted by line/col. Uses {@link #candidateMode}.
     * @param line
     * @param col
     * @param candidate
     */
    private void toggleCandidateInCell(int line, int col, int candidate) {
        SudokuCell cell = sudoku.getCell(line, col);
        if (cell.getValue() == 0) {
            if (cell.isCandidate(candidateMode, candidate)) {
                sudoku.setCandidate(line, col, candidateMode, candidate, false);
            } else {
                sudoku.setCandidate(line, col, candidateMode, candidate, true);
            }
        }
        updateCellZoomPanel();
    }

    /**
     * Creates an image of the current sudoku in the given size.
     * 
     * @param size
     * @return
     */
    public BufferedImage getSudokuImage(int size) {
        BufferedImage fileImage = new BufferedImage(size, size, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = fileImage.createGraphics();
        this.g2 = g;
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, size, size);
        drawPage(size, size, true, false);
        return fileImage;
    }

    /**
     * Writes an image of the current sudoku as png into a file. The image
     * is <code>size</code> pixels wide and high, the resolution in the png
     * file is set to <code>dpi</code>.
     * 
     * @param file
     * @param size
     * @param dpi
     */
    public void saveSudokuAsPNG(File file, int size, int dpi) {
        BufferedImage fileImage = getSudokuImage(size);
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
//                if (line == aktLine && col == aktCol && !isPrint) {
//                    g2.setColor(Options.getInstance().getAktCellColor());
//                }
//                if (selectedCells.contains(cellIndex) && ! isPrint) {
//                    g2.setColor(Options.getInstance().getAktCellColor());
//                }
                boolean isSelected = (selectedCells.isEmpty() && line == aktLine && col == aktCol) || selectedCells.contains(cellIndex);
                if (isSelected && ! isPrint) {
                    g2.setColor(Options.getInstance().getAktCellColor());
                }
                // check if the candidate denoted by showHintCellValue is a valid candidate; if showCandidates == false,
                // this can be done by SudokuCell.isCandidateValid(); if it is true, candidates entered by the user
                // are highlighted, regardless of validity
                boolean candidateValid = false;
                if (showHintCellValue != 0) {
                    if (showCandidates) {
                        candidateValid = cell.isCandidateValid(candidateMode, showHintCellValue);
                    } else {
                        candidateValid = cell.isCandidate(candidateMode, showHintCellValue);
                    }
                }
                if (isShowInvalidOrPossibleCells() && isInvalidCells() &&
                        (cell.getValue() != 0 || (showHintCellValue != 0 && !candidateValid))) {
//                        (cell.getValue() != 0 || (getShowHintCellValue() != 0 && !cell.isCandidateValid(SudokuCell.PLAY, getShowHintCellValue())))) {
                    g2.setColor(Options.getInstance().getInvalidCellColor());
                }
                if (isShowInvalidOrPossibleCells() && !isInvalidCells() && cell.getValue() == 0 &&
                        showHintCellValue != 0 && candidateValid) {
//                        getShowHintCellValue() != 0 && cell.isCandidateValid(SudokuCell.PLAY, getShowHintCellValue())) {
                    g2.setColor(Options.getInstance().getPossibleCellColor());
                }
                //if (cell.getValue() == 0 && coloringMap.containsKey(cellIndex)) {
                if (coloringMap.containsKey(cellIndex)) {
                    // coloring
                    g2.setColor(Options.getInstance().getColoringColors()[coloringMap.get(cellIndex)]);
                }
                g2.fillRect(getX(line, col), getY(line, col), cellSize, cellSize);
                if (isSelected && !isPrint && g2.getColor() != Options.getInstance().getAktCellColor()) {
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
                                int alsIndex = step.getAlsIndex(index, chainIndex);
                                if (alsIndex != -1 && ((chainIndex == -1 && ! step.getType().isKrakenFish()) || alsToShow.contains(alsIndex))) {
                                    hintColor = Options.getInstance().getHintCandidateAlsBackColors()[alsIndex % Options.getInstance().getHintCandidateAlsBackColors().length];
                                    candColor = Options.getInstance().getHintCandidateAlsColors()[alsIndex % Options.getInstance().getHintCandidateAlsColors().length];
                                }
                                for (int k = 0; k < step.getChains().size(); k++) {
                                    if (step.getType().isKrakenFish() && chainIndex == -1) {
                                        // Index 0 means show no chain at all
                                        continue;
                                    }
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
                            Color coloringColor = null;
                            if (coloringCandidateMap.containsKey(cellIndex * 10 + i)) {
                                //if (coloringMap.containsKey(cellIndex)) {
                                // coloring
                                coloringColor = Options.getInstance().coloringColors[coloringCandidateMap.get(cellIndex * 10 + i)];
                            }
                            Color oldColor = g2.getColor();
                            if (coloringColor != null) {
                                g2.setColor(coloringColor);
                                g2.fillRect(startX + shiftX + dcx - 2 * (ddy - ddx) / 3, startY + shiftY + dcy - 4 * ddy / 5 - 1, ddy, ddy);
                            }
                            if (hintColor != null) {
                                g2.setColor(hintColor);
                                g2.fillOval(startX + shiftX + dcx - 2 * (ddy - ddx) / 3, startY + shiftY + dcy - 4 * ddy / 5 - 1, ddy, ddy);
                            }
                            //g2.setColor(candColor);
                            g2.setColor(oldColor);
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
                if (step.getType().isKrakenFish() && chainIndex == -1) {
                    continue;
                }
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
                if (step.getType().isKrakenFish() && chainIndex == -1) {
                    continue;
                }
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
                if (step.getType().isKrakenFish() && chainIndex == -1) {
                    continue;
                }
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

    /**
     * Checks whether a candidate has been clicked. The correct values
     * for font metrics and candidate factors are ignored: the valid
     * candidate region is simple the corresponding ninth of the cell.
     * To adjust for the ignored values the "clickable region" of a candidate
     * is reduced by a sixth in every direction.
     *
     * @param p The point of a mouse click
     * @param line The line, in which p lies (may be -1 for "invalid")
     * @param col The column, in which p lies (may be -1 for "invalid")
     * @return The number of a candidate, if a click could be confirmed, or else -1
     */
    private int getCandidate(Point p, int line, int col) {
        // check if a cell was clicked
        if (line < 0 || col < 0) {
            // clicked between cells -> cant mean a candidate
            return -1;
        }
        // calculate the coordinates of the left upper corner of the cell
        //System.out.println("startSX = " + startSX + ", startSY = " + startSY + ", cellSize = " + cellSize + ", delta = " + delta);
        double startX = startSX + col * cellSize;
        if (col > 2) {
            startX += delta;
        }
        if (col > 5) {
            startX += delta;
        }
        double startY = startSY + line * cellSize;
        if (line > 2) {
            startY += delta;
        }
        if (line > 5) {
            startY += delta;
        }
        // now check if a candidate was clicked
        int candidate = -1;
        double cs3 = cellSize / 3.0;
        double dx = cs3 * 2.0 / 3.0;
        double leftDx = cs3 / 6.0;
        //System.out.println("p = " + p + ", startX = " + startX + ", startY = " + startY + ", dx = " + dx + ", leftDX = " + leftDx);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double sx = startX + i * cs3 + leftDx;
                double sy = startY + j * cs3 + leftDx;
                //System.out.println("cand = " + (j * 3 + i + 1) + ", sx = " + sx + ", sy = " + sy);
                if (p.x >= sx && p.x <= sx + dx &&
                        p.y >= sy && p.y <= sy + dx) {
                    // canddiate was clicked
                    candidate = j * 3 + i + 1;
                    //System.out.println("Candidate clicked: " + candidate);
                    return candidate;
                }
            }
        }
        return -1;
    }

    public void setActiveColor(int colorNumber) {
        aktColorIndex = colorNumber;
        if (aktColorIndex < 0) {
            // reset everything to normal
            if (oldCursor != null) {
                setCursor(oldCursor);
                colorCursor = null;
                colorCursorShift = null;
            }
        } else {
            // create new Cursors and set them
            if (oldCursor == null) {
                oldCursor = getCursor();
            }
            createColorCursors();
            setCursor(colorCursor);
        }
        // no region selectes are allowed in coloring
        clearRegion();
        updateCellZoomPanel();
    }

    public int getActiveColor() {
        return aktColorIndex;
    }

    public void resetActiveColor() {
        int temp = aktColorIndex;
        setActiveColor(-1);
        setActiveColor(temp);
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
    private javax.swing.JPopupMenu cellPopupMenu;
    private javax.swing.JMenuItem color1aMenuItem;
    private javax.swing.JMenuItem color1bMenuItem;
    private javax.swing.JMenuItem color2aMenuItem;
    private javax.swing.JMenuItem color2bMenuItem;
    private javax.swing.JMenuItem color3aMenuItem;
    private javax.swing.JMenuItem color3bMenuItem;
    private javax.swing.JMenuItem color4aMenuItem;
    private javax.swing.JMenuItem color4bMenuItem;
    private javax.swing.JMenuItem color5aMenuItem;
    private javax.swing.JMenuItem color5bMenuItem;
    private javax.swing.JMenuItem exclude1MenuItem;
    private javax.swing.JMenuItem exclude2MenuItem;
    private javax.swing.JMenuItem exclude3MenuItem;
    private javax.swing.JMenuItem exclude4MenuItem;
    private javax.swing.JMenuItem exclude5MenuItem;
    private javax.swing.JMenuItem exclude6MenuItem;
    private javax.swing.JMenuItem exclude7MenuItem;
    private javax.swing.JMenuItem exclude8MenuItem;
    private javax.swing.JMenuItem exclude9MenuItem;
    private javax.swing.JMenuItem excludeSeveralMenuItem;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JMenuItem make1MenuItem;
    private javax.swing.JMenuItem make2MenuItem;
    private javax.swing.JMenuItem make3MenuItem;
    private javax.swing.JMenuItem make4MenuItem;
    private javax.swing.JMenuItem make5MenuItem;
    private javax.swing.JMenuItem make6MenuItem;
    private javax.swing.JMenuItem make7MenuItem;
    private javax.swing.JMenuItem make8MenuItem;
    private javax.swing.JMenuItem make9MenuItem;
    // End of variables declaration//GEN-END:variables

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
        //System.out.println("undoStack: " + undoStack + "/" + undoStack.size());
        return undoStack.size() > 0;
    }

    public boolean redoPossible() {
        //System.out.println("redoStack: " + redoStack + "/" + redoStack.size());
        return redoStack.size() > 0;
    }

    public void undo() {
        if (undoPossible()) {
            redoStack.push(sudoku);
            sudoku = undoStack.pop();
            updateCellZoomPanel();
            mainFrame.check();
            repaint();
        }
    }

    public void redo() {
        if (redoPossible()) {
            undoStack.push(sudoku);
            sudoku = redoStack.pop();
            updateCellZoomPanel();
            mainFrame.check();
            repaint();
        }
    }

    /**
     * Clears undo/redo. Is called from {@link SolutionPanel} when a step
     * is double clicked.
     */
    public void clearUndoRedo() {
        undoStack.clear();
        redoStack.clear();
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
            // the sudoku must be set in the solver to reset the step list
            // (otherwise the result panels are not updated correctly)
            sudoku.setLevel(Options.getInstance().getDifficultyLevels()[DifficultyType.EASY.ordinal()]);
            sudoku.setScore(0);
            Sudoku tmpSudoku = sudoku.clone();
            if (! alreadySolved) {
                getSolver().setSudoku(tmpSudoku);
            }
            solvedSudoku = sudoku.clone();
            boolean unique = true;
            boolean sudokuCompleted = solvedSudoku.isSolved();
            if (! sudokuCompleted) {
                unique = creator.validSolution(solvedSudoku);
            }
            if (!unique) {
                JOptionPane.showMessageDialog(this,
                        java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.multiple_solutions"),
                        java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.invalid_puzzle"),
                        JOptionPane.ERROR_MESSAGE);
            } else {
                if (! sudokuCompleted) {
                    solvedSudoku = creator.getSolvedSudoku().clone();
                }
                if (!sudoku.checkSudoku(solvedSudoku)) {
                    JOptionPane.showMessageDialog(this,
                            java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.wrong_values"),
                            java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.invalid_puzzle"),
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    if (!alreadySolved) {
                        //Sudoku tmpSudoku = sudoku.clone();
                        //getSolver().setSudoku(tmpSudoku);
                        getSolver().solve(true);
                    }
//                sudoku.setLevel(tmpSudoku.getLevel());
//                sudoku.setScore(tmpSudoku.getScore());
                    sudoku.setLevel(getSolver().getSudoku().getLevel());
                    sudoku.setScore(getSolver().getSudoku().getScore());
                }
            }
        }
        updateCellZoomPanel();
        if (mainFrame != null) {
            mainFrame.check();
        }
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
        } else if (step.getType().isKrakenFish() && chainIndex > -1) {
            chainIndex--;
        }
        if (chainIndex >= 0 && chainIndex > step.getChainAnz() - 1) {
            chainIndex = -1;
        }
        //System.out.println("chainIndex = " + chainIndex);
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
            updateCellZoomPanel();
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
                
                String companionFileName = file.getPath();
                if (companionFileName.toLowerCase().endsWith(".png")) {
                    companionFileName = companionFileName.substring(0, companionFileName.length() - 4);
                }
                companionFileName += ".txt";
                PrintWriter cOut = new PrintWriter(new BufferedWriter(new FileWriter(companionFileName)));
                cOut.println(getSudokuString(ClipboardMode.CLUES_ONLY));
                cOut.println(getSudokuString(ClipboardMode.LIBRARY));
                cOut.println(getSudokuString(ClipboardMode.PM_GRID));
                if (step != null) {
                    cOut.println(getSudokuString(ClipboardMode.PM_GRID_WITH_STEP));
                }
                cOut.close();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, e.getLocalizedMessage(),
                        java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.error"),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * @return the colorCells
     */
    public boolean isColorCells() {
        return colorCells;
    }

    /**
     * @param colorCells the colorCells to set
     */
    public void setColorCells(boolean colorCells) {
        this.colorCells = colorCells;
        updateCellZoomPanel();
    }

    /**
     * Creates cursors for coloring: The color is specified by {@link #aktColorIndex},
     * cursors for both colors of the pair are created and stored in {@link #colorCursor}
     * and {@link #colorCursorShift}.
     */
    private void createColorCursors() {
        try {
            Point cursorHotSpot = new Point(2, 4);
            BufferedImage img1 = ImageIO.read(getClass().getResource("/img/c_color.png"));
            Graphics2D gImg1 = (Graphics2D) img1.getGraphics();
            gImg1.setColor(Options.getInstance().coloringColors[aktColorIndex]);
            gImg1.fillRect(19, 18, 12, 12);
            //System.out.println(aktColorIndex + "/" + Options.getInstance().coloringColors[aktColorIndex]);
            colorCursor = Toolkit.getDefaultToolkit().createCustomCursor(img1, cursorHotSpot, "c_strong");
            
            BufferedImage img2 = ImageIO.read(getClass().getResource("/img/c_color.png"));
            Graphics2D gImg2 = (Graphics2D) img2.getGraphics();
            if (aktColorIndex % 2 == 0) {
                gImg2.setColor(Options.getInstance().coloringColors[aktColorIndex + 1]);
            } else {
                gImg2.setColor(Options.getInstance().coloringColors[aktColorIndex - 1]);
            }
            //System.out.println(aktColorIndex + "/" + Options.getInstance().coloringColors[aktColorIndex + 1]);
            gImg2.fillRect(19, 18, 12, 12);
            colorCursorShift = Toolkit.getDefaultToolkit().createCustomCursor(img2, cursorHotSpot, "c_weak");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Checks whether the candidate in the given cell is a Hidden Single.
     * 
     * @param candidate
     * @param line
     * @param col
     * @return
     */
    private boolean isHiddenSingle(int candidate, int line, int col) {
        SimpleSolver ss = (SimpleSolver) solver.getSpecialisedSolver(SimpleSolver.class);
        List<SolutionStep> steps = ss.findAllHiddenXle(sudoku, 1, true);
        for (SolutionStep act : steps) {
            if (act.getType() == SolutionType.HIDDEN_SINGLE && act.getValues().get(0) == candidate &&
                    act.getIndices().get(0) == Sudoku.getIndex(line, col)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets all the color icons in the popup menu.
     */
    public void setColorIconsInPopupMenu() {
        setColorIconInPopupMenu(color1aMenuItem, Options.getInstance().coloringColors[0]);
        setColorIconInPopupMenu(color1bMenuItem, Options.getInstance().coloringColors[1]);
        setColorIconInPopupMenu(color2aMenuItem, Options.getInstance().coloringColors[2]);
        setColorIconInPopupMenu(color2bMenuItem, Options.getInstance().coloringColors[3]);
        setColorIconInPopupMenu(color3aMenuItem, Options.getInstance().coloringColors[4]);
        setColorIconInPopupMenu(color3bMenuItem, Options.getInstance().coloringColors[5]);
        setColorIconInPopupMenu(color4aMenuItem, Options.getInstance().coloringColors[6]);
        setColorIconInPopupMenu(color4bMenuItem, Options.getInstance().coloringColors[7]);
        setColorIconInPopupMenu(color5aMenuItem, Options.getInstance().coloringColors[8]);
        setColorIconInPopupMenu(color5bMenuItem, Options.getInstance().coloringColors[9]);
    }

    /**
     * Creates an icon (rectangle showing color) and sets it on the MenuItem.
     * @param item
     * @param color
     */
    private void setColorIconInPopupMenu(JMenuItem item, Color color) {
        try {
            BufferedImage img = ImageIO.read(getClass().getResource("/img/c_icon.png"));
            Graphics2D gImg = (Graphics2D) img.getGraphics();
            gImg.setColor(color);
            gImg.fillRect(1, 1, 12, 12);
            item.setIcon(new ImageIcon(img));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Collects the intersection or union of all valid candidates in all
     * selected cells. Used to adjust the popup menu.
     * @param intersection
     * @return
     */
    private SudokuSet collectCandidates(boolean intersection) {
        SudokuSet resultSet = new SudokuSet();
        SudokuSet tmpSet = new SudokuSet();
        if (intersection) {
            resultSet.setAll();
        }
        if (selectedCells.isEmpty()) {
            if (sudoku.getCell(aktLine, aktCol).getValue() == 0) {
                // get candidates only when cell is not set!
                sudoku.getCell(aktLine, aktCol).getCandidateSet(tmpSet, candidateMode);
                if (intersection) {
                    resultSet.and(tmpSet);
                } else {
                    resultSet.or(tmpSet);
                }
            }
        } else {
            for (int index : selectedCells) {
                if (sudoku.getCell(index).getValue() == 0) {
                    // get candidates only when cell is not set!
                    sudoku.getCell(index).getCandidateSet(tmpSet, candidateMode);
                    if (intersection) {
                        resultSet.and(tmpSet);
                    } else {
                        resultSet.or(tmpSet);
                    }
                }
            }
        }
        return resultSet;
    }

    /**
     * Brings up the popup menu for the cell at line/col. If the cell is
     * already set, no menu is displayed. For every other cell the contents
     * of the menu is restricted to sensible actions.<br>
     * If a region of cells is selected, "Make x" is restricted to
     * candidates, that appear in all cells, "Exclude x" is restricted
     * to the combined set of candidates in all cells.
     * @param line
     * @param col
     */
    private void showPopupMenu(int line, int col) {
        jSeparator2.setVisible(true);
        SudokuCell cell = sudoku.getCell(line, col);
        if (cell.getValue() != 0 && selectedCells.isEmpty()) {
            // cell is already set -> no popup!
            return;
        }
        if (selectedCells.isEmpty()) {
            aktLine = line;
            aktCol = col;
        }
        excludeSeveralMenuItem.setVisible(false);
        for (int i = 1; i <= 9; i++) {
            makeItems[i - 1].setVisible(false);
            excludeItems[i - 1].setVisible(false);
        }
        SudokuSet candSet = collectCandidates(true);
        for (int i = 0; i < candSet.size(); i++) {
            makeItems[candSet.get(i) - 1].setVisible(true);
        }
        candSet = collectCandidates(false);
        if (candSet.size() > 1) {
            if (candSet.size() > 2) {
                excludeSeveralMenuItem.setVisible(true);
            }
            for (int i = 0; i < candSet.size(); i++) {
                excludeItems[candSet.get(i) - 1].setVisible(true);
            }
        } else {
            jSeparator2.setVisible(false);
        }
        cellPopupMenu.show(this, getX(line, col) + cellSize, getY(line, col));
    }

    /**
     * Handles activation of a "Make x" menu item. The selected number is
     * set in all selected cells (if they are not already set).
     * @param menuItem
     */
    private void popupSetCell(JMenuItem menuItem) {
        int candidate = -1;
        for (int i = 0; i < makeItems.length; i++) {
            if (makeItems[i] == menuItem) {
                candidate = i + 1;
                break;
            }
        }
        if (candidate != -1) {
            undoStack.push(sudoku.clone());
            boolean changed = false;
            if (selectedCells.isEmpty()) {
                if (sudoku.getCell(aktLine, aktCol).getValue() == 0) {
                    setCell(aktLine, aktCol, candidate);
                    changed = true;
                }
            } else {
                for (int index : selectedCells) {
                    if (sudoku.getCell(index).getValue() == 0) {
                        setCell(Sudoku.getLine(index), Sudoku.getCol(index), candidate);
                        changed = true;
                    }
                }
            }
            if (changed) {
                redoStack.clear();
            } else {
                undoStack.pop();
            }
            updateCellZoomPanel();
            mainFrame.fixFocus();
            repaint();
        }
    }

    /**
     * Removes the candidate from all selected cells.
     * @param candidate
     * @return true if sudoku is changed, false otherwise
     */
    private boolean removeCandidateFromActiveCells(int candidate) {
        boolean changed = false;
        if (selectedCells.isEmpty()) {
            SudokuCell cell = sudoku.getCell(aktLine, aktCol);
            if (cell.getValue() == 0 && cell.isCandidate(candidateMode, candidate)) {
                sudoku.setCandidate(aktLine, aktCol, candidateMode, candidate, false);
                changed = true;
            }
        } else {
            for (int index : selectedCells) {
                SudokuCell cell = sudoku.getCell(index);
                if (cell.getValue() == 0 && cell.isCandidate(candidateMode, candidate)) {
                    sudoku.setCandidate(index, candidateMode, candidate, false);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Handles candidate changed done in {@link CellZoomPanel}. Should not be
     * used otherwise.
     * @param candidate
     */
    public void toggleOrRemoveCandidateFromCellZoomPanel(int candidate) {
        if (candidate != -1) {
            undoStack.push(sudoku.clone());
            boolean changed = false;
            if (selectedCells.isEmpty()) {
                SudokuCell cell = sudoku.getCell(aktLine, aktCol);
                if (cell.isCandidate(candidateMode, candidate)) {
                    sudoku.setCandidate(aktLine, aktCol, candidateMode, candidate, false);
                } else {
                    sudoku.setCandidate(aktLine, aktCol, candidateMode, candidate, true);
                }
                changed = true;
            } else {
                changed = removeCandidateFromActiveCells(candidate);
            }
            if (changed) {
                redoStack.clear();
            } else {
                undoStack.pop();
            }
            updateCellZoomPanel();
            mainFrame.check();
            repaint();
        }
    }

    /**
     * Handles activation of an "Exclude x" menu item. The selected number is
     * deleted in all selected cells (if they present).
     * @param menuItem
     */
    private void popupExcludeCandidate(JMenuItem menuItem) {
        int candidate = -1;
        for (int i = 0; i < excludeItems.length; i++) {
            if (excludeItems[i] == menuItem) {
                candidate = i + 1;
                break;
            }
        }
        if (candidate != -1) {
            undoStack.push(sudoku.clone());
            boolean changed = removeCandidateFromActiveCells(candidate);
            if (changed) {
                redoStack.clear();
            } else {
                undoStack.pop();
            }
            updateCellZoomPanel();
            mainFrame.check();
            repaint();
        }
    }

    /**
     * Handles activation of an "Toggle color x" menu item. Th color is set in the
     * cell if not present or deleted if already present.
     * @param menuItem
     */
    private void popupToggleColor(JMenuItem menuItem) {
        int color = -1;
        for (int i = 0; i < toggleColorItems.length; i++) {
            if (toggleColorItems[i] == menuItem) {
                color = i;
                break;
            }
        }
        if (color != -1) {
            //removeCandidateFromActiveCells(color);
            // coloring is active
            handleColoring(aktLine, aktCol, -1, color);
            updateCellZoomPanel();
            mainFrame.check();
            repaint();
        }
    }

    /**
     * @return the cellZoomPanel
     */
    public CellZoomPanel getCellZoomPanel() {
        return cellZoomPanel;
    }

    /**
     * @param cellZoomPanel the cellZoomPanel to set
     */
    public void setCellZoomPanel(CellZoomPanel cellZoomPanel) {
        this.cellZoomPanel = cellZoomPanel;
    }

    /**
     * Update the {@link CellZoomPanel}. FOr more information see
     * {@link CellZoomPanel#update(sudoku.SudokuSet, sudoku.SudokuSet, int, boolean, java.util.SortedMap, java.util.SortedMap) CellZoomPanel.update()}.
     */
    private void updateCellZoomPanel() {
        if (cellZoomPanel != null) {
            SudokuCell cell = sudoku.getCell(aktLine, aktCol);
            boolean singleCell = selectedCells.isEmpty() && cell.getValue() == 0;
            int index = Sudoku.getIndex(aktLine, aktCol);
            if (aktColorIndex == -1) {
                // normal operation -> collect candidates for selected cell(s)
                if (cell.getValue() != 0 && selectedCells.isEmpty()) {
                    // cell is already set -> nothing can be selected
                    cellZoomPanel.update(SudokuSetBase.EMPTY_SET, SudokuSetBase.EMPTY_SET, -1, index, false, singleCell, null, null);
                    return;
                } else {
                    SudokuSet valueSet = collectCandidates(true);
                    SudokuSet candSet = collectCandidates(false);
                    cellZoomPanel.update(valueSet, candSet, -1, index, false, singleCell, null, null);
                    return;
                }
            } else {
                if (! selectedCells.isEmpty() || (selectedCells.isEmpty() && cell.getValue() != 0)) {
                    // no coloring, when set of cells is selected
                    cellZoomPanel.update(SudokuSetBase.EMPTY_SET, SudokuSetBase.EMPTY_SET, aktColorIndex, index, colorCells, singleCell, null, null);
                    return;
                } else  {
                    SudokuSet valueSet = collectCandidates(true);
                    SudokuSet candSet = collectCandidates(false);
                    cellZoomPanel.update(valueSet, candSet, aktColorIndex, index, colorCells, singleCell, coloringMap, coloringCandidateMap);
                    return;
                }
            }
        }
    }

    /**
     * Gets a 81 character string. For every digit in that string, the corresponding cell is set
     * as a given.
     *
     * @param givens
     */
    public void setGivens(String givens) {
        undoStack.push(sudoku.clone());
        sudoku.setGivens(givens);
        updateCellZoomPanel();
        repaint();
        mainFrame.check();
    }
}
