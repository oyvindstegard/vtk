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

import groovy.lang.Binding;
import groovy.lang.GroovyRuntimeException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.slf4j.LoggerFactory;

/**
 * Creates instances of {@link GroovyShellSession}.
 *
 * <p>{@code ShellSession} instances returned from this factory are <em>not thread safe</em>.
 */
public class GroovyShellSessionFactory extends ShellSessionFactorySupport {

    private String prompt = "groovy> ";
    private boolean wrapResultInBrackets = true;
    private int clearClassCacheInterval = 100;
    private boolean printWelcomeMessage = true;

    @Override
    public ShellSession newSession(BufferedReader input, PrintStream output) throws Exception {
        GroovyShellSession gs = new GroovyShellSession(input, output, prompt);
        super.initializeSession(gs, LoggerFactory.getLogger(GroovyShellSession.class));
        return gs;
    }

    private class GroovyShellSession extends ShellSession {

        private final groovy.lang.GroovyShell interpreter;
        private final Binding binding;
        private final ImportCustomizer imports;
        private int evaluationCounter = 0;

        public GroovyShellSession(BufferedReader input, PrintStream output, String prompt) {
            super(input, output, prompt);

            this.binding = new Binding();
            this.imports = new ImportCustomizer();
            CompilerConfiguration cc = new CompilerConfiguration();
            cc.addCompilationCustomizers(imports);
            this.interpreter = new groovy.lang.GroovyShell(binding, cc);
            // Magic property which overrides GroovyShells' use of System.out for "println" and friends:
            this.interpreter.setProperty("out", output);

            if (printWelcomeMessage) {
                output.println("Groovy on, use \":help\" for meta command listing");
            }
        }

        @Override
        public void bind(String name, Object value) {
            this.binding.setVariable(name, value);
        }

        @Override
        public Object evaluate(String line, PrintStream out) {
            if (line.isEmpty()) {
                return null;
            }
            if (line.startsWith(":")) {
                return metaCommandEvaluate(line, out);
            }

            return evaluate(new StringReader(line), out);
        }

        private final String[] GROOVES = {"Psyche!", "Far Out!", "Dream On", "Catch you on the Flip-side",
        "Boogie", "Right On!", "Can you dig it?", "Cool Beans", "Do Me a Solid", "Groovy.", "What a Fry", "Funkin Donuts"};

        private Object metaCommandEvaluate(String line, PrintStream out) {
            Matcher m = Pattern.compile("^:([a-z?]+)(\\s+(.*))?").matcher(line);
            if (m.matches()) {
                String metaCommand = m.group(1);
                switch (metaCommand) {
                    case "load":
                        String path = m.group(3);
                        if (path == null) {
                            out.println("Error: empty path");
                            return null;
                        }
                        path = path.trim();
                        if (path.startsWith("~" + File.separator)) {
                            path = System.getProperty("user.home") + path.substring(1);
                        }
                        if (!path.matches("^[a-z]+:.*")) {
                            path = "file:" + path;
                        }
                        try (Reader reader = new InputStreamReader(resourceLoader.getResource(path).getInputStream(), StandardCharsets.UTF_8)) {
                            return evaluate(reader);
                        } catch (IOException io) {
                            out.println("Error: failed to load Groovy script: " + io.getMessage());
                            return null;
                        }

                    case "import":
                        String className = m.group(3);
                        if (className == null) {
                            out.println("Error: empty import");
                            return null;
                        }
                        if (className.endsWith(";")) {
                            className = className.substring(0, className.length()-1);
                        }
                        className = className.trim();
                        if (className.endsWith(".*")) {
                            imports.addStarImports(className.substring(0, className.length()-2));
                            return null;
                        }
                        final Class clazz;
                        try {
                            clazz = Class.forName(className);
                        } catch (ClassNotFoundException cnf) {
                            out.println("Error: Class not found: " + className);
                            return null;
                        }

                        imports.addImports(className);
                        return clazz;

                    case "quit":
                    case "q":
                        out.println(GROOVES[(int)(Math.random()*GROOVES.length)]);
                        close();
                        return null;

                    case "?":
                    case "h":
                    case "help":
                        out.println("Available meta commands:");
                        out.println(":load <file or resource URL>  (evaluate a Groovy script)");
                        out.println(":import <class or package.*>  (import classes to session globally)");
                        out.println(":quit, :q                     (quit shell)");
                        out.println(":help, :h, :?                 (show this help)");
                        return null;

                    default:
                        out.println("Unknown meta command: " + metaCommand);
                        return null;
                }
            } else {
                out.println("Bad meta command syntax: " + line);
                return null;
            }
        }

        /**
         * See documentation for {@link ShellSession#evaluate(java.io.Reader, java.io.PrintStream) overriden method}.
         *
         * @param source
         * @param out
         * @return
         */
        @Override
        public Object evaluate(Reader source, PrintStream out) {
            Object result = null;
            try {
                this.interpreter.setProperty("out", out); // OK, since instances of this class are declared as not thread safe
                result = interpreter.evaluate(source);
                if (wrapResultInBrackets) {
                    out.println("[" + result + "]");
                } else {
                    out.println(result);
                }
                bind("_", result);

                if (++evaluationCounter % clearClassCacheInterval == 0) {
                    // Prevent Groovy class cache from growing too big
                    // (one class is created for each piece of code evaluated)
                    interpreter.resetLoadedClasses();
                }
            } catch (GroovyRuntimeException ge) {
                out.println("Evaluation error: " + ge.getMessage());
            } catch (Exception e) {
                out.println("Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }

            return result;
        }
    }

    /**
     * Set shell prompt.
     * @param prompt
     */
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public void setWrapResultInBrackets(boolean wrap) {
        this.wrapResultInBrackets = wrap;
    }

    public void setClearClassCacheInterval(int interval) {
        if (interval <= 0) {
            throw new IllegalArgumentException("Interval must be > 0");
        }
        this.clearClassCacheInterval = interval;
    }

    /**
     * Set whether to print an initial shell message to output when session
     * is started.
     *
     * <p>Default is {@code true}
     *
     * @param print {@code true} to print a message indicating help options
     */
    public void setPrintWelcomeMessage(boolean print) {
        this.printWelcomeMessage = print;
    }

}
