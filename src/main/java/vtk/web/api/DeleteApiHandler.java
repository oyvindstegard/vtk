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

import org.springframework.web.HttpRequestHandler;
import vtk.repository.*;
import vtk.util.Result;
import vtk.util.web.HttpUtil;
import vtk.web.RequestContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class DeleteApiHandler implements HttpRequestHandler {
    @Override
    public void handleRequest(
            HttpServletRequest request, HttpServletResponse response
    ) throws ServletException, IOException {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Result<DeleteRequest> deleteRequest = deleteRequest(requestContext);
        Result<ApiResponseBuilder> builder = deleteRequest.flatMap(req -> {
            return delete(req, requestContext);
        }).recover(ex -> {
            if (ex instanceof ResourceNotFoundException) {
                return new ApiResponseBuilder(HttpServletResponse.SC_NOT_FOUND)
                    .header("Content-Type", "text/plain")
                    .message("Resource " + requestContext.getResourceURI() + " does not exist");
            }
            if (ex instanceof InvalidRequestException) {
                return new ApiResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
                    .message(ex.getMessage());
            }
            if (ex instanceof AuthorizationException) {
                return new ApiResponseBuilder(HttpServletResponse.SC_FORBIDDEN)
                    .message(ex.getMessage());
            }
            if (ex instanceof ResourceLockedException) {
                return new ApiResponseBuilder(HttpUtil.SC_LOCKED)
                    .message(ex.getMessage());
            }
            return new ApiResponseBuilder(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                .message("An unexpected error occurred: " + ex.getMessage());
        });

        builder.forEach(resp -> resp.writeTo(response));
    }

    private Result<ApiResponseBuilder> delete(
        DeleteRequest request, RequestContext requestContext
    ) {
        return Result.attempt(() -> {
            Path uri = request.resource.getURI();
            boolean restorable = true;
            try {
                requestContext.getRepository().delete(
                    requestContext.getSecurityToken(),
                    null,
                    uri,
                    restorable
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return new ApiResponseBuilder(HttpServletResponse.SC_OK)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("Resource " + uri + " deleted\n");
        });
    }

    private static class DeleteRequest {
        public final Resource resource;

        private DeleteRequest(Resource resource) {
            this.resource = resource;
        }
    }

    private Result<DeleteRequest> deleteRequest(RequestContext requestContext) {
        return Result.attempt(() -> {
            try {
                Resource resource = requestContext.getRepository().retrieve(
                    requestContext.getSecurityToken(),
                    requestContext.getResourceURI(),
                    false
                );
                return new DeleteRequest(resource);
            }
            catch (AuthorizationException | ResourceNotFoundException e) {
                throw e;
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
