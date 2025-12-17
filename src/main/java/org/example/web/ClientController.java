package org.example.web;

import org.example.domain.Client;
import org.example.repo.ClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/clients")
public class ClientController {

    private final ClientRepository repo;

    public ClientController(ClientRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("clients", repo.findAll());
        return "clients/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("client", new Client());
        return "clients/form.html";
    }

    @PostMapping
    public String save(@ModelAttribute Client client) {
        repo.save(client);
        return "redirect:/clients";
    }

    @GetMapping("/new/ping")
    @ResponseBody
    public String newPing() {
        return "new OK";
    }

}