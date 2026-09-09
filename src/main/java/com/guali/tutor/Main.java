package com.guali.tutor;

import com.guali.tutor.controller.MainController;
import com.guali.tutor.form.MainForm;

import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainForm mainForm = new MainForm();
            new MainController(mainForm);
            mainForm.setVisible(true);
        });
    }
}
