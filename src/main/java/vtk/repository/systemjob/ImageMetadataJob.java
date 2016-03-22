/* Copyright (c) 2013, University of Oslo, Norway
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

package vtk.repository.systemjob;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.Set;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import vtk.graphics.ImageUtil;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceLockedException;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.resourcetype.PropertyTypeDefinition;

/**
 * Update metadata properties defined by resource type <code>media-mixin</code>
 * for resources of type <code>image</code>.
 */
public class ImageMetadataJob extends AbstractResourceJob {

    private int width;
    private Set<String> supportedFormats;
    private long maxSourceImageFileSize = 35000000;
    private long maxSourceImageRawMemoryUsage = 100000000;

    private PropertyTypeDefinition thumbnailPropDef;
    private PropertyTypeDefinition mediaMetadataStatusPropDef;
    private PropertyTypeDefinition imageHeightPropDef;
    private PropertyTypeDefinition imageWidthPropDef;
    
    private final Logger logger = LoggerFactory.getLogger(ImageMetadataJob.class.getName());

    public ImageMetadataJob() {
        setAbortOnException(false);
    }
    
    @Override
    protected void executeForResource(Resource resource, ExecutionContext ctx) throws Exception {

        // Sanity check resource type
        if (!"image".equals(resource.getResourceType())) {
            logger.warn("Expected resource of type image, but got type " 
                    + resource.getResourceType() + " for resource at " + resource.getURI());
            return;
        }
        
        final Repository repository = ctx.getRepository();
        final String token = ctx.getToken();
        final Path path = resource.getURI();
        
        resource.removeProperty(thumbnailPropDef);
        resource.removeProperty(imageHeightPropDef);
        resource.removeProperty(imageWidthPropDef);
        
        Dimension dim;
        try {
            dim = ImageUtil.getImageStreamDimension(repository.getInputStream(token, path, true));
            if (dim == null) {
                logger.warn("Failed to read image dimension for " + path);
                storeWithStatus(resource, ctx, "CORRUPT");
                return;
            }
        } catch (Exception e) {
            logger.warn("Failed to read image dimension for " + path, e);
            storeWithStatus(resource, ctx, "CORRUPT");
            return;
        }

        // Add dimension props now, even if we can't create thumbnail later:
        Property imageHeightProp = imageHeightPropDef.createProperty();
        imageHeightProp.setIntValue(dim.height);
        resource.addProperty(imageHeightProp);

        Property imageWidthProp = imageWidthPropDef.createProperty();
        imageWidthProp.setIntValue(dim.width);
        resource.addProperty(imageWidthProp);

        // Check max source content length constraint
        if (resource.getContentLength() >= maxSourceImageFileSize) {
            logger.info("Image size exceeds maximum limit: " + path);
            storeWithStatus(resource, ctx, "IMAGE_SIZE_EXCEEDS_LIMIT");
            return;
        }

        // Check max source image memory usage constraint
        long estimatedMemoryUsage = estimateMemoryUsage(dim);
        if (logger.isDebugEnabled()) {
            logger.debug("Estimated memory usage for image " + path + " of " + dim.width + "x" + dim.height + " = "
                    + estimatedMemoryUsage + " bytes");
        }
        if (estimatedMemoryUsage > maxSourceImageRawMemoryUsage) {
            logger.info("Estimated memory usage of image exceeds limit: " + path);
            storeWithStatus(resource, ctx, "MEMORY_USAGE_EXCEEDS_LIMIT");
            return;
        }
        
        BufferedImage image;
        try {
            image = ImageIO.read(repository.getInputStream(token, path, true));
        } catch (Exception e) {
            logger.warn("Failed to read image at " + path, e);
            storeWithStatus(resource, ctx, "CORRUPT");
            return;
        }
        if (image == null) {
            logger.warn("Failed to read image at " + path);
            storeWithStatus(resource, ctx, "CORRUPT");
            return;
        }

        String mimetype = resource.getContentType();
        String imageFormat = mimetype.substring(mimetype.lastIndexOf("/") + 1);

        if (!supportedFormats.contains(imageFormat.toLowerCase())) {
            logger.info("Unsupported format of image " + path + ": " + imageFormat);
            storeWithStatus(resource, ctx, "UNSUPPORTED_FORMAT");
            return;
        }

        if (image.getWidth() <= width) {
            if (logger.isDebugEnabled()) {
                logger.debug("Will not create thumbnail for image " + path + ": width less than/equal to " + width);
            }
            storeWithStatus(resource, ctx, "TOO_SMALL_FOR_THUMBNAIL");
            return;
        }

        BufferedImage thumbnail = ImageUtil.downscaleToWidth(image, width); // Potentially time consuming part
        String thumbnailFormat = "jpeg";
        if (imageFormat.equalsIgnoreCase("gif") || imageFormat.equalsIgnoreCase("png")) {
            thumbnailFormat = "png";
        }

        Property property = thumbnailPropDef.createProperty();
        property.setBinaryValue(ImageUtil.getImageBytes(thumbnail, thumbnailFormat), "image/" + thumbnailFormat);
        resource.addProperty(property);

        resource.removeProperty(mediaMetadataStatusPropDef);
        storeIfUnmodified(resource, ctx);
    }
    
    private void storeWithStatus(Resource resource, ExecutionContext ctx, String status) {
        Property statusProp = mediaMetadataStatusPropDef.createProperty();
        statusProp.setStringValue(status);
        resource.addProperty(statusProp);
        storeIfUnmodified(resource, ctx);
    }

    private void storeIfUnmodified(Resource resource, ExecutionContext ctx) {
        try {
            // To minimize potential race between initial load of resource and completed
            // thumbnail generation, we sanity check last modified time before storing.
            Resource current = ctx.getRepository().retrieve(ctx.getToken(), resource.getURI(), false);
            if (!current.getLastModified().equals(resource.getLastModified())) {
                logger.warn("Resource " + resource.getURI() 
                        + " was modified during image metadata extraction, skipping store.");
                return;
            }
            
            ctx.getRepository().store(ctx.getToken(), resource, ctx.getSystemChangeContext());
            if (resource.getProperty(thumbnailPropDef) != null) {
                logger.info("Created thumbnail for " + resource);
            }
        } catch (ResourceLockedException e) {
            logger.warn("Store of " + resource + " failed due to resource being locked.");
        } catch (ResourceNotFoundException e) {
            // ignore
        } catch (Exception e) {
            logger.warn("Exception while trying to store " + resource, e);
        }
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
     * {@link vtk.repository.content.ImageContentFactory}.
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
