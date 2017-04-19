/* Copyright (c) 2007, University of Oslo, Norway
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Optional;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.util.io.InputSource;


public class RepositoryInputSource implements InputSource {

    private Repository repository;
    private String token;
    private Path uri; 
    
    public RepositoryInputSource(Path uri, Repository repository, String token) {
        this.repository = repository;
        this.token = token;
        this.uri = uri;
    }

    @Override
    public String getID() {
        return uri.toString();
    }
    
    @Override
    public Optional<Instant> getLastModified() throws UncheckedIOException {
        try {
            Resource resource = repository.retrieve(
                    token, this.uri, true);
            return Optional.of(Instant.ofEpochMilli(resource.getLastModified().getTime()));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (Exception e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    @Override
    public Charset getCharacterEncoding() {
        try {
            Resource resource = repository.retrieve(
                    token, uri, true);
            return Charset.forName(resource.getCharacterEncoding());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (Exception e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    @Override
    public InputStream getInputStream() {
        try {
            return repository.getInputStream(token, uri, true);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (Exception e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    @Override
    public String toString() {
        return getID();
    }

}

