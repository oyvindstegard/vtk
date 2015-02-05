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
package vtk.repository.content;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

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

    private ApplicationContext applicationContext;
    private Map<Class<?>, ContentFactory<?>> contentFactories;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void afterPropertiesSet() {

        this.contentFactories = new HashMap<>();

        Collection<ContentFactory> contentFactoryBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(
                this.applicationContext, ContentFactory.class, false, false).values();

        for (ContentFactory factory : contentFactoryBeans) {
            Class<?> factoryType = factory.getRepresentationType();
            contentFactories.put(factoryType, factory);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <T> T createRepresentation(Class<T> clazz, InputStream content) throws Exception {

        ContentFactory factory = this.contentFactories.get(clazz);

        if (factory != null) {
            return (T) factory.getContentRepresentation(content);
        }

        // The default representations:
        if (clazz == byte[].class) {
            return (T)getContentAsByteArray(content);
        } else if (clazz == java.nio.ByteBuffer.class) {
            return (T)ByteBuffer.wrap(getContentAsByteArray(content));
        }

        throw new UnsupportedContentRepresentation("Content type '" + clazz.getName() + "' not supported.");
    }

    private static byte[] getContentAsByteArray(InputStream content) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        int n;
        byte[] buffer = new byte[5000];
        while ((n = content.read(buffer, 0, buffer.length)) != -1) {
            bout.write(buffer, 0, n);
        }

        return bout.toByteArray();
    }

}
