/*
 * Copyright 2018 Joyent, Inc
 * Copyright 2020 The University of Queensland
 * Copyright 2013 Licel LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.licel.jcardsim.remote;

import com.licel.jcardsim.base.CardManager;
import com.licel.jcardsim.base.Simulator;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.Enumeration;
import java.util.Properties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.net.SocketException;

/**
 * VSmartCard Card Implementation.
 *
 * @author alex@cooperi.net
 */
public class VSmartCard {

    Simulator sim;

    public VSmartCard(String host, int port) throws IOException {
        VSmartCardTCPProtocol driverProtocol = new VSmartCardTCPProtocol();
        driverProtocol.connect(host, port);
        startThread(driverProtocol);
    }

    static public void main(String args[]) throws Exception {
        if (args.length !=1) {
            System.out.println("Usage: java com.licel.jcardsim.remote.VSmartCard <jcardsim.cfg>");
            System.exit(-1);
        }
        Properties cfg = new Properties();
        // init Simulator
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(args[0]);
            cfg.load(fis);
        } catch (Throwable t) {
            System.err.println("Unable to load configuration " + args[0] + " due to: " + t.getMessage());
            System.exit(-1);
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
        final Enumeration<?> keys = cfg.propertyNames();
        while (keys.hasMoreElements()) {
            String propertyName = (String) keys.nextElement();
            System.setProperty(propertyName, cfg.getProperty(propertyName));
        }

        String propKey = "com.licel.jcardsim.vsmartcard.host";
        String host = System.getProperty(propKey);
        if (host == null) {
            throw new InvalidParameterException("Missing value for property: " + propKey);
        }

        propKey = "com.licel.jcardsim.vsmartcard.port";
        String port = System.getProperty(propKey);
        if (port == null) {
            throw new InvalidParameterException("Missing value for property: " + propKey);
        }

        new VSmartCard(host, Integer.parseInt(port));
    }

    private void startThread(VSmartCardTCPProtocol driverProtocol) throws IOException {
        sim = new Simulator();
        final IOThread ioThread = new IOThread(sim, driverProtocol);
        final KeyThread keyThread = new KeyThread(driverProtocol);
        ShutDownHook hook = new ShutDownHook(ioThread, keyThread);
        Runtime.getRuntime().addShutdownHook(hook);
        ioThread.start();
        keyThread.start();
    }

    static class ShutDownHook extends Thread {
        IOThread ioThread;
        KeyThread keyThread;

        public ShutDownHook(IOThread ioThread, KeyThread keyThread) {
            this.ioThread = ioThread;
            this.keyThread = keyThread;
        }

        public void run() {
            ioThread.isRunning = false;
            keyThread.isRunning = false;
            System.out.println("Shutdown connections");
            ioThread.driverProtocol.disconnect();
        }
    }

    static class IOThread extends Thread {
        VSmartCardTCPProtocol driverProtocol;
        Simulator sim;
        boolean isRunning;

        public IOThread(Simulator sim, VSmartCardTCPProtocol driverProtocol) {
            this.sim = sim;
            this.driverProtocol = driverProtocol;
            isRunning = true;
        }

        private void hexDump(byte[] apdu) {
            for (int i = 0; i < apdu.length; i += 8) {
                System.out.printf("%04X:  ", i);
                for (int j = i; j < i + 4; ++j) {
                    if (j >= apdu.length)
                        break;
                    System.out.printf("%02X ", apdu[j]);
                }
                System.out.printf(" ");
                for (int j = i + 4; j < i + 8; ++j) {
                    if (j >= apdu.length)
                        break;
                    System.out.printf("%02X ", apdu[j]);
                }
                System.out.printf("\n");
            }
        }

        @Override
        public void run() {
            while (isRunning) {
                try {
                    int cmd = driverProtocol.readCommand();
                    switch (cmd) {
                        case VSmartCardTCPProtocol.POWER_ON:
                        case VSmartCardTCPProtocol.RESET:
                            sim.reset();
                            break;
                        case VSmartCardTCPProtocol.GET_ATR:
                            driverProtocol.writeData(sim.getATR());
                            break;
                        case VSmartCardTCPProtocol.APDU:
                            final byte[] apdu = driverProtocol.readData();
                            System.out.println("== APDU");
                            hexDump(apdu);
                            final byte[] reply = CardManager.dispatchApdu(sim, apdu);
                            System.out.println("== Reply APDU");
                            hexDump(reply);
                            driverProtocol.writeData(reply);
                            break;
                    }
                } catch (Exception e) {
                    System.out.println(e.toString());
                }
            }
        }
    }

    static class KeyThread extends Thread {
        VSmartCardTCPProtocol driverProtocol;
        boolean isRunning;

        public KeyThread(VSmartCardTCPProtocol driverProtocol) {
            this.driverProtocol = driverProtocol;
            isRunning = true;
        }

        @Override
        public void run() {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("== Listening for command keys");
            while (isRunning) {
                try {
                    String cmd = reader.readLine();
                    if(cmd.equals("r")) {
                        System.out.println("== Resetting socket...");
                        driverProtocol.reconnect();
                        System.out.println("== Socket was reset");
                    }
                } catch (Exception e) {
                    System.out.println(e.toString());
                }
            }
        }
    }

}
