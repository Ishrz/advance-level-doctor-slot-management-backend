package com.doctor.slot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DoctorSlotApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(DoctorSlotApiApplication.class, args);
	}

}
