/* Copyright (c) 2016 University of Oslo, Norway
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
package vtk.web.servlet;

import java.util.Arrays;
import java.util.Optional;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.Session;
import org.springframework.session.web.http.HttpSessionStrategy;

/**
 * HttpSessionStrategy implementation that supports different cookie
 * names for http and https.
 */
public class CookieHttpSessionStrategy implements HttpSessionStrategy {
    private static final Logger logger = LoggerFactory.getLogger(CookieHttpSessionStrategy.class);
    
    private String cookieNameHttp;
    private String cookieNameHttps;
    
    public CookieHttpSessionStrategy(String cookieNameHttp, String cookieNameHttps) {
        this.cookieNameHttp = cookieNameHttp;
        this.cookieNameHttps = cookieNameHttps;
    }

    @Override
    public String getRequestedSessionId(HttpServletRequest request) {
        Optional<Cookie> cookie = sessionCookie(request);
        if (cookie.isPresent()) return cookie.get().getValue();
        return null;
    }

    @Override
    public void onNewSession(Session session, HttpServletRequest request,
            HttpServletResponse response) {
        String cookieName = request.isSecure() ? cookieNameHttps : cookieNameHttp;
        Cookie cookie = new Cookie(cookieName, session.getId());
        logger.debug("New session cookie: " + cookieName + ":" + session.getId());
        cookie.setDomain(request.getServerName());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setMaxAge(-1); // Session cookie (not stored)
        response.addCookie(cookie);
    }

    @Override
    public void onInvalidateSession(HttpServletRequest request,
            HttpServletResponse response) {
        Optional<String> id = sessionCookie(request).map(cookie -> cookie.getValue());
        String cookieName = request.isSecure() ? cookieNameHttps : cookieNameHttp;
        Cookie cookie = new Cookie(cookieName, id.orElse(""));
        logger.debug("Remove session cookie: " + cookieName);
        cookie.setDomain(request.getServerName());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
    
    private Optional<Cookie> sessionCookie(HttpServletRequest request) {
        String cookieName = request.isSecure() ? cookieNameHttps : cookieNameHttp;

        return Optional.ofNullable(request.getCookies())
            .flatMap(cookies -> Arrays.stream(cookies)
                    .filter(c -> c.getName().equals(cookieName)).findFirst());
    }
}
