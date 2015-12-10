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
package vtk.web.decorating.components;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.context.ServletContextAware;

import vtk.repository.AuthorizationException;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.search.preprocessor.QueryStringPreProcessor;
import vtk.security.AuthenticationException;
import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlPageParser;
import vtk.text.html.HtmlUtil;
import vtk.util.cache.ContentCache;
import vtk.util.io.StreamUtil;
import vtk.util.repository.ContentTypeHelper;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.service.URL;
import vtk.web.servlet.BufferedResponse;
import vtk.web.servlet.ConfigurableRequestWrapper;
import vtk.web.servlet.VTKServlet;


public class IncludeComponent extends AbstractDecoratorComponent
implements ServletContextAware {

    private static final String PARAMETER_VIRTUAL = "virtual";
    private static final String PARAMETER_VIRTUAL_DESC =
        "Either a complete URL, or a path to the file to include. "
        + "Both relative and absolute paths are interpreted.";
    private static final String PARAMETER_FILE = "file";
    private static final String PARAMETER_FILE_DESC =
        "The path to the file to include. "
        + "Both relative and absolute paths are interpreted";
    private static final String PARAMETER_AS_CURRENT_USER = "authenticated";
    private static final String PARAMETER_AS_CURRENT_USER_DESC = 
        "The default is that only resources readable for everyone is included. " +
        "If this is set to 'true', the include is done as the currently " +
        "logged in user (if any). This should only be used when the same " +
        "permissions apply to the resource including and the resource included." +
        "Note that this doesn't apply to virtual includes of full URLs.";

    private static final String PARAMETER_ELEMENT = "element";
    private static final String PARAMETER_ELEMENT_DESC =
        "Selects an element from the included document (used in conjunction with the '"
        + PARAMETER_FILE + "' or '" + PARAMETER_VIRTUAL
        + "' parameters). The parameter must be a dot-separated path "
        + "from the root element to the desired element: for example, the expression "
        + "'html.body.h1' selects the (first) h1 element in the HTML body.";

    static final String INCLUDE_ATTRIBUTE_NAME =
        IncludeComponent.class.getName() + ".IncludeRequestAttribute";

    private ServletContext servletContext;
    private ContentCache<String, URLObject> httpIncludeCache;
    private HtmlPageParser htmlParser;
    private QueryStringPreProcessor uriPreProcessor;

    @Required
    @Override
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    /**
     *
     * @param httpIncludeCache
     */
    @Required
    public void setHttpIncludeCache(ContentCache<String, URLObject> httpIncludeCache) {
        this.httpIncludeCache = httpIncludeCache;
    }

    @Required public void setHtmlParser(HtmlPageParser htmlParser) {
        this.htmlParser = htmlParser;
    }
    
    private Pattern esiLocations = null;
    public void setEsiLocations(String esiLocations) {
        if (esiLocations == null || esiLocations.trim().equals("")) {
            return;
        }
        this.esiLocations = Pattern.compile(esiLocations);
    }
    
    private Pattern inlineEsiLocations = null;
    public void setInlineEsiLocations(String inlineEsiLocations) {
        if (inlineEsiLocations == null || inlineEsiLocations.trim().equals("")) {
            return;
        }
        this.inlineEsiLocations = Pattern.compile(inlineEsiLocations);
    }
    
    
    private static Log logger = LogFactory.getLog(IncludeComponent.class);
    public void render(DecoratorRequest request, DecoratorResponse response)
    throws Exception {

        String esi = request.getStringParameter("esi");
        
        if (esi != null && esiLocations == null) {
            throw new DecoratorComponentException("ESI  not enabled in this location");
        }
        
        if (esi != null) {
            StringBuffer requestURL = request.getServletRequest().getRequestURL();
            
            if (!esiLocations.matcher(requestURL).matches()) {
                throw new DecoratorComponentException("ESI  not enabled in this location");
            }
            
            if (!esi.startsWith("/")) {
                throw new DecoratorComponentException("Invalid ESI URL: must start with '/'");
            }
            
            if (inlineEsiLocations != null && inlineEsiLocations.matcher(requestURL).matches()) {
                try {
                    URL url = URL.create(request.getServletRequest());
                    url = url.relativeURL(esi);
                    Map<String, String[]> query = new LinkedHashMap<String, String[]>();
                    for (String p: url.getParameterNames()) {
                        query.put(p, new String[] {url.getParameter(p)});
                    }
                    handleVirtualInclude(url.getPath(), query, request, response);
                    return;
                } catch (Throwable t) {
                    throw new DecoratorComponentException("ESI include: " + t.getMessage(), t);
                }
            }
            
            Writer writer = response.getWriter();
            writer.write("<esi:include src=\"" + HtmlUtil.encodeBasicEntities(esi) + "\" />");
            writer.flush();
            writer.close();
            return;
        }
        
        String uri = request.getStringParameter(PARAMETER_FILE);
        boolean ignoreNotFound = false;
        
        if (uri != null) {
            ignoreNotFound = ! uri.equals(request.getRawParameter(PARAMETER_FILE));
            if (!uri.startsWith("/")) {
                Path currentCollection = RequestContext.getRequestContext().getCurrentCollection();
                uri = currentCollection.expand(uri).toString();
            }
            handleDirectInclude(uri, request, response, ignoreNotFound);
            return;
        }

        uri = request.getStringParameter(PARAMETER_VIRTUAL);
        if (uri == null) {
            throw new DecoratorComponentException(
            "One of parameters 'file' or 'virtual' must be specified");
        }

        if (uriPreProcessor != null) {
            uri = uriPreProcessor.process(uri);
        }

        if (uri.startsWith("http:") || uri.startsWith("https:")) {
            handleHttpInclude(uri, request, response);
            return;
        }

        if (!uri.startsWith("/")) {
            Path currentCollection = RequestContext.getRequestContext().getCurrentCollection();
            uri = currentCollection.expand(uri).toString();
        }

        handleVirtualInclude(uri, request, response);
    }


    private void handleDirectInclude(String address, DecoratorRequest request,
            DecoratorResponse response, boolean ignoreNotFound) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = null;

        boolean asCurrentPrincipal = "true".equals(request.getStringParameter(
                PARAMETER_AS_CURRENT_USER));
        
        // VTK-2460
        if (requestContext.isViewUnauthenticated()) {
            asCurrentPrincipal = false;
        }
        
        if (asCurrentPrincipal) {
            token = requestContext.getSecurityToken();
        }
        Path uri = Path.fromString(address);
        Resource r = null;
        try {
            r = repository.retrieve(token, uri, false);
        } catch (ResourceNotFoundException e) {
            if (ignoreNotFound) {
                return;
            }
            throw new DecoratorComponentException(
                    "Resource '" + address + "' not found");
        } catch (AuthenticationException e) {
            if (asCurrentPrincipal)
                throw new DecoratorComponentException(
                        "Resource '" + address + "' requires authentication");
            throw new DecoratorComponentException(
                    "Resource '" + address + "' not readable with anonymous access");
        } catch (AuthorizationException e) {
            throw new DecoratorComponentException(
                    "Not authorized to read resource '" + address + "'");
        }

        if (r.isCollection() || !ContentTypeHelper.isTextContentType(r.getContentType())) {
            throw new DecoratorComponentException(
                    "Cannot include URI '" + address + "' with content type '"
                    + r.getContentType() + "': not a textual resource");
        }

        String characterEncoding = r.getCharacterEncoding();
        InputStream is = repository.getInputStream(token, uri, true);

        String elementParam = request.getStringParameter(PARAMETER_ELEMENT);

        if (elementParam != null && ContentTypeHelper.isHTMLOrXHTMLContentType(r.getContentType())) {
            HtmlPage page = htmlParser.parse(is, characterEncoding);

            String result = "";
            List<HtmlElement> elements = page.select(elementParam);
            if (elements.size() > 0) {
                result = elements.get(0).getContent();
            }
            Writer writer = response.getWriter();
            writer.write(result);
            writer.close();
        } else {
            byte[] bytes = StreamUtil.readInputStream(is);
            response.setCharacterEncoding(characterEncoding);
            OutputStream out = response.getOutputStream();
            out.write(bytes);
            out.close();
        }
    }
    
    private void handleVirtualInclude(String uri, DecoratorRequest request,
            DecoratorResponse response) throws Exception {
        String decodedURI = uri;

        Map<String, String[]> queryMap = new HashMap<String, String[]>();
        String queryString = null;

        if (uri.indexOf("?") != -1) {
            queryString = uri.substring(uri.indexOf("?") + 1);
            decodedURI = uri.substring(0, uri.indexOf("?"));
            queryMap = URL.splitQueryString(queryString);
        }

        Path decodedPath = Path.fromString(decodedURI);
        decodedPath = URL.decode(decodedPath);

        handleVirtualInclude(decodedPath, queryMap, request, response);
    }


    private void handleVirtualInclude(Path uri, Map<String, String[]> queryMap, DecoratorRequest request, 
            DecoratorResponse response) throws Exception {
        
        HttpServletRequest servletRequest = request.getServletRequest();
        URL url = URL.create(servletRequest);
        url.setPath(uri);
        url.clearParameters();
        for (String param: queryMap.keySet()) {
            for (String value: queryMap.get(param)) {
                url.addParameter(param, value);
            }
        }

        if (logger.isDebugEnabled()) {
            logger.info("handle virtual include: " + uri + " -> " + url);
        }
        
        if (servletRequest.getAttribute(INCLUDE_ATTRIBUTE_NAME) != null) {
            throw new DecoratorComponentException(
                    "Error including URI '" + uri + "': possible include loop detected ");
        }

        ConfigurableRequestWrapper requestWrapper =
            new ConfigurableRequestWrapper(servletRequest, url);
        
        requestWrapper.setHeader("If-Modified-Since", null);

        BufferedResponse servletResponse = new BufferedResponse();

        try {
            requestWrapper.setAttribute(INCLUDE_ATTRIBUTE_NAME, new Object());
            String servletName = (String) servletRequest
            .getAttribute(VTKServlet.SERVLET_NAME_REQUEST_ATTRIBUTE);

            RequestDispatcher rd = servletContext
            .getNamedDispatcher(servletName);
            if (rd == null) {
                throw new RuntimeException("No request dispatcher for name '"
                        + servletName + "' available");
            }

            rd.forward(requestWrapper, servletResponse);

            if (servletResponse.getStatus() != HttpServletResponse.SC_OK) {
                throw new DecoratorComponentException("Included resource '"
                        + uri + "' returned HTTP status code "
                        + servletResponse.getStatus());
            }
        } finally {
            requestWrapper.setAttribute(INCLUDE_ATTRIBUTE_NAME, null);
        }

        if (!ContentTypeHelper.isTextContentType(servletResponse.getContentType())) {
            throw new DecoratorComponentException(
                    "Cannot include URI '" + uri + "': not a textual resource. " +
                    "Reported content type is '" + servletResponse.getContentType() + "'");
        }

        String elementParam = request.getStringParameter(PARAMETER_ELEMENT);

        if (elementParam != null && ContentTypeHelper.isHTMLOrXHTMLContentType(servletResponse.getContentType())) {
            byte[] bytes = servletResponse.getContentBuffer();
            InputStream is = new ByteArrayInputStream(bytes);

            HtmlPage page = htmlParser.parse(is, servletResponse.getCharacterEncoding());
            String result = "";

            List<HtmlElement> elements = page.select(elementParam);
            if (elements.size() > 0) {
                result = elements.get(0).getContent();
            }
            Writer writer = response.getWriter();
            writer.write(result);
            writer.close();

        } else {
            byte[] bytes = servletResponse.getContentBuffer();
            response.setCharacterEncoding(servletResponse.getCharacterEncoding());
            OutputStream out = response.getOutputStream();
            out.write(bytes);
            out.close();
        }
    }
    
    
    private void handleHttpInclude(String uri,
            DecoratorRequest request, DecoratorResponse response) throws Exception {
        URLObject obj = httpIncludeCache.get(uri);
        String result = "";
        String elementParam = request.getStringParameter(PARAMETER_ELEMENT);
        if (elementParam != null) {
            try {
                // XXX: cache results
                HtmlPage page = htmlParser.parse(obj.getInputStream(), obj.getCharacterEncoding());
                List<HtmlElement> elements = page.select(elementParam);
                if (elements.size() > 0) {
                    result = elements.get(0).getContent();
                }
            } catch (Exception e) {
                result = e.getMessage();
            }
        } else {
            result = obj.getContent();
        }
        Writer writer = response.getWriter();
        writer.write(result);
        writer.close();
    }



    protected String getDescriptionInternal() {
        return "Includes the contents of another document in the page";
    }

    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new HashMap<String, String>();
        map.put(PARAMETER_FILE, PARAMETER_FILE_DESC);
        map.put(PARAMETER_VIRTUAL, PARAMETER_VIRTUAL_DESC);
        map.put(PARAMETER_AS_CURRENT_USER, PARAMETER_AS_CURRENT_USER_DESC);
        map.put(PARAMETER_ELEMENT, PARAMETER_ELEMENT_DESC);
        return map;
    }

    public void setUriPreProcessor(QueryStringPreProcessor uriPreProcessor) {
        this.uriPreProcessor = uriPreProcessor;
    }

}
