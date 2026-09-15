package com.example.springsoap.config;

import java.util.List;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.*;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadValidatingInterceptor;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.*;

@EnableWs
@Configuration
public class SoapConfig extends WsConfigurerAdapter {
  @Bean
  public ServletRegistrationBean<MessageDispatcherServlet> soapServlet(ApplicationContext context) {
    var servlet = new MessageDispatcherServlet();
    servlet.setApplicationContext(context);
    servlet.setTransformWsdlLocations(true);
    return new ServletRegistrationBean<>(servlet, "/ws/*");
  }

  @Bean
  public XsdSchema inventorySchema() {
    return new SimpleXsdSchema(new ClassPathResource("xsd/inventory.xsd"));
  }

  @Bean(name = "inventory")
  public DefaultWsdl11Definition wsdl(XsdSchema inventorySchema) {
    var wsdl = new DefaultWsdl11Definition();
    wsdl.setPortTypeName("InventoryPort");
    wsdl.setLocationUri("/ws");
    wsdl.setTargetNamespace("https://stockbridge.example/inventory/v1");
    wsdl.setSchema(inventorySchema);
    return wsdl;
  }

  @Override
  public void addInterceptors(List<EndpointInterceptor> interceptors) {
    var validation = new PayloadValidatingInterceptor();
    validation.setXsdSchema(inventorySchema());
    validation.setValidateRequest(true);
    validation.setValidateResponse(true);
    interceptors.add(validation);
  }
}
