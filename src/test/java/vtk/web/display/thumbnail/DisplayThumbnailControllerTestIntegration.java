package vtk.web.display.thumbnail;

import static junit.framework.TestCase.assertNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.ContentStream;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Resource;
import vtk.repository.ResourceImpl;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinitionImpl;
import vtk.web.AbstractControllerTest;

public class DisplayThumbnailControllerTestIntegration extends AbstractControllerTest {

    private DisplayThumbnailController controller;
    private Path requestPath;

    protected final Property mockThumbnail = context.mock(Property.class);
    private final String thumbnailMimeType = "image/png";

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        controller = new DisplayThumbnailController();
    }

    @Override
    protected Path getRequestPath() {
        requestPath = Path.fromString("/images/testImage.gif");
        return requestPath;
    }

    @Test
    public void noThumbnailAvailableWillRedirect() throws Exception {
        prepareRequest(false, true);

        // No thumbnail, so we should redirect
        context.checking(new Expectations() {
            {
                oneOf(mockResponse).sendRedirect(requestPath.toString());
            }
        });

        handleRequest();
    }

    @Test
    public void displayNoMimeTypeThumbnail() throws Exception {
        prepareRequest(true, true);

        // No mimetype for binary data, so we should redirect
        context.checking(new Expectations() {
            {
                oneOf(mockThumbnail).getBinaryContentType();
                will(returnValue(""));
            }
        });

        handleRequest();
    }

    @Test
    public void displayThumbnail() throws Exception {
        prepareRequest(true, false);

        BufferedImage image = ImageIO.read(this.getClass().getResourceAsStream("testImage.gif"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        final byte[] imageBytes = out.toByteArray();
        out.close();
        InputStream in = new ByteArrayInputStream(imageBytes);
        final ContentStream contentStream = new ContentStream(in, imageBytes.length);

        context.checking(new Expectations() {
            {
                atLeast(2).of(mockThumbnail).getBinaryContentType();
                will(returnValue(thumbnailMimeType));
            }
        });
        context.checking(new Expectations() {
            {
                oneOf(mockThumbnail).getBinaryStream();
                will(returnValue(contentStream));
            }
        });
        context.checking(new Expectations() {
            {
                oneOf(mockResponse).setContentType(thumbnailMimeType);
            }
        });
        context.checking(new Expectations() {
            {
                oneOf(mockResponse).setContentLength(imageBytes.length);
            }
        });

        final ServletOutputStream responseOut = new MockServletOutputStream();
        context.checking(new Expectations() {
            {
                oneOf(mockResponse).getOutputStream();
                will(returnValue(responseOut));
            }
        });

        handleRequest();
    }

    private void prepareRequest(final boolean withThumbnail, boolean expectRedirect) throws Exception {
        // Retrieve the image to display thumbnail for
        context.checking(new Expectations() {
            {
                oneOf(mockRepository).retrieve(null, requestPath, true);
                will(returnValue(getImageResource(withThumbnail)));
            }
        });
        if (expectRedirect) {
            context.checking(new Expectations() {
                {
                    oneOf(mockResponse).sendRedirect(requestPath.toString());
                }
            });
        }
    }

    private void handleRequest() throws Exception {
        ModelAndView result = controller.handleRequest(mockRequest, mockResponse);
        assertNull("Unexpected model&view was returned", result);
    }

    private Resource getImageResource(boolean withThumbnail) throws IOException {
        ResourceImpl image = new ResourceImpl(requestPath);
        image.setResourceType("image");

        if (withThumbnail) {
            final PropertyTypeDefinitionImpl thumbnailPropDef = new PropertyTypeDefinitionImpl();
            thumbnailPropDef.setType(Type.BINARY);
            thumbnailPropDef.setNamespace(Namespace.DEFAULT_NAMESPACE);
            thumbnailPropDef.setName(PropertyType.THUMBNAIL_PROP_NAME);

            context.checking(new Expectations() {
                {
                    oneOf(mockThumbnail).getDefinition();
                    will(returnValue(thumbnailPropDef));
                }
            });

            image.addProperty(mockThumbnail);
        }

        return image;
    }

    private class MockServletOutputStream extends ServletOutputStream {

        @Override
        public void write(int b) throws IOException {
        }

        @Override
        public boolean isReady() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            throw new UnsupportedOperationException();
        }

    }

}
