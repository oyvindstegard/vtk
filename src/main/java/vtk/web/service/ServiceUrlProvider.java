package vtk.web.service;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.util.repository.LocaleHelper;

import java.util.*;

public class ServiceUrlProvider implements ApplicationContextAware {
    private ApplicationContext context;
    private Map<String, String> defaultUrlMap;

    public ServiceUrlBuilder builder(String serviceName) {
        try {
            return new ServiceUrlBuilder((Service) context.getBean(serviceName));
        } catch (NoSuchBeanDefinitionException e) {
            String serviceUrl = defaultUrlMap.get(serviceName);
            if (serviceUrl == null) throw e;
            return new ServiceUrlBuilder(serviceUrl);
        }
    }

    public void setDefaultUrlMap(Map<String, String> defaultUrlMap) {
        this.defaultUrlMap = defaultUrlMap;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    public static class ServiceUrlBuilder {
        private final Service service;
        private URL url;
        private Resource resource = null;
        private Principal principal = null;
        private Map<String, List<String>> parameters = new HashMap<>();
        private boolean matchAssertions = true;
        private Path path = Path.ROOT;
        private Locale locale = null;
        private String localeKey = "lang";

        private ServiceUrlBuilder(Service service) {
            this.service = service;
            this.url = null;
        }

        private ServiceUrlBuilder(String serviceUrl) {
            this.service = null;
            this.url = URL.parse(serviceUrl);
        }

        public ServiceUrlBuilder withResource(Resource resource) {
            this.resource = resource;
            return this;
        }

        public ServiceUrlBuilder withPrincipal(Principal principal) {
            this.principal = principal;
            return this;
        }

        public ServiceUrlBuilder withParameters(Map<String, List<String>> parameters) {
            this.parameters.putAll(parameters);
            return this;
        }

        public ServiceUrlBuilder withParameter(String key, String ...values) {
            this.parameters.put(key, Arrays.asList(values));
            return this;
        }

        public ServiceUrlBuilder withLocaleParameter(String key, Locale locale) {
            this.locale = locale;
            this.localeKey = key;
            return this;
        }

        public ServiceUrlBuilder withMatchAssertions(boolean matchAssertions) {
            this.matchAssertions = matchAssertions;
            return this;
        }

        public ServiceUrlBuilder withPath(Path path) {
            this.path = path;
            return this;
        }

        public URL build() {
            if (url == null) {
                if (resource == null)
                    url = service.constructURL(path);
                else
                    url =  service.constructURL(resource, principal, matchAssertions);
            }
            for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
                for (String value : entry.getValue()) {
                    url.addParameter(entry.getKey(), value);
                }
            }
            if (locale != null) {
                String preferredLang = LocaleHelper.getPreferredLang(locale);
                if (preferredLang != null) {
                    url.addParameter(this.localeKey, preferredLang);
                }
            }
            return url;
        }
    }
}
