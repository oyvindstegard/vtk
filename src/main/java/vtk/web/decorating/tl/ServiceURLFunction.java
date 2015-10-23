package vtk.web.decorating.tl;

import vtk.repository.Path;
import vtk.text.tl.Context;
import vtk.text.tl.Symbol;
import vtk.text.tl.expr.Function;
import vtk.web.decorating.tl.DomainTypes.Failure;
import vtk.web.decorating.tl.DomainTypes.RequestContextType;
import vtk.web.decorating.tl.DomainTypes.Success;
import vtk.web.service.ServiceUrlProvider;

public class ServiceURLFunction extends Function {
    private final ServiceUrlProvider serviceUrlProvider;
    
    public ServiceURLFunction(Symbol symbol, ServiceUrlProvider serviceUrlProvider) {
        super(symbol, 3);
        this.serviceUrlProvider = serviceUrlProvider;
    }

    @Override
    public Object eval(Context ctx, Object... args) {
        // args: (RequestContext, Path, Service)
        Object arg0 = args[0];
        Object arg1 = args[1];
        Object arg2 = args[2];
        
        if (!(arg0 instanceof RequestContextType))
            return new Failure<>("Not a request context: " + arg0);

        if (arg1 == null || arg2 == null)
            return new Failure<>("NULL argument(s)");
        String path = arg1.toString();
        String serviceName = arg2.toString();

        try {
            return new Success<>(serviceUrlProvider.builder(serviceName)
                .withPath(Path.fromString(path)).build().toString()
            );
        } catch (Throwable t) { return new Failure<>(t); }
    }

}
