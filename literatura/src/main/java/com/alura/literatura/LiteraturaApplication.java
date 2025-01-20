package com.alura.literatura;
import com.alura.literatura.Principal.Principal;
import com.alura.literatura.Service.ConsumoAPI;
import com.alura.literatura.Service.ConvierteDatos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;


@SpringBootApplication
public class LiteraturaApplication implements CommandLineRunner {

	private final Principal principal;


	public LiteraturaApplication(@Lazy Principal principal) {
		this.principal = principal;
	}



	public static void main(String[] args) {
		SpringApplication.run(LiteraturaApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {

	}

	@Bean
	public ConsumoAPI consumoAPI() {
		return new ConsumoAPI();
	}

	@Bean
	public ConvierteDatos conversor() {
		return new ConvierteDatos();
	}

	public Principal getPrincipal() {
		return principal;
	}
}



