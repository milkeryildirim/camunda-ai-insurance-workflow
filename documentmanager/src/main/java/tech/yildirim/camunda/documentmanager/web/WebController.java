package tech.yildirim.camunda.documentmanager.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Web controller for serving the main application pages.
 */
@Controller
public class WebController {

    /**
     * Serves the main index page.
     *
     * @return the index.html template
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}
