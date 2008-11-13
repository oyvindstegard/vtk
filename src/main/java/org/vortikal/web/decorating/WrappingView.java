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
package org.vortikal.web.decorating;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.View;

import org.vortikal.web.referencedata.ReferenceDataProvider;
import org.vortikal.web.referencedata.ReferenceDataProviding;
import org.vortikal.web.servlet.ConfigurableRequestWrapper;

/**
 * Wrapper class for view, running {@link ReferenceDataProvider referenceDataProviders}
 * before the wrapped view is run (and the necessary model is available),
 * wrapping the view in an optional {@link ViewWrapper}
 * 
 * @see AbstractWrappingViewResolver, ViewWrapper, ReferenceDataProvider, 
 * @see ReferenceDataProviding
 */
public class WrappingView implements View, InitializingBean {

    private static Log logger = LogFactory.getLog(WrappingView.class);

    private ReferenceDataProvider[] referenceDataProviders;
    private View view;
    private ViewWrapper viewWrapper;
    
    public WrappingView() {}
    
    /**
     * @param view - the view to eventually run
     * @param referenceDataProviders - the set of reference data
     * providers for this view
     */
    public WrappingView(View view, ReferenceDataProvider[] resolverProviders,
                     ViewWrapper viewWrapper) {

        this.view = view;
        this.viewWrapper = viewWrapper;
        this.referenceDataProviders = resolverProviders;

        afterPropertiesSet();
    }

    @SuppressWarnings("unchecked")
    public void render(Map model, HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        if (this.referenceDataProviders != null && this.referenceDataProviders.length > 0) {

            if (model == null) {
                model = new HashMap<String, Object>();
            }
            
            for (int i = 0; i < this.referenceDataProviders.length; i++) {
                ReferenceDataProvider provider = this.referenceDataProviders[i];
                if (logger.isDebugEnabled())
                    logger.debug("Invoking reference data provider '" + provider + "'");
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
            this.viewWrapper.renderView(this.view, model, requestWrapper, response);
        } else {
            this.view.render(model, requestWrapper, response);
        }
        
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(this.getClass().getName()).append(":");
        sb.append(" [view = ").append(this.view);
        sb.append(", viewWrapper = ").append(this.viewWrapper).append("]");
        return sb.toString();
    }

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

    public void afterPropertiesSet() {
        if (this.view == null)
            throw new IllegalArgumentException(
                    "The wrapped view cannot be null");

        List<ReferenceDataProvider> providerList = new ArrayList<ReferenceDataProvider>();

        if (this.referenceDataProviders != null) {
            providerList.addAll(Arrays.asList(this.referenceDataProviders));
        }

        if (this.viewWrapper != null
            && (this.viewWrapper instanceof ReferenceDataProviding)) {

            ReferenceDataProvider[] wrapperProviders = null;

            wrapperProviders = ((ReferenceDataProviding) this.viewWrapper)
                    .getReferenceDataProviders();
            if (wrapperProviders != null) {
                providerList.addAll(Arrays.asList(wrapperProviders));
            }
        }
        
        if (this.view instanceof ReferenceDataProviding) {
            ReferenceDataProvider[] viewProviders = null;

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
