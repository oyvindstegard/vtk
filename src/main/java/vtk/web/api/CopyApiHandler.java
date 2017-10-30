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

import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.HttpRequestHandler;

import vtk.repository.AuthorizationException;
import vtk.repository.IllegalOperationException;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.ResourceOverwriteException;
import vtk.util.Result;
import vtk.web.RequestContext;

public class CopyApiHandler implements HttpRequestHandler {
    
    @Override
    public void handleRequest(HttpServletRequest request, 
            HttpServletResponse response) {
        
        ApiResponseBuilder builder = 
                "GET".equals(request.getMethod()) ? 
                        handleGet(request, response)
                : "POST".equals(request.getMethod()) ?
                        handlePost(request, response)
                        : unknownMethod(request, response);

        builder.writeTo(response);
    }
    
    private ApiResponseBuilder handleGet(HttpServletRequest request, 
            HttpServletResponse response) {
        return new ApiResponseBuilder(HttpServletResponse.SC_OK)
            .header("Content-Type", "text/plain;charset=utf-8")
            .message("A POST request is required, with path parameters "
                     + "'source' and 'destination' specified\n");
    }

    private ApiResponseBuilder  handlePost(HttpServletRequest request,
            HttpServletResponse response) {
        Result<CopyRequest> copyRequest = copyRequest(request);
        if (copyRequest.failure.isPresent()) {
            return new ApiResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message(copyRequest.failure.get().getMessage());
        }
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Result<ApiResponseBuilder> result = 
                doCopy(copyRequest.result.get(), requestContext);
        
        if (result.failure.isPresent()) {
            return new ApiResponseBuilder(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message(result.failure.get().getMessage());
        }
        return result.result.get();
    }
    
    private ApiResponseBuilder unknownMethod(HttpServletRequest request, 
            HttpServletResponse response) {
        return new ApiResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
        .header("Content-Type", "text/plain;charset=utf-8")
        .message("A POST request is required, with path parameters "
                 + "'source' and 'destination' specified\n");
    }

    private Result<ApiResponseBuilder> doCopy(CopyRequest req, 
            RequestContext requestContext) {
        return Result.attempt(() -> {
            try {
                Repository repository = requestContext.getRepository();
                String token = requestContext.getSecurityToken();
                repository.copy(token, null, req.source, req.destination, false, false);
                return new ApiResponseBuilder(HttpServletResponse.SC_OK)
                        .header("ContentType", "text/plain;charset=utf-8")
                        .message("Copy " + req.source + " to " + req.destination
                                    + " succeeded\n");
            }
            catch (ResourceOverwriteException e) {
                return new ApiResponseBuilder(HttpServletResponse.SC_CONFLICT)
                        .header("Content-Type", "text/plain;charset=utf-8")
                        .message("Copy " + req.source + " to " + req.destination + 
                                " failed: resource " + req.destination + " already exists");
            }
            catch (AuthorizationException | IllegalOperationException e) {
                return new ApiResponseBuilder(HttpServletResponse.SC_FORBIDDEN)
                        .header("Content-Type", "text/plain;charset=utf-8")
                        .message("Copy " + req.source + " to " + req.destination + 
                                " failed: " + e.getMessage());
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Result<CopyRequest> copyRequest(HttpServletRequest request) {
        return Result.attempt(() -> 
            Objects.requireNonNull(request.getParameter("source"),  
                "Missing request parameter 'source'"))
            .flatMap(sourceStr -> Result.attempt(() -> Path.fromString(sourceStr)))
            .flatMap(sourcePath -> {
                return Result.attempt(() -> Objects.requireNonNull(
                        request.getParameter("destination"), 
                        "Missing request parameter 'destination'"))
                  .flatMap(destStr -> Result.attempt(() -> Path.fromString(destStr))
                          .map(destPath -> {
                              return new CopyRequest(sourcePath, destPath);
                   }));
            });
    }
    
    private static class CopyRequest {
        public final Path source;
        public final Path destination;
        private CopyRequest(Path source, Path destination) {
            this.source = source; this.destination = destination;
        }
        @Override
        public String toString() {
            return getClass().getSimpleName() + 
                    "(" + source + ", " + destination+ ")";
        }
    }
}
