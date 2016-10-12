/* Copyright (c) 2016, University of Oslo, Norway
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
package vtk.cluster;

import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps an underlying ClusterAware and converts all calls to asynchronous
 * versions while preserving the order of the individual calls.
 * To be used when the caller cannot afford the ClusterAware to block.
 */
public class AsyncClusterAware implements ClusterAware {
    private final ClusterAware underlyingClusterAware;
    private final Logger log;
    private final ExecutorService executorService;

    public AsyncClusterAware(ExecutorService executorService, ClusterAware underlying) {
        this.underlyingClusterAware = underlying;
        this.log = LoggerFactory.getLogger(
            getClass().getName() + "." + underlying.getClass().getSimpleName());
        this.executorService = executorService;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + underlyingClusterAware + ")";
    }

    @Override
    public void clusterContext(ClusterContext context) {
        executorService.submit(() -> {
            try {
                underlyingClusterAware.clusterContext(context);
            } catch (Exception e) {
                log.error("Failed clusterContext", e);
            }
        });
    }

    @Override
    public void roleChange(ClusterRole role) {
        executorService.submit(() -> {
            try {
                underlyingClusterAware.roleChange(role);
            } catch (Exception e) {
                log.error("Failed roleChange: " + role, e);
            }
        });
    }

    @Override
    public void clusterMessage(Object message) {
        executorService.submit(() -> {
            try {
                underlyingClusterAware.clusterMessage(message);
            } catch (Exception e) {
                log.error("Failed clusterMessage: " + message, e);
            }
        });
    }
}
