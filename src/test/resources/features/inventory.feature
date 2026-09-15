Feature: Reliable stock management through SOAP
  A warehouse operator needs orders to reserve stock consistently
  so that customers cannot buy units that are unavailable.

  Scenario: Place an order with sufficient stock
    Given a product with 10 available units
    When I order 3 units through SOAP
    Then the order is confirmed
    And the available stock is 7

  Scenario: Reject an order without enough stock
    Given a product with 2 available units
    When I order 3 units through SOAP
    Then a SOAP fault reports "INSUFFICIENT_STOCK"
    And the available stock is 2

  Scenario: Reject a non-positive order quantity
    Given a product with 10 available units
    When I order 0 units through SOAP
    Then a SOAP fault reports "INVALID_INPUT"
    And the available stock is 10

  Scenario: Repeated cancellation restores stock only once
    Given a product with 10 available units
    When I order 3 units through SOAP
    And I cancel the order twice through SOAP
    Then the order is cancelled
    And the available stock is 10

  Scenario: Restock through the integration service
    Given a product with 2 available units
    When I restock 5 units through SOAP
    Then the available stock is 7
