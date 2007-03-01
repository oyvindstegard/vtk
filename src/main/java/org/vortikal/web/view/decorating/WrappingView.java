package org.vortikal.web.view.decorating;

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

/**
 * Wrapper class for view, running {@link ReferenceDataProvider referenceDataProviders}
 * before the wrapped view is run (and the necessary model is available),
 * wrapping the view in an optional {@ling ViewWrapper}
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

    public void render(Map model, HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        if (this.referenceDataProviders != null && this.referenceDataProviders.length > 0) {

            if (model == null) {
                model = new HashMap();
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

        RequestWrapper requestWrapper = new RequestWrapper(request, method);
        
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

        List providerList = new ArrayList();

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
