package com.example.fridiet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FridietApplication {

	public static void main(String[] args) {
		SpringApplication.run(FridietApplication.class, args);
	}

}
