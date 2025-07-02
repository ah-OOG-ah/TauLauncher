package org.taumc.launcher.gui.components;

import javax.swing.*;

public class Debounce {
    private final int delay;
    private Timer timer;

    public Debounce(int delayMs) { delay = delayMs; }

    public void call(Runnable r) {
        if (timer != null) timer.stop();
        timer = new Timer(delay, e -> r.run());
        timer.setRepeats(false);
        timer.start();
    }
}
