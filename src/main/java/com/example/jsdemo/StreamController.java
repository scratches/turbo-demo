package com.example.jsdemo;

import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import reactor.core.publisher.Flux;

@Controller
public class StreamController {

	@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ModelAndView> stream() {
		return Flux.interval(Duration.ofSeconds(5)).map(value -> new ModelAndView("time").addObject("value", value)
				.addObject("time", System.currentTimeMillis()));
	}
}
