package vtk.web.actions.copymove;

import java.net.URLDecoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceLockedException;
import vtk.web.RequestContext;


public class CopyBackupController implements Controller {
    
    private CopyHelper copyHelper;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Path resourceToCopySrcUri = null;
        try {
            String uri = URLDecoder.decode((String) request.getParameter("uri"), "UTF-8");
            resourceToCopySrcUri = Path.fromStringWithTrailingSlash(uri);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        if (resourceToCopySrcUri == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        // Retrieve resource
        RequestContext requestContext = RequestContext.getRequestContext(request);
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        Resource resource = repository.retrieve(token, resourceToCopySrcUri, false);
        
        // If resource is a collection OR parent resource does NOT equals request resource
        if(resource.isCollection() || !requestContext.getResourceURI().equals(resource.getURI().getParent())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        
        // Copy resource
        Path resourceCopyDestUri = null;
        try {
          resourceCopyDestUri = copyHelper.makeDestUri(resourceToCopySrcUri, repository, token, resource);
          repository.copy(token, null, resourceToCopySrcUri, resourceCopyDestUri, false, true);
        } catch (ResourceLockedException rle) {
          response.setStatus(423);
          return null;
        } catch (Exception e) {
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          return null;
        }
        
        // Return 201 with destination uri in the Location-header
        response.setStatus(HttpServletResponse.SC_CREATED);
        response.setHeader("Location", resourceCopyDestUri.toString());
        
        return null;
    }

    @Required
    public void setCopyHelper(CopyHelper copyHelper) {
        this.copyHelper = copyHelper;
    }

}
