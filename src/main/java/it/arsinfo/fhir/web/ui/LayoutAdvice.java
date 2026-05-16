package it.arsinfo.fhir.web.ui;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {AdminRolesController.class, AdminUsersController.class})
class LayoutAdvice {

    @ModelAttribute("currentUri")
    String currentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
