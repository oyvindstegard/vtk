/* Copyright (c) 2004, 2008 University of Oslo, Norway
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
package vtk.web.decorating;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.View;
import vtk.web.servlet.BufferedResponse;
import vtk.web.servlet.ConfigurableRequestWrapper;


/**
 * Abstract decorator which takes a
 * view, which is rendered into a string, and the result is merged
 * with the output from the previous view using the abstract {@link
 * #processInternal} method.
 *
 * <p>Configurable properties:
 * <ul>
 *   <li>view - the {@link View} to process when filtering
 * </ul>
 *
 */
public abstract class AbstractViewProcessingDecorator 
    implements Decorator, InitializingBean {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    private View view;


    public void setView(View view) {
        this.view = view;
    }
    

    public void afterPropertiesSet() throws Exception {
        if (this.view == null) {
            throw new BeanInitializationException(
                "Bean property 'view' must be set");
        }
    }
    
    

    public boolean match(HttpServletRequest request, HttpServletResponse response) throws Exception {
        return true;
    }


    /**
     * Processes the wrapped view, converts its output to text and
     * applies the result to the {@link #processInternal} method.
     *
     * @param model the MVC model
     * @param request the servlet request
     * @param content the textual content from the original view
     * @return the content as returned from the {@link
     * #processInternal} method.
     * @exception Exception if an error occurs
     */
    @SuppressWarnings("rawtypes")
    public final PageContent decorate(Map model, HttpServletRequest request,
                                  PageContent content) throws Exception {

        String viewContent = renderView(model, request);
        return processInternal(content, viewContent);
    }
    
    

    /**
     * Filters (merges) the textual content from the original view
     * with the content from the wrapped view. Subclasses must
     * implement this method.
     *
     * @param content the content from the original view (the one
     * specified in {@link WrappingView}
     * @param viewContent the content rendered by the view in this
     * class
     * @return the merged content from the two views
     * @exception Exception if an error occurs
     */
    protected abstract PageContent processInternal(PageContent content, String viewContent) 
        throws Exception;
    


    @SuppressWarnings("rawtypes")
    private String renderView(Map model, HttpServletRequest request) throws Exception {

        ConfigurableRequestWrapper requestWrapper = new ConfigurableRequestWrapper(request);
        requestWrapper.setMethod("GET");
        BufferedResponse tmpResponse = new BufferedResponse();

        this.view.render(model, requestWrapper, tmpResponse);

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Rendered wrapped view " + this.view + ". "
                         + "Character encoding was: "
                         + tmpResponse.getCharacterEncoding() + ", "
                         + "Content-Length was: " + tmpResponse.getContentLength());
        }

        return new String(tmpResponse.getContentBuffer(), tmpResponse.getCharacterEncoding());
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(this.getClass().getName()).append(": [");
        sb.append("view = ").append(this.view).append("]");
        return sb.toString();
    }
    
}
