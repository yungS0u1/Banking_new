package org.example.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HelloController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("active", "home");
        return "pages/home";
    }

    @GetMapping("/health")
    public String health(Model model) {
        model.addAttribute("active", "health");
        return "pages/health";
    }
}