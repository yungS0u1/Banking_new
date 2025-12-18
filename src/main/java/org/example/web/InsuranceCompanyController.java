package org.example.web;

import org.example.domain.InsuranceCompany;
import org.example.repo.InsuranceCompanyRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/insurers")
public class InsuranceCompanyController {

    private final InsuranceCompanyRepository repo;

    public InsuranceCompanyController(InsuranceCompanyRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("active", "insurers");
        model.addAttribute("insurers", repo.findAll());
        return "insurers/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("active", "insurers");
        model.addAttribute("insurer", new InsuranceCompany());
        return "insurers/form.html";
    }

    @PostMapping
    public String save(@ModelAttribute InsuranceCompany insurer) {
        repo.save(insurer);
        return "redirect:/insurers";
    }
}
