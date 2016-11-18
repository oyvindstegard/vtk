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
package vtk.repository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * A source of content which can be stored in the content repository.
 *
 * <p>The source can be anything, and it shall be accessible through this API either
 * as a generic stream or a local file.
 *
 * @see ContentInputSources
 */
public interface ContentInputSource {

    /**
     * @return Should <code>true</code> if the source is a file, <code>false</code> otherwise.
     */
    boolean isFile();

    /**
     * @return a <code>File</code> object for the source file, or <code>null</code>
     * if the source is not file based.
     */
    File file();

    /**
     * @return <code>true</code> if the source is a [temporary] file which can
     * be automatically deleted after storing its contents in the repository.
     */
    boolean canDeleteSourceFile();

    /**
     * @return an input stream for the content, never <code>null</code>. May return
     * the same stream instance on multiple invocations, so the repository does
     * not rely on being able to read this stream more than once.
     *
     * @throws IOException if an error occurs when providing stream
     */
    InputStream stream() throws IOException;

}
