package com.feilonglab.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 应用程序主启动类。
 * 启动和装配整个 Spring 容器及关联的批处理与 Web 服务组件。
 */
@SpringBootApplication
public class ZeroTraceSpringBootApplication {

	/**
	 * 应用程序的 main 执行入口。
	 * 启动时支持解析命令行参数，控制是否以 Web 应用程序模式或单纯的命令行/批处理模式启动。
	 *
	 * @param args 命令行传入的参数数组
	 */
	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(ZeroTraceSpringBootApplication.class);

		// 如果是命令行触发批处理任务，则不启动 Tomcat Web 容器，执行完即退出
		for (String arg : args) {
			if ("--run-batch-job".equals(arg)) {
				app.setWebApplicationType(WebApplicationType.NONE);
				break;
			}
		}

		app.run(args);
	}
}
