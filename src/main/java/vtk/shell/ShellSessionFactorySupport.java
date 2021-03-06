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
package vtk.shell;


import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Basic support for shell session factories.
 *
 * <p>Provides code to handle session initialization with default variable
 * bindings and configurable set of initialization files.
 */
public abstract class ShellSessionFactorySupport implements ShellSessionFactory,
        ApplicationContextAware, ResourceLoaderAware {

    protected ResourceLoader resourceLoader;
    protected ApplicationContext applicationContext;
    protected List<String> initFiles = Collections.emptyList();
    protected List<String> initExpressions = Collections.emptyList();

    private final Logger logger = LoggerFactory.getLogger(
            ShellSessionFactorySupport.class.getName());

    /**
     * Binds well known variables "context", "resourceLoader" and "logger" to the
     * session context, then evaluates all configured init files.
     *
     * @param ss the shell session to initialize
     * @param shellContextLogger the logger instance for the shell session
     * @throws Exception
     */
    protected void initializeSession(ShellSession ss, Logger shellContextLogger) throws Exception {
        ss.bind("context", applicationContext);
        ss.bind("resourceLoader", resourceLoader);
        ss.bind("logger", shellContextLogger);

        for (String expression: initExpressions) {
            ss.evaluate(expression);
        }

        for (String fileResource: initFiles) {
            Resource resource = this.resourceLoader.getResource(fileResource);
            try (InputStream stream = resource.getInputStream()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Evaluating init file " + resource);
                }

                ss.evaluate(new InputStreamReader(stream, StandardCharsets.UTF_8));
            } catch (IOException e) {
                logger.warn("Cannot resolve init file path '"  + fileResource + "'", e);
            }
        }
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Set a list of source code files used to initialize a shell session.
     *
     * <p>The files are evaluated by calling {@link ShellSession#evaluate(java.io.Reader) }, and the
     * contents should use UTF-8 encoding.
     *
     * <p>Evaluataion of init files occur after {@link #setInitExpressions(java.util.List) evaluation
     * of init expressions}.
     * @param initFiles
     */
    public void setInitFiles(List<String> initFiles) {
        this.initFiles = Objects.requireNonNull(initFiles, "initFiles cannot be null").stream()
                .filter(f -> !f.trim().isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Set a list of expressions which can be used to initialize new sessions.
     *
     * <p>The expressions are evaluated by calling {@link ShellSession#evaluate(java.lang.String) }.
     *
     * <p>Evaluation of init expressions occur before {@link #setInitFiles(java.util.List) evaluation
     * of init files}.
     *
     * @param initExpressions
     */
    public void setInitExpressions(List<String> initExpressions) {
        this.initExpressions = Objects.requireNonNull(initExpressions, "initExpressions cannot be null").stream()
                .map(e -> e.trim())
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toList());
    }

}
