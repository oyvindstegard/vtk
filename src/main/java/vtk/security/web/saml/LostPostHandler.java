/* Copyright (c) 2011, University of Oslo, Norway
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
package vtk.security.web.saml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Path;
import vtk.security.AuthenticationProcessingException;
import vtk.util.io.IO;
import vtk.util.text.Json;
import vtk.util.text.JsonStreamer;
import vtk.util.web.HttpUtil;
import vtk.web.service.URL;

public class LostPostHandler implements Controller {
    private static final Logger logger = LoggerFactory.getLogger(LostPostHandler.class);
    private static final String COOKIE_NAME = "vrtx.lostpost.id";
    
    private File workingDirectory;
    private String viewName;
    private Path redirectURI;
    private int savedStateTimeout = 600;
    private int limitPerAddress = 100;
    private int maxPostSize = -1;
    private boolean secureCookies;

    public boolean hasSavedState(HttpServletRequest request) {
        Config config = new Config(this.workingDirectory, this.limitPerAddress);
        PostState state = PostState.load(config, request);
        if (state == null) {
            return false;
        }
        return !state.isExpired(this.savedStateTimeout);
    }

    public void saveState(HttpServletRequest request, HttpServletResponse response) {
        if (!"application/x-www-form-urlencoded".equals(request.getContentType())) {
            return;
        }
        if (this.maxPostSize > 0 && request.getContentLength() > this.maxPostSize) {
            return;
        }
        
        try {
            Config config = new Config(this.workingDirectory, this.limitPerAddress);
            
            PostState state = PostState.create(config, request);
            int cookieTimeout = -1;
            Cookie cookie = createCookie(state.identifier, cookieTimeout);
            response.addCookie(cookie);
        } catch (IOException e) {
            throw new AuthenticationProcessingException(e);
        }
    }

    public void redirect(HttpServletRequest request, HttpServletResponse response) {
        URL redirectURL = URL.create(request).clearParameters().setPath(this.redirectURI);
        response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
        response.setHeader("Location", redirectURL.toString());
    }
    
    @Override
    public ModelAndView handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        Config config = new Config(this.workingDirectory, this.limitPerAddress);
        PostState state = PostState.load(config, request);
        if (state == null) {
            throw new AuthenticationProcessingException("No previous POST request found");
        }
        
        Cookie cookie = createCookie(state.identifier, 0);
        response.addCookie(cookie);
        
        URL postURL = state.getPostURL();
        Json.MapContainer body = state.getBody();
        state.delete();

        Map<String, Object> model = new HashMap<>();
        model.put("postURL", postURL);
        model.put("body", body);
        return new ModelAndView(this.viewName, model);
    }

    @Required
    public void setWorkingDirectory(String path) {
        File tmp = new File(path);
        if (!tmp.exists()) {
            if (!tmp.mkdir()) {
                throw new IllegalArgumentException(
                        "Working directory does not exist: " + tmp + " (attempt to create failed)");
                
            }
        }
        if (!tmp.isDirectory()) {
            throw new IllegalArgumentException("Unable to set working directory: " + tmp + ": not a directory");
        }
        this.workingDirectory = tmp;
    }
    
    @Required
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }
    
    @Required
    public void setRedirectURI(String redirectURI) {
        this.redirectURI = Path.fromString(redirectURI);
    }
    
    public void setSavedStateTimeout(int savedStateTimeout) {
        this.savedStateTimeout = savedStateTimeout;
    }
    
    public void setLimitPerAddress(int limitPerAddress) {
        this.limitPerAddress = limitPerAddress;
    }
    
    public void setMaxPostSize(int maxPostSize) {
        this.maxPostSize = maxPostSize;
    }
    
    public void setSecureCookies(boolean secureCookies) {
        this.secureCookies = secureCookies;
    }
    
    public void init() {
        try {
            cleanup();
        } catch (Throwable t) {
            logger.warn("Failed to perform cleanup during initialization", t);
        }
    }
    
    public void cleanup() {
        for (File f: this.workingDirectory.listFiles()) {
            if (!f.isDirectory()) {
                continue;
            }
            for (File d: f.listFiles()) {
                if (!d.isDirectory()) {
                    continue;
                }
                PostState state = PostState.load(d);
                if (state.isExpired(this.savedStateTimeout)) {
                    state.delete();
                }
            }
        }
    }

    private Cookie createCookie(String identifier, int maxAge) {
        Cookie cookie = new Cookie(COOKIE_NAME, identifier);
        cookie.setPath("/");
        cookie.setSecure(this.secureCookies);
        cookie.setMaxAge(maxAge);
        return cookie;
    }
    
    private static class Config {
        File baseDir;
        int maxEntries;
        Config(File baseDir, int maxEntries) {
            this.baseDir = baseDir;
            this.maxEntries = maxEntries;
        }
    }
    
    private static class PostState {
        private String identifier;
        private File dir;
        private PostState() {}
        
        public boolean isExpired(int maxAgeSeconds) {
            long expiry = System.currentTimeMillis() - (maxAgeSeconds * 1000);
            if (this.dir.lastModified() < expiry) {
                return true;
            }
            return false;
        }

        static PostState create(Config config, HttpServletRequest request) throws IOException {
            URL postURL = URL.create(request);
            String clientAddr = request.getRemoteAddr();
            
            File top = new File(config.baseDir + File.separator + clientAddr);
            if (top.exists() && top.isFile()) {
                throw new IOException("Cannot create directory: " + top);
            }
            if (!top.exists()) {
                if (!top.mkdir()) {
                    throw new IOException("Cannot create directory: " + top);
                }
            }
            
            if (top.list().length >= config.maxEntries) {
                throw new IOException("Max number of entries in directory " 
                        + top + " reached, refusing to create");
            }
            
            PostState state = new PostState();
            while (true) {
                String identifier = UUID.randomUUID().toString();
                File f = new File(top + File.separator + identifier);
                if (!f.exists()) {
                    if (!f.mkdir()) {
                        throw new IOException("Cannot create directory: " + identifier);
                    }
                    state.identifier = identifier;
                    state.dir = f;
                    break;
                }
            }
            File tmp = state.dir;
            
            File id = new File(tmp.getAbsolutePath() + File.separator + "identifier");
            if (!id.createNewFile()) {
                throw new IOException("Cannot create file");
            }
            IO.write(state.identifier.getBytes(), new FileOutputStream(id)).perform();

            File url = new File(tmp.getAbsolutePath() + File.separator + "url");
            if (!url.createNewFile()) {
                throw new IOException("Cannot create file");
            }
            IO.write(postURL.toString().getBytes(), new FileOutputStream(url)).perform();
            
            File ip = new File(tmp.getAbsolutePath() + File.separator + "client-addr");
            if (!ip.createNewFile()) {
                throw new IOException("Cannot create file");
            }
            IO.write(clientAddr.getBytes(), new FileOutputStream(ip)).perform();
            
            File body = new File(tmp.getAbsolutePath() + File.separator + "body");
            if (!body.createNewFile()) {
                throw new IOException("Cannot create file");
            }
            
            Json.MapContainer json = new Json.MapContainer();
            @SuppressWarnings("unchecked")
            Map<String, String[]> parameterMap = request.getParameterMap();
            for (Map.Entry<String, String[]> entry: parameterMap.entrySet()) {
                String name = entry.getKey();
                Json.ListContainer values = new Json.ListContainer();
                for (String value: entry.getValue()) {
                    if (!value.equals(postURL.getParameter(name))) {
                        values.add(value);
                    }
                }
                if (!values.isEmpty()) {
                    json.put(name, values);
                }
            }
            
            Writer writer = new OutputStreamWriter(new FileOutputStream(body));
            JsonStreamer streamer = new JsonStreamer(writer, 2);
            try {
                streamer.object(json);
            } finally {
                writer.close();
            }
            return state;
        }
        
        public static PostState load(File dir) {
            String identifier = dir.getName();
            PostState state = new PostState();
            state.identifier = identifier;
            state.dir = dir;
            return state;
        }
        
        public static PostState load(Config config, HttpServletRequest request) {
            
            String identifier = identifier(request);
            if (identifier == null) {
                return null;
            }
            String fileName = config.baseDir.getAbsolutePath() + File.separator + request.getRemoteAddr();
            File top = new File(fileName);
            if (!top.exists() || top.isFile()) {
                return null;
            }
            
            fileName = top.getAbsolutePath() + File.separator + identifier;
            File f = new File(fileName);
            if (!f.exists()) {
                return null;
            }
            if (!f.isDirectory()) {
                return null;
            }
            PostState state = new PostState();
            state.identifier = identifier;
            state.dir = f;
            return state;
        }

        public void delete() {
            if (!this.dir.exists()) {
                return;
            }
            String[] children = this.dir.list();
            for (String s: children) {
                String fileName = this.dir.getAbsolutePath() + File.separator + s;
                File f = new File(fileName);
                if (!f.delete()) {
                    throw new IllegalStateException("Unable to delete temporary file " + s);
                }
            }
            File parent = this.dir.getParentFile();
            
            if (!this.dir.delete()) {
                throw new IllegalStateException("Unable to delete temporary file " + this.dir.getName());
            }
            if (dir.getParentFile().list().length == 0) {
                parent.delete();
            }
        }
        
        public URL getPostURL() {
            String s = field("url");
            return URL.parse(s);
        }
        
        public Json.MapContainer getBody() {
            String s = field("body");
            return Json.parseToContainer(s).asObject();
        }
        
        private String field(String name) {
            try {
                String fileName = this.dir.getAbsolutePath() + File.separator + name;
                File file = new File(fileName);
                if (!file.exists()) {
                    throw new IllegalStateException("No 'url' file found");
                }

                return IO.readString(new FileInputStream(file)).perform();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to retrieve attribute '" + name + "'");
            }
        }
        
        private static String identifier(HttpServletRequest request) {
            return HttpUtil.getCookie(request, COOKIE_NAME).map(Cookie::getValue).orElse(null);
        }
    }
}
