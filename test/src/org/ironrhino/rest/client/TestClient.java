package org.ironrhino.rest.client;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@RestApi(restTemplate = "restTemplate")
public interface TestClient {

	@RequestMapping(value = "/echo", method = RequestMethod.POST)
	String echo(@RequestParam String name);

}
