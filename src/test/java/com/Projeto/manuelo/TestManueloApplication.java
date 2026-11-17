package com.Projeto.manuelo;

import org.springframework.boot.SpringApplication;

public class TestManueloApplication {

	public static void main(String[] args) {
		SpringApplication.from(ManueloApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
