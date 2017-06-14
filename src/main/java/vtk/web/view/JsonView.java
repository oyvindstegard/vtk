/* Copyright (c) 2011, University of Oslo, Norway
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

package vtk.web.view;

import java.io.PrintWriter;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.view.AbstractView;

import vtk.util.text.JsonStreamer;

/**
 * Simple JSON view, which can render maps, lists and primitive objects as a JSON
 * data stream.
 * 
 * <p>Supports the following structural data types for model object:
 * <ul>
 * <li>{@code java.util.Map} (JSON objects)
 * <li>{@code java.util.List} (JSON arrays)
 * <li>Java arrays (JSON arrays)
 * </ul>
 * 
 * <p>Supported primitive data types:
 * <ul>
 * <li>{@code java.lang.String} (JSON strings)
 * <li>{@code java.lang.Number} (JSON numbers)
 * <li>{@code java.lang.Boolean} (JSON booleans)
 * <li>Java {@code null} values (JSON nulls)
 * </ul>
 * 
 * <p>Other types will be serialized to a string representation using their
 * {@code toString} method. In other words, this view does not support serializing
 * POJOs, beans and other complex Java types.
 * 
 * <p>Bean properties:
 *  <ul>
 *   <li><code>modelKey</code> - key used to lookup the object to serialize in model.
 * Default value is "jsonObject".
 *   <li><code>httpStatusKey</code> - key used to lookup optional HTTP status code. The
 * status code should be an <code>Integer</code> instance.
 *   <li><code>indentFactor</code> - if greater than 0, JSON will be pretty printed
 * with the indent factor requested. Default is -1 (no pretty printing).
 * <li><code>escapeSlashes</code> - whether to escape slashes in JSON output or not.
 * </ul>
 * 
 * <p>Request params:
 * <ul>
 * <li>{@code indent} - override default indentation value (integer).
 * <li>{@code escapeSlashes} - override default escape slash setting (boolean by presence)
 * </ul>
 */
public class JsonView extends AbstractView {

    private String modelKey = "jsonObject";
    private String httpStatusKey = "httpStatus";
    private int indentFactor = -1;
    private boolean escapeSlashes = false;

    @Override
    protected void renderMergedOutputModel(Map<String, Object> model, HttpServletRequest request,
                                           HttpServletResponse response) throws Exception {

        if (!model.containsKey(modelKey)) {
            throw new IllegalArgumentException("Missing object with key '" 
                                                + modelKey + "' in model data.");
        }
        
        response.setContentType("application/json; charset=UTF-8");
        if (model.containsKey(httpStatusKey)) {
            response.setStatus((Integer)model.get(httpStatusKey));
        }
        
        Object toSerialize = model.get(modelKey);
        try (PrintWriter writer = response.getWriter()) {
            JsonStreamer js = new JsonStreamer(writer, 
                    getIndent(request), isEscapeSlashes(request));
            
            js.value(toSerialize);
        }
        response.flushBuffer();
    }
    
    private boolean isEscapeSlashes(HttpServletRequest r) {
        return r.getParameter("escapeSlashes") != null ? true : this.escapeSlashes;
    }

    private int getIndent(HttpServletRequest request) {
        int indent = indentFactor;

        String indentParam = request.getParameter("indent");
        if (indentParam != null) {
            try {
                int n = Integer.parseInt(indentParam);
                if (n > 0 && n <= 10) {
                    indent = n;
                }
            } catch (Exception e) {}
        }

        return indent;
    }

    /**
     * @param modelKey the modelKey to set
     */
    public void setModelKey(String modelKey) {
        this.modelKey = modelKey;
    }

    /**
     * @param indentFactor the indentFactor to set
     */
    public void setIndentFactor(int indentFactor) {
        if (indentFactor < 1 || indentFactor > 10) {
            throw new IllegalArgumentException("indentFactor must between 1 and 10");
        }
        this.indentFactor = indentFactor;
    }
    
    /**
     * @param httpStatusKey the httpStatusKey to set
     */
    public void setHttpStatusKey(String httpStatusKey) {
        this.httpStatusKey = httpStatusKey;
    }

    /**
     * Whether to escape slash characters in JSON data by default.
     * 
     * <p>Default value is {@code false}.
     * @param escapeSlashes the escapeSlashes to set
     */
    public void setEscapeSlashes(boolean escapeSlashes) {
        this.escapeSlashes = escapeSlashes;
    }

}
