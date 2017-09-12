package vtk.web.decorating.tl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.text.tl.Context;
import vtk.text.tl.Symbol;
import vtk.text.tl.expr.Function;
import vtk.web.RequestContext;
import vtk.web.decorating.DynamicDecoratorTemplate;
import vtk.web.decorating.tl.DomainTypes.Failure;
import vtk.web.decorating.tl.DomainTypes.Result;
import vtk.web.decorating.tl.DomainTypes.Success;

public class AuthorizeFunction extends Function {
    
    private static final Map<String, RepositoryAction> actions = initActions();

    public AuthorizeFunction(Symbol symbol) {
        super(symbol, 2);
    }
    
    @Override
    public Object eval(Context ctx, Object... args) {
        Object ref = args[0];
        if (ref == null) {
            return new Failure<>("First parameter (resource reference) is NULL");
        }
        Object actionParam = args[1];
        if (actionParam == null) {
            return new Failure<>("Second parameter (action) is NULL");
        }
        
        HttpServletRequest request = (HttpServletRequest) 
                ctx.getAttribute(DynamicDecoratorTemplate.SERVLET_REQUEST_CONTEXT_ATTR);
        if (request == null) {
            throw new RuntimeException("Servlet request not found in context by attribute: "
                    + DynamicDecoratorTemplate.SERVLET_REQUEST_CONTEXT_ATTR);
        }
        RequestContext requestContext = RequestContext.getRequestContext(request);
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        
        Result<Path> result = DomainTypes.toPath(ref, requestContext);
        if (!result.isSuccess()) return result;
        Path uri = result.asSuccess().value();
        
        
        RepositoryAction action;
        action = actions.get(actionParam.toString());
        if (action == null) {
            return new Failure<>("Invalid action parameter: " + actionParam 
                    + ". Must be one of " + actions.keySet());
        }

        try {
            Resource resource = repository.retrieve(token, uri, true);
            boolean value = repository.isAuthorized(resource, action, 
                    requestContext.getPrincipal(), false);
            return new Success<>(value);
        }
         catch (Throwable t) {
            return new Failure<>(t);
        }

    }

    private static Map<String, RepositoryAction> initActions() {
        Map<String, RepositoryAction> result = new HashMap<>();
        result.put(RepositoryAction.READ.getName(), RepositoryAction.READ);
        result.put(RepositoryAction.READ_PROCESSED.getName(), RepositoryAction.READ_PROCESSED);
        result.put(RepositoryAction.READ_WRITE.getName(), RepositoryAction.READ_WRITE);
        result.put(RepositoryAction.READ_WRITE_UNPUBLISHED.getName(), RepositoryAction.READ_WRITE_UNPUBLISHED);
        result.put(RepositoryAction.ALL.getName(), RepositoryAction.ALL);
        result.put(RepositoryAction.ADD_COMMENT.getName(), RepositoryAction.ADD_COMMENT);
        return Collections.unmodifiableMap(result);
    }
    
    
}
