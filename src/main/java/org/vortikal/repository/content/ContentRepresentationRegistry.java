/* Copyright (c) 2006, University of Oslo, Norway
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
package org.vortikal.repository.content;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.vortikal.util.io.StreamUtil;

/**
 * A resource content representation registry.
 * 
 * <p>
 * Looks up registered {@link ContentFactory} beans in the application context
 * upon initialization, recording their registered content representations.
 * 
 * <p>
 * Also contains default representations for the classes <code>byte[]</code> and
 * <code>java.nio.ByteBuffer</code>.
 */
public class ContentRepresentationRegistry implements ApplicationContextAware, InitializingBean {

    private Log logger = LogFactory.getLog(this.getClass());

    private ApplicationContext applicationContext;
    private Map<Class<?>, ContentFactory> contentFactories;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void afterPropertiesSet() {

        this.contentFactories = new HashMap<Class<?>, ContentFactory>();

        Collection<ContentFactory> contentFactoryBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(
                this.applicationContext, ContentFactory.class, false, false).values();

        for (ContentFactory factory : contentFactoryBeans) {
            Class<?>[] supportedClasses = factory.getRepresentationClasses();
            for (Class<?> supportedClass : supportedClasses) {
                this.logger.info("Registering content factory for class '" + supportedClass + "': " + factory);

                this.contentFactories.put(supportedClass, factory);
            }
        }
    }

    public Object createRepresentation(Class<?> clazz, InputStream content) throws Exception {

        ContentFactory factory = this.contentFactories.get(clazz);

        if (factory != null) {
            return factory.getContentRepresentation(clazz, content);
        }

        // The default representations:
        if (clazz == byte[].class) {
            return StreamUtil.readInputStream(content);
        } else if (clazz == java.nio.ByteBuffer.class) {
            return ByteBuffer.wrap(StreamUtil.readInputStream(content));
        }

        throw new UnsupportedContentRepresentationException("Content type '" + clazz.getName() + "' not supported.");

    }

}
