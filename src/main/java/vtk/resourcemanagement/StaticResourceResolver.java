/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.resourcemanagement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import vtk.repository.Path;
import vtk.util.web.LinkTypesPrefixes;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StaticResourceResolver implements InitializingBean, ApplicationContextAware {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Map<Path, String> locationsMap = new HashMap<>();
    private ApplicationContext applicationContext;

    public Resource resolve(Path path) {
        List<Path> paths = path.getPaths();
        Path uriPrefix = null;
        String resourceLocation = null;
        for (int i = paths.size() - 1; i >= 0; i--) {
            Path prefix = paths.get(i);
            if (this.locationsMap.containsKey(prefix)) {
                resourceLocation = this.locationsMap.get(prefix);
                uriPrefix = prefix;
            }
        }

        if (resourceLocation == null) {
            return null;
        }

        Path uri = Path.fromString(path.toString());
        if (uriPrefix != null) {
            Path p = Path.ROOT;
            int offset = uriPrefix.getDepth() + 1;
            List<String> elements = uri.getElements();
            for (int i = offset; i < elements.size(); i++) {
                p = p.extend(elements.get(i));
            }
            uri = p;
        }

        String loc = resourceLocation;
        if (loc.endsWith("/")) {
            loc = loc.substring(0, loc.length() - 1);
        }
        loc += uri;

        if (loc.startsWith(LinkTypesPrefixes.FILE + "//")) {
            String actualPath = loc.substring((LinkTypesPrefixes.FILE + "//").length());
            return new FileSystemResource(actualPath);
        }

        if (loc.startsWith("classpath://")) {
            String actualPath = loc.substring("classpath://".length());
            return new ClassPathResource(actualPath);
        }
        return null;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Map<String, StaticResourceLocation> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(
                this.applicationContext, StaticResourceLocation.class, true, false);
        Collection<StaticResourceLocation> allLocations = matchingBeans.values();

        for (StaticResourceLocation location : allLocations) {
            Path uri = location.getPrefix();
            String resourceLocation = location.getResourceLocation();
            this.locationsMap.put(uri, resourceLocation);
        }
        logger.debug("Locations map: " + this.locationsMap);
    }
}
