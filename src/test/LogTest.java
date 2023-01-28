/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Bernhard Hobiger
 */
public class LogTest {

    private void openFile() {
        Logger tmpLogger = Logger.getLogger("test2");
        tmpLogger.logp(Level.INFO, "LogTest", "main()", "Und noch einmal Guten Morgen!");
        try {
            FileWriter out = new FileWriter("");
        } catch (IOException ex) {
            //Logger.getLogger(LogTest.class.getName()).log(Level.SEVERE, null, ex);
            Logger.getLogger(LogTest.class.getName()).logp(Level.SEVERE, getClass().getName(), "openFile()", null, ex);
        }
    }

    @SuppressWarnings("empty-statement")
    public static void main(String[] args) throws IOException, InterruptedException {
        Thread.sleep(10000);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        try {
            String line = null;
            while ((line = in.readLine()).compareTo("q") != 0) {
                ;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

//        Handler fh = new FileHandler("%t/hodoku.log", true);
//        fh.setFormatter(new SimpleFormatter());
//        fh.setLevel(Level.SEVERE);
//        Handler ch = new ConsoleHandler();
//        Logger rootLogger = Logger.getLogger("");
//        rootLogger.addHandler(fh);
//        //rootLogger.addHandler(ch);
//        rootLogger.setLevel(Level.ALL);
//        
//        rootLogger.log(Level.INFO, "Guten Morgen!");
//        
//        Logger tmpLogger = Logger.getLogger("test");
//        tmpLogger.logp(Level.INFO, "LogTest", "main()", "Noch einmal Guten Morgen!");
//        LogTest test = new LogTest();
//        test.openFile();
    }
}
