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
package vtk.repository.resourcetype;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.util.text.PathMappingConfig;
import vtk.util.text.PathMappingConfig.Entry;

/**
 * A resource assertion which supports a typical "deny/allow" policy for paths
 * based on a dynamically reloadable path mapping configuration file stored in the
 * repository.
 *
 * <p>For any given path, if the path is ALLOWed according to configuration,
 * the assertion/predicate will match, otherwise it will not match.
 *
 * <p>It also acts as a functional
 * {@link java.util.function.Predicate predicate} on resource paths.
 *
 * <p>The path mapping configuration file must map paths to either value "DENY" or "ALLOW", with
 * the usual override rules.
 *
 * <p>If no match is found in config, a default policy is used, which is configurable as well.
 *
 */
public class ConfigurablePathPolicyAssertion implements RepositoryAssertion, Predicate<Path> {

    private Path configurationFile;
    private Repository repository;
    private Policy defaultPolicy = Policy.ALLOW;
    private volatile PathMappingConfig<Policy> config = null;

    private final Logger logger = LoggerFactory.getLogger(ConfigurablePathPolicyAssertion.class.getName());

    @Override
    public boolean matches(Optional<Resource> resource, Optional<Principal> principal) {
        if (!resource.isPresent()) {
            return false;
        }
        return test(resource.get().getURI());
    }

    @Override
    public boolean test(Path path) {
        Policy p = defaultPolicy;
        if (config != null) {
            List<Entry<Policy>> entries = config.getMatchAncestor(path);
            if (entries != null) {
                Entry<Policy> e = entries.get(entries.size()-1);
                p = e.value;
            }
        }
        return p == Policy.ALLOW;
    }

    public enum Policy {
        ALLOW, DENY;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Required
    public void setConfigurationFile(String uri) {
        this.configurationFile = Path.fromString(uri);
    }

    /**
     * Default is: {@link Policy#ALLOW}.
     * @param defaultPolicy
     */
    public void setDefaultPolicy(Policy defaultPolicy)  {
        this.defaultPolicy = Objects.requireNonNull(defaultPolicy);
    }

    public void loadConfiguration() {
        try (InputStream is = repository.getInputStream(null, configurationFile, true)){
            this.config = new PathMappingConfig<>(is, v -> {
                if ("allow".equalsIgnoreCase(v)) {
                    return Policy.ALLOW;
                } else if ("deny".equalsIgnoreCase(v)) {
                    return Policy.DENY;
                } else {
                    throw new IllegalStateException("Do not understand policy value: " + v);
                }
            });
        } catch (Throwable t) {
            logger.warn("Failed to load policy configuration file "+ configurationFile + ": " + t.getMessage());
        }
    }

}