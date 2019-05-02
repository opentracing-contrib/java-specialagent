package io.opentracing.contrib.specialagent.spring.webmvc;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@EnableWebMvc
@Configuration
@Import({TestController.class})
public class SpringMVCConfiguration extends WebMvcConfigurerAdapter implements
    ServletContextListener {

  @Override
  public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
    configurer.enable();
  }

  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {

  }

  @Override
  public void contextDestroyed(ServletContextEvent servletContextEvent) {

  }
}

