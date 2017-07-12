/* Copyright (c) 2012, University of Oslo, Norway
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

import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.util.net.NetUtils;

/**
 * Canonical resource URL construction.
 * 
 * Best-effort, based on available information. If wild-card values
 * are configured for host, port, etc. then request context is used if available.
 * Otherwise a fallback values are used.
 * 
 * Constructed URLs are service-agnostic.
 * 
 */
public class CanonicalUrlConstructorImpl implements CanonicalUrlConstructor {
    
    // Used for constructing canonical resource URL.
    private static final String WILDCARD_VALUE = "*";
    private static final int PORT_ANY = -1;

    private static final String FALLBACK_WEB_HOST_NAME = NetUtils.guessHostName();
    
    private String webHostName = WILDCARD_VALUE;
    private String webProtocol = WILDCARD_VALUE;
    private String webProtocolRestricted = WILDCARD_VALUE;
    private int webPort = PORT_ANY;
    private int webPortRestricted = PORT_ANY;
    
    @Override
    public URL canonicalUrl(Resource resource) {
        return canonicalUrl(resource.getURI(), resource.isCollection(), resource.isReadRestricted());
    }
    
    @Override
    public URL canonicalUrl(Path path) {
        return canonicalUrl(path, false, false);
    }
    
    @Override
    public URL canonicalUrl(Path path, boolean collection, boolean readRestricted) {
        String protocol;
        String host;
        int port;
        
        if (WILDCARD_VALUE.equals(this.webHostName)) {
            host = FALLBACK_WEB_HOST_NAME;
        }
        else {
            host = this.webHostName;
        }
        
        if (WILDCARD_VALUE.equals(this.webProtocol)) {
            protocol = "http";
        }
        else {
            protocol = this.webProtocol;
        }
        
        if (this.webPort == PORT_ANY) {
          protocol = "http";
                port = 80;
        }
        else {
            port = this.webPort;
        }
        
        if (readRestricted) {
            if (! WILDCARD_VALUE.equals(this.webProtocolRestricted)) {
                protocol = this.webProtocolRestricted;

                if (this.webPortRestricted == PORT_ANY && "https".equals(protocol)) {
                    port = 443;
                }
            }
            if (this.webPortRestricted != PORT_ANY) {
                port = this.webPortRestricted;
            }
        }
        
        URL url = new URL(protocol, host, path);
        url.setCollection(collection);
        url.setPort(port);
        
        return url;
    }
    
    public void setWebHostName(String webHostName) {
        String[] names = webHostName.trim().split("\\s*,\\s*");
        for (String name: names) {
            if (WILDCARD_VALUE.equals(name)) {
                this.webHostName = WILDCARD_VALUE;
                return;
            }
        }
        this.webHostName = names[0];
    }
    
    public void setWebProtocol(String webProtocol) {
        this.webProtocol = webProtocol;
    }
    
    public void setWebProtocolRestricted(String webProtocolRestricted) {
        this.webProtocolRestricted = webProtocolRestricted;
    }
    
    public void setWebPort(String webPort) throws NumberFormatException {
        if (WILDCARD_VALUE.equals(webPort)) {
            this.webPort = PORT_ANY;
        }
        else {
            this.webPort = Integer.parseInt(webPort);
        }
    }
    
    public void setWebPortRestricted(String webPortRestricted) throws NumberFormatException {
        if (WILDCARD_VALUE.equals(webPortRestricted)) {
            this.webPortRestricted = PORT_ANY;
        }
        else {
            this.webPortRestricted = Integer.parseInt(webPortRestricted);
        }
    }
}
