package dev.shop.inventory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages={"dev.shop.inventory","dev.shop.common"})
public class InventoryApplication { public static void main(String[] args) { SpringApplication.run(InventoryApplication.class,args); } }
