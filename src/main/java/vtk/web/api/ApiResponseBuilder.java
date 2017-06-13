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
package vtk.web.api;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.servlet.http.HttpServletResponse;

public class ApiResponseBuilder {
    private int status;
    private Map<String, String> headers = new HashMap<>();
    private Consumer<HttpServletResponse> handler;
    
    public ApiResponseBuilder(int status) { 
        this.status = status;
     }
    
    public static ApiResponseBuilder ok(String message) {
        return messageResponse(message, HttpServletResponse.SC_OK);
    }
    
    public static ApiResponseBuilder notFound(String message) {
        return messageResponse(message, HttpServletResponse.SC_NOT_FOUND);
    }
    
    public static ApiResponseBuilder badRequest(String message) {
        return messageResponse(message, HttpServletResponse.SC_BAD_REQUEST);
    }
    
    public static ApiResponseBuilder forbidden(String message) {
        return messageResponse(message, HttpServletResponse.SC_FORBIDDEN);
    }
    
    public static ApiResponseBuilder internalServerError(String message) {
        return messageResponse(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    public static ApiResponseBuilder messageResponse(String message, int status) {
        return new ApiResponseBuilder(status)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message(message);
    }
    
    public ApiResponseBuilder message(String message) {
        this.handler = response -> {
          try {
              PrintWriter writer = response.getWriter();
              writer.write(message);
              writer.close();
          }
          catch (IOException e) {
              throw new UncheckedIOException(e);
          }
        };
        return this;
    }
    
    public ApiResponseBuilder handler(Consumer<HttpServletResponse> handler) {
        this.handler = handler;
        return this;
    }
    
    public ApiResponseBuilder header(String name, String value) { 
        this.headers.put(name, value); 
        return this; 
    }
    
    public void writeTo(HttpServletResponse response) throws UncheckedIOException {
        try {
            response.setStatus(status);
            for (String name: headers.keySet()) {
                response.setHeader(name, headers.get(name));
            }
            if (handler!= null) {
                handler.accept(response);
            }
            response.flushBuffer();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
