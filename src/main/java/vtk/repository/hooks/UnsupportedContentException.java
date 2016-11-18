/* Copyright (c) 2014, University of Oslo, Norway
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
package vtk.repository.hooks;

import java.io.IOException;
import vtk.repository.ContentInputSource;
import vtk.repository.ContentInputSources;
import vtk.util.io.IO;

/**
 * Exception which may be thrown by type handler hooks when the provided
 * content is unsupported, even though content-type was matched for the hook. In
 * such cases, the repository likely needs to execute fallback actions and needs
 * access to the original content stream that was uploaded. We assume the input
 * stream provided to the type handling hook was consumed in the initial attempt to
 * analyze or process the content, so a fresh stream needs to be provided back
 * to the repository via this exception.
 * 
 * This exception should <em>not</em> be used for transient error conditions, only
 * for permanent errors where type handler hook will never be able to process
 * the particular content correctly. It should then be processed in the regular
 * way by the repository.
 */
public class UnsupportedContentException extends TypeHandlerHookException {

    private final ContentInputSource content;
    
    public UnsupportedContentException(String message, ContentInputSource content) {
        super(message);
        this.content = content;
    }
    
    public UnsupportedContentException(String message, ContentInputSource content, Throwable cause) {
        super(message, cause);
        this.content = content;
    }
    
    /**
     * Convenience constructor which provides a <code>ContentStream</code>
     * backed by a <code>TempFile</code> instance.
     * 
     * @param message
     * @param contentFile 
     */
    public UnsupportedContentException(String message, IO.TempFile contentFile) throws IOException {
        super(message);
        this.content = ContentInputSources.fromFile(contentFile.file(), true);
    }
    
    /**
     * Convenience constructor which provides a <code>ContentStream</code>
     * backed by a <code>TempFile</code> instance.
     * 
     * @param message
     * @param contentFile 
     */
    public UnsupportedContentException(String message, IO.TempFile contentFile, Throwable cause) throws IOException {
        super(message, cause);
        this.content = ContentInputSources.fromFile(contentFile.file(), true);
    }
    
    /**
     * Get a <code>ContentInputSource</code> representation of the content that
     * was unsupported. May return <code>null</code> if none could be provided.
     * @return a content input source, or <code>null</code> if none could be provided.
     */
    public ContentInputSource getContentInputSource() {
        return this.content;
    }

}
