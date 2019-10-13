package com.buzzo.tipsmatcher.exceltipsmatcher;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/")
public class HomeController {
@GetMapping("/")
    public String redirect(Model model)
    {
        List<Integer> days = new ArrayList<>();
        for(int i = 1; i <= 31; i++){
            days.add(i);
        }
        model.addAttribute("days", days);
        return "index";
    }
}
