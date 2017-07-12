/* Copyright (c) 2007, University of Oslo, Norway
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

import java.util.List;
import java.util.Optional;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.util.Assert;

import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.security.Principal;

public class ConfigurableRequestURIAssertion implements WebAssertion {

    private String matchValue;
    private Properties configuration;
    private boolean matchOnEmptyConfiguration = false;
    
    public void setConfiguration(Properties configuration) {
        this.configuration = configuration;
    }
    
    @Required
    public void setMatchValue(String matchValue) {
        Assert.notNull(matchValue, "Argument cannot be NULL");
        this.matchValue = matchValue;
    }
    
    public void setMatchOnEmptyConfiguration(boolean matchOnEmptyConfiguration) {
        this.matchOnEmptyConfiguration = matchOnEmptyConfiguration;
    }
    
    public boolean conflicts(WebAssertion assertion) {
        return false;
    }

    @Override
    public URL processURL(URL url) {
        return url;
    }
    
    @Override
    public Optional<URL> processURL(URL url, Resource resource, Principal principal) {
        boolean matches = matchInternal(url.getPath());
        if (matches) return Optional.of(url);
        return Optional.empty();
    }


    @Override
    public boolean matches(HttpServletRequest request, Resource resource,
                           Principal principal) {
        URL url = URL.create(request);
        return matchInternal(url.getPath());
    }

    private boolean matchInternal(Path uri) {
        if (this.configuration == null || this.configuration.isEmpty()) {
            return this.matchOnEmptyConfiguration;
        }
        
        List<Path> paths = uri.getPaths();
        for (int i = paths.size() - 1; i >= 0; i--) {
            String prefix = paths.get(i).toString();
            String value = this.configuration.getProperty(prefix);
            if (value != null) {
                boolean match = this.matchValue.equals(value.trim());
                return match;
            }
        }
        return false;
    }
    

}
