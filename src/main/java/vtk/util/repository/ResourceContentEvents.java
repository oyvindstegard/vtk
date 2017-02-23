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
package vtk.util.repository;

import java.io.InputStream;
import java.util.function.Consumer;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.event.ContentModificationEvent;
import vtk.repository.event.RepositoryEvent;
import vtk.repository.event.RepositoryInitEvent;
import vtk.util.Result;

public class ResourceContentEvents implements Consumer<RepositoryEvent> {
    private Path uri;
    private String token;
    private Consumer<Result<InputStream>> next;
    
    public ResourceContentEvents(Path uri, String token, 
            Consumer<Result<InputStream>> next) {
        this.uri = uri;
        this.next = next;
        this.token = token;
    }
    
    @Override
    public void accept(RepositoryEvent event) {
        boolean match = (event instanceof RepositoryInitEvent)
                || ((event instanceof ContentModificationEvent)
                        && ((ContentModificationEvent) event).getURI().equals(uri));
        if (!match) {
            return;
        }
        try {
            InputStream stream = ((Repository) event.getSource())
                    .getInputStream(token, uri, true);
            next.accept(Result.success(stream));
        }
        catch (Throwable t) {
            next.accept(Result.failure(t));
        }
    }
}
