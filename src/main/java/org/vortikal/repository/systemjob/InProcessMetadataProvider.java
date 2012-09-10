/* Copyright (c) 2012, University of Oslo, Norway
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of the University of Oslo nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.vortikal.repository.systemjob;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.graphics.ImageService;
import org.vortikal.graphics.ScaledImage;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Path;
import org.vortikal.repository.Property;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.SystemChangeContext;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;

public class InProcessMetadataProvider implements MediaMetadataProvider {

    private ImageService imageService;
    private int width;
    private Set<String> supportedFormats;
    private boolean scaleUp = false;
    private long maxSourceImageFileSize = 35000000;
    private long maxSourceImageRawMemoryUsage = 100000000;

    private PropertyTypeDefinition thumbnailPropDef;
    private PropertyTypeDefinition mediaMetadataStatusPropDef;
    private PropertyTypeDefinition imageHeightPropDef;
    private PropertyTypeDefinition imageWidthPropDef;

    private final Log logger = LogFactory.getLog(getClass());

    @Override
    public void generateMetadata(final Repository repository, final SystemChangeContext context, String token,
            Path path, Resource resource) throws Exception {
        generateImageMetadata(repository, context, token, path, resource);
    }

    private void generateImageMetadata(final Repository repository, final SystemChangeContext context, String token,
            Path path, Resource resource) throws Exception {
        if (resource == null) {
            return;
        }

        // Check max source content length constraint
        if (resource.getContentLength() >= maxSourceImageFileSize) {
            logger.info("Image size exceeds maximum limit: " + path);
            setThumbnailGeneratorStatus(repository, context, token, resource, "IMAGE_SIZE_EXCEEDS_LIMIT");
            return;
        }

        // Check max source image memory usage constraint
        Dimension dim = null;
        try {
            dim = getImageDimension(repository.getInputStream(token, path, true));
        } catch (Throwable t) {
            logger.info("Failed to read image " + path, t);
            setThumbnailGeneratorStatus(repository, context, token, resource, "CORRUPT");
            return;

        }
        if (dim != null) {
            long estimatedMemoryUsage = estimateMemoryUsage(dim);
            if (logger.isDebugEnabled()) {
                logger.debug("Estimated memory usage for image " + path + " of " + dim.width + "x" + dim.height + " = "
                        + estimatedMemoryUsage + " bytes");
            }
            if (estimatedMemoryUsage > maxSourceImageRawMemoryUsage) {
                logger.info("Estimated memory usage of image exceeds limit: " + path);
                setThumbnailGeneratorStatus(repository, context, token, resource, "MEMORY_USAGE_EXCEEDS_LIMIT");
                return;
            }
        }

        BufferedImage image = null;
        try {
            image = ImageIO.read(repository.getInputStream(token, path, true));
        } catch (Throwable t) {
            logger.info("Failed to read image " + path, t);
            setThumbnailGeneratorStatus(repository, context, token, resource, "CORRUPT");
            return;
        }
        if (image == null) {
            logger.info("Failed to read image " + path);
            setThumbnailGeneratorStatus(repository, context, token, resource, "CORRUPT");
            return;
        }

        Property contentType = resource.getProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTTYPE_PROP_NAME);

        String mimetype = contentType.getStringValue();
        String imageFormat = mimetype.substring(mimetype.lastIndexOf("/") + 1);

        if (!supportedFormats.contains(imageFormat.toLowerCase())) {
            logger.info("Unsupported format of image " + path + ": " + imageFormat);
            setThumbnailGeneratorStatus(repository, context, token, resource, "UNSUPPORTED_FORMAT");
            return;
        }

        if (!scaleUp && image.getWidth() <= width) {
            if (logger.isDebugEnabled()) {
                logger.debug("Will not create thumbnail for image " + path + ": configured not to scale up");
            }
            setThumbnailGeneratorStatus(repository, context, token, resource, "CONFIGURED_NOT_TO_SCALE_UP");
            return;
        }

        ScaledImage thumbnail = imageService.scaleImage(image, imageFormat, width, ImageService.HEIGHT_ANY);
        String thumbnailFormat;
        if (imageFormat.equalsIgnoreCase("gif") || imageFormat.equalsIgnoreCase("png"))
            thumbnailFormat = "png";
        else
            thumbnailFormat = !imageFormat.equalsIgnoreCase("jpeg") ? "jpeg" : imageFormat;

        Property property = thumbnailPropDef.createProperty();
        property.setBinaryValue(thumbnail.getImageBytes(thumbnailFormat), "image/" + thumbnailFormat);
        resource.addProperty(property);

        if (dim != null) {
            Property imageHeightProp = imageHeightPropDef.createProperty();
            imageHeightProp.setIntValue(dim.height);
            resource.addProperty(imageHeightProp);

            Property imageWidthProp = imageWidthPropDef.createProperty();
            imageWidthProp.setIntValue(dim.width);
            resource.addProperty(imageWidthProp);
        }

        if (resource.getLock() == null) {
            resource.removeProperty(mediaMetadataStatusPropDef);
            repository.store(token, resource, context);
            logger.info("Created thumbnail for " + resource);
        } else {
            logger.warn("Resource " + resource + " currently locked, will not invoke store.");

        }
    }

    private void setThumbnailGeneratorStatus(final Repository repository, final SystemChangeContext context,
            final String token, Resource resource, String status) {
        Property statusProp = mediaMetadataStatusPropDef.createProperty();
        statusProp.setValue(new Value(status, org.vortikal.repository.resourcetype.PropertyType.Type.STRING));
        resource.addProperty(statusProp);
        try {
            repository.store(token, resource, context);
        } catch (Exception e) {
            e.printStackTrace();
            // Resource currently locked or moved.. try again in next
            // batch
        }
    }

    private Dimension getImageDimension(InputStream content) throws Exception {

        ImageInputStream iis = new FileCacheImageInputStream(content, null);
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                reader.setInput(iis);
                int width = reader.getWidth(reader.getMinIndex());
                int height = reader.getHeight(reader.getMinIndex());
                reader.dispose();
                return new Dimension(width, height);
            }
        } finally {
            iis.close();
        }

        return null;
    }

    /**
     * Estimates the raw memory usage for an image where each pixel uses 24 bits
     * or 3 bytes of memory.
     * 
     * @param dim
     *            The <code>Dimension</code> of the image.
     * @return The estimated raw memory usage in bytes.
     */
    private long estimateMemoryUsage(Dimension dim) {
        return (long) dim.height * (long) dim.width * 24 / 8;
    }

    @Required
    public void setImageService(ImageService imageService) {
        this.imageService = imageService;
    }

    @Required
    public void setWidth(int width) {
        if (width <= 1) {
            throw new IllegalArgumentException("scale width must be >= 1");
        }
        this.width = width;
    }

    @Required
    public void setSupportedFormats(Set<String> supportedFormats) {
        this.supportedFormats = supportedFormats;
    }

    public void setScaleUp(boolean scaleUp) {
        this.scaleUp = scaleUp;
    }

    public void setMaxSourceImageFileSize(long maxSourceImageFileSize) {
        if (maxSourceImageFileSize < 1) {
            throw new IllegalArgumentException("maxSourceImageFileSize must be >= 1");
        }
        this.maxSourceImageFileSize = maxSourceImageFileSize;
    }

    /**
     * Set cap on estimated raw memory usage on image during scale operation.
     * The estimate is based upon a memory usage of 24 bits per pixel, which
     * should be the most common type. 32bpp images will consume more than
     * actual estimate. To fix that, one needs to provide the bpp value from the
     * {@link org.vortikal.repository.content.ImageContentFactory}.
     * 
     * Default value of 100MB is roughly equivalent to an image of about 33
     * megapixels.
     * 
     * @param maxSourceImageRawMemoryUsage
     */
    public void setMaxSourceImageRawMemoryUsage(long maxSourceImageRawMemoryUsage) {
        if (maxSourceImageRawMemoryUsage < 1) {
            throw new IllegalArgumentException("maxSourceImageRawMemoryUsage must be >= 1");
        }
        this.maxSourceImageRawMemoryUsage = maxSourceImageRawMemoryUsage;
    }

    @Required
    public void setThumbnailPropDef(PropertyTypeDefinition thumbnailPropDef) {
        this.thumbnailPropDef = thumbnailPropDef;
    }

    @Required
    public void setMediaMetadataStatusPropDef(PropertyTypeDefinition mediaMetadataStatusPropDef) {
        this.mediaMetadataStatusPropDef = mediaMetadataStatusPropDef;
    }

    @Required
    public void setImageHeightPropDef(PropertyTypeDefinition imageHeightPropDef) {
        this.imageHeightPropDef = imageHeightPropDef;
    }

    @Required
    public void setImageWidthPropDef(PropertyTypeDefinition imageWidthPropDef) {
        this.imageWidthPropDef = imageWidthPropDef;
    }

}
