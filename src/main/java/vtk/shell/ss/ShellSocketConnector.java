/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.shell.ss;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import vtk.shell.SessionAuthenticator;

/**
 * Manages one or more socket based shell sessions.
 */
public class ShellSocketConnector implements InitializingBean, DisposableBean, BeanNameAware {

    private String beanName;
    private ShellSessionFactory shellSessionFactory;
    private Optional<SessionAuthenticator> sessionAuthenticator = Optional.empty();
    private int port = 2222;
    private String listenAddress = "localhost";

    private ExecutorService sessionExecutor;
    private final Map<ShellSession,Socket> sessions = new HashMap<>();
    private ServerSocket serverSocket = null;
    private Thread acceptThread = null;

    private final Logger logger = LoggerFactory.getLogger(ShellSocketConnector.class.getName());

    @Override
    public void afterPropertiesSet() throws Exception {
  
        this.sessionExecutor = Executors.newCachedThreadPool(new CustomizableThreadFactory(beanName + "-"));
        
        startAcceptThread();
    }

    private void startAcceptThread() throws IOException {
        try {
            if ("*".equals(listenAddress)) {
                serverSocket = new ServerSocket(port);
            } else {
                InetAddress address = InetAddress.getByName(listenAddress);
                serverSocket = new ServerSocket(this.port, -1, address);
            }
        } catch (IOException e) {
            logger.warn("Unable to listen to port " + this.port, e);
            return;
        }
        logger.info("Listening for connections on port " + this.port);

        acceptThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    final Socket clientSocket = serverSocket.accept();
                    try {
                        InputStream input = clientSocket.getInputStream();
                        OutputStream output = clientSocket.getOutputStream();

                        BufferedReader br = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                        PrintStream ps = new PrintStream(output);

                        authenticateSession(br, ps);

                        final ShellSession ss = shellSessionFactory.newSession(br, ps);
                        ss.setClientId(clientSocket.getInetAddress().toString());
                        ss.setTerminationCallback(session -> {
                            Socket s = null;
                            synchronized(sessions) {
                                s = sessions.remove(session);
                            }
                            try { if (s != null) s.close(); }
                            catch (IOException io){ }
                            logger.info("Client disconnected: " + session.clientId().orElse("unknown client"));
                        });

                        synchronized(sessions) {
                            sessions.put(ss, clientSocket);
                        }

                        sessionExecutor.submit(ss);

                        logger.info("Connection established with " + ss.clientId().orElse("unknown client"));
                    } catch (Throwable t) {
                        clientSocket.close();
                    }
                } catch (Throwable t) {
                    // Ignore SocketException due to regular server socket close
                    // by checking shutdown status.
                    if (!serverSocket.isClosed()) {
                        logger.warn("Error while waiting for connections", t);
                    }
                }
            }
        });
        acceptThread.setName(beanName + "-socket-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void authenticateSession(BufferedReader input, PrintStream output) throws Exception {
        if (!this.sessionAuthenticator.isPresent()) {
            return;
        }
        if (!sessionAuthenticator.get().authenticate(input, output)) {
            throw new RuntimeException("Client failed authentication");
        }
    }

    @Override
    public void setBeanName(String name) {
        this.beanName = name;
    }

    @Override
    public void destroy() throws Exception {
        serverSocket.close();

        sessionExecutor.shutdownNow();

        synchronized(sessions) {
            for (Socket s: sessions.values()) {
                s.close();
            }
            sessions.clear();
        }

    }

    public void setSessionAuthenticator(SessionAuthenticator sa) {
        this.sessionAuthenticator = Optional.of(sa);
    }

    public void setPort(int port) {
        if (port <= 0) {
            throw new IllegalArgumentException("Invalid port number: " + port);
        }
        this.port = port;
    }

    public void setListenAddress(String listenAddress) {
        this.listenAddress = listenAddress;
    }

    @Required
    public void setShellSessionFactory(ShellSessionFactory f) {
        this.shellSessionFactory = f;
    }

}
