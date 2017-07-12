/* Copyright (c) 2010, University of Oslo, Norway
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

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import vtk.repository.Path;
import vtk.repository.Resource;

public class ConfigurableURLPostProcessor implements URLPostProcessor {
    private String protocol = null; 
    private String host = null;
    private Integer port = null;
    private Path path = null;
    private Map<String, String> parameters;
    
    @Override
    public BiFunction<URL, Optional<Resource>, URL> urlProcessor(
            Service service, URL base) {
        return (url, optResource) -> {
            if (protocol != null) {
                url.setProtocol(protocol);
            }
            if (host != null) {
                url.setHost(host);
            }
            if (port != null) {
                url.setPort(port);
            }
            if (path != null) {
                url.setPath(path);
            }
            if (parameters != null) {
                for (String param: parameters.keySet()) {
                    url.setParameter(param, parameters.get(param));
                }
            }
            return url;
        };
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

}
