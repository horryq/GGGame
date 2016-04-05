package com.gg.example.user;

import java.io.IOException;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

import com.gg.common.Constants;
import com.gg.core.harbor.GGHarbor;
import com.gg.example.common.ExampleConst;

import io.grpc.internal.ServerImpl;

@SpringBootApplication
public class UserServiceApp {
	private static final Logger logger = LoggerFactory.getLogger(UserServiceApp.class);

	public static void init(ApplicationContext ctx) throws BeansException, IOException {
		ServerImpl server = GGHarbor.start(ctx, ExampleConst.UserService, Constants.Localhost, ExampleConst.UserServicePort, Executors.newSingleThreadExecutor());

		while (!server.isShutdown()) {
			try {
				server.awaitTermination();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) throws BeansException, IOException {
		try (ConfigurableApplicationContext ctx = SpringApplication.run(UserServiceApp.class, args)) {
			ctx.addApplicationListener(new ApplicationListener<ApplicationEvent>() {
				@Override
				public void onApplicationEvent(ApplicationEvent event) {
					logger.info("Shutdown...");
				}
			});
			init(ctx);
		}
	}
}