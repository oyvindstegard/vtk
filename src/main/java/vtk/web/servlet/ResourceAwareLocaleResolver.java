/* Copyright (c) 2004, University of Oslo, Norway
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

import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AbstractLocaleResolver;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.web.RequestContext;

/**
 * Resolves locale for resources. If no locale is set on resource, then a
 * configurable default Locale is returned.
 */
public class ResourceAwareLocaleResolver extends AbstractLocaleResolver {

    private String trustedToken;
    private Repository repository;

    /**
     * @see LocaleResolver#resolveLocale(javax.servlet.http.HttpServletRequest)
     * @param request
     * @return The @{link Locale locale} object for the current resource being
     *         requested.
     */
    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Path uri = requestContext.getResourceURI();
        return resolveResourceLocale(uri);
    }

    /**
     * Resolve locale for resource at URI. If no locale is set, the default
     * locale is returned.
     * 
     * @param uri
     * @return The @{link Locale locale} object for resource at URI.
     */
    public Locale resolveResourceLocale(Path uri) {
        Locale locale = null;
        try {
            Resource r = this.repository.retrieve(this.trustedToken, uri, true);
            locale = r.getContentLocale();
        } catch (Exception e) {
        }

        if (locale == null) {
            return getDefaultLocale();
        }

        return locale;

    }

    /**
     * Resolve locale for resource. If resource has no locale set, the default
     * locale is returned.
     * 
     * @param resource
     * @return The resource @{link Locale locale} object.
     */
    public Locale resolveResourceLocale(Resource resource) {
        Locale locale = resource.getContentLocale();
        return locale != null ? locale : getDefaultLocale();
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException(
                "This locale resolver does not support explicitly setting the request locale");
    }

    public void setTrustedToken(String trustedToken) {
        this.trustedToken = trustedToken;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

}
