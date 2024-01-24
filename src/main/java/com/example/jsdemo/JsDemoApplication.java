package com.example.jsdemo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextScheduledExecutorService;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import jakarta.annotation.PostConstruct;

@SpringBootApplication
@Controller
public class JsDemoApplication {

	@Bean
	public BeanPostProcessor foo() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
				if (bean instanceof RequestMappingHandlerAdapter) {
					((RequestMappingHandlerAdapter) bean).setTaskExecutor(threadPoolTaskScheduler());
				}
				return bean;
			}
		};
	
	}

	@GetMapping("/user")
	@ResponseBody
	public Map<String, Object> user() {
		Map<String, Object> map = new HashMap<>();
		map.put("name", "Fred");
		return map;
	}

	@GetMapping("/pops")
	@ResponseBody
	public Chart bar() {
		return new Chart();
	}

	@PostMapping(path = "/test", produces = "text/vnd.turbo-stream.html")
	public List<ModelAndView> test() {
		return List.of(new ModelAndView("test").addObject("id", "hello").addObject("value", "Hello"),
				new ModelAndView("test").addObject("id", "world").addObject("value", "World"));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAsync
	static class AsyncConfig implements AsyncConfigurer, WebMvcConfigurer {
		@Override
		public Executor getAsyncExecutor() {
			return ContextExecutorService.wrap(Executors.newCachedThreadPool(), ContextSnapshot::captureAll);
		}

		@Override
		public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
			configurer.setTaskExecutor(new SimpleAsyncTaskExecutor(
					r -> new Thread(ContextSnapshotFactory.builder().build().captureAll().wrap(r))));
		}
	}

	@Bean(name = "taskExecutor", destroyMethod = "shutdown")
	static ThreadPoolTaskScheduler threadPoolTaskScheduler() {
		ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler() {
			@Override
			protected ExecutorService initializeExecutor(ThreadFactory threadFactory,
					RejectedExecutionHandler rejectedExecutionHandler) {
				ExecutorService executorService = super.initializeExecutor(threadFactory, rejectedExecutionHandler);
				return ContextExecutorService.wrap(executorService, ContextSnapshot::captureAll);
			}

			@Override
			public ScheduledExecutorService getScheduledExecutor() throws IllegalStateException {
				return ContextScheduledExecutorService.wrap(super.getScheduledExecutor());
			}
		};
		threadPoolTaskScheduler.initialize();
		return threadPoolTaskScheduler;
	}

	public static void main(String[] args) {
		SpringApplication.run(JsDemoApplication.class, args);
	}

	public static class Chart {
		public Data data = new Data();
		public Options options = new Options();

		public static class Data {
			public List<String> labels = Arrays.asList("Africa", "Asia", "Europe", "Latin America", "North America");
			public List<Map<String, Object>> datasets = Arrays.asList(Map.of("label", "Population (millions)", //
					"backgroundColor", Arrays.asList("#3e95cd", "#8e5ea2", "#3cba9f", "#e8c3b9", "#c45850"), //
					"data", Arrays.asList(2478, 5267, 734, 784, 433)));
		};

		public static class Options {
			public Map<String, Object> plugins = new HashMap<>();
			{
				plugins.put("legend", Map.of("display", false));
				plugins.put("title", Map.of("display", true, "text", "Predicted world population (millions) in 2050"));
			}
		};
	}

	static class Greeting {
		private String value;

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

}