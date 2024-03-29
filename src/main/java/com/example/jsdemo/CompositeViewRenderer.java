package com.example.jsdemo;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.jsdemo.ServletUtils.ServerlessHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class CompositeViewRenderer implements HandlerMethodReturnValueHandler, WebMvcConfigurer {

	private static Log logger = LogFactory.getLog(CompositeViewRenderer.class);

	private ViewResolver resolver;

	private final ApplicationContext context;

	public CompositeViewRenderer(ApplicationContext context) {
		this.context = context;
	}

	@Override
	public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
		handlers.add(this);
	}

	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		initialize();
		if (List.class.isAssignableFrom(returnType.getParameterType())) {
			if (ModelAndView.class
					.isAssignableFrom(ResolvableType.forMethodParameter(returnType).getGeneric().resolve())) {
				return true;
			}
		}
		return false;
	}

	private void initialize() {
		if (this.resolver == null) {
			this.resolver = context.getBean("viewResolver", ViewResolver.class);
		}
	}

	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
		String[] methodAnnotation = returnType
				.getMethodAnnotation(RequestMapping.class).produces();
		MediaType type = methodAnnotation.length > 0 ? MediaType.valueOf(methodAnnotation[0]) : MediaType.TEXT_HTML;
		var response = webRequest.getNativeResponse(HttpServletResponse.class);
		var request = webRequest.getNativeRequest(HttpServletRequest.class);
		if (response.getContentType() == null) {
			response.setContentType(type.toString());
		}
		@SuppressWarnings("unchecked")
		List<ModelAndView> renderings = resolve(request, response, (List<ModelAndView>) returnValue);
		mavContainer.setView(new CompositeView(renderings));
	}

	private List<ModelAndView> resolve(HttpServletRequest request, HttpServletResponse response,
			List<ModelAndView> renderings) {
		for (ModelAndView rendering : renderings) {
			try {
				resolve(request, response, rendering);
			} catch (Exception e) {
				logger.error("Failed to resolve view", e);
			}
		}
		return renderings;
	}

	private void resolve(HttpServletRequest request, HttpServletResponse response, ModelAndView rendering)
			throws Exception {
		View view = null;
		if (rendering.getView() instanceof View) {
			view = (View) rendering.getView();
		} else {
			Locale locale = response.getLocale();
			if (locale == null) {
				locale = Locale.getDefault();
			}
			// Make sure HTML is always producible
			request.setAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE, Set.of(MediaType.TEXT_HTML));
			view = resolver.resolveViewName((String) rendering.getViewName(), locale);
		}
		rendering.setView(view);
	}

	static class CompositeView implements View {

		private List<ModelAndView> renderings;

		public CompositeView(List<ModelAndView> renderings) {
			this.renderings = renderings;
		}

		@Override
		public void render(@Nullable Map<String, ?> model, HttpServletRequest request, HttpServletResponse response)
				throws Exception {
			StringBuilder content = new StringBuilder();
			for (ModelAndView rendering : renderings) {
				ServerlessHttpServletResponse wrapper = new ServerlessHttpServletResponse();
				rendering.getView().render(rendering.getModel(), request, wrapper);
				content.append(wrapper.getContentAsString());
				content.append("\n\n");
			}
			response.getWriter().write(content.toString());
		}

	}
}