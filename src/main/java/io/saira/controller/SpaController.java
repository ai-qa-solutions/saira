package io.saira.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Контроллер для SPA — перенаправляет все frontend маршруты на index.html. */
@Controller
public class SpaController {

    /** Перенаправление SPA-маршрутов на index.html. */
    @GetMapping({
        "/",
        "/traces",
        "/traces/**",
        "/evaluations",
        "/evaluations/**",
        "/settings",
        "/shadow",
        "/shadow/**",
        "/dashboard"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
