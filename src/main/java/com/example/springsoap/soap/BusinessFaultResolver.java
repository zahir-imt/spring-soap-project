package com.example.springsoap.soap;

import com.example.springsoap.service.BusinessException;
import javax.xml.namespace.QName;
import org.springframework.stereotype.Component;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;
import org.springframework.ws.soap.server.endpoint.SoapFaultMappingExceptionResolver;

@Component
public class BusinessFaultResolver extends SoapFaultMappingExceptionResolver {
  public BusinessFaultResolver() {
    setOrder(0);
  }

  @Override
  protected SoapFaultDefinition getFaultDefinition(Object endpoint, Exception e) {
    var d = new SoapFaultDefinition();
    d.setFaultCode(
        e instanceof BusinessException ? SoapFaultDefinition.CLIENT : SoapFaultDefinition.SERVER);
    d.setFaultStringOrReason(
        e instanceof BusinessException ? e.getMessage() : "The request could not be completed.");
    return d;
  }

  @Override
  protected void customizeFault(Object endpoint, Exception e, SoapFault fault) {
    String code = e instanceof BusinessException b ? b.getCode() : "SERVER_ERROR";
    fault
        .addFaultDetail()
        .addFaultDetailElement(new QName("https://stockbridge.example/inventory/v1", "code"))
        .addText(code);
  }
}
