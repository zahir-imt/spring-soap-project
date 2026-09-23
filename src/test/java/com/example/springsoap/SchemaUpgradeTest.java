package com.example.springsoap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class SchemaUpgradeTest {
  @Test
  void upgradeKeepsExistingProductsOrdersAndMovements() {
    var source = new DriverManagerDataSource("jdbc:h2:mem:migration;DB_CLOSE_DELAY=-1", "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("legacy/schema-v1.sql")).execute(source);
    var db = new JdbcTemplate(source);
    db.update("INSERT INTO products VALUES ('LEGACY','Existing product','Old',10.00,7,3)");
    db.update(
        "INSERT INTO customer_orders VALUES ('legacy-order','Existing"
            + " customer','CONFIRMED',20.00,CURRENT_TIMESTAMP)");
    db.update(
        "INSERT INTO order_lines(order_id,sku,name,quantity,unit_price) VALUES"
            + " ('legacy-order','LEGACY','Existing product',2,10.00)");
    db.update(
        "INSERT INTO stock_movements(sku,delta,balance,reason,created_at) VALUES"
            + " ('LEGACY',-2,7,'Existing movement',CURRENT_TIMESTAMP)");
    var upgrade = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
    upgrade.execute(source);
    upgrade.execute(source);
    assertThat(db.queryForObject("SELECT quantity FROM products WHERE sku='LEGACY'", Integer.class))
        .isEqualTo(7);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM customer_orders", Integer.class))
        .isEqualTo(1);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM order_lines", Integer.class)).isEqualTo(1);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM stock_movements", Integer.class))
        .isEqualTo(1);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM order_request_lock", Integer.class))
        .isEqualTo(1);
  }
}
