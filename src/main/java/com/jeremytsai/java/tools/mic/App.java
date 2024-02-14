package com.jeremytsai.java.tools.mic;


import com.formdev.flatlaf.FlatDarkLaf;
import com.jeremytsai.java.tools.mic.panel.MainPanel;

import javax.swing.*;

/**
 * @author Jeremy Tsai
 * @version 1.0
 * @since 2024-02-14 12:46
 */
public class App {


    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(App::createGuiAndShow);
    }

    public static void createGuiAndShow() {

        JFrame frame = new JFrame("Markdown images converter");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add(new MainPanel());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

}
