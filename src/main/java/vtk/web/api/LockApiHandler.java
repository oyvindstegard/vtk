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
import java.time.Duration;
import java.util.Objects;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.HttpRequestHandler;

import vtk.repository.AuthorizationException;
import vtk.repository.Lock;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceLockedException;
import vtk.repository.ResourceNotFoundException;
import vtk.util.Result;
import vtk.util.web.HttpUtil;
import vtk.web.RequestContext;

public class LockApiHandler implements HttpRequestHandler {
    private final Duration refreshTimeout;
    private final Duration defaultTimeout;

    public LockApiHandler() {
        this(Duration.ofMinutes(10), Duration.ofMinutes(10));
    }

    public LockApiHandler(Duration refreshTimeout, Duration defaultTimeout) {
        this.refreshTimeout = refreshTimeout;
        this.defaultTimeout = defaultTimeout;
    }

    @Override
    public void handleRequest(
            HttpServletRequest request, HttpServletResponse response
    ) throws ServletException, IOException {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Result<LockRequest> lockRequest = lockRequest(request);

        Result<ApiResponseBuilder> builder = lockRequest.flatMap(req -> {
            switch(req.action) {
                case LOCK:
                    return lock(req, requestContext);
                case UNLOCK:
                    return unlock(req, requestContext);
                default:
                    return refresh(req, requestContext);
            }
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

    private enum LockAction {
        LOCK,
        UNLOCK,
        REFRESH
    }

    private static class LockRequest {
        public final LockAction action;
        public final Resource resource;
        public final int timeout;

        private LockRequest(LockAction action, Resource resource, int timeout) {
            this.action = action;
            this.resource = resource;
            this.timeout = timeout;
        }
    }

    private Result<LockRequest> lockRequest(HttpServletRequest request) {
        RequestContext requestContext = RequestContext.getRequestContext(request);

        Result<LockAction> lockAction = Result.attempt(() -> {
            if (!"POST".equals(request.getMethod()))
                throw new InvalidRequestException("POST method is required");
            return Objects.requireNonNull(request.getParameter("action"),
                    "Missing request parameter 'action'");
        }).flatMap(action -> Result.attempt(() -> {
            if ("lock".equals(action)) return LockAction.LOCK;
            else if ("unlock".equals(action)) return LockAction.UNLOCK;
            else if ("refresh".equals(action)) return LockAction.REFRESH;
            else throw new InvalidRequestException(
                    "Unknown action: " + action  + ": must be one of (lock, unlock, refresh)"
                );
        }));

        return lockAction.flatMap(action ->
                Result.attempt(() -> {
                    try {
                        Resource resource = requestContext.getRepository().retrieve(
                                requestContext.getSecurityToken(),
                                requestContext.getResourceURI(),
                                false
                        );
                        String timeoutHeader = request.getHeader("TimeOut");
                        int timeout = (timeoutHeader == null)
                                ? (int) this.defaultTimeout.getSeconds()
                                : HttpUtil.parseTimeoutHeader(timeoutHeader);

                        return new LockRequest(action, resource, timeout);
                    }
                    catch (AuthorizationException | ResourceNotFoundException e) {
                        throw e;
                    }
                    catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
        );
    }

    private Result<ApiResponseBuilder> lock(
            LockRequest request, RequestContext requestContext
    ) {
        return Result.attempt(() -> {
            String lockToken = null;
            Lock lock = request.resource.getLock();
            if (lock != null) {
                lockToken = lock.getLockToken();
            }
            Path uri = request.resource.getURI();
            String token = requestContext.getSecurityToken();
            String ownerInfo = requestContext.getPrincipal().toString();
            try {
                requestContext.getRepository().lock(
                        token, uri, ownerInfo, Repository.Depth.ZERO, request.timeout, lockToken
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return new ApiResponseBuilder(HttpServletResponse.SC_OK)
                    .header("Content-Type", "text/plain;charset=utf-8")
                    .message("Resource " + uri + " locked\n");

        });
    }

    private Result<ApiResponseBuilder> unlock(
            LockRequest request, RequestContext requestContext
    ) {
        return Result.attempt(() -> {
            Path uri = request.resource.getURI();
            if (request.resource.getLock() != null) {
                String token = requestContext.getSecurityToken();
                try {
                    requestContext.getRepository().unlock(token, uri, null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            return new ApiResponseBuilder(HttpServletResponse.SC_OK)
                    .header("Content-Type", "text/plain;charset=utf-8")
                    .message("Resource " + uri + " unlocked\n");
        });
    }

    private Result<ApiResponseBuilder> refresh(
            LockRequest request, RequestContext requestContext
    ) {
        return Result.attempt(() -> {
            Path uri = request.resource.getURI();
            Lock lock = request.resource.getLock();
            String message = "not locked";
            if (lock != null) {
                String token = requestContext.getSecurityToken();
                long nowSeconds = System.currentTimeMillis() / 1000;
                long lockReleaseTimeSeconds = lock.getTimeout().getTime() / 1000;
                long refreshTimeSeconds = nowSeconds + this.refreshTimeout.getSeconds();

                message = String.format(
                        "%d seconds before you can refresh", lockReleaseTimeSeconds - refreshTimeSeconds
                );
                if (lockReleaseTimeSeconds <= refreshTimeSeconds) {
                    // Refresh lock:
                    try {
                        requestContext.getRepository().lock(
                                token,
                                uri,
                                lock.getOwnerInfo(),
                                lock.getDepth(),
                                request.timeout,
                                lock.getLockToken()
                        );
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                    message = "refreshed";
                }
            }

            return new ApiResponseBuilder(HttpServletResponse.SC_OK)
                    .header("Content-Type", "text/plain;charset=utf-8")
                    .message("Resource " + uri + " " + message + "\n");
        });
    }

}
