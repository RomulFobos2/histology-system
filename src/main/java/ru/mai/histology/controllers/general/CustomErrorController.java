package ru.mai.histology.controllers.general;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CustomErrorController implements ErrorController {

    @GetMapping("/403")
    public String accessDenied() {
        return "errors/403"; // Возвращаем название шаблона 403.html
    }

    @GetMapping("/error")
    public String handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            if (statusCode == 404) {
                return "errors/404"; // Возвращаем шаблон 404.html
            }
            if (statusCode == 405) {
                return "errors/405"; // Возвращаем шаблон 405.html
            }
            // Обработка других кодов ошибок, если нужно
        }
        return "errors/error"; // Общий шаблон для других ошибок
    }


}
