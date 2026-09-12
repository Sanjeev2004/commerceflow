package dev.shop.product;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages={"dev.shop.product","dev.shop.common"})
public class ProductApplication { public static void main(String[] args) { SpringApplication.run(ProductApplication.class,args); } }
