package com.example.springsoap.soap;

import com.example.springsoap.contract.*;
import com.example.springsoap.model.Models;
import com.example.springsoap.service.InventoryService;
import org.springframework.ws.server.endpoint.annotation.*;

@Endpoint
public class InventoryEndpoint {
  public static final String NS = "https://stockbridge.example/inventory/v1";
  private final InventoryService service;

  public InventoryEndpoint(InventoryService service) {
    this.service = service;
  }

  private Product product(Models.Product p) {
    var r = new Product();
    r.setSku(p.sku());
    r.setName(p.name());
    r.setCategory(p.category());
    r.setPrice(p.price());
    r.setQuantity(p.quantity());
    r.setReorderLevel(p.reorderLevel());
    return r;
  }

  private Order order(Models.Order o) {
    var r = new Order();
    r.setId(o.id());
    r.setCustomer(o.customer());
    r.setStatus(o.status());
    r.setTotal(o.total());
    r.setCreatedAt(o.createdAt().toString());
    for (var l : o.lines()) {
      var line = new OrderLine();
      line.setSku(l.sku());
      line.setName(l.name());
      line.setQuantity(l.quantity());
      line.setUnitPrice(l.unitPrice());
      r.getLines().add(line);
    }
    return r;
  }

  @PayloadRoot(namespace = NS, localPart = "listProductsRequest")
  @ResponsePayload
  public ListProductsResponse list(@RequestPayload ListProductsRequest request) {
    var r = new ListProductsResponse();
    service.products().forEach(p -> r.getProducts().add(product(p)));
    return r;
  }

  @PayloadRoot(namespace = NS, localPart = "getProductRequest")
  @ResponsePayload
  public GetProductResponse get(@RequestPayload GetProductRequest request) {
    var r = new GetProductResponse();
    r.setProduct(product(service.product(request.getSku())));
    return r;
  }

  @PayloadRoot(namespace = NS, localPart = "createProductRequest")
  @ResponsePayload
  public CreateProductResponse create(@RequestPayload CreateProductRequest request) {
    var p = request.getProduct();
    var r = new CreateProductResponse();
    r.setProduct(
        product(
            service.create(
                new Models.NewProduct(
                    p.getSku(),
                    p.getName(),
                    p.getCategory(),
                    p.getPrice(),
                    p.getQuantity(),
                    p.getReorderLevel()))));
    return r;
  }

  @PayloadRoot(namespace = NS, localPart = "restockProductRequest")
  @ResponsePayload
  public RestockProductResponse restock(@RequestPayload RestockProductRequest request) {
    var r = new RestockProductResponse();
    r.setProduct(product(service.restock(request.getSku(), request.getQuantity())));
    return r;
  }

  @PayloadRoot(namespace = NS, localPart = "placeOrderRequest")
  @ResponsePayload
  public PlaceOrderResponse place(@RequestPayload PlaceOrderRequest request) {
    var r = new PlaceOrderResponse();
    r.setOrder(
        order(
            service.place(
                new Models.NewOrder(
                    request.getCustomer(),
                    request.getLines().stream()
                        .map(l -> new Models.LineRequest(l.getSku(), l.getQuantity()))
                        .toList()))));
    return r;
  }

  @PayloadRoot(namespace = NS, localPart = "getOrderRequest")
  @ResponsePayload
  public GetOrderResponse getOrder(@RequestPayload GetOrderRequest request) {
    var r = new GetOrderResponse();
    r.setOrder(order(service.order(request.getId())));
    return r;
  }

  @PayloadRoot(namespace = NS, localPart = "cancelOrderRequest")
  @ResponsePayload
  public CancelOrderResponse cancel(@RequestPayload CancelOrderRequest request) {
    var r = new CancelOrderResponse();
    r.setOrder(order(service.cancel(request.getId())));
    return r;
  }
}
