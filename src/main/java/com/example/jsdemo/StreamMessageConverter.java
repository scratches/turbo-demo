package com.example.jsdemo;

import java.io.IOException;
import java.util.List;

import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import com.example.servlet.ServerlessHttpServletRequest;
import com.example.servlet.ServerlessHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class StreamMessageConverter implements HttpMessageConverter<ModelAndView> {

	private ViewResolver resolver;

	private final ApplicationContext context;

	public StreamMessageConverter(ApplicationContext context) {
		this.context = context;
	}

	private void initialize() {
		if (this.resolver == null) {
			this.resolver = context.getBean("viewResolver", ViewResolver.class);
		}
	}

	@Override
	public boolean canRead(Class<?> clazz, MediaType mediaType) {
		throw new UnsupportedOperationException("Cannot read messages (write only)");
	}

	@Override
	public boolean canWrite(Class<?> clazz, MediaType mediaType) {
		return ModelAndView.class.isAssignableFrom(clazz);
	}

	@Override
	public List<MediaType> getSupportedMediaTypes() {
		return List.of(MediaType.TEXT_EVENT_STREAM);
	}

	@Override
	public ModelAndView read(Class<? extends ModelAndView> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {
		throw new UnsupportedOperationException("Write only");
	}

	@Override
	public void write(ModelAndView rendering, MediaType contentType, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {
		initialize();
		HttpServletRequest request = new ServerlessHttpServletRequest("GET", "/");
		ServerlessHttpServletResponse wrapper = new ServerlessHttpServletResponse();
		try {
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
			View view = resolver.resolveViewName(rendering.getViewName(), wrapper.getLocale());
			view.render(rendering.getModelMap(), request, wrapper);
			outputMessage.getBody().write(wrapper.getContentAsString().replace("\n", "\ndata:").getBytes());
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
