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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseEvent;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.filechooser.FileFilter;

/**
 *
 * @author  Bernhard Hobiger
 */
public class MainFrame extends javax.swing.JFrame implements FlavorListener {

    public static final String VERSION = "HoDoKu - v1.0";
    private SudokuPanel sudokuPanel;
    private DifficultyLevel level = Options.getInstance().getDifficultyLevels()[DifficultyType.EASY.ordinal()];
    private Cursor[] numberCursors = new Cursor[10];
    private JLabel[] statusLabels = new JLabel[10];
    //private JLabel[] statusCandidateLabels = new JLabel[3];
    private JToggleButton[] toggleButtons = new JToggleButton[9];
    private JRadioButtonMenuItem[] levelMenuItems = new JRadioButtonMenuItem[5];
    private Color statusLabelAktColor = Color.WHITE;
    private Color statusLabelNormColor = Color.BLACK;
    private boolean oldShowDeviations = true;
    private SplitPanel splitPanel = new SplitPanel();
    private SummaryPanel summaryPanel = new SummaryPanel(this);
    private SolutionPanel solutionPanel = new SolutionPanel(this);
    private AllStepsPanel allStepsPanel = new AllStepsPanel(this, null);
    private JTabbedPane tabPane = new JTabbedPane();    // Ausdruck
    private PageFormat pageFormat = null;
    private PrinterJob job = null;
    //private File bildFile = new File("C:\\0_temp\\test.png");
    private File bildFile = new File(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.PNG_path"));
    private double bildSize = 400;
    private int bildAuflösung = 96;
    private int bildEinheit = 2;    // File/IO
    private MyFileFilter[] fileFilters = new MyFileFilter[]{
        new MyFileFilter(0), new MyFileFilter(1)
    };
    private MyCaretListener caretListener = new MyCaretListener();
    private boolean outerSplitPaneInitialized = false; // used to adjust divider bar at startup!
    private int resetHDivLocLoc = -1;            // when resetting windows, the divider location gets changed by some layout function
    private boolean resetHDivLoc = false;   // adjust DividerLocation after change
    private long resetHDivLocTicks = 0;     // only adjust within a second or so
    private String configFileExt = java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.config_file_ext");
    private String solutionFileExt = java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.solution_file_ext");
    private MessageFormat formatter = new MessageFormat("");

    /** Creates new form MainFrame */
    public MainFrame() {
        initComponents();
        setTitle(VERSION);

        Color lafMenuBackColor = UIManager.getColor("textHighlight");
        Color lafMenuColor = UIManager.getColor("textHighlightText");
        Color lafMenuInactiveColor = UIManager.getColor("textInactiveText");
        if (lafMenuBackColor == null) {
            lafMenuBackColor = Color.BLUE;
        }
        if (lafMenuColor == null) {
            lafMenuColor = Color.BLACK;
        }
        if (lafMenuInactiveColor == null) {
            lafMenuInactiveColor = Color.WHITE;
        }
        statusLinePanel.setBackground(lafMenuBackColor);
        statusLabelLevel.setForeground(lafMenuColor);
        statusLabelAktColor = lafMenuInactiveColor;
        statusLabelNormColor = lafMenuColor;
        summaryPanel.setTitleLabelColors(lafMenuColor, lafMenuBackColor);
        solutionPanel.setTitleLabelColors(lafMenuColor, lafMenuBackColor);

//        UIDefaults def = UIManager.getDefaults();
//        Enumeration defEnum = def.keys();
//        SortedSet<String> defSet = new TreeSet<String>();
//        while (defEnum.hasMoreElements()) {
//            Object tmp = defEnum.nextElement();
//            if (tmp instanceof String) {
//                defSet.add((String)tmp);
//            }
//        }
//        for (String key : defSet) {
//            System.out.println(key + ": " + def.get(key));
//        }

        Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
        clip.addFlavorListener(this);
        adjustPasteMenuItem();

        sudokuPanel = new SudokuPanel(this);
        outerSplitPane.setLeftComponent(splitPanel);
        splitPanel.setSplitPane(sudokuPanel, null);

        tabPane.addTab(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.summary"), summaryPanel);
        tabPane.addTab(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.solution_path"), solutionPanel);
        tabPane.addTab(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.all_steps"), allStepsPanel);
        tabPane.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tabPaneMouseClicked(evt);
            }
        });

        if (Options.getInstance().saveWindowLayout) {
            setWindowLayout(false);
        } else {
            setWindowLayout(true);
        }
        outerSplitPaneInitialized = false;
        if (Options.getInstance().initialXPos != -1 && Options.getInstance().initialYPos != -1) {
            Toolkit t = Toolkit.getDefaultToolkit();
            Dimension screenSize = t.getScreenSize();
            int x = Options.getInstance().initialXPos;
            int y = Options.getInstance().initialYPos;
            if (x + getWidth() > screenSize.width) {
                x = screenSize.width - getWidth() - 10;
            }
            if (y + getHeight() > screenSize.height) {
                y = screenSize.height - getHeight() - 10;
            }
            if (x < 0) {
                x = 0;
            }
            if (y < 0) {
                y = 0;
            }
            setLocation(x, y);
        }

        // Level-Menüs und Combo-Box
        levelMenuItems[0] = levelLeichtMenuItem;
        levelMenuItems[1] = levelMittelMenuItem;
        levelMenuItems[2] = levelKniffligMenuItem;
        levelMenuItems[3] = levelSchwerMenuItem;
        levelMenuItems[4] = levelExtremMenuItem;
        FontMetrics metrics = levelComboBox.getFontMetrics(levelComboBox.getFont());
        int miWidth = 0;
        int miHeight = metrics.getHeight();
        Set<Character> mnemonics = new HashSet<Character>();
        for (int i = 1; i < DifficultyType.values().length; i++) {
            levelMenuItems[i - 1].setText(Options.getInstance().getDifficultyLevels()[i].getName());
            char mnemonic = 0;
            boolean mnemonicFound = false;
            for (int j = 0; j < Options.getInstance().getDifficultyLevels()[i].getName().length(); j++) {
                mnemonic = Options.getInstance().getDifficultyLevels()[i].getName().charAt(j);
                if (!mnemonics.contains(mnemonic)) {
                    mnemonicFound = true;
                    break;
                }
            }
            if (mnemonicFound) {
                mnemonics.add(mnemonic);
                levelMenuItems[i - 1].setMnemonic(mnemonic);
            }
            levelComboBox.addItem(Options.getInstance().getDifficultyLevels()[i].getName());
            int aktWidth = metrics.stringWidth(Options.getInstance().getDifficultyLevels()[i].getName());
            //System.out.println(Options.getInstance().getDifficultyLevels()[i].getName() + ": " + aktWidth);
            if (aktWidth > miWidth) {
                miWidth = aktWidth;
            }
        }
        mnemonics = null;
        // in Windows miWidth = 35, miHeight = 14; size = 60/20
        if (miWidth > 35) {
            Dimension newLevelSize = new Dimension(60 + (miWidth - 35) + 8, 20 + (miHeight - 14) + 3);
            levelComboBox.setMaximumSize(newLevelSize);
            levelComboBox.setMinimumSize(newLevelSize);
            levelComboBox.setPreferredSize(newLevelSize);
            levelComboBox.setSize(newLevelSize);
        //System.out.println("Size changed to: " + newLevelSize);
        //jToolBar1.doLayout();
        //repaint();
        }

        // Menüzustand prüfen, übernimmt Werte von SudokuPanel; muss am Anfang stehen,
        // weil die Werte später in der Methode verwendet werden
        check();

        // Die Panels für die Zahlen in ein Array stecken, ist später einfacher
        statusLabels[0] = statusLabelAus;
        statusLabels[1] = statusLabel1;
        statusLabels[2] = statusLabel2;
        statusLabels[3] = statusLabel3;
        statusLabels[4] = statusLabel4;
        statusLabels[5] = statusLabel5;
        statusLabels[6] = statusLabel6;
        statusLabels[7] = statusLabel7;
        statusLabels[8] = statusLabel8;
        statusLabels[9] = statusLabel9;
        setStatusLabel(0);

        // gleiches für ToggleButtons
        toggleButtons[0] = f1ToggleButton;
        toggleButtons[1] = f2ToggleButton;
        toggleButtons[2] = f3ToggleButton;
        toggleButtons[3] = f4ToggleButton;
        toggleButtons[4] = f5ToggleButton;
        toggleButtons[5] = f6ToggleButton;
        toggleButtons[6] = f7ToggleButton;
        toggleButtons[7] = f8ToggleButton;
        toggleButtons[8] = f9ToggleButton;
        setToggleButton(null);

        // Maus-Icons mit Zahlen erzeugen
        createNumberCursors();

        // Caret-Listener for display of Forcing Chains
        hinweisTextArea.addCaretListener(caretListener);

        fixFocus();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        levelButtonGroup = new javax.swing.ButtonGroup();
        eingebenSpielenButtonGroup = new javax.swing.ButtonGroup();
        viewButtonGroup = new javax.swing.ButtonGroup();
        statusLinePanel = new javax.swing.JPanel();
        statusLabel1 = new javax.swing.JLabel();
        statusLabel2 = new javax.swing.JLabel();
        statusLabel3 = new javax.swing.JLabel();
        statusLabel4 = new javax.swing.JLabel();
        statusLabel5 = new javax.swing.JLabel();
        statusLabel6 = new javax.swing.JLabel();
        statusLabel7 = new javax.swing.JLabel();
        statusLabel8 = new javax.swing.JLabel();
        statusLabel9 = new javax.swing.JLabel();
        statusLabelAus = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        statusLabelLevel = new javax.swing.JLabel();
        jToolBar1 = new javax.swing.JToolBar();
        undoToolButton = new javax.swing.JButton();
        redoToolButton = new javax.swing.JButton();
        jSeparator9 = new javax.swing.JSeparator();
        neuesSpielToolButton = new javax.swing.JButton();
        jSeparator12 = new javax.swing.JSeparator();
        levelComboBox = new javax.swing.JComboBox();
        jSeparator13 = new javax.swing.JSeparator();
        jSeparator11 = new javax.swing.JSeparator();
        redGreenToggleButton = new javax.swing.JToggleButton();
        f1ToggleButton = new javax.swing.JToggleButton();
        f2ToggleButton = new javax.swing.JToggleButton();
        f3ToggleButton = new javax.swing.JToggleButton();
        f4ToggleButton = new javax.swing.JToggleButton();
        f5ToggleButton = new javax.swing.JToggleButton();
        f6ToggleButton = new javax.swing.JToggleButton();
        f7ToggleButton = new javax.swing.JToggleButton();
        f8ToggleButton = new javax.swing.JToggleButton();
        f9ToggleButton = new javax.swing.JToggleButton();
        outerSplitPane = new javax.swing.JSplitPane();
        hintPanel = new javax.swing.JPanel();
        neuerHinweisButton = new javax.swing.JButton();
        hinweisAusführenButton = new javax.swing.JButton();
        hinweisKonfigurierenButton = new javax.swing.JButton();
        hinweisAbbrechenButton = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        hinweisTextArea = new javax.swing.JTextArea();
        jMenuBar1 = new javax.swing.JMenuBar();
        dateiMenu = new javax.swing.JMenu();
        neuMenuItem = new javax.swing.JMenuItem();
        jSeparator14 = new javax.swing.JSeparator();
        loadMenuItem = new javax.swing.JMenuItem();
        saveAsMenuItem = new javax.swing.JMenuItem();
        jSeparator4 = new javax.swing.JSeparator();
        seiteEinrichtenMenuItem = new javax.swing.JMenuItem();
        druckenMenuItem = new javax.swing.JMenuItem();
        speichernAlsBildMenuItem = new javax.swing.JMenuItem();
        jSeparator15 = new javax.swing.JSeparator();
        spielEingebenMenuItem = new javax.swing.JRadioButtonMenuItem();
        spielenMenuItem = new javax.swing.JRadioButtonMenuItem();
        jSeparator16 = new javax.swing.JSeparator();
        beendenMenuItem = new javax.swing.JMenuItem();
        bearbeitenMenu = new javax.swing.JMenu();
        undoMenuItem = new javax.swing.JMenuItem();
        redoMenuItem = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JSeparator();
        copyCluesMenuItem = new javax.swing.JMenuItem();
        copyFilledMenuItem = new javax.swing.JMenuItem();
        copyPmGridMenuItem = new javax.swing.JMenuItem();
        copyPmGridWithStepMenuItem = new javax.swing.JMenuItem();
        copyLibraryMenuItem = new javax.swing.JMenuItem();
        pasteMenuItem = new javax.swing.JMenuItem();
        jSeparator17 = new javax.swing.JSeparator();
        resetSpielMenuItem = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JSeparator();
        configMenuItem = new javax.swing.JMenuItem();
        optionenMenu = new javax.swing.JMenu();
        showCandidatesMenuItem = new javax.swing.JCheckBoxMenuItem();
        showWrongValuesMenuItem = new javax.swing.JCheckBoxMenuItem();
        showDeviationsMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator10 = new javax.swing.JSeparator();
        levelMenu = new javax.swing.JMenu();
        levelLeichtMenuItem = new javax.swing.JRadioButtonMenuItem();
        levelMittelMenuItem = new javax.swing.JRadioButtonMenuItem();
        levelKniffligMenuItem = new javax.swing.JRadioButtonMenuItem();
        levelSchwerMenuItem = new javax.swing.JRadioButtonMenuItem();
        levelExtremMenuItem = new javax.swing.JRadioButtonMenuItem();
        rätselMenu = new javax.swing.JMenu();
        vageHintMenuItem = new javax.swing.JMenuItem();
        mediumHintMenuItem = new javax.swing.JMenuItem();
        lösungsSchrittMenuItem = new javax.swing.JMenuItem();
        jSeparator5 = new javax.swing.JSeparator();
        alleHiddenSinglesSetzenMenuItem = new javax.swing.JMenuItem();
        ansichtMenu = new javax.swing.JMenu();
        sudokuOnlyMenuItem = new javax.swing.JRadioButtonMenuItem();
        jSeparator18 = new javax.swing.JSeparator();
        summaryMenuItem = new javax.swing.JRadioButtonMenuItem();
        solutionMenuItem = new javax.swing.JRadioButtonMenuItem();
        allStepsMenuItem = new javax.swing.JRadioButtonMenuItem();
        jSeparator7 = new javax.swing.JSeparator();
        resetViewMenuItem = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        keyMenuItem = new javax.swing.JMenuItem();
        jSeparator6 = new javax.swing.JSeparator();
        aboutMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("intl/MainFrame"); // NOI18N
        setTitle(bundle.getString("MainFrame.title")); // NOI18N
        setIconImage(getIcon());
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        statusLinePanel.setBackground(new java.awt.Color(0, 153, 255));
        statusLinePanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        statusLabel1.setFont(new java.awt.Font("Tahoma", 1, 14));
        statusLabel1.setText(bundle.getString("MainFrame.statusLabel1.text")); // NOI18N
        statusLabel1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                statusLabel1MouseClicked(evt);
            }
        });
        statusLinePanel.add(statusLabel1);

        statusLabel2.setFont(new java.awt.Font("Tahoma", 1, 14));
        statusLabel2.setText(bundle.getString("MainFrame.statusLabel2.text")); // NOI18N
        statusLabel2.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                statusLabel2MouseClicked(evt);
            }
        });
        statusLinePanel.add(statusLabel2);

        statusLabel3.setFont(new java.awt.Font("Tahoma", 1, 14));
        statusLabel3.setText(bundle.getString("MainFrame.statusLabel3.text")); // NOI18N
        statusLabel3.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                statusLabel3MouseClicked(evt);
            }
        });
        statusLinePanel.add(statusLabel3);

        statusLabel4.setFont(new java.awt.Font("Tahoma", 1, 14));
        statusLabel4.setText(bundle.getString("MainFrame.statusLabel4.text")); // NOI18N
        statusLabel4.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                statusLabel4MouseClicked(evt);
            }
        });
        statusLinePanel.add(statusLabel4);

        statusLabel5.setFont(new java.awt.Font("Tahoma", 1, 14));
        statusLabel5.setText(bundle.getString("MainFrame.statusLabel5.text")); // NOI18N
        statusLabel5.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                statusLabel5MouseClicked(evt);
            }
        });
        statusLinePanel.add(statusLabel5);

        statusLabel6.setFont(new java.awt.Font("Tahoma", 1, 14));
        statusLabel6.setText(bundle.getString("MainFrame.statusLabel6.text")); // NOI18N
        statusLabel6.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                statusLabel6MouseClicked(evt);
            }
        });
        statusLinePanel.add(statusLabel6);

        statusLabel7.setFont(new java.awt.Font("Tahoma", 1, 14));
        statusLabel7.setText(bundle.getString("MainFrame.statusLabel7.text")); // NOI18N
        statusLabel7.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                statusLabel7MouseClicked(evt);
            }
        });
        statusLinePanel.add(statusLabel7);

        statusLabel8.setFont(new java.awt.Font("Tahoma", 1, 14));
        statusLabel8.setText(bundle.getString("MainFrame.statusLabel8.text")); // NOI18N
        statusLabel8.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                statusLabel8MousePressed(evt);
            }
        });
        statusLinePanel.add(statusLabel8);

        statusLabel9.setFont(new java.awt.Font("Tahoma", 1, 14));
        statusLabel9.setText(bundle.getString("MainFrame.statusLabel9.text")); // NOI18N
        statusLabel9.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                statusLabel9MouseClicked(evt);
            }
        });
        statusLinePanel.add(statusLabel9);

        statusLabelAus.setFont(new java.awt.Font("Tahoma", 1, 14));
        statusLabelAus.setText(bundle.getString("MainFrame.statusLabelAus.text")); // NOI18N
        statusLabelAus.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                statusLabelAusMouseClicked(evt);
            }
        });
        statusLinePanel.add(statusLabelAus);

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);
        jSeparator1.setPreferredSize(new java.awt.Dimension(2, 17));
        statusLinePanel.add(jSeparator1);

        statusLabelLevel.setFont(new java.awt.Font("Tahoma", 1, 14));
        statusLinePanel.add(statusLabelLevel);

        getContentPane().add(statusLinePanel, java.awt.BorderLayout.SOUTH);

        undoToolButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/NavBack.png"))); // NOI18N
        undoToolButton.setEnabled(false);
        undoToolButton.setRequestFocusEnabled(false);
        undoToolButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                undoToolButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(undoToolButton);

        redoToolButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/NavForward.png"))); // NOI18N
        redoToolButton.setEnabled(false);
        redoToolButton.setRequestFocusEnabled(false);
        redoToolButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                redoToolButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(redoToolButton);

        jSeparator9.setOrientation(javax.swing.SwingConstants.VERTICAL);
        jSeparator9.setMaximumSize(new java.awt.Dimension(5, 32767));
        jToolBar1.add(jSeparator9);

        neuesSpielToolButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/neuesSudoku.png"))); // NOI18N
        neuesSpielToolButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                neuesSpielToolButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(neuesSpielToolButton);

        jSeparator12.setEnabled(false);
        jSeparator12.setMaximumSize(new java.awt.Dimension(3, 0));
        jToolBar1.add(jSeparator12);

        levelComboBox.setMaximumSize(new java.awt.Dimension(80, 20));
        levelComboBox.setMinimumSize(new java.awt.Dimension(15, 8));
        levelComboBox.setPreferredSize(new java.awt.Dimension(20, 10));
        levelComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                levelComboBoxActionPerformed(evt);
            }
        });
        jToolBar1.add(levelComboBox);

        jSeparator13.setMaximumSize(new java.awt.Dimension(3, 0));
        jToolBar1.add(jSeparator13);

        jSeparator11.setOrientation(javax.swing.SwingConstants.VERTICAL);
        jSeparator11.setMaximumSize(new java.awt.Dimension(5, 32767));
        jToolBar1.add(jSeparator11);

        redGreenToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/rgDeselected.png"))); // NOI18N
        redGreenToggleButton.setSelected(true);
        redGreenToggleButton.setSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/img/rgSelected.png"))); // NOI18N
        redGreenToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                redGreenToggleButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(redGreenToggleButton);

        f1ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_1.png"))); // NOI18N
        f1ToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                f1ToggleButtonActionPerformed1(evt);
            }
        });
        jToolBar1.add(f1ToggleButton);

        f2ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_2.png"))); // NOI18N
        f2ToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                f1ToggleButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(f2ToggleButton);

        f3ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_3.png"))); // NOI18N
        f3ToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                f1ToggleButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(f3ToggleButton);

        f4ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_4.png"))); // NOI18N
        f4ToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                f1ToggleButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(f4ToggleButton);

        f5ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_5.png"))); // NOI18N
        f5ToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                f1ToggleButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(f5ToggleButton);

        f6ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_6.png"))); // NOI18N
        f6ToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                f1ToggleButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(f6ToggleButton);

        f7ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_7.png"))); // NOI18N
        f7ToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                f1ToggleButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(f7ToggleButton);

        f8ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_8.png"))); // NOI18N
        f8ToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                f1ToggleButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(f8ToggleButton);

        f9ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_9.png"))); // NOI18N
        f9ToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                f1ToggleButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(f9ToggleButton);

        getContentPane().add(jToolBar1, java.awt.BorderLayout.NORTH);

        outerSplitPane.setDividerLocation(525);
        outerSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        outerSplitPane.setResizeWeight(1.0);
        outerSplitPane.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                outerSplitPanePropertyChange(evt);
            }
        });

        hintPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("MainFrame.hintPanel.border.title"))); // NOI18N
        hintPanel.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                hintPanelPropertyChange(evt);
            }
        });

        neuerHinweisButton.setMnemonic('n');
        neuerHinweisButton.setText(bundle.getString("MainFrame.neuerHinweisButton.text")); // NOI18N
        neuerHinweisButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                neuerHinweisButtonActionPerformed(evt);
            }
        });

        hinweisAusführenButton.setMnemonic('f');
        hinweisAusführenButton.setText(bundle.getString("MainFrame.hinweisAusführenButton.text")); // NOI18N
        hinweisAusführenButton.setEnabled(false);
        hinweisAusführenButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hinweisAusführenButtonActionPerformed(evt);
            }
        });

        hinweisKonfigurierenButton.setMnemonic('k');
        hinweisKonfigurierenButton.setText(bundle.getString("MainFrame.hinweisKonfigurierenButton.text")); // NOI18N
        hinweisKonfigurierenButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hinweisKonfigurierenButtonActionPerformed(evt);
            }
        });

        hinweisAbbrechenButton.setMnemonic('a');
        hinweisAbbrechenButton.setText(bundle.getString("MainFrame.hinweisAbbrechenButton.text")); // NOI18N
        hinweisAbbrechenButton.setEnabled(false);
        hinweisAbbrechenButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hinweisAbbrechenButtonActionPerformed(evt);
            }
        });

        hinweisTextArea.setColumns(20);
        hinweisTextArea.setEditable(false);
        hinweisTextArea.setFont(new java.awt.Font("Arial", 0, 10));
        hinweisTextArea.setLineWrap(true);
        hinweisTextArea.setRows(5);
        hinweisTextArea.setWrapStyleWord(true);
        jScrollPane1.setViewportView(hinweisTextArea);

        javax.swing.GroupLayout hintPanelLayout = new javax.swing.GroupLayout(hintPanel);
        hintPanel.setLayout(hintPanelLayout);
        hintPanelLayout.setHorizontalGroup(
            hintPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, hintPanelLayout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 298, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(hintPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(neuerHinweisButton)
                    .addComponent(hinweisKonfigurierenButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(hintPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(hinweisAusführenButton)
                    .addComponent(hinweisAbbrechenButton)))
        );

        hintPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {hinweisAbbrechenButton, hinweisAusführenButton, hinweisKonfigurierenButton, neuerHinweisButton});

        hintPanelLayout.setVerticalGroup(
            hintPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(hintPanelLayout.createSequentialGroup()
                .addGroup(hintPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hinweisAusführenButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(neuerHinweisButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(hintPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hinweisAbbrechenButton)
                    .addComponent(hinweisKonfigurierenButton)))
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 52, Short.MAX_VALUE)
        );

        hintPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {hinweisAbbrechenButton, hinweisAusführenButton, hinweisKonfigurierenButton, neuerHinweisButton});

        outerSplitPane.setRightComponent(hintPanel);

        getContentPane().add(outerSplitPane, java.awt.BorderLayout.CENTER);

        dateiMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.dateiMenuMnemonic").charAt(0));
        dateiMenu.setText(bundle.getString("MainFrame.dateiMenu.text")); // NOI18N

        neuMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_MASK));
        neuMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.neuMenuItemMnemonic").charAt(0));
        neuMenuItem.setText(bundle.getString("MainFrame.neuMenuItem.text")); // NOI18N
        neuMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                neuMenuItemActionPerformed(evt);
            }
        });
        dateiMenu.add(neuMenuItem);
        dateiMenu.add(jSeparator14);

        loadMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_MASK));
        loadMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.loadMenuItemMnemonic").charAt(0));
        loadMenuItem.setText(bundle.getString("MainFrame.loadMenuItem.text")); // NOI18N
        loadMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadMenuItemActionPerformed(evt);
            }
        });
        dateiMenu.add(loadMenuItem);

        saveAsMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_MASK));
        saveAsMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.saveAsMenuItemMnemonic").charAt(0));
        saveAsMenuItem.setText(bundle.getString("MainFrame.saveAsMenuItem.text")); // NOI18N
        saveAsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveAsMenuItemActionPerformed(evt);
            }
        });
        dateiMenu.add(saveAsMenuItem);
        dateiMenu.add(jSeparator4);

        seiteEinrichtenMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.seiteEinrichtenMenuItemMnemonic").charAt(0));
        seiteEinrichtenMenuItem.setText(bundle.getString("MainFrame.seiteEinrichtenMenuItem.text")); // NOI18N
        seiteEinrichtenMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                seiteEinrichtenMenuItemActionPerformed(evt);
            }
        });
        dateiMenu.add(seiteEinrichtenMenuItem);

        druckenMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_MASK));
        druckenMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.druckenMenuItemMnemonic").charAt(0));
        druckenMenuItem.setText(bundle.getString("MainFrame.druckenMenuItem.text")); // NOI18N
        druckenMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                druckenMenuItemActionPerformed(evt);
            }
        });
        dateiMenu.add(druckenMenuItem);

        speichernAlsBildMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.speichernAlsBildMenuItemMnemonic").charAt(0));
        speichernAlsBildMenuItem.setText(bundle.getString("MainFrame.speichernAlsBildMenuItem.text")); // NOI18N
        speichernAlsBildMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                speichernAlsBildMenuItemActionPerformed(evt);
            }
        });
        dateiMenu.add(speichernAlsBildMenuItem);
        dateiMenu.add(jSeparator15);

        eingebenSpielenButtonGroup.add(spielEingebenMenuItem);
        spielEingebenMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.spielEingebenMenuItemMnemonic").charAt(0));
        spielEingebenMenuItem.setText(bundle.getString("MainFrame.spielEingebenMenuItem.text")); // NOI18N
        spielEingebenMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                spielEingebenMenuItemActionPerformed(evt);
            }
        });
        dateiMenu.add(spielEingebenMenuItem);

        eingebenSpielenButtonGroup.add(spielenMenuItem);
        spielenMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.spielenMenuItemMnemonic").charAt(0));
        spielenMenuItem.setSelected(true);
        spielenMenuItem.setText(bundle.getString("MainFrame.spielenMenuItem.text")); // NOI18N
        spielenMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                spielenMenuItemActionPerformed(evt);
            }
        });
        dateiMenu.add(spielenMenuItem);
        dateiMenu.add(jSeparator16);

        beendenMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.ALT_MASK));
        beendenMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.beendenMenuItemMnemonic").charAt(0));
        beendenMenuItem.setText(bundle.getString("MainFrame.beendenMenuItem.text")); // NOI18N
        beendenMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                beendenMenuItemActionPerformed(evt);
            }
        });
        dateiMenu.add(beendenMenuItem);

        jMenuBar1.add(dateiMenu);

        bearbeitenMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.bearbeitenMenuMnemonic").charAt(0));
        bearbeitenMenu.setText(bundle.getString("MainFrame.bearbeitenMenu.text")); // NOI18N

        undoMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_MASK));
        undoMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.undoMenuItemMnemonic").charAt(0));
        undoMenuItem.setText(bundle.getString("MainFrame.undoMenuItem.text")); // NOI18N
        undoMenuItem.setEnabled(false);
        undoMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                undoMenuItemActionPerformed(evt);
            }
        });
        bearbeitenMenu.add(undoMenuItem);

        redoMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, java.awt.event.InputEvent.CTRL_MASK));
        redoMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.redoMenuItemMnemonic").charAt(0));
        redoMenuItem.setText(bundle.getString("MainFrame.redoMenuItem.text")); // NOI18N
        redoMenuItem.setEnabled(false);
        redoMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                redoMenuItemActionPerformed(evt);
            }
        });
        bearbeitenMenu.add(redoMenuItem);
        bearbeitenMenu.add(jSeparator3);

        copyCluesMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, java.awt.event.InputEvent.CTRL_MASK));
        copyCluesMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.copyCluesMenuItemMnemonic").charAt(0));
        copyCluesMenuItem.setText(bundle.getString("MainFrame.copyCluesMenuItem.text")); // NOI18N
        copyCluesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyCluesMenuItemActionPerformed(evt);
            }
        });
        bearbeitenMenu.add(copyCluesMenuItem);

        copyFilledMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.copyFilledMenuItemMnemonic").charAt(0));
        copyFilledMenuItem.setText(bundle.getString("MainFrame.copyFilledMenuItem.text")); // NOI18N
        copyFilledMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyFilledMenuItemActionPerformed(evt);
            }
        });
        bearbeitenMenu.add(copyFilledMenuItem);

        copyPmGridMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_MASK));
        copyPmGridMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.copyPmGridMenuItemMnemonic").charAt(0));
        copyPmGridMenuItem.setText(bundle.getString("MainFrame.copyPmGridMenuItem.text")); // NOI18N
        copyPmGridMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyPmGridMenuItemActionPerformed(evt);
            }
        });
        bearbeitenMenu.add(copyPmGridMenuItem);

        copyPmGridWithStepMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.copyPmGridWithStepMenuItemMnemonic").charAt(0));
        copyPmGridWithStepMenuItem.setText(bundle.getString("MainFrame.copyPmGridWithStepMenuItem.text")); // NOI18N
        copyPmGridWithStepMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyPmGridWithStepMenuItemActionPerformed(evt);
            }
        });
        bearbeitenMenu.add(copyPmGridWithStepMenuItem);

        copyLibraryMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.copyLibraryMenuItemMnemonic").charAt(0));
        copyLibraryMenuItem.setText(bundle.getString("MainFrame.copyLibraryMenuItem.text")); // NOI18N
        copyLibraryMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyLibraryMenuItemActionPerformed(evt);
            }
        });
        bearbeitenMenu.add(copyLibraryMenuItem);

        pasteMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, java.awt.event.InputEvent.CTRL_MASK));
        pasteMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.pasteMenuItemMnemonic").charAt(0));
        pasteMenuItem.setText(bundle.getString("MainFrame.pasteMenuItem.text")); // NOI18N
        pasteMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pasteMenuItemActionPerformed(evt);
            }
        });
        bearbeitenMenu.add(pasteMenuItem);
        bearbeitenMenu.add(jSeparator17);

        resetSpielMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_MASK));
        resetSpielMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.resetSpielMenuItemMnemonic").charAt(0));
        resetSpielMenuItem.setText(bundle.getString("MainFrame.resetSpielMenuItem.text")); // NOI18N
        resetSpielMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetSpielMenuItemActionPerformed(evt);
            }
        });
        bearbeitenMenu.add(resetSpielMenuItem);
        bearbeitenMenu.add(jSeparator2);

        configMenuItem.setAccelerator(KeyStroke.getKeyStroke(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.configMenuItemAccelerator")));
        configMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.configMenuItemMnemonic").charAt(0));
        configMenuItem.setText(bundle.getString("MainFrame.configMenuItem.text")); // NOI18N
        configMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                configMenuItemActionPerformed(evt);
            }
        });
        bearbeitenMenu.add(configMenuItem);

        jMenuBar1.add(bearbeitenMenu);

        optionenMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.optionenMenuMnemonic").charAt(0));
        optionenMenu.setText(bundle.getString("MainFrame.optionenMenu.text")); // NOI18N

        showCandidatesMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.showCandidatesMenuItemMnemonic").charAt(0));
        showCandidatesMenuItem.setText(bundle.getString("MainFrame.showCandidatesMenuItem.text")); // NOI18N
        showCandidatesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showCandidatesMenuItemActionPerformed(evt);
            }
        });
        optionenMenu.add(showCandidatesMenuItem);

        showWrongValuesMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.showWrongValuesMenuItemMnemonic").charAt(0));
        showWrongValuesMenuItem.setText(bundle.getString("MainFrame.showWrongValuesMenuItem.text")); // NOI18N
        showWrongValuesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showWrongValuesMenuItemActionPerformed(evt);
            }
        });
        optionenMenu.add(showWrongValuesMenuItem);

        showDeviationsMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.showDeviationsMenuItemMnemonic").charAt(0));
        showDeviationsMenuItem.setSelected(true);
        showDeviationsMenuItem.setText(bundle.getString("MainFrame.showDeviationsMenuItem.text")); // NOI18N
        showDeviationsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showDeviationsMenuItemActionPerformed(evt);
            }
        });
        optionenMenu.add(showDeviationsMenuItem);
        optionenMenu.add(jSeparator10);

        levelMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.levelMenuMnemonic").charAt(0));
        levelMenu.setText(bundle.getString("MainFrame.levelMenu.text")); // NOI18N

        levelLeichtMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        levelButtonGroup.add(levelLeichtMenuItem);
        levelLeichtMenuItem.setSelected(true);
        levelLeichtMenuItem.setText("Leicht"); // NOI18N
        levelLeichtMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                levelLeichtMenuItemActionPerformed(evt);
            }
        });
        levelMenu.add(levelLeichtMenuItem);

        levelMittelMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_2, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        levelButtonGroup.add(levelMittelMenuItem);
        levelMittelMenuItem.setText("Mittel"); // NOI18N
        levelMittelMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                levelMittelMenuItemActionPerformed(evt);
            }
        });
        levelMenu.add(levelMittelMenuItem);

        levelKniffligMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_3, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        levelButtonGroup.add(levelKniffligMenuItem);
        levelKniffligMenuItem.setText("Schwer\n"); // NOI18N
        levelKniffligMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                levelKniffligMenuItemActionPerformed(evt);
            }
        });
        levelMenu.add(levelKniffligMenuItem);

        levelSchwerMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_4, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        levelButtonGroup.add(levelSchwerMenuItem);
        levelSchwerMenuItem.setText("Unfair"); // NOI18N
        levelSchwerMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                levelSchwerMenuItemActionPerformed(evt);
            }
        });
        levelMenu.add(levelSchwerMenuItem);

        levelExtremMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_5, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        levelButtonGroup.add(levelExtremMenuItem);
        levelExtremMenuItem.setText("Extrem"); // NOI18N
        levelExtremMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                levelExtremMenuItemActionPerformed(evt);
            }
        });
        levelMenu.add(levelExtremMenuItem);

        optionenMenu.add(levelMenu);

        jMenuBar1.add(optionenMenu);

        rätselMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.rätselMenuMnemonic").charAt(0));
        rätselMenu.setText(bundle.getString("MainFrame.rätselMenu.text")); // NOI18N

        vageHintMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F12, java.awt.event.InputEvent.ALT_MASK));
        vageHintMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.vageHintMenuItemMnemonic").charAt(0));
        vageHintMenuItem.setText(bundle.getString("MainFrame.vageHintMenuItem")); // NOI18N
        vageHintMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                vageHintMenuItemActionPerformed(evt);
            }
        });
        rätselMenu.add(vageHintMenuItem);

        mediumHintMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F12, java.awt.event.InputEvent.CTRL_MASK));
        mediumHintMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.mediumHintMenuItemMnemonic").charAt(0));
        mediumHintMenuItem.setText(bundle.getString("MainFrame.mediumHintMenuItem.text")); // NOI18N
        mediumHintMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mediumHintMenuItemActionPerformed(evt);
            }
        });
        rätselMenu.add(mediumHintMenuItem);

        lösungsSchrittMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F12, 0));
        lösungsSchrittMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.lösungsSchrittMenuItemMnemonic").charAt(0));
        lösungsSchrittMenuItem.setText(bundle.getString("MainFrame.lösungsSchrittMenuItem.text")); // NOI18N
        lösungsSchrittMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lösungsSchrittMenuItemActionPerformed(evt);
            }
        });
        rätselMenu.add(lösungsSchrittMenuItem);
        rätselMenu.add(jSeparator5);

        alleHiddenSinglesSetzenMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F11, 0));
        alleHiddenSinglesSetzenMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.alleHiddenSinglesSetzenMenuItemMnemonic").charAt(0));
        alleHiddenSinglesSetzenMenuItem.setText(bundle.getString("MainFrame.alleHiddenSinglesSetzenMenuItem.text")); // NOI18N
        alleHiddenSinglesSetzenMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                alleHiddenSinglesSetzenMenuItemActionPerformed(evt);
            }
        });
        rätselMenu.add(alleHiddenSinglesSetzenMenuItem);

        jMenuBar1.add(rätselMenu);

        ansichtMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.ansichtMenuMnemonic").charAt(0));
        ansichtMenu.setText(bundle.getString("MainFrame.ansichtMenu.text")); // NOI18N

        sudokuOnlyMenuItem.setAccelerator(KeyStroke.getKeyStroke("shift control " + java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.sudokuOnlyMenuItemMnemonic").toUpperCase().charAt(0)));
        viewButtonGroup.add(sudokuOnlyMenuItem);
        sudokuOnlyMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.sudokuOnlyMenuItemMnemonic").charAt(0));
        sudokuOnlyMenuItem.setSelected(true);
        sudokuOnlyMenuItem.setText(bundle.getString("MainFrame.sudokuOnlyMenuItem.text")); // NOI18N
        sudokuOnlyMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sudokuOnlyMenuItemActionPerformed(evt);
            }
        });
        ansichtMenu.add(sudokuOnlyMenuItem);
        ansichtMenu.add(jSeparator18);

        summaryMenuItem.setAccelerator(KeyStroke.getKeyStroke("shift control " + java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.summaryMenuItemMnemonic").toUpperCase().charAt(0)));
        viewButtonGroup.add(summaryMenuItem);
        summaryMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.summaryMenuItemMnemonic").charAt(0));
        summaryMenuItem.setText(bundle.getString("MainFrame.summaryMenuItem.text")); // NOI18N
        summaryMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                summaryMenuItemActionPerformed(evt);
            }
        });
        ansichtMenu.add(summaryMenuItem);

        solutionMenuItem.setAccelerator(KeyStroke.getKeyStroke("shift control " + java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.solutionMenuItemMnemonic").toUpperCase().charAt(0)));
        viewButtonGroup.add(solutionMenuItem);
        solutionMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.solutionMenuItemMnemonic").charAt(0));
        solutionMenuItem.setText(bundle.getString("MainFrame.solutionMenuItem.text")); // NOI18N
        solutionMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                solutionMenuItemActionPerformed(evt);
            }
        });
        ansichtMenu.add(solutionMenuItem);

        allStepsMenuItem.setAccelerator(KeyStroke.getKeyStroke("shift control " + java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.allStepsMenuItemMnemonic").toUpperCase().charAt(0)));
        viewButtonGroup.add(allStepsMenuItem);
        allStepsMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.allStepsMenuItemMnemonic").charAt(0));
        allStepsMenuItem.setText(bundle.getString("MainFrame.allStepsMenuItem.text")); // NOI18N
        allStepsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allStepsMenuItemActionPerformed(evt);
            }
        });
        ansichtMenu.add(allStepsMenuItem);
        ansichtMenu.add(jSeparator7);

        resetViewMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.resetViewMenuItemMnemonic").charAt(0));
        resetViewMenuItem.setText(bundle.getString("MainFrame.resetViewMenuItem.text")); // NOI18N
        resetViewMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetViewMenuItemActionPerformed(evt);
            }
        });
        ansichtMenu.add(resetViewMenuItem);

        jMenuBar1.add(ansichtMenu);

        helpMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.helpMenu.mnemonic").charAt(0));
        helpMenu.setText(bundle.getString("MainFrame.helpMenu.text")); // NOI18N

        keyMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.keyMenuItem.mnemonic").charAt(0));
        keyMenuItem.setText(bundle.getString("MainFrame.keyMenuItem.text")); // NOI18N
        keyMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                keyMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(keyMenuItem);
        helpMenu.add(jSeparator6);

        aboutMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.aboutMenuItem.").charAt(0));
        aboutMenuItem.setText(bundle.getString("MainFrame.aboutMenuItem.text")); // NOI18N
        aboutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(aboutMenuItem);

        jMenuBar1.add(helpMenu);

        setJMenuBar(jMenuBar1);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void saveAsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveAsMenuItemActionPerformed
        JFileChooser chooser = new JFileChooser(Options.getInstance().defaultFileDir);
        for (int i = 0; i < fileFilters.length; i++) {
            chooser.addChoosableFileFilter(fileFilters[i]);
        }
        chooser.setAcceptAllFileFilterUsed(false);
        int returnVal = chooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
                String path = chooser.getSelectedFile().getPath();
                path = path.substring(0, path.lastIndexOf(File.separatorChar));
                Options.getInstance().defaultFileDir = path;
                MyFileFilter actFilter = (MyFileFilter) chooser.getFileFilter();
                path = chooser.getSelectedFile().getAbsolutePath();
                if (actFilter == fileFilters[0] || path.endsWith("." + configFileExt)) {
                    // Options
                    if (!path.endsWith("." + configFileExt)) {
                        path += "." + configFileExt;
                    }
                    //Options.getInstance().writeOptions(path);
                    writeOptionsWithWindowState(path);
                } else if (actFilter == fileFilters[1] || path.endsWith("." + solutionFileExt)) {
                    // Sudoku und Lösung
                    if (!path.endsWith("." + solutionFileExt)) {
                        path += "." + solutionFileExt;
                    }
                    ZipOutputStream zOut = new ZipOutputStream(new FileOutputStream(path));
                    zOut.putNextEntry(new ZipEntry("SudokuData"));
                    XMLEncoder out = new XMLEncoder(zOut);
//                    out.writeObject(sudokuPanel.getSudoku());
//                    out.writeObject(sudokuPanel.getSolvedSudoku());
//                    SudokuSolver s = SudokuSolver.getInstance();
//                    out.writeObject(s.getAnzSteps());
//                    for (int i = 0; i < s.getSteps().size(); i++) {
//                        out.writeObject(s.getSteps().get(i));
//                    }
                    out.writeObject(sudokuPanel.getSudoku());
                    out.writeObject(sudokuPanel.getSolvedSudoku());
                    out.writeObject(SudokuSolver.getInstance().getAnzSteps());
                    out.writeObject(SudokuSolver.getInstance().getSteps());
                    out.writeObject(solutionPanel.getTitels());
                    out.writeObject(solutionPanel.getTabSteps());
                    out.close();
                    zOut.flush();
                    zOut.close();
                } else {
                    formatter.applyPattern(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.invalid_filename"));
                    String msg = formatter.format(new Object[]{path});
                    JOptionPane.showMessageDialog(this, msg,
                            java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"), JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex2) {
                JOptionPane.showMessageDialog(this, ex2.toString(), java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_saveAsMenuItemActionPerformed

    private void loadMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadMenuItemActionPerformed
        JFileChooser chooser = new JFileChooser(Options.getInstance().defaultFileDir);
        for (int i = 0; i < fileFilters.length; i++) {
            chooser.addChoosableFileFilter(fileFilters[i]);
        }
        chooser.setAcceptAllFileFilterUsed(false);
        int returnVal = chooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
                String path = chooser.getSelectedFile().getPath();
                path = path.substring(0, path.lastIndexOf(File.separatorChar));
                Options.getInstance().defaultFileDir = path;
                path = chooser.getSelectedFile().getAbsolutePath();
                MyFileFilter actFilter = (MyFileFilter) chooser.getFileFilter();
                if (actFilter == fileFilters[0] || path.endsWith("." + configFileExt)) {
                    // Options
                    Options.readOptions(path);
                } else if (actFilter == fileFilters[1] || path.endsWith("." + solutionFileExt)) {
                    ZipInputStream zIn = new ZipInputStream(new FileInputStream(path));
                    zIn.getNextEntry();
                    XMLDecoder in = new XMLDecoder(zIn);
//                    Sudoku s = (Sudoku) in.readObject();
//                    Sudoku ss = (Sudoku) in.readObject();
//                    SudokuSolver solv = SudokuSolver.getInstance();
//                    solv.setAnzSteps((int[]) in.readObject());
//                    solv.getSteps().clear();
//                    try {
//                        while (true) {
//                            solv.getSteps().add((SolutionStep) in.readObject());
//                        }
//                    } catch (ArrayIndexOutOfBoundsException ex) {
//                        // alles gelesen
//                    }
                    Sudoku s = (Sudoku) in.readObject();
                    Sudoku ss = (Sudoku) in.readObject();
                    SudokuSolver solv = SudokuSolver.getInstance();
                    solv.setAnzSteps((int[]) in.readObject());
                    solv.setSteps((List<SolutionStep>) in.readObject());
                    List<String> titles = (List<String>) in.readObject();
                    List<List<SolutionStep>> solutions = (List<List<SolutionStep>>) in.readObject();
                    in.close();
                    sudokuPanel.loadFromFile(s, ss);
                    summaryPanel.initialize(SudokuSolver.getInstance());
                    //solutionPanel.initialize(SudokuSolver.getInstance().getSteps());
                    solutionPanel.initialize(titles, solutions);
                    sudokuPanel.abortStep();
                    allStepsPanel.setSudoku(sudokuPanel.getSudoku());
                    hinweisTextArea.setText("");
                    hinweisAbbrechenButton.setEnabled(false);
                    hinweisAusführenButton.setEnabled(false);
                    fixFocus();
                } else {
                    formatter.applyPattern(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.invalid_filename"));
                    String msg = formatter.format(new Object[]{path});
                    JOptionPane.showMessageDialog(this, msg,
                            java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"), JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex2) {
                JOptionPane.showMessageDialog(this, ex2.toString(), java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_loadMenuItemActionPerformed

    private void configMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_configMenuItemActionPerformed
        new ConfigDialog(this, true).setVisible(true);
        fixFocus();
        sudokuPanel.repaint();
    }//GEN-LAST:event_configMenuItemActionPerformed

    private void statusLabel1MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusLabel1MouseClicked
        setStatusLabel(1);
        fixFocus();
    }//GEN-LAST:event_statusLabel1MouseClicked

    private void statusLabel2MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusLabel2MouseClicked
        setStatusLabel(2);
        fixFocus();
    }//GEN-LAST:event_statusLabel2MouseClicked

    private void statusLabel3MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusLabel3MouseClicked
        setStatusLabel(3);
        fixFocus();
    }//GEN-LAST:event_statusLabel3MouseClicked

    private void statusLabel4MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusLabel4MouseClicked
        setStatusLabel(4);
        fixFocus();
    }//GEN-LAST:event_statusLabel4MouseClicked

    private void statusLabel5MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusLabel5MouseClicked
        setStatusLabel(5);
        fixFocus();
    }//GEN-LAST:event_statusLabel5MouseClicked

    private void statusLabel6MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusLabel6MouseClicked
        setStatusLabel(6);
        fixFocus();
    }//GEN-LAST:event_statusLabel6MouseClicked

    private void statusLabel7MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusLabel7MouseClicked
        setStatusLabel(7);
        fixFocus();
    }//GEN-LAST:event_statusLabel7MouseClicked

    private void statusLabel8MousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusLabel8MousePressed
        setStatusLabel(8);
        fixFocus();
    }//GEN-LAST:event_statusLabel8MousePressed

    private void statusLabel9MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusLabel9MouseClicked
        setStatusLabel(9);
        fixFocus();
    }//GEN-LAST:event_statusLabel9MouseClicked

    private void statusLabelAusMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusLabelAusMouseClicked
        setStatusLabel(0);
        fixFocus();
    }//GEN-LAST:event_statusLabelAusMouseClicked

    private void allStepsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allStepsMenuItemActionPerformed
        allStepsPanel.setSudoku(sudokuPanel.getSudoku());
        setSplitPane(allStepsPanel);
        //initializeResultPanels();
        repaint();
    }//GEN-LAST:event_allStepsMenuItemActionPerformed

    private void solutionMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_solutionMenuItemActionPerformed
        setSplitPane(solutionPanel);
        //initializeResultPanels();
        repaint();
    }//GEN-LAST:event_solutionMenuItemActionPerformed

    private void sudokuOnlyMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sudokuOnlyMenuItemActionPerformed
        splitPanel.setRight(null);
        if (getExtendedState() != JFrame.MAXIMIZED_BOTH) {
            if (splitPanel.getBounds().getWidth() < splitPanel.getBounds().getHeight()) {
                setSize(getWidth() + 1, getHeight());
            }
        }
    }//GEN-LAST:event_sudokuOnlyMenuItemActionPerformed

    private void summaryMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_summaryMenuItemActionPerformed
        setSplitPane(summaryPanel);
        //initializeResultPanels();
        repaint();
    }//GEN-LAST:event_summaryMenuItemActionPerformed

    private void speichernAlsBildMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_speichernAlsBildMenuItemActionPerformed
        WriteAsPNGDialog dlg = new WriteAsPNGDialog(this, true, bildFile, bildSize, bildAuflösung, bildEinheit);
        dlg.setVisible(true);
        if (dlg.isOk()) {
            bildFile = dlg.getBildFile();
            bildAuflösung = dlg.getAuflösung();
            bildSize = dlg.getBildSize();
            bildEinheit = dlg.getEinheit();
            int size = 0;
            switch (bildEinheit) {
                case 0:
                    size = (int) (bildSize / 25.4 * bildAuflösung);
                    break;
                case 1:
                    size = (int) (bildSize * bildAuflösung);
                    break;
                case 2:
                    size = (int) bildSize;
                    break;
            }
            sudokuPanel.saveSudokuAsPNG(bildFile, size, bildAuflösung);
        }
    }//GEN-LAST:event_speichernAlsBildMenuItemActionPerformed

    private void druckenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_druckenMenuItemActionPerformed
        if (job == null) {
            job = PrinterJob.getPrinterJob();
        }
        if (pageFormat == null) {
            pageFormat = job.defaultPage();
        }
        try {
            job.setPrintable(sudokuPanel, pageFormat);
            if (job.printDialog()) {
                job.print();
            }
        } catch (PrinterException ex) {
            JOptionPane.showMessageDialog(this, ex.toString(),
                    java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"), JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_druckenMenuItemActionPerformed

    private void seiteEinrichtenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_seiteEinrichtenMenuItemActionPerformed
        if (job == null) {
            job = PrinterJob.getPrinterJob();
        }
        if (pageFormat == null) {
            pageFormat = job.defaultPage();
        }
        pageFormat = job.pageDialog(pageFormat);
    }//GEN-LAST:event_seiteEinrichtenMenuItemActionPerformed

    private void resetSpielMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetSpielMenuItemActionPerformed
        if (JOptionPane.showConfirmDialog(this, java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.start_new_game"),
                java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.start_new"),
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            sudokuPanel.setSudoku(sudokuPanel.getSudokuString(ClipboardMode.CLUES_ONLY));
        //initializeResultPanels();
        }
    }//GEN-LAST:event_resetSpielMenuItemActionPerformed

    private void spielenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_spielenMenuItemActionPerformed
        if (sudokuPanel.getAnzFilled() > 0) {
            sudokuPanel.setSudoku(sudokuPanel.getSudokuString(ClipboardMode.VALUES_ONLY));
            allStepsPanel.setSudoku(sudokuPanel.getSudoku());
            initializeResultPanels();
        }
        setSpielen(true);
    }//GEN-LAST:event_spielenMenuItemActionPerformed

    private void spielEingebenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_spielEingebenMenuItemActionPerformed
        // bestehendes Sudoku kann gelöscht werden, muss aber nicht
        if (sudokuPanel.getAnzFilled() != 0) {
            int antwort = JOptionPane.showConfirmDialog(this, java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.delete_sudoku"),
                    java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.new_input"),
                    JOptionPane.YES_NO_OPTION);
            if (antwort == JOptionPane.YES_OPTION) {
                sudokuPanel.setSudoku((String) null);
                allStepsPanel.setSudoku(sudokuPanel.getSudoku());
                resetResultPanels();
            }
        }
        sudokuPanel.setNoClues();
        hinweisAbbrechenButtonActionPerformed(null);
        setSpielen(false);
    }//GEN-LAST:event_spielEingebenMenuItemActionPerformed

    private void beendenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_beendenMenuItemActionPerformed
        formWindowClosed(null);
        System.exit(0);
    }//GEN-LAST:event_beendenMenuItemActionPerformed

    private void copyLibraryMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyLibraryMenuItemActionPerformed
        copyToClipboard(ClipboardMode.LIBRARY);
    }//GEN-LAST:event_copyLibraryMenuItemActionPerformed

    private void copyPmGridWithStepMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyPmGridWithStepMenuItemActionPerformed
        SolutionStep activeStep = sudokuPanel.getStep();
        if (activeStep == null) {
            JOptionPane.showMessageDialog(this, java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.no_step_selected"),
                    java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        copyToClipboard(ClipboardMode.PM_GRID_WITH_STEP);
    }//GEN-LAST:event_copyPmGridWithStepMenuItemActionPerformed

    private void copyPmGridMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyPmGridMenuItemActionPerformed
        copyToClipboard(ClipboardMode.PM_GRID);
    }//GEN-LAST:event_copyPmGridMenuItemActionPerformed

    private void copyFilledMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyFilledMenuItemActionPerformed
        copyToClipboard(ClipboardMode.VALUES_ONLY);
    }//GEN-LAST:event_copyFilledMenuItemActionPerformed

    private void showDeviationsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showDeviationsMenuItemActionPerformed
        sudokuPanel.setShowDeviations(showDeviationsMenuItem.isSelected());
        check();
        fixFocus();
    }//GEN-LAST:event_showDeviationsMenuItemActionPerformed

    private void neuesSpielToolButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_neuesSpielToolButtonActionPerformed
        // neues Spiel in der gewünschten Schwierigkeitsstufe erzeugen
        GenerateSudokuProgressDialog dlg = new GenerateSudokuProgressDialog(this, true, level);
        dlg.setVisible(true);
        Sudoku tmpSudoku = dlg.getSudoku();
        if (tmpSudoku != null) {
            sudokuPanel.setSudoku(tmpSudoku, true);
            allStepsPanel.setSudoku(sudokuPanel.getSudoku());
            initializeResultPanels();
            repaint();
        }
        setSpielen(true);
        fixFocus();
    }//GEN-LAST:event_neuesSpielToolButtonActionPerformed

    private void levelComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_levelComboBoxActionPerformed
        level = Options.getInstance().getDifficultyLevels()[levelComboBox.getSelectedIndex() + 1];
        check();
        fixFocus();
    }//GEN-LAST:event_levelComboBoxActionPerformed

    private void levelExtremMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_levelExtremMenuItemActionPerformed
        setLevelFromMenu();
    }//GEN-LAST:event_levelExtremMenuItemActionPerformed

    private void levelSchwerMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_levelSchwerMenuItemActionPerformed
        setLevelFromMenu();
    }//GEN-LAST:event_levelSchwerMenuItemActionPerformed

    private void levelKniffligMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_levelKniffligMenuItemActionPerformed
        setLevelFromMenu();
    }//GEN-LAST:event_levelKniffligMenuItemActionPerformed

    private void levelMittelMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_levelMittelMenuItemActionPerformed
        setLevelFromMenu();
    }//GEN-LAST:event_levelMittelMenuItemActionPerformed

    private void levelLeichtMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_levelLeichtMenuItemActionPerformed
        setLevelFromMenu();
    }//GEN-LAST:event_levelLeichtMenuItemActionPerformed

    private void f1ToggleButtonActionPerformed1(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_f1ToggleButtonActionPerformed1
        f1ToggleButtonActionPerformed(evt);
    }//GEN-LAST:event_f1ToggleButtonActionPerformed1

    private void f1ToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_f1ToggleButtonActionPerformed
        setToggleButton((JToggleButton) evt.getSource());
    }//GEN-LAST:event_f1ToggleButtonActionPerformed

    private void redGreenToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_redGreenToggleButtonActionPerformed
        sudokuPanel.setInvalidCells(!sudokuPanel.isInvalidCells());
        sudokuPanel.repaint();
        check();
        fixFocus();
    }//GEN-LAST:event_redGreenToggleButtonActionPerformed

    private void mediumHintMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mediumHintMenuItemActionPerformed
        if (sudokuPanel.getSudoku().isSolved()) {
            JOptionPane.showMessageDialog(this, java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.already_solved"));
            return;
        }
        if (!sudokuPanel.isShowCandidates()) {
            JOptionPane.showMessageDialog(this, java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.not_available"),
                    java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.hint"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SolutionStep step = sudokuPanel.getNextStep(false);
        sudokuPanel.abortStep();
        fixFocus();
        if (step != null) {
            JOptionPane.showMessageDialog(this, java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.possible_step") +
                    step.toString(1),
                    java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.medium_hint"), JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.dont_know"),
                    java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"), JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_mediumHintMenuItemActionPerformed

    private void vageHintMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_vageHintMenuItemActionPerformed
        if (sudokuPanel.getSudoku().isSolved()) {
            JOptionPane.showMessageDialog(this, java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.already_solved"));
            return;
        }
        if (!sudokuPanel.isShowCandidates()) {
            JOptionPane.showMessageDialog(this, java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.not_available"),
                    java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.hint"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SolutionStep step = sudokuPanel.getNextStep(false);
        sudokuPanel.abortStep();
        fixFocus();
        if (step != null) {
            JOptionPane.showMessageDialog(this, java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.possible_step") +
                    step.toString(0), java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.vage_hint"), JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.dont_know"),
                    java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"), JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_vageHintMenuItemActionPerformed

    private void alleHiddenSinglesSetzenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_alleHiddenSinglesSetzenMenuItemActionPerformed
        hinweisAbbrechenButtonActionPerformed(null);
        SolutionStep step = null;
        while ((step = sudokuPanel.getNextStep(true)) != null &&
                (step.getType() == SolutionType.HIDDEN_SINGLE || step.getType() == SolutionType.FULL_HOUSE ||
                step.getType() == SolutionType.NAKED_SINGLE)) {
            sudokuPanel.doStep();
        }
        sudokuPanel.abortStep();
        fixFocus();
        repaint();
    }//GEN-LAST:event_alleHiddenSinglesSetzenMenuItemActionPerformed

    private void hinweisAbbrechenButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hinweisAbbrechenButtonActionPerformed
        sudokuPanel.abortStep();
        hinweisTextArea.setText("");
        hinweisAbbrechenButton.setEnabled(false);
        hinweisAusführenButton.setEnabled(false);
        fixFocus();
    }//GEN-LAST:event_hinweisAbbrechenButtonActionPerformed

    private void hinweisAusführenButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hinweisAusführenButtonActionPerformed
        sudokuPanel.doStep();
        hinweisTextArea.setText("");
        hinweisAbbrechenButton.setEnabled(false);
        hinweisAusführenButton.setEnabled(false);
        fixFocus();
    }//GEN-LAST:event_hinweisAusführenButtonActionPerformed

    private void lösungsSchrittMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lösungsSchrittMenuItemActionPerformed
        if (sudokuPanel.getSudoku().isSolved()) {
            JOptionPane.showMessageDialog(this, java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.already_solved"));
            return;
        }
        if (!sudokuPanel.isShowCandidates()) {
            JOptionPane.showMessageDialog(this, java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.not_available"),
                    java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.hint"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SolutionStep step = sudokuPanel.getNextStep(false);
        if (step != null) {
            setSolutionStep(step, false);
        } else {
            hinweisTextArea.setText(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.dont_know"));
            hinweisTextArea.setCaretPosition(0);
        }
        check();
        fixFocus();
    }//GEN-LAST:event_lösungsSchrittMenuItemActionPerformed

    private void neuerHinweisButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_neuerHinweisButtonActionPerformed
        lösungsSchrittMenuItemActionPerformed(evt);
    }//GEN-LAST:event_neuerHinweisButtonActionPerformed

    private void neuMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_neuMenuItemActionPerformed
        neuesSpielToolButtonActionPerformed(null);
    }//GEN-LAST:event_neuMenuItemActionPerformed

    private void copyCluesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyCluesMenuItemActionPerformed
        copyToClipboard(ClipboardMode.CLUES_ONLY);
    }//GEN-LAST:event_copyCluesMenuItemActionPerformed

    private void showWrongValuesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showWrongValuesMenuItemActionPerformed
        sudokuPanel.setShowWrongValues(showWrongValuesMenuItem.isSelected());
        check();
        fixFocus();
    }//GEN-LAST:event_showWrongValuesMenuItemActionPerformed

    private void showCandidatesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showCandidatesMenuItemActionPerformed
        sudokuPanel.setShowCandidates(showCandidatesMenuItem.isSelected());
        check();
        fixFocus();
    }//GEN-LAST:event_showCandidatesMenuItemActionPerformed

    private void redoToolButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_redoToolButtonActionPerformed
        sudokuPanel.redo();
        check();
        fixFocus();
    }//GEN-LAST:event_redoToolButtonActionPerformed

    private void redoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_redoMenuItemActionPerformed
        sudokuPanel.redo();
        check();
        fixFocus();
    }//GEN-LAST:event_redoMenuItemActionPerformed

    private void undoToolButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_undoToolButtonActionPerformed
        sudokuPanel.undo();
        check();
        fixFocus();
    }//GEN-LAST:event_undoToolButtonActionPerformed

    private void undoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_undoMenuItemActionPerformed
        sudokuPanel.undo();
        check();
        fixFocus();
    }//GEN-LAST:event_undoMenuItemActionPerformed

    private void pasteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteMenuItemActionPerformed
        try {
            Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable clipboardContent = clip.getContents(this);
            if ((clipboardContent != null) && (clipboardContent.isDataFlavorSupported(DataFlavor.stringFlavor))) {
                String content = (String) clipboardContent.getTransferData(DataFlavor.stringFlavor);
                sudokuPanel.setSudoku(content);
                allStepsPanel.setSudoku(sudokuPanel.getSudoku());
                initializeResultPanels();
                repaint();
            }
        } catch (Exception ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error pasting from clipboard", ex);
        }
        setSpielen(true);
        check();
        fixFocus();
    }//GEN-LAST:event_pasteMenuItemActionPerformed

private void outerSplitPanePropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_outerSplitPanePropertyChange
    if (!outerSplitPaneInitialized && outerSplitPane.getSize().getHeight() != 0 &&
            hintPanel.getSize().getHeight() != 0) {
        // adjust to minimum size of hintPanel to allow for LAF differences
        outerSplitPaneInitialized = true; // beware of recursion!
        int diff = (int) (hintPanel.getMinimumSize().getHeight() - hintPanel.getSize().getHeight());
        if (diff > 0) {
            resetHDivLocLoc = outerSplitPane.getDividerLocation() - diff - 1;
            outerSplitPane.setDividerLocation(resetHDivLocLoc);
            //System.out.println("Divider adjusted (" + (diff + 1) + ")!");
            //System.out.println("   absolut position: " + outerSplitPane.getDividerLocation());
        }
        //System.out.println("outerSplitPaneinitialized = true!");
    }
//    System.out.println("gdl: " + outerSplitPane.getDividerLocation() + " (" +
//            outerSplitPaneInitialized + "/" + outerSplitPane.getSize().getHeight() + "/" +
//            hintPanel.getSize().getHeight());
    if (resetHDivLoc && outerSplitPane.getDividerLocation() != resetHDivLocLoc) {
        resetHDivLoc = false;
        if (System.currentTimeMillis() - resetHDivLocTicks < 1000) {
            //System.out.println("Reset adjusted!");
            outerSplitPane.setDividerLocation(resetHDivLocLoc);
            setSize(getWidth() + 1, getHeight());
        } else {
            //System.out.println("Reset: nothing done!");
        }
    }
}//GEN-LAST:event_outerSplitPanePropertyChange

private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
    try {
        writeOptionsWithWindowState(null);
    } catch (FileNotFoundException ex) {
        Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, "Can't write options", ex);
    }
}//GEN-LAST:event_formWindowClosed

private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
    formWindowClosed(null);
}//GEN-LAST:event_formWindowClosing

private void hinweisKonfigurierenButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hinweisKonfigurierenButtonActionPerformed
    configMenuItemActionPerformed(null);
    check();
    fixFocus();
}//GEN-LAST:event_hinweisKonfigurierenButtonActionPerformed

private void aboutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutMenuItemActionPerformed
    new AboutDialog(this, true).setVisible(true);
    check();
    fixFocus();
}//GEN-LAST:event_aboutMenuItemActionPerformed

private void keyMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_keyMenuItemActionPerformed
    new KeyboardLayoutFrame().setVisible(true);
    check();
    fixFocus();
}//GEN-LAST:event_keyMenuItemActionPerformed

private void resetViewMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetViewMenuItemActionPerformed
    setWindowLayout(true);
    check();
    fixFocus();
    repaint();
}//GEN-LAST:event_resetViewMenuItemActionPerformed

private void hintPanelPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_hintPanelPropertyChange
    //System.out.println("hintPanelPropertyChanged!");
    outerSplitPanePropertyChange(null);
}//GEN-LAST:event_hintPanelPropertyChange
    
    private void tabPaneMouseClicked(java.awt.event.MouseEvent evt) {
        if (evt.getButton() == MouseEvent.BUTTON1) {
            //System.out.println("tab clicked: " + tabPane.getSelectedIndex());
            switch (tabPane.getSelectedIndex()) {
                case 0:
                    summaryMenuItem.setSelected(true);
                    break;
                case 1:
                    solutionMenuItem.setSelected(true);
                    break;
                case 2:
                    allStepsMenuItem.setSelected(true);
                    break;
            }
        }
    }

    private void writeOptionsWithWindowState(String fileName) throws FileNotFoundException {
        // save window state
        Options o = Options.getInstance();
        o.initialXPos = getX();
        o.initialYPos = getY();
        o.initialHeight = getHeight();
        o.initialWidth = getWidth();
        o.initialHorzDividerLoc = outerSplitPane.getDividerLocation();
        o.initialDisplayMode = 0; // sudoku only
        if (summaryMenuItem.isSelected()) {
            o.initialDisplayMode = 1;
        }
        if (solutionMenuItem.isSelected()) {
            o.initialDisplayMode = 2;
        }
        if (allStepsMenuItem.isSelected()) {
            o.initialDisplayMode = 3;
        }
        o.initialVertDividerLoc = -1;
        if (o.initialDisplayMode != 0) {
            splitPanel.getDividerLocation();
        }
        if (fileName == null) {
            Options.getInstance().writeOptions();
        } else {
            Options.getInstance().writeOptions(fileName);
        }
    }
    
    private void setWindowLayout(boolean reset) {
        Options o = Options.getInstance();
        //System.out.println("initialHorzDividerLoc: " + o.initialHorzDividerLoc);

        if (reset) {
            o.initialDisplayMode = Options.INITIAL_DISP_MODE;
            o.initialHeight = Options.INITIAL_HEIGHT;
            o.initialHorzDividerLoc = Options.INITIAL_HORZ_DIVIDER_LOC;
            o.initialVertDividerLoc = Options.INITIAL_VERT_DIVIDER_LOC;
            o.initialWidth = Options.INITIAL_WIDTH;
        }
        //System.out.println("initialHorzDividerLoc: " + o.initialHorzDividerLoc);
        
        Toolkit t = Toolkit.getDefaultToolkit();
        Dimension screenSize = t.getScreenSize();
        int width = o.initialWidth;
        int height = o.initialHeight;
        int horzDivLoc = o.initialHorzDividerLoc;
        if (screenSize.height - 40 < height) {
            height = screenSize.height - 40;
        }
        if (horzDivLoc > height - 204) {
            horzDivLoc = height - 204;
        }
        setSize(width, height);
        switch (o.initialDisplayMode) {
            case 0:
                splitPanel.setRight(null);
                sudokuOnlyMenuItem.setSelected(true);
                break;
            case 1:
                setSplitPane(summaryPanel);
                summaryMenuItem.setSelected(true);
                break;
            case 2:
                setSplitPane(solutionPanel);
                solutionMenuItem.setSelected(true);
                break;
            case 3:
                allStepsPanel.setSudoku(sudokuPanel.getSudoku());
                setSplitPane(allStepsPanel);
                allStepsMenuItem.setSelected(true);
                break;
        }
        if (o.initialVertDividerLoc != -1) {
            splitPanel.setDividerLocation(o.initialVertDividerLoc);
        }
        //System.out.println("horzDivLoc: " + horzDivLoc);
        outerSplitPane.setDividerLocation(horzDivLoc);
        
        // doesnt work at reset sometimes -> adjust in PropertyChangeListener
        if (reset) {
            outerSplitPaneInitialized = false;
            resetHDivLocLoc = horzDivLoc;
            resetHDivLocTicks = System.currentTimeMillis();
            resetHDivLoc = true;
        }
    }
    
    private void resetResultPanels() {
        summaryPanel.initialize(null);
        solutionPanel.initialize(null);
        allStepsPanel.resetPanel();
    }
    
    private void initializeResultPanels() {
        summaryPanel.initialize(sudokuPanel.getSolver());
        solutionPanel.initialize(sudokuPanel.getSolver().getSteps());
        allStepsPanel.resetPanel();
    }
    
    private void setSplitPane(JPanel panel) {
        if (! splitPanel.hasRight()) {
            splitPanel.setRight(tabPane);
        }
        tabPane.setSelectedComponent(panel);
    }
    
    private void setSpielen(boolean isSpielen) {
        if (isSpielen) {
            showDeviationsMenuItem.setSelected(oldShowDeviations);
        } else {
            oldShowDeviations = showDeviationsMenuItem.isSelected();
            showDeviationsMenuItem.setSelected(false);
        }
        showDeviationsMenuItemActionPerformed(null);
        
        vageHintMenuItem.setEnabled(isSpielen);
        mediumHintMenuItem.setEnabled(isSpielen);
        lösungsSchrittMenuItem.setEnabled(isSpielen);
        alleHiddenSinglesSetzenMenuItem.setEnabled(isSpielen);
        showDeviationsMenuItem.setEnabled(isSpielen);
    }
    
    public void setSolutionStep(SolutionStep step, boolean setInSudokuPanel) {
        if (setInSudokuPanel) {
            if (step == null) {
                sudokuPanel.abortStep();
            } else {
                sudokuPanel.setStep(step);
            }
        }
        if (step == null) {
            return;
        }
        hinweisTextArea.setText(step.toString());
        hinweisTextArea.setCaretPosition(0);
        hinweisAbbrechenButton.setEnabled(true);
        hinweisAusführenButton.setEnabled(true);
        getRootPane().setDefaultButton(hinweisAusführenButton);
        fixFocus();
    }
    
    private void copyToClipboard(ClipboardMode mode) {
        String clipStr = sudokuPanel.getSudokuString(mode);
        try {
            Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection content = new StringSelection(clipStr);
            clip.setContents(content, null);
        } catch (Exception ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error writing to clipboard", ex);
        }
        fixFocus();
    }
    
    public void setStatusLabel(int index) {
        sudokuPanel.setCursor(numberCursors[index]);
        statusLinePanel.setCursor(numberCursors[index]);
        sudokuPanel.setAktNumber(index);
        for (int i = 0; i < statusLabels.length; i++) {
            if (i == index) {
                statusLabels[i].setForeground(statusLabelAktColor);
            } else {
                statusLabels[i].setForeground(statusLabelNormColor);
            }
        }
    }
    
    private void setToggleButton(JToggleButton button) {
        if (button == null) {
            sudokuPanel.setShowHintCellValue(0);
            sudokuPanel.setShowInvalidOrPossibleCells(false);
        } else {
            int index = 0;
            for (index = 0; index < toggleButtons.length; index++) {
                if (toggleButtons[index] == button) {
                    break;
                }
            }
            if (button.isSelected()) {
                sudokuPanel.setShowHintCellValue(index + 1);
                sudokuPanel.setShowInvalidOrPossibleCells(true);
            } else {
                sudokuPanel.setShowHintCellValue(0);
                sudokuPanel.setShowInvalidOrPossibleCells(false);
            }
        }
        check();
        sudokuPanel.repaint();
        fixFocus();
    }
    
    private void createNumberCursors() {
        numberCursors[0] = getCursor();
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        for (int i = 1; i <= 9; i++) {
            Image cursorImage = toolkit.createImage(getClass().getResource("/img/c_" + i + ".gif"));
            Point cursorHotSpot = new Point(2, 4);
            numberCursors[i] = toolkit.createCustomCursor(cursorImage, cursorHotSpot, "c" + i);
        }
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, "Error setting LaF", ex);
        }
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MainFrame().setVisible(true);
            }
        });
    }
    
    private void setLevelFromMenu() {
        int selected = 0;
        for (int i = 1; i < levelMenuItems.length; i++) {
            if (levelMenuItems[i].isSelected()) {
                selected = i + 1;
                break;
            }
        }
        level = Options.getInstance().getDifficultyLevels()[selected];
        check();
        fixFocus();
    }
    
    public void stepAusführen() {
        hinweisAusführenButtonActionPerformed(null);
    }
    
    public void fixFocus() {
        sudokuPanel.requestFocusInWindow();
    }
    
    public SudokuPanel getSudokuPanel() {
        return sudokuPanel;
    }
    
    public SolutionPanel getSolutionPanel() {
        return solutionPanel;
    }
    
    public void check() {
        if (sudokuPanel != null) {
            undoMenuItem.setEnabled(sudokuPanel.undoPossible());
            undoToolButton.setEnabled(sudokuPanel.undoPossible());
            redoMenuItem.setEnabled(sudokuPanel.redoPossible());
            redoToolButton.setEnabled(sudokuPanel.redoPossible());
            showCandidatesMenuItem.setSelected(sudokuPanel.isShowCandidates());
            showWrongValuesMenuItem.setSelected(sudokuPanel.isShowWrongValues());
            showDeviationsMenuItem.setSelected(sudokuPanel.isShowDeviations());
            for (int i = 0; i < toggleButtons.length; i++) {
                if (i == sudokuPanel.getShowHintCellValue() - 1) {
                    if (toggleButtons[i] != null) {
                        toggleButtons[i].setSelected(true);
                    }
                } else {
                    if (toggleButtons[i] != null) {
                        toggleButtons[i].setSelected(false);
                    }
                }
            }
            if (sudokuPanel.getShowHintCellValue() != 0) {
                redGreenToggleButton.setSelected(sudokuPanel.isInvalidCells());
            }
            Sudoku sudoku = sudokuPanel.getSudoku();
            if (sudoku != null) {
                DifficultyLevel tmpLevel = sudoku.getLevel();
                if (tmpLevel != null) {
                    statusLabelLevel.setText(StepConfig.getLevelName(tmpLevel) + " (" + sudoku.getScore() + ")");
                }
            }
        }
        if (levelMenuItems[level.getOrdinal() - 1] != null) {
            levelMenuItems[level.getOrdinal() - 1].setSelected(true);
            levelComboBox.setSelectedIndex(level.getOrdinal() - 1);
        }
    }

    private boolean isStringFlavorInClipboard() {
        Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
        if (clip.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            return true;
        }
        return false;
    }
    
    private void adjustPasteMenuItem() {
        if (isStringFlavorInClipboard()) {
            pasteMenuItem.setEnabled(true);
        } else {
            pasteMenuItem.setEnabled(false);
        }
    }
    
    @Override
    public void flavorsChanged(FlavorEvent e) {
        adjustPasteMenuItem();
    }
    
    private Image getIcon() {
        URL url = getClass().getResource("/img/hodoku01.png");
        //URL url = getClass().getResource("/img/hodoku_16.png");
        return getToolkit().getImage(url);
    }
    
    class MyFileFilter extends FileFilter {
        private int type;
        
        MyFileFilter(int type) {
            this.type = type;
        }
        
        @Override
        public boolean accept(File f) {
            if (f.isDirectory()) return true;
            String[] parts = f.getName().split("\\.");
            if (parts.length > 1) {
                String ext = parts[parts.length - 1];
                switch (type) {
                    case 0:
                        // Configuration Files
                        if (ext.equalsIgnoreCase(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.config_file_ext")))
                            return true;
                    case 1:
                        // Puzzles with Solutions
                        if (ext.equalsIgnoreCase(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.solution_file_ext")))
                            return true;
                    default:
                        return false;
                }
            }
            return false;
        }
        
        @Override
        public String getDescription() {
            switch (type) {
                case 0:
                    return java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.config_file_descr");
                case 1:
                    return java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.solution_file_descr");
                default:
                    return java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.unknown_file_type");
            }
        }
    }
    
    
    class MyCaretListener implements CaretListener {
        private boolean inUpdate = false;

        @Override
        public void caretUpdate(CaretEvent e) {
            if (inUpdate) {
                // no recursion!
                return;
            }
            //System.out.println("caretUpdate(): " + e.getDot() + "/" + e.getMark());
            // if everything is highlighted, don't interfere or it will be impossible
            // to copy the step to the clipboard
            // try to identify the line
            String text = hinweisTextArea.getText();
            if (e.getDot() == 0 && e.getMark() == text.length()) {
                // do nothing!
                return;
            }
            //System.out.println(text);
            int dot = e.getDot() > e.getMark() ? e.getDot() : e.getMark();
            int line = 0;
            int start = 0;
            int end = 0;
            int act = -1;
            while (act < dot) {
                act = text.indexOf('\n', act + 1);
                if (act == -1) {
                    // ok, done
                    end = text.length() - 1;
                    break;
                } else if (act < dot) {
                    start = act;
                    line++;
                } else {
                    end = act;
                    break;
                }
            }
            if (end > 0) {
                //System.out.println("Found: start = " + start + ", end = " + end + ", line = " + line);
                inUpdate = true;
                if (line == 0) {
//                    hinweisTextArea.setSelectionStart(0);
//                    hinweisTextArea.setSelectionEnd(0);
                    sudokuPanel.setChainInStep(-1);
                } else {
                    hinweisTextArea.setSelectionStart(start + 1);
                    hinweisTextArea.setSelectionEnd(end);
                    sudokuPanel.setChainInStep(line - 1);
                }
                inUpdate = false;
            }
        }
        
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem aboutMenuItem;
    private javax.swing.JRadioButtonMenuItem allStepsMenuItem;
    private javax.swing.JMenuItem alleHiddenSinglesSetzenMenuItem;
    private javax.swing.JMenu ansichtMenu;
    private javax.swing.JMenu bearbeitenMenu;
    private javax.swing.JMenuItem beendenMenuItem;
    private javax.swing.JMenuItem configMenuItem;
    private javax.swing.JMenuItem copyCluesMenuItem;
    private javax.swing.JMenuItem copyFilledMenuItem;
    private javax.swing.JMenuItem copyLibraryMenuItem;
    private javax.swing.JMenuItem copyPmGridMenuItem;
    private javax.swing.JMenuItem copyPmGridWithStepMenuItem;
    private javax.swing.JMenu dateiMenu;
    private javax.swing.JMenuItem druckenMenuItem;
    private javax.swing.ButtonGroup eingebenSpielenButtonGroup;
    private javax.swing.JToggleButton f1ToggleButton;
    private javax.swing.JToggleButton f2ToggleButton;
    private javax.swing.JToggleButton f3ToggleButton;
    private javax.swing.JToggleButton f4ToggleButton;
    private javax.swing.JToggleButton f5ToggleButton;
    private javax.swing.JToggleButton f6ToggleButton;
    private javax.swing.JToggleButton f7ToggleButton;
    private javax.swing.JToggleButton f8ToggleButton;
    private javax.swing.JToggleButton f9ToggleButton;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JPanel hintPanel;
    private javax.swing.JButton hinweisAbbrechenButton;
    private javax.swing.JButton hinweisAusführenButton;
    private javax.swing.JButton hinweisKonfigurierenButton;
    private javax.swing.JTextArea hinweisTextArea;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator10;
    private javax.swing.JSeparator jSeparator11;
    private javax.swing.JSeparator jSeparator12;
    private javax.swing.JSeparator jSeparator13;
    private javax.swing.JSeparator jSeparator14;
    private javax.swing.JSeparator jSeparator15;
    private javax.swing.JSeparator jSeparator16;
    private javax.swing.JSeparator jSeparator17;
    private javax.swing.JSeparator jSeparator18;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JSeparator jSeparator6;
    private javax.swing.JSeparator jSeparator7;
    private javax.swing.JSeparator jSeparator9;
    private javax.swing.JToolBar jToolBar1;
    private javax.swing.JMenuItem keyMenuItem;
    private javax.swing.ButtonGroup levelButtonGroup;
    private javax.swing.JComboBox levelComboBox;
    private javax.swing.JRadioButtonMenuItem levelExtremMenuItem;
    private javax.swing.JRadioButtonMenuItem levelKniffligMenuItem;
    private javax.swing.JRadioButtonMenuItem levelLeichtMenuItem;
    private javax.swing.JMenu levelMenu;
    private javax.swing.JRadioButtonMenuItem levelMittelMenuItem;
    private javax.swing.JRadioButtonMenuItem levelSchwerMenuItem;
    private javax.swing.JMenuItem loadMenuItem;
    private javax.swing.JMenuItem lösungsSchrittMenuItem;
    private javax.swing.JMenuItem mediumHintMenuItem;
    private javax.swing.JMenuItem neuMenuItem;
    private javax.swing.JButton neuerHinweisButton;
    private javax.swing.JButton neuesSpielToolButton;
    private javax.swing.JMenu optionenMenu;
    private javax.swing.JSplitPane outerSplitPane;
    private javax.swing.JMenuItem pasteMenuItem;
    private javax.swing.JToggleButton redGreenToggleButton;
    private javax.swing.JMenuItem redoMenuItem;
    private javax.swing.JButton redoToolButton;
    private javax.swing.JMenuItem resetSpielMenuItem;
    private javax.swing.JMenuItem resetViewMenuItem;
    private javax.swing.JMenu rätselMenu;
    private javax.swing.JMenuItem saveAsMenuItem;
    private javax.swing.JMenuItem seiteEinrichtenMenuItem;
    private javax.swing.JCheckBoxMenuItem showCandidatesMenuItem;
    private javax.swing.JCheckBoxMenuItem showDeviationsMenuItem;
    private javax.swing.JCheckBoxMenuItem showWrongValuesMenuItem;
    private javax.swing.JRadioButtonMenuItem solutionMenuItem;
    private javax.swing.JMenuItem speichernAlsBildMenuItem;
    private javax.swing.JRadioButtonMenuItem spielEingebenMenuItem;
    private javax.swing.JRadioButtonMenuItem spielenMenuItem;
    private javax.swing.JLabel statusLabel1;
    private javax.swing.JLabel statusLabel2;
    private javax.swing.JLabel statusLabel3;
    private javax.swing.JLabel statusLabel4;
    private javax.swing.JLabel statusLabel5;
    private javax.swing.JLabel statusLabel6;
    private javax.swing.JLabel statusLabel7;
    private javax.swing.JLabel statusLabel8;
    private javax.swing.JLabel statusLabel9;
    private javax.swing.JLabel statusLabelAus;
    private javax.swing.JLabel statusLabelLevel;
    private javax.swing.JPanel statusLinePanel;
    private javax.swing.JRadioButtonMenuItem sudokuOnlyMenuItem;
    private javax.swing.JRadioButtonMenuItem summaryMenuItem;
    private javax.swing.JMenuItem undoMenuItem;
    private javax.swing.JButton undoToolButton;
    private javax.swing.JMenuItem vageHintMenuItem;
    private javax.swing.ButtonGroup viewButtonGroup;
    // End of variables declaration//GEN-END:variables

}
