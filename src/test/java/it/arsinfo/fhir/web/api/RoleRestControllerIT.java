package it.arsinfo.fhir.web.api;

import it.arsinfo.fhir.domain.entity.Role;
import it.arsinfo.fhir.repository.RoleRepository;
import it.arsinfo.fhir.web.dto.RoleDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class RoleRestControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired RoleRepository roleRepository;
    @Autowired ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void listRoles_superAdmin_returns200WithBuiltInRoles() throws Exception {
        mockMvc.perform(get("/api/admin/roles"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[?(@.name=='SUPER_ADMIN')]").exists())
               .andExpect(jsonPath("$[?(@.name=='PRACTITIONER')]").exists());
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void listRoles_insufficientRole_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/roles"))
               .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createRole_validPayload_returns201() throws Exception {
        RoleDto dto = new RoleDto();
        dto.setName("RADIOLOGIST");
        dto.setDescription("Radiologist access");

        mockMvc.perform(post("/api/admin/roles")
                   .contentType(MediaType.APPLICATION_JSON)
                   .content(objectMapper.writeValueAsString(dto)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.name").value("RADIOLOGIST"));

        assertThat(roleRepository.existsByName("RADIOLOGIST")).isTrue();
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createRole_duplicateName_returns409() throws Exception {
        RoleDto dto = new RoleDto();
        dto.setName("SUPER_ADMIN"); // already exists (seeded)
        dto.setDescription("Duplicate");

        mockMvc.perform(post("/api/admin/roles")
                   .contentType(MediaType.APPLICATION_JSON)
                   .content(objectMapper.writeValueAsString(dto)))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void deleteBuiltInRole_returns409() throws Exception {
        Role superAdmin = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
        mockMvc.perform(delete("/api/admin/roles/{id}", superAdmin.getId()))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void getRole_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/admin/roles/99999"))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void deleteCustomRole_returns204() throws Exception {
        // Create a custom role first
        Role custom = new Role();
        custom.setName("TEMP_ROLE");
        custom.setDescription("Temp");
        roleRepository.save(custom);

        mockMvc.perform(delete("/api/admin/roles/{id}", custom.getId()))
               .andExpect(status().isNoContent());

        assertThat(roleRepository.existsByName("TEMP_ROLE")).isFalse();
    }
}
