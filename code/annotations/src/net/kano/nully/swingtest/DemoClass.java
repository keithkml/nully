/*
 *  Copyright (c) 2005, Keith Lea
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *  - Neither the name of the Joust Project nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *  COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 *
 */

package net.kano.nully.swingtest;

import javax.swing.JTextField;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class DemoClass {
    private JTextField paymentField;
    private JTextField historyArea;

    private Object principal;
    private Object rate;
    private Object term;
    private ExecutorService executor = Executors.newSingleThreadExecutor();;

    private void calculateButtonHandler() {
        paymentField.setText("Calculating ...");
        AtomicReference<Double> paymentHolder = new AtomicReference<Double>();
        updatePayment(paymentHolder);
        updateHistory(paymentHolder);
    }

    private void updateHistory(final AtomicReference<Double> paymentHolder) {
        new SwingWorker<String>(executor) {
            public String computeValue() {
                return getHistory(paymentHolder.get());
            }

            public void updateUI(String history) {
                historyArea.setText(history);
            }
        };
    }

    private void updatePayment(final AtomicReference<Double> paymentHolder) {
        new SwingWorker<Double>(executor) {
            public Double computeValue() {
                return getPayment(principal, rate, term);
            }

            public void updateUI(Double amount) {
                paymentField.setText(amount.toString());
                paymentHolder.set(amount);
            }
        };
    }

    private double getPayment(Object principal, Object rate, Object term) {
        return 0;
    }

    private String getHistory(double aDouble) {
        return "..";
    }
}
