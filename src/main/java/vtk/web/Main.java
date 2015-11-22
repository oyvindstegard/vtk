package vtk.web;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.Servlet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ErrorMvcAutoConfiguration;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.web.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import vtk.web.Main.VTKConfiguration;
import vtk.web.servlet.VTKServlet;

@Configuration
@EnableAutoConfiguration(
   exclude={
        DataSourceAutoConfiguration.class,
        ErrorMvcAutoConfiguration.class
   })
@EnableWebMvc
@ComponentScan(basePackageClasses = { VTKConfiguration.class })
public class Main extends SpringBootServletInitializer {

    public static class VTKConfiguration extends WebMvcConfigurationSupport { }

    @Bean
    public Servlet dispatcherServlet() {
        VTKServlet servlet = new VTKServlet();
        return servlet;
    }

    @Component
    public static class CustomizationBean implements EmbeddedServletContainerCustomizer {

        public CustomizationBean() { }

        public void init() {}

        @Override
        public void customize(ConfigurableEmbeddedServletContainer container) {
            container.setPort(9322);
        }
    }

    public static void main(String[] args) throws IOException {
        List<Object> params = new ArrayList<>();
        params.add("classpath:/vtk/beans/vhost/main.xml");

        File home = new File(System.getProperty("user.home"));

        if (new File(home.getCanonicalPath() + File.separator + ".vrtx-context.xml").exists()) {
            params.add("file://${user.home}/.vrtx-context.xml");
        }
        if (new File(home.getCanonicalPath() + File.separator + ".vrtx.xml").exists()) {
            params.add("file://${user.home}/.vrtx.xml");
        }
        params.add(Main.class);
        SpringApplication.run(params.toArray(new Object[params.size()]), args);
    }

}
