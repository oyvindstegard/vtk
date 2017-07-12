/* Copyright (c) 2006, University of Oslo, Norway
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
package vtk.web.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.resourcemanagement.StaticResourceLocation;
import vtk.security.Principal;


public class StaticResourceLocationsAssertion
  implements WebAssertion, ApplicationContextAware, InitializingBean {

    private Set<Path> prefixes;
    private ApplicationContext applicationContext;


    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    

    @SuppressWarnings("unchecked")
    public void afterPropertiesSet() {
        @SuppressWarnings("rawtypes")
        Map matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(
            this.applicationContext, StaticResourceLocation.class, true, false);
        Collection<StaticResourceLocation> allLocations = matchingBeans.values();
        this.prefixes = new HashSet<>();

        for (StaticResourceLocation location: allLocations) {
            Path uri = location.getPrefix();
            this.prefixes.add(uri);
        }
    }


    @Override
    public boolean conflicts(WebAssertion assertion) {
        return false;
    }


    @Override
    public String toString() {
        return "request.uri.paths in (" + this.prefixes + ")";
    }


    @Override
    public Optional<URL> processURL(URL url, Resource resource, Principal principal) {
        return Optional.of(url);
    }

    @Override
    public URL processURL(URL url) {
        return url;
    }


    @Override
    public boolean matches(HttpServletRequest request, Resource resource, Principal principal) {
        URL url = URL.create(request);
        List<Path> paths = url.getPath().getPaths();
        for (int i = paths.size() - 1; i >= 0; i--) {
            Path prefix = paths.get(i);
            if (this.prefixes.contains(prefix)) {
                return true;
            }
        }
        return false;
    }


}
