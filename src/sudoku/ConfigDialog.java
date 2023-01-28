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

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.FileNotFoundException;
import java.text.MessageFormat;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

/**
 *
 * @author  Bernhard Hobiger
 */
public class ConfigDialog extends javax.swing.JDialog {
    private ConfigSolverPanel myConfigSolverPanel;
    private ConfigGeneralPanel myGeneralPanel;
    private ConfigStepPanel myConfigStepPanel;
    private ConfigColorPanel myConfigColorPanel;
    private ConfigFindAllStepsPanel myConfigFindAllStepsPanel;
    private ConfigProgressPanel myConfigProgressPanel;
    private ConfigTrainigPanel myConfigTrainingPanel;

    /** Creates new form ConfigDialog */
    public ConfigDialog(java.awt.Frame parent, boolean modal, int tabIndex) {
        super(parent, modal);
        initComponents();
        
        getRootPane().setDefaultButton(okButton);

        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
        Action escapeAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible( false );
            }
        };
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "ESCAPE");
        getRootPane().getActionMap().put("ESCAPE", escapeAction);
        
        myConfigSolverPanel = new ConfigSolverPanel();
        solverPanel.add(myConfigSolverPanel, BorderLayout.CENTER);
        
        myGeneralPanel = new ConfigGeneralPanel(parent);
        generalPanel.add(myGeneralPanel, BorderLayout.CENTER);
        
        myConfigStepPanel = new ConfigStepPanel();
        stepConfigPanel.add(myConfigStepPanel, BorderLayout.CENTER);
        
        myConfigColorPanel = new ConfigColorPanel();
        colorPanel.add(myConfigColorPanel, BorderLayout.CENTER);
        
        myConfigFindAllStepsPanel = new ConfigFindAllStepsPanel();
        findAllStepsPanel.add(myConfigFindAllStepsPanel, BorderLayout.CENTER);
        
        myConfigProgressPanel = new ConfigProgressPanel();
        heuristicsPanel.add(myConfigProgressPanel, BorderLayout.CENTER);

        myConfigTrainingPanel = new ConfigTrainigPanel();
        trainingPanel.add(myConfigTrainingPanel, BorderLayout.CENTER);
        
        if (tabIndex != -1) {
            tabbedPane.setSelectedIndex(tabIndex);
        }
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tabbedPane = new javax.swing.JTabbedPane();
        generalPanel = new javax.swing.JPanel();
        solverPanel = new javax.swing.JPanel();
        findAllStepsPanel = new javax.swing.JPanel();
        heuristicsPanel = new javax.swing.JPanel();
        stepConfigPanel = new javax.swing.JPanel();
        trainingPanel = new javax.swing.JPanel();
        colorPanel = new javax.swing.JPanel();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("intl/ConfigDialog"); // NOI18N
        setTitle(bundle.getString("ConfigDialog.title")); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        generalPanel.setLayout(new java.awt.BorderLayout());
        tabbedPane.addTab(bundle.getString("ConfigDialog.generalPanel.TabConstraints.tabTitle"), generalPanel); // NOI18N

        solverPanel.setLayout(new java.awt.BorderLayout());
        tabbedPane.addTab(bundle.getString("ConfigDialog.solverPanel.TabConstraints.tabTitle"), solverPanel); // NOI18N

        findAllStepsPanel.setLayout(new java.awt.BorderLayout());
        tabbedPane.addTab(bundle.getString("ConfigDialog.findAllStepsPanel.TabConstraints.tabTitle"), findAllStepsPanel); // NOI18N

        heuristicsPanel.setLayout(new java.awt.BorderLayout());
        tabbedPane.addTab(bundle.getString("ConfigDialog.heuristicsPanel.TabConstraints.tabTitle"), heuristicsPanel); // NOI18N

        stepConfigPanel.setLayout(new java.awt.BorderLayout());
        tabbedPane.addTab(bundle.getString("ConfigDialog.stepConfigPanel.TabConstraints.tabTitle"), stepConfigPanel); // NOI18N

        trainingPanel.setLayout(new java.awt.BorderLayout());
        tabbedPane.addTab(bundle.getString("ConfigDialog.trainingPanel.TabConstraints.tabTitle"), trainingPanel); // NOI18N

        colorPanel.setLayout(new java.awt.BorderLayout());
        tabbedPane.addTab(bundle.getString("ConfigDialog.colorPanel.TabConstraints.tabTitle"), colorPanel); // NOI18N

        okButton.setMnemonic(java.util.ResourceBundle.getBundle("intl/ConfigDialog").getString("ConfigDialog.okButton.mnemonic").charAt(0));
        okButton.setText(bundle.getString("ConfigDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        cancelButton.setMnemonic(java.util.ResourceBundle.getBundle("intl/ConfigDialog").getString("ConfigDialog.cancelButton.mnemonic").charAt(0));
        cancelButton.setText(bundle.getString("ConfigDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(okButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton))
                    .addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 546, Short.MAX_VALUE))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {cancelButton, okButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 478, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(okButton)
                    .addComponent(cancelButton))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        myConfigSolverPanel.okPressed();
        myGeneralPanel.okPressed();
        myConfigStepPanel.okPressed();
        myConfigColorPanel.okPressed();
        myConfigFindAllStepsPanel.okPressed();
        myConfigProgressPanel.okPressed();
        myConfigTrainingPanel.okPressed();
        try {
            Options.getInstance().writeOptions();
        } catch (FileNotFoundException ex) {
            MessageFormat formatter = new MessageFormat(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.invalid_filename"));
            String msg = formatter.format(new Object[] { ex.getLocalizedMessage() });
            JOptionPane.showMessageDialog(this, msg, 
                    java.util.ResourceBundle.getBundle("intl/ConfigDialog").getString("ConfigDialog.error"), JOptionPane.ERROR_MESSAGE);
        }
        setVisible(false);
    }//GEN-LAST:event_okButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        //System.out.println("Cancel pressed...");
        setVisible(false);
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        int oldHeight = getHeight();
        int newHeight = oldHeight;
        int oldWidth = getWidth();
        int newWidth = oldWidth;
        int diff = (cancelButton.getY() + cancelButton.getHeight()) - (getHeight() - getInsets().top - getInsets().bottom - 5);
        if (diff > 0) {
            newHeight += diff;
        }
        diff = (tabbedPane.getX() + tabbedPane.getWidth()) - (getWidth() - getInsets().right - getInsets().left - 5);
        if (diff > 0) {
            newWidth += diff;
        }
        if (newHeight != oldHeight || newWidth != oldWidth) {
            setSize(newWidth, newHeight);
        }
    }//GEN-LAST:event_formWindowOpened

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {}
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ConfigDialog(new javax.swing.JFrame(), true, -1).setVisible(true);
            }
        });
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JPanel colorPanel;
    private javax.swing.JPanel findAllStepsPanel;
    private javax.swing.JPanel generalPanel;
    private javax.swing.JPanel heuristicsPanel;
    private javax.swing.JButton okButton;
    private javax.swing.JPanel solverPanel;
    private javax.swing.JPanel stepConfigPanel;
    private javax.swing.JTabbedPane tabbedPane;
    private javax.swing.JPanel trainingPanel;
    // End of variables declaration//GEN-END:variables
    
}
