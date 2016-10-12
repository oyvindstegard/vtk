/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.util.repository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.ApplicationListener;

import vtk.repository.event.RepositoryEvent;

/**
 * Abstract base class for handlers of
 * {@link RepositoryEvent repository events}.
 * 
 * <p>Can operate in either synchronous or asynchronous (the default)
 * mode. In the synchronous mode events are processed in the
 * currently executing thread, whereas in the asynchronous mode the
 * event processing occurs in a separate thread, scheduled by a 
 * single-threaded {@link ExecutorService}.</p>
 */
public abstract class AbstractRepositoryEventHandler
    implements ApplicationListener<RepositoryEvent> {

    private ExecutorService executorService = null;

    /**
     * Constructor with setting for private async execution of the event handler
     * method.
     *
     * <p>Event handlers that will never block or execute long running/heavy
     * operations generally do not need async handling.
     *
     * @param async whether to create a dedicated thread for handling events, if
     * <code>true</code> then a dedicated thread is created and used to execute
     * the event handler method, otherwise the event handler executes in the
     * thread that caused the repository event to be published.
     */
    public AbstractRepositoryEventHandler(boolean async) {
        if (async) {
            executorService = Executors.newSingleThreadExecutor(r ->
                new Thread(r, name()));
        }
    }

    @Override
    public final void onApplicationEvent(RepositoryEvent event) {
        if (executorService != null) {
            executorService.submit(() -> handleEvent(event));
        }
        else {
            handleEvent(event);
        }
    }

    protected String name() {
        return getClass().getSimpleName();
    }

    public abstract void handleEvent(RepositoryEvent event);
}
