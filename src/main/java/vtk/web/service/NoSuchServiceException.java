package vtk.web.service;

/**
 * Thrown if no VTK Service is found.
 *
 * @see vtk.web.service.Service
 */
public class NoSuchServiceException extends RuntimeException {
    public NoSuchServiceException() {
        super();
    }

    public NoSuchServiceException(String message) {
        super(message);
    }

    public NoSuchServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchServiceException(Throwable cause) {
        super(cause);
    }
}
