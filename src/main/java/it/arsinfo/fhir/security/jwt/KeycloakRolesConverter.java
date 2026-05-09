package it.arsinfo.fhir.security.jwt;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Converts a JWT into a {@link JwtAuthenticationToken} with authorities drawn from:
 *
 * 1. Standard OIDC scopes (via {@link JwtGrantedAuthoritiesConverter}) → "SCOPE_xxx"
 * 2. Role names extracted via the configured claim path → "ROLE_xxx"
 *
 * The "ROLE_" prefix makes roles usable with @PreAuthorize("hasRole('SUPER_ADMIN')").
 */
@Component
public class KeycloakRolesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtClaimsExtractor extractor;
    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    public KeycloakRolesConverter(JwtClaimsExtractor extractor) {
        this.extractor = extractor;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = buildAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private Collection<GrantedAuthority> buildAuthorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>(scopeConverter.convert(jwt));
        for (String roleName : extractor.extractRoleNames(jwt)) {
            String cleaned = roleName.trim().toUpperCase().replace('-', '_').replace(' ', '_');
            authorities.add(new SimpleGrantedAuthority("ROLE_" + cleaned));
        }
        return authorities;
    }
}
