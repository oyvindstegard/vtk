package vtk.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.store.PrincipalMetadataDAO;
import vtk.security.SecurityContext;
import vtk.web.service.Service;

public abstract class AbstractControllerTest {

    static {
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.Log4JLogger");
        System.setProperty("log4j.configuration", "log4j.test.xml");
    }

    protected Mockery context = new JUnit4Mockery();
    protected final HttpServletRequest mockRequest = context.mock(HttpServletRequest.class);    
    protected final HttpServletResponse mockResponse = context.mock(HttpServletResponse.class);
    protected final Service mockService = context.mock(Service.class);
    protected final Repository mockRepository = context.mock(Repository.class);
    protected final PrincipalMetadataDAO mockPrincipalMetadataDao = context.mock(PrincipalMetadataDAO.class);

    public void setUp() throws Exception {
        SecurityContext securityContext = new SecurityContext(null, null);
        context.checking(new Expectations() {{ 
            allowing(mockRequest).setAttribute("vtk.security.SecurityContext.requestAttribute", securityContext);
            allowing(mockRequest).getAttribute("vtk.security.SecurityContext.requestAttribute");
            will(returnValue(securityContext));
        }});
        SecurityContext.setSecurityContext(securityContext, mockRequest);
        context.checking(new Expectations() {{ 
            allowing(mockRequest).getRequestURL(); 
            will(returnValue(new StringBuffer("http://localhost/" + getRequestPath())));
            
            allowing(mockRequest).isSecure(); 
            will(returnValue(false));
            
            allowing(mockRequest).getQueryString(); 
            will(returnValue(""));
            
            allowing(mockRequest).getParameter("vrtxPreviewUnpublished"); 
            allowing(mockRequest).getParameter("revision");
            allowing(mockRequest).getParameter("vrtxPreviewUnpublished");
            allowing(mockRequest).getHeader("Referer");
            
            allowing(mockRequest).setAttribute(with(equal("vtk.web.RequestContext.requestAttribute")), 
                    with(any(RequestContext.class)));
               allowing(mockService).getName();
            
        }});
        RequestContext requestContext = new RequestContext(mockRequest, securityContext, mockService, null,
                null, getRequestPath(), null, false, false, true, mockRepository, mockPrincipalMetadataDao);
        context.checking(new Expectations() {{
            allowing(mockRequest).getAttribute("vtk.web.RequestContext.requestAttribute");
            will(returnValue(new java.util.Stack<>()));
        }});
        RequestContext.setRequestContext(requestContext, mockRequest);
    }

    protected abstract Path getRequestPath();

}
