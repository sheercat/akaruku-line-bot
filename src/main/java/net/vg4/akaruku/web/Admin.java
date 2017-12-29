package net.vg4.akaruku.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller("/")
public class Admin {
    @GetMapping("/")
    public String index(){
        return "index";
    }

}
