package org.ironrhino.core.freemarker;

import java.io.IOException;
import java.util.Locale;

import freemarker.template.Configuration;
import freemarker.template.Template;

public interface FallbackTemplateProvider {

	void setConfiguration(Configuration configuration);

	Template getTemplate(String name, Locale locale, String encoding, boolean parse) throws IOException;

}
