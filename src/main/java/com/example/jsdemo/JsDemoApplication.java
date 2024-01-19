package com.example.jsdemo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@SpringBootApplication
@Controller
public class JsDemoApplication {

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