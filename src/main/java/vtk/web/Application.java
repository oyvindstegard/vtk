/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.web;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.DispatcherType;
import javax.servlet.Servlet;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.jetty.server.NetworkTrafficServerConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.web.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerPropertiesAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.jetty.JettyServerCustomizer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import vtk.cluster.AkkaClusterManager;
import vtk.util.Version;
import vtk.web.filter.StandardRequestFilter;
import vtk.web.servlet.VTKServlet;

@EnableWebMvc
@Import({
    AopAutoConfiguration.class,
    AopAutoConfiguration.JdkDynamicAutoProxyConfiguration.class,
    DispatcherServletAutoConfiguration.class,
    ServerPropertiesAutoConfiguration.class
})
public class Application  {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    private static final String GRACEFUL_ARG = "--graceful";
    private static final int DEFAULT_GRACE_PERIOD = 15;

    @Bean(name="dispatcherServlet")
    public Servlet dispatcherServlet() {
        VTKServlet servlet = new VTKServlet();
        return servlet;
    }

    @Bean(name="vtk.standardRequestFilter")
    public FilterRegistrationBean standardRequestFilter() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        StandardRequestFilter filter = new StandardRequestFilter();
        registration.setFilter(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        if (handleXForward()) {
            filter.setxForwardedFor(".*");
            filter.setxForwardedProto("X-Forwarded-Proto");
            filter.setxForwardedPort("X-Forwarded-Port");
        }
        registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        return registration;
    }

    @Bean(name="vtk.servletContainer")
    public EmbeddedServletContainerFactory containerFactory() throws UnknownHostException {

        HostPort[] listenAddrs = listenAddrs();

        if (listenAddrs.length == 0) {
            throw new IllegalStateException(
                    "No listen address configured. "
                  + " Please specify -Dvtk.listen=host1:port1,host2:port2:...");
        }

        final int maxThreads = maxThreads();
        final int minThreads = minThreads();
        final int idleTimeout = idleTimeout();

        JettyEmbeddedServletContainerFactory factory =
                new JettyEmbeddedServletContainerFactory();


        factory.setAddress(InetAddress.getByName(listenAddrs[0].addr));
        factory.setPort(listenAddrs[0].port);

        // Supports X-Forwarded-For and X-Forwarded-Proto, 
        // but not X-Forwarded-Port (or RFC 7239):
        //factory.setUseForwardHeaders(handleXForward());

        factory.addServerCustomizers(new JettyServerCustomizer() {
            @Override
            public void customize(Server server) {
                final QueuedThreadPool threadPool = server.getBean(QueuedThreadPool.class);
                threadPool.setMaxThreads(maxThreads);
                threadPool.setMinThreads(minThreads);
                threadPool.setIdleTimeout(idleTimeout);

                for (int i = 1; i < listenAddrs.length; i++) {
                    HostPort hp = listenAddrs[i];
                    NetworkTrafficServerConnector connector =
                            new NetworkTrafficServerConnector(server);
                    connector.setHost(hp.addr);
                    connector.setPort(hp.port);
                    server.addConnector(connector);
                }
            }
        });
        return factory;
    }

    protected final void run(String[] args) throws Exception {
        String[] springArgs = args;
        boolean gracefulRestart = false;
        int gracePeriodSeconds = DEFAULT_GRACE_PERIOD;

        for (int i=0; i < args.length; i++) {
            if (args[i].equals(GRACEFUL_ARG) || args[i].startsWith(GRACEFUL_ARG + "=")) {
                gracefulRestart = true;
                String[] parts = args[i].split("=");
                if (parts.length > 1) {
                    gracePeriodSeconds = Integer.parseInt(parts[1]);
                }
                springArgs = (String[])ArrayUtils.remove(args, i);
                break;
            }
        }

        List<Object> params = new ArrayList<>();
        params.addAll(mainXml());
        params.addAll(extensions());
        params.addAll(userBeanDefinitions());

        params.add(Application.class);

        SpringApplication app = new SpringApplication(params.toArray(new Object[params.size()]));
        app.addInitializers(new ApplicationContextInitializer<ConfigurableApplicationContext>() {

            @Override
            public void initialize(
                    ConfigurableApplicationContext applicationContext) {

                applicationContext.addBeanFactoryPostProcessor(new BeanFactoryPostProcessor() {

                    @Override
                    public void postProcessBeanFactory(
                            ConfigurableListableBeanFactory beanFactory)
                                    throws BeansException {
                        beanFactory.registerSingleton("vtk.configLocations", propertiesFiles());
                    }
                });
            }
        });

        app.setBanner(banner());
        app.setHeadless(true);
        if (gracefulRestart) {
            app.setRegisterShutdownHook(false);
        }
        ConfigurableApplicationContext applicationContext = app.run(springArgs);
        if (gracefulRestart) {
            Runtime.getRuntime().addShutdownHook(new GracefulShutdownHook(applicationContext, gracePeriodSeconds));
        }
    }


    protected HostPort[] listenAddrs() {
        String prop = System.getProperty("vtk.listen");
        if (prop == null) return new HostPort[0];
        List<HostPort> result = Arrays.stream(prop.split(","))
                .map(str -> HostPort.forString(str))
                .collect(Collectors.toList());

        return result.toArray(new HostPort[result.size()]);
    }
    
    protected int maxThreads() {
        String prop = System.getProperty("vtk.maxThreads");
        if (prop != null) {
            try {
                int n = Integer.parseInt(prop);
                if (n > 0) return n;
            }
            catch (Throwable t) {}
        }
        return 200;
    }
    
    protected int minThreads() {
        String prop = System.getProperty("vtk.minThreads");
        if (prop != null) {
            try {
                int n = Integer.parseInt(prop);
                if (n > 0) return n;
            }
            catch (Throwable t) {}
        }
        return 8;
    }
    
    protected int idleTimeout() {
        String prop = System.getProperty("vtk.idleTimeout");
        if (prop != null) {
            try {
                int n = Integer.parseInt(prop);
                if (n > 0) return n;
            }
            catch (Throwable t) {}
        }
        return 60000;
    }

    protected List<String> mainXml() {
        return Collections.singletonList("classpath:/vtk/beans/vhost/main.xml");
    }

    protected List<String> extensions() {
        String prop = System.getProperty("vtk.extensions");
        if (prop == null) return Collections.emptyList();
        List<String> result = Arrays.stream(prop.split(","))
                .map(ext -> "classpath:/vtk/beans/standard-extensions/" + ext + "/" + ext + ".xml")
                .collect(Collectors.toList());
        return result;
    }

    protected boolean handleXForward() {
        String prop = System.getProperty("vtk.xForward");
        if (prop == null) return false;
        return "true".equals(prop.trim().toLowerCase());
    }

    protected List<String> userBeanDefinitions() throws IOException {
        String prop = System.getProperty("vtk.beanLocations");
        if (prop == null) return Collections.emptyList();
        List<String> result = Arrays.stream(prop.split(","))
                .map(f-> {
                    try {
                        return "file://" + new File(f).getCanonicalPath();
                    }
                    catch (IOException e) {
                        throw new RuntimeException(e);
                    }})
                .collect(Collectors.toList());
        return result;
        //        File home = new File(System.getProperty("user.home"));
        //
        //        if (new File(home.getCanonicalPath() + File.separator + ".vrtx-context.xml").exists()) {
        //            params.add("file://${user.home}/.vrtx-context.xml");
        //        }
        //        if (new File(home.getCanonicalPath() + File.separator + ".vrtx.xml").exists()) {
        //            params.add("file://${user.home}/.vrtx.xml");
        //        }
    }


    protected List<String> propertiesFiles() {
        List<String> propertyFiles = new ArrayList<>();
        propertyFiles.addAll(defaultPropertiesFiles());
        propertyFiles.addAll(userPropertyFiles());
        return propertyFiles;
    }

    protected List<String> defaultPropertiesFiles() {
        List<String> result = new ArrayList<>();
        result.add("classpath:/vtk/beans/vtk.properties");
        result.add("classpath:/vtk/util/vtk_version.properties");
        result.add("classpath:/vtk/conf/*.properties");
        return result;
    }

    protected List<String> userPropertyFiles() {
        String prop = System.getProperty("vtk.configLocations");
        if (prop == null) return Collections.emptyList();
        List<String> result = Arrays.stream(prop.split(","))
                .map(f-> {
                    try {
                        return "file://" + new File(f).getCanonicalPath();
                    }
                    catch (IOException e) {
                        throw new RuntimeException(e);
                    }})
                .collect(Collectors.toList());
        return result;
    }

    protected Banner banner() {
        return new Banner() {

            @Override
            public void printBanner(Environment environment,
                    Class<?> sourceClass, PrintStream out) {
                out.println(bannerText());
            }
        };
    }

    protected String bannerText() {
        return  Version.getFrameworkTitle() 
                + " - " + Version.getVersion();
    }
    
    
    protected static final class HostPort {
        private String addr;
        private int port;

        private HostPort(String addr, int port) {
            this.addr = addr;
            this.port = port;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + addr + ", " + port + ")";
        }

        public static HostPort forString(String str) {
            try {
                int idx = str.indexOf(':');
                if (idx == -1)
                    throw new IllegalArgumentException("Expected ':' in input string");
                String host = str.substring(0, idx);
                int port = Integer.parseInt(str.substring(idx + 1));
                return new HostPort(host, port);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class GracefulShutdownHook extends Thread {
        private final ConfigurableApplicationContext applicationContext;
        private final int gracePeriodSeconds;

        GracefulShutdownHook(ConfigurableApplicationContext applicationContext, int gracePeriodSeconds) {
            this.applicationContext = applicationContext;
            this.gracePeriodSeconds = gracePeriodSeconds;
        }

        @Override
        public void run() {
            logger.info(String.format(
                    "Graceful shutdown initiated - waiting for %d seconds.",
                    gracePeriodSeconds
            ));

            ClusterStatus status = applicationContext.getBean(ClusterStatus.class);
            status.setDraining(true);
            try {
                Thread.sleep(gracePeriodSeconds * 1000);
                try {
                    AkkaClusterManager clusterManager = applicationContext.getBean(AkkaClusterManager.class);
                    Thread akkaShutdown = clusterManager.destroy();
                    akkaShutdown.join();
                } catch (BeansException e) {
                    //Akka cluster not activated
                }
            } catch (InterruptedException e) {
                logger.warn("Graceful shutdown interrupted");
            }
            applicationContext.close();
        }
    }
}
