package com.Projeto.manuelo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.spring.annotation.EnableVaadin;

@SpringBootApplication(scanBasePackages = { "com.Projeto.manuelo", "br.com.manuelo" })
@EnableVaadin("br.com.manuelo")
public class ManueloApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManueloApplication.class, args);
    }
}
