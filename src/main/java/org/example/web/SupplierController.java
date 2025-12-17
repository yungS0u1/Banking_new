package org.example.web;

import org.example.domain.Supplier;
import org.example.repo.SupplierRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/suppliers")
public class SupplierController {
    private final SupplierRepository repo;

    public SupplierController(SupplierRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("suppliers", repo.findAll());
        return "suppliers/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("supplier", new Supplier());
        return "suppliers/form.html";
    }

    @PostMapping
    public String save(@ModelAttribute Supplier supplier) {
        repo.save(supplier);
        return "redirect:/suppliers";
    }
}
