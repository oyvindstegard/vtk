/* Copyright (c) 2005, University of Oslo, Norway
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 
 *  * Neither the name of the University of Oslo nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *      
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.vortikal.shell;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A shell handler that reads commands from network clients. Supports
 * several simultaneously connected clients sharing the same shell.
 *
 * <p>WARNING: should not be used in production environments, as there
 * is no access control built in (anyone can connect to the listening
 * port).
 * 
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>port</code> - the port to listen on (default
 *   <code>9999</code>)
 * </ul>
 */
public class SocketHandlerThread extends ShellHandlerThread {

    private Log logger = LogFactory.getLog(this.getClass());
    private boolean alive = true;
    private int port = 9999;
    private Set connections = new HashSet();
    
        
    public void setPort(int port) {
        if (port <= 0) {
            throw new IllegalArgumentException("Invalid port number: " + port);
        }
        this.port = port;
    }
    

    public void interrupt() {
        this.logger.info("Exiting");
        this.alive = false;
        Set interruptableConnections = new HashSet();
        for (Iterator i = this.connections.iterator(); i.hasNext();) {
            interruptableConnections.add(i.next());
        }

        for (Iterator i = interruptableConnections.iterator(); i.hasNext();) {
            ConnectionHandlerThread thread = (ConnectionHandlerThread) i.next();
            try {
                thread.interrupt();
            } catch (Throwable t) {
                this.logger.warn("Error interrupting connection handler " + thread);
            }
        }
        super.interrupt();
    }
        

    public void run() {

        ServerSocket serverSocket = null;
        long count = 0;

        try {
            serverSocket = new ServerSocket(this.port);
        } catch (IOException e) {
            this.logger.warn("Unable to listen to port " + this.port, e);
            return;
        }

        this.logger.info("Listening for connections on port " + this.port);

        while (this.alive) {
            try {

                Socket clientSocket = serverSocket.accept();
                count++;
                ConnectionHandlerThread handlerThread = new ConnectionHandlerThread(
                    this.getName() + ".handler.", clientSocket);
                handlerThread.setName(handlerThread.getName()
                                      + count);
                this.connections.add(handlerThread);
                handlerThread.start();

            } catch (Throwable t) {
                this.logger.warn("Error creating connection handler", t);
            }
        }

        try {
            this.logger.info("Closing socket " + serverSocket);
            serverSocket.close();
        } catch (IOException e) {
            this.logger.warn("Unable to close socket " + serverSocket, e);
        }

    }


    private class ConnectionHandlerThread extends Thread {
        private boolean alive = true;
        private Socket clientSocket;
        private InputStream inStream;
        private OutputStream outStream;
        private BufferedReader reader;
        private PrintStream outputter;
        private InetAddress peer;

        public ConnectionHandlerThread(String name, Socket clientSocket) throws IOException {
            super(name);
            this.clientSocket = clientSocket;
            this.inStream = clientSocket.getInputStream();
            this.outStream = clientSocket.getOutputStream();
            this.reader = new BufferedReader(new InputStreamReader(
                                                 this.inStream));
            this.outputter = new PrintStream(this.outStream);
            this.peer = clientSocket.getInetAddress();
        }
        
        public void interrupt() {
            SocketHandlerThread.this.logger.info("Closing connection with " + this.peer);
            this.alive = false;
            try {
                this.clientSocket.close();
            } catch (IOException e) {
                SocketHandlerThread.this.logger.warn("Unable to close connection with " + this.peer, e);
            }
            super.interrupt();
        }
        

        public void run() {

            SocketHandlerThread.this.logger.info("Connection established with: " + this.peer);

            while (this.alive) {
                try {
                    
                    if (this.alive) {
                        this.outputter.print("vrtx$ ");
                        
                        String line = this.reader.readLine();

                        if (line == null) {
                            SocketHandlerThread.this.logger.info("Disconnected from " + this.peer);
                            return;
                        }

                        if (SocketHandlerThread.this.logger.isDebugEnabled()) {
                            SocketHandlerThread.this.logger.debug("Read line from " + this.peer);
                        }

                        getShell().eval(line, this.outputter);
                    }
                } catch (Throwable t) {

                    this.alive = false;

                    try {
                        this.outputter.println("Error: " + t.getMessage());
                        t.printStackTrace(this.outputter);
                    } catch (Throwable t2) {
                        SocketHandlerThread.this.logger.info("Unable to write error message to " + this.peer, t2);

                    } finally {
                        SocketHandlerThread.this.logger.warn("Error handling connection with " + this.peer, t);
                    }
                }
            }
            SocketHandlerThread.this.connections.remove(this);
        }
    }
    
}
