package vtk.web.service;

import java.util.Optional;

public interface ServiceResolver {

    public Optional<Service> service(String name);
}
