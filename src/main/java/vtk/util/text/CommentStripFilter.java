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
package vtk.util.text;

import java.io.IOException;
import java.io.Reader;

/**
 * Strip C++-style comments from a wrapped {@link Reader} or {@link String}
 * instance.
 *
 * <p>
 * Alle <strong>//double-slash line comments</strong> and <strong>/&#42;
 * slash-star star-slash &#42;/</strong>
 * are stripped except those occuring within double-quoted strings in the input.
 * Double quote chars may be backslash escaped within strings.
 *
 * <p>The filter is compatible with JSON syntax and for stripping comments
 * from JSON sources, amongst others.
 *
 * <p>Instances if this class are not thread safe.
 */
public class CommentStripFilter extends Reader {

    private static final int DEFAULT=0, STRING=1, ESCAPE=2, ESCAPE_IN_STRING=3,
                             BLOCK_COMMENT=4, LINE_COMMENT=5;

    private final Reader wrappedReader;

    private int state=DEFAULT;
    private int current=-1;
    private int next=-1;

    /**
     * Construct a new filter wrapping the provided reader.
     * @param reader 
     */
    public CommentStripFilter(Reader reader) {
        this.wrappedReader = reader;
    }
    
    /**
     * Construct a new filter using the provided string as source of input.
     * @param source 
     */
    public CommentStripFilter(String source) {
        this.wrappedReader = new SimpleStringReader(source);
    }
    
    /**
     * Utility method which strips comments from an input string and returns
     * the output string.
     * @param input the string to process
     * @return a string with comments stripped.
     * @throws NullPointerException if input string is <code>null</code>
     */
    public static String stripComments(String input) {
        if (input.isEmpty()) {
            return input;
        }
        final char[] chars = new char[input.length()];
        int count=0;
        try {
            CommentStripFilter filter = new CommentStripFilter(input);
            int ret;
            while ((ret = filter.read(chars, count, chars.length-count)) != -1) {
                count += ret;
            }
        } catch (IOException io) {
            // Can't happen.
        }
        return new String(chars, 0, count);
    }
    
    private final char[] oneCharBuffer = new char[1];
    @Override
    public int read() throws IOException {
        if(read(oneCharBuffer, 0, 1) == -1) {
            return -1;
        } else {
            return oneCharBuffer[0];
        }
    }

    @Override
    public int read(final char[] cbuf, int off, final int len) throws IOException {

        int written=0;
        while ((current != -1 || (current=wrappedReader.read()) != -1) && written < len) {
            next = wrappedReader.read();
            
            switch (state) {
                case DEFAULT:
                    if (current == '\\') {
                        if (next != '/') {
                            state = ESCAPE;
                        }
                        cbuf[off++] = (char)current;
                        ++written;
                    } else if (current == '\"') {
                        state = STRING;
                        cbuf[off++] = (char)current;
                        ++written;
                    } else if (current == '/' && next == '/') {
                        state = LINE_COMMENT;
                        next=-1;
                    } else if (current == '/' && next == '*') {
                        state = BLOCK_COMMENT;
                        next=-1;
                    } else {
                        cbuf[off++] = (char)current;
                        ++written;
                    }
                    break;
                    
                case STRING:
                    if (current == '\\') {
                        state = ESCAPE_IN_STRING;
                    } else if (current == '\"') {
                        state = DEFAULT;
                    }
                    cbuf[off++] = (char)current;
                    ++written;
                    break;
                    
                case ESCAPE_IN_STRING:
                    cbuf[off++] = (char)current;
                    ++written;
                    state = STRING;
                    break;
                    
                case ESCAPE:
                    cbuf[off++] = (char)current;
                    ++written;
                    state = DEFAULT;
                    break;
                    
                case LINE_COMMENT:
                    if (current == '\n' || current == '\r') {
                        cbuf[off++] = (char)current;
                        ++written;
                        state = DEFAULT;
                    }
                    break;
                    
                case BLOCK_COMMENT:
                    if (current == '*' && next == '/') {
                        state = DEFAULT;
                        next=-1;
                    }
            }
            
            current=next;
        }

        if (written == 0 && current == -1) {
            return -1;
        } else {
            return written;
        }
    }

    @Override
    public void close() throws IOException {
        this.wrappedReader.close();
    }
    
    private static final class SimpleStringReader extends Reader {
        private int next;
        private final String source;
        private final int sourceLength;
        
        SimpleStringReader(String source) {
            this.source = source;
            this.sourceLength = source.length();
            this.next = 0;
        }

        @Override
        public int read() throws IOException {
            if (next >= sourceLength) return -1;
            return source.charAt(next++);
        }
        
        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            if (len == 0) return 0;
            int n = Math.min(len, sourceLength-next);
            source.getChars(next, next+n, cbuf, off);
            next += n;
            return n;
        }

        @Override
        public void close() throws IOException {
        }
    }
}
