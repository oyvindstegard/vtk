package vtk.web.decorating.tl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import vtk.repository.Path;
import vtk.text.tl.Context;
import vtk.text.tl.Symbol;
import vtk.text.tl.expr.Function;
import vtk.web.decorating.tl.DomainTypes.Failure;
import vtk.web.decorating.tl.DomainTypes.RequestContextType;
import vtk.web.decorating.tl.DomainTypes.Success;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class ServiceURLFunction extends Function {
    
    private Map<String, Service> services = new HashMap<>();
    
    public ServiceURLFunction(Symbol symbol, Collection<Service> services) {
        super(symbol, 3);
        for (Service s: services) 
            this.services.put(s.getName(), s);
    }

    @Override
    public Object eval(Context ctx, Object... args) {
        // args: (RequestContext, Path, Service)
        Object arg0 = args[0];
        Object arg1 = args[1];
        Object arg2 = args[2];
        
        if (!(arg0 instanceof RequestContextType))
            return new Failure<>("Not a request context: " + arg0);

        if (arg0 == null || arg1 == null || arg2 == null)
            return new Failure<>("NULL argument(s)");
        
        Service service = services.get(arg2.toString());
        if (service == null) {
            return new Failure<>("No such service: " + arg2);
        }
        try {
            Path uri = Path.fromString(arg1.toString());
            URL url = service.constructURL(uri);
            return new Success<>(url.toString());
        } catch (Throwable t) { return new Failure<>(t); }
    }

}
