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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Helper class to construct repository <code>ContentInputSource</code> instances
 * from input streams or files.
 */
public final class ContentInputSources {

    private ContentInputSources() {}

    /**
     * Make a stream based content input source.
     * @param content the stream for the source
     * @return a content input source based on the provided input stream
     */
    public static ContentInputSource fromStream(final InputStream content) {
        return new ContentInputSource() {
            @Override
            public boolean isFile() {
                return false;
            }
            @Override
            public boolean canDeleteSourceFile() {
                return false;
            }
            @Override
            public File file() {
                return null;
            }
            @Override
            public InputStream stream() throws IOException {
                return content;
            }
        };
    }

    /**
     * Make a file based content input source.
     * @param content a file which contains the source content
     * @return a content input source based on the provided file
     */
    public static ContentInputSource fromFile(final File content) {
        return fromFile(content, false);
    }

    /**
     * Make a file based content input source.
     * @param content a file which contains the source content
     * @param canDelete <code>true</code> if the source file is temporary and can be deleted
     * by the repository if storing is successful, <code>false</code> otherwise. Allowing
     * delete can dramatically improve performance if the source file is on the same
     * file system as the repository content store, because then it can be moved in place.
     * @return a content input source based on the provided file
     */
    public static ContentInputSource fromFile(final File content, final boolean canDelete) {
        return new ContentInputSource() {
            @Override
            public boolean isFile() {
                return true;
            }
            @Override
            public File file() {
                return content;
            }
            @Override
            public boolean canDeleteSourceFile() {
                return canDelete;
            }
            @Override
            public InputStream stream() throws IOException {
                return new FileInputStream(content);
            }
        };
    }

    /**
     * Construct input source from bytes in memory.
     * @param bytes
     * @return
     */
    public static ContentInputSource fromBytes(final byte[] bytes) {
        return new ContentInputSource() {
            @Override
            public boolean isFile() {
                return false;
            }
            @Override
            public File file() {
                return null;
            }
            @Override
            public boolean canDeleteSourceFile() {
                return false;
            }
            @Override
            public InputStream stream() throws IOException {
                return new ByteArrayInputStream(bytes);
            }
        };
    }

}
