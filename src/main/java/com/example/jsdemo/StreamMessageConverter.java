package com.example.jsdemo;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.jsdemo.ServletUtils.ServerlessHttpServletResponse;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import reactor.core.publisher.Hooks;

@Component
public class StreamMessageConverter
implements HttpMessageConverter<ModelAndView>, WebMvcConfigurer, HandlerInterceptor {
	
	private ViewResolver resolver;
	
	private final ApplicationContext context;
	
	public StreamMessageConverter(ApplicationContext context) {
		Hooks.enableAutomaticContextPropagation();
		ContextRegistry.getInstance().registerThreadLocalAccessor(new RequestContextHolderAccessor());
		this.context = context;
	}

	private void initialize() {
		if (this.resolver == null) {
			this.resolver = context.getBean("viewResolver", ViewResolver.class);
		}
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		return true;
	}

	class RequestContextHolderAccessor implements ThreadLocalAccessor<RequestAttributes> {

		private Log log  = LogFactory.getLog(getClass());

		@Override
		public Object key() {
			return RequestAttributes.class.getName();
		}

		@Override
		public RequestAttributes getValue() {
			log.info("Getting value: " + RequestContextHolder.getRequestAttributes());
			return RequestContextHolder.getRequestAttributes();
		}

		@Override
		public void setValue(RequestAttributes value) {
			log.info("Setting value: " + RequestContextHolder.getRequestAttributes() + ", " + value);
			RequestContextHolder.setRequestAttributes(value);
		}

		@Override
		public void setValue() {
			log.info("Resetting value: " + RequestContextHolder.getRequestAttributes());
			//RequestContextHolder.resetRequestAttributes();
		}

	}

	@Override
	public boolean canRead(Class<?> clazz, MediaType mediaType) {
		return false;
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
		HttpServletRequest request = getRequest();
		ServerlessHttpServletResponse wrapper = new ServerlessHttpServletResponse();
		try {
			View view = resolver.resolveViewName(rendering.getViewName(), wrapper.getLocale());
			view.render(rendering.getModelMap(), request, wrapper);
			outputMessage.getBody().write(wrapper.getContentAsString().replace("\n", "\ndata:").getBytes());
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private HttpServletRequest getRequest() {
		extractThreadLocals();
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();
		// Make sure HTML is always producible
		attrs.setAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE, Set.of(MediaType.TEXT_HTML),
				RequestAttributes.SCOPE_REQUEST);
		return request;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void extractThreadLocals() {
		// for (ThreadLocalAccessor accessor : ContextRegistry.getInstance().getThreadLocalAccessors()) {
		// 	Object value = accessor.getValue();
		// 	if (value != null) {
		// 		accessor.setValue(value);
		// 	}
		// }
	}

}
