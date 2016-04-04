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
package vtk.web.decorating;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.View;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.referencedata.ReferenceDataProviding;
import vtk.web.servlet.ConfigurableRequestWrapper;

/**
 * Wrapper class for view, running {@link ReferenceDataProvider referenceDataProviders}
 * before the wrapped view is run (and the necessary model is available),
 * wrapping the view in an optional {@link ViewWrapper}
 * 
 * @see AbstractWrappingViewResolver
 * @see ViewWrapper
 * @see ReferenceDataProvider
 * @see ReferenceDataProviding
 */
public class WrappingView implements View, InitializingBean {

    private static Logger logger = LoggerFactory.getLogger(WrappingView.class);

    private ReferenceDataProvider[] referenceDataProviders;
    private View view;
    private ViewWrapper viewWrapper;
    
    public WrappingView() {}
    
    /**
     * @param view - the view to eventually run
     * @param referenceDataProviders - the set of reference data
     * providers for this view
     */
    public WrappingView(View view, ReferenceDataProvider[] referenceDataProviders,
                     ViewWrapper viewWrapper) {

        this.view = view;
        this.viewWrapper = viewWrapper;
        this.referenceDataProviders = referenceDataProviders;

        afterPropertiesSet();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void render(Map model, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        if (model == null) {
            model = new HashMap<String, Object>();
        }
        if (this.referenceDataProviders != null && this.referenceDataProviders.length > 0) {
            for (int i = 0; i < this.referenceDataProviders.length; i++) {
                ReferenceDataProvider provider = this.referenceDataProviders[i];
                if (logger.isDebugEnabled())
                    logger.debug("Invoking reference data provider: " + provider);
                provider.referenceData(model, request);
            }
        }
        String method = request.getMethod();
        
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            method = "GET";
        }

        ConfigurableRequestWrapper requestWrapper = new ConfigurableRequestWrapper(request);
        requestWrapper.setMethod(method);
        
        if (this.viewWrapper != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Rendering response " + response + " with view wrapper " + this.viewWrapper);
            }
            this.viewWrapper.renderView(this.view, model, requestWrapper, response);
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Rendering response " + response + " with view: " + this.view);
            }
            this.view.render(model, requestWrapper, response);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getName()).append(":");
        sb.append(" [view = ").append(this.view);
        sb.append(", viewWrapper = ").append(this.viewWrapper).append("]");
        return sb.toString();
    }

    @Override
    public String getContentType() {
        return null;
    }

    public void setReferenceDataProviders(
            ReferenceDataProvider[] referenceDataProviders) {
        this.referenceDataProviders = referenceDataProviders;
    }

    public void setView(View view) {
        this.view = view;
    }

    public void setViewWrapper(ViewWrapper viewWrapper) {
        this.viewWrapper = viewWrapper;
    }

    @Override
    public final void afterPropertiesSet() {
        if (this.view == null)
            throw new IllegalArgumentException(
                    "The wrapped view cannot be null");

        List<ReferenceDataProvider> providerList = new ArrayList<ReferenceDataProvider>();

        if (this.referenceDataProviders != null) {
            providerList.addAll(Arrays.asList(this.referenceDataProviders));
        }

        if (this.viewWrapper != null
            && (this.viewWrapper instanceof ReferenceDataProviding)) {

            ReferenceDataProvider[] wrapperProviders;

            wrapperProviders = ((ReferenceDataProviding) this.viewWrapper)
                    .getReferenceDataProviders();
            if (wrapperProviders != null) {
                providerList.addAll(Arrays.asList(wrapperProviders));
            }
        }
        
        if (this.view instanceof ReferenceDataProviding) {
            ReferenceDataProvider[] viewProviders;

            viewProviders = ((ReferenceDataProviding) this.view)
                    .getReferenceDataProviders();
            if (viewProviders != null) {
                providerList.addAll(Arrays.asList(viewProviders));
            }
        }

        if (providerList.size() > 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("Found reference data providers for view "
                        + this.view + ": " + providerList);
            }

            this.referenceDataProviders = (ReferenceDataProvider[]) providerList.
                toArray(new ReferenceDataProvider[providerList.size()]);
        }        
    }

}
