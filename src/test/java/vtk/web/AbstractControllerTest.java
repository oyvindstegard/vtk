package vtk.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;

import vtk.context.BaseContext;
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
        BaseContext.pushContext();
        SecurityContext securityContext = new SecurityContext(null, null);
        SecurityContext.setSecurityContext(securityContext);
        RequestContext requestContext = new RequestContext(mockRequest, securityContext, mockService, null,
                getRequestPath(), null, false, false, true, mockRepository, mockPrincipalMetadataDao);
        RequestContext.setRequestContext(requestContext);
    }

    protected abstract Path getRequestPath();

}
