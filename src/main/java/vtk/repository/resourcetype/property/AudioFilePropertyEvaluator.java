/* Copyright (c) 2007, University of Oslo, Norway
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
package vtk.repository.resourcetype.property;

import javax.sound.sampled.AudioFileFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.PropertyEvaluationContext.Type;
import vtk.repository.resourcetype.PropertyEvaluator;

/**
 * XXX this evaluator is not able to extract much interesting since it's using
 * javax.sound and without any interesting audio SPI impls available. Likely, it
 * can be discarded entirely, since we should probably avoid extracting data
 * from complex multimedia formats in-process. Better to use external system for
 * audio file storage and metadata extraction in the future. (For simpler
 * installations, an out of process ffmpeg-based metadata-extractor can be created,
 * but we won't do it in the near future.)
 */
public class AudioFilePropertyEvaluator implements PropertyEvaluator {

    private final Logger logger = LoggerFactory.getLogger(AudioFilePropertyEvaluator.class);

    private String audioFileProperty;

    public void setAudioFileProperty(String audioFileProperty) {
        this.audioFileProperty = audioFileProperty;
    }

    @Override
    public boolean evaluate(Property property, PropertyEvaluationContext ctx) throws PropertyEvaluationException {
        if (ctx.getEvaluationType() != Type.ContentChange && ctx.getEvaluationType() != Type.Create) {
            return false;
        }

        AudioFileFormat audioFileFormat = null;

        try {
            audioFileFormat = ctx.getContent().getContentRepresentation(AudioFileFormat.class);
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Unable to get AudioFileFormat representation of content", e);
            }
            return false;
        }

        String propertyValue = (String) audioFileFormat.properties()
                .get(this.audioFileProperty);

        if (propertyValue == null) {
            return false;
        }
        property.setStringValue(propertyValue);
        return true;
    }

}
