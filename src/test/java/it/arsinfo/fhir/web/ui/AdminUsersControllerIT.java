package it.arsinfo.fhir.web.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AdminUsersControllerIT {

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void listUsers_superAdmin_returns200() throws Exception {
        mockMvc.perform(get("/admin/users"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/users/list"));
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void listUsers_insufficientRole_returns403() throws Exception {
        mockMvc.perform(get("/admin/users"))
               .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void searchUser_validSubject_redirectsToUserRoles() throws Exception {
        mockMvc.perform(get("/admin/users/search").param("subject", "user-123"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/users/user-123/roles"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void searchUser_pathTraversalSubject_returns400() throws Exception {
        mockMvc.perform(get("/admin/users/search").param("subject", "../evil"))
               .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void searchUser_subjectWithNewline_returns400() throws Exception {
        mockMvc.perform(get("/admin/users/search").param("subject", "user\r\nevil-header: injected"))
               .andExpect(status().isBadRequest());
    }
}
