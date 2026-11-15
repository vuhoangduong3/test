package com.example.Vi_vu_Viet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.example.Vi_vu_Viet.Security.CompanyAuthorization;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthFlowTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyAuthorization companyAuthorization;
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpResponse<String> call(String method, String path, String body, String token) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
        if (token != null) req.header("Authorization", "Bearer " + token);
        return client.send(req.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String field(String body, String name) {
        var match = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertTrue(match.find(), body);
        return match.group(1);
    }
    @Test void registrationLoginRefreshAndLogout() throws Exception {
        String register = "{\"wsRequest\":{\"fullName\":\"Test User\",\"email\":\"auth-test@example.com\",\"password\":\"Password123!\"}}";
        String login = "{\"wsRequest\":{\"identifier\":\"auth-test@example.com\",\"password\":\"Password123!\"}}";
        assertEquals(200, call("POST", "/api/v1/auth/register", register, null).statusCode());
        UUID company = UUID.randomUUID(), membership = UUID.randomUUID();
        UUID user = jdbc.queryForObject("select id from users where email = ?", UUID.class, "auth-test@example.com");
        jdbc.update("insert into bus_companies (id, name, slug, status, created_at, updated_at) values (?, ?, ?, ?, current_timestamp, current_timestamp)", company, "Test company", "test-company", "ACTIVE");
        jdbc.update("insert into company_members (id, company_id, user_id, status, created_at, updated_at) values (?, ?, ?, ?, current_timestamp, current_timestamp)", membership, company, user, "ACTIVE");
        jdbc.update("insert into company_member_roles (id, company_id, membership_id, role, created_at, updated_at) values (?, ?, ?, ?, current_timestamp, current_timestamp)", UUID.randomUUID(), company, membership, "COMPANY_ADMIN");
        assertEquals(401, call("GET", "/api/v1/me", null, null).statusCode());
        assertEquals(401, call("POST", "/api/v1/auth/login", login.replace("Password123!", "Wrong123!"), null).statusCode());
        var response = call("POST", "/api/v1/auth/login", login, null);
        assertEquals(200, response.statusCode());
        String access = field(response.body(), "accessToken"), refresh = field(response.body(), "refreshToken");
        var me = call("GET", "/api/v1/me", null, access);
        assertEquals(200, me.statusCode());
        String authority = "COMPANY_ADMIN:" + company;
        assertTrue(me.body().contains(authority), me.body());
        var authentication = new UsernamePasswordAuthenticationToken("user", null, List.of(new SimpleGrantedAuthority(authority)));
        assertTrue(companyAuthorization.hasCompanyRole(authentication, company, "COMPANY_ADMIN"));
        assertFalse(companyAuthorization.hasCompanyRole(authentication, UUID.randomUUID(), "COMPANY_ADMIN"));
        assertEquals(200, call("GET", "/api/v1/me/companies", null, access).statusCode());
        UUID from = UUID.randomUUID(), to = UUID.randomUUID();
        jdbc.update("insert into areas (id, code, name, type, status, created_at, updated_at) values (?, ?, ?, ?, ?, current_timestamp, current_timestamp)", from, "TEST-A", "A", "CITY", "ACTIVE");
        jdbc.update("insert into areas (id, code, name, type, status, created_at, updated_at) values (?, ?, ?, ?, ?, current_timestamp, current_timestamp)", to, "TEST-B", "B", "CITY", "ACTIVE");
        String prefix = "/api/v1/bus-companies/" + company;
        String routeJson = "{\"wsRequest\":{\"code\":\"TEST-R\",\"name\":\"Test\",\"originAreaId\":\"" + from + "\",\"destinationAreaId\":\"" + to + "\",\"distanceKm\":10,\"durationMinutes\":30,\"baseFareAmount\":10000}}";
        assertEquals(401, call("POST", prefix + "/routes", routeJson, null).statusCode());
        var route = call("POST", prefix + "/routes", routeJson, access); assertEquals(200, route.statusCode(), route.body());
        String routeId = field(route.body(), "id");
        LocalDateTime depart = LocalDateTime.now().plusDays(3).withNano(0);
        String tripJson = "{\"wsRequest\":{\"routeId\":\"" + routeId + "\",\"tripCode\":\"TEST-T\",\"departureAt\":\"" + depart + "\",\"expectedArrivalAt\":\"" + depart.plusHours(1) + "\"}}";
        var trip = call("POST", prefix + "/trips", tripJson, access); assertEquals(200, trip.statusCode(), trip.body());
        String tripId = field(trip.body(), "id");
        assertEquals(200, call("POST", prefix + "/trips/" + tripId + "/publish", null, access).statusCode());
        var search = call("GET", "/api/v1/trips?origin_area_id=" + from + "&destination_area_id=" + to + "&departure_date=" + depart.toLocalDate(), null, null);
        assertEquals(200, search.statusCode()); assertTrue(search.body().contains(tripId), search.body());
        var rotated = call("POST", "/api/v1/auth/refresh", "{\"wsRequest\":{\"refreshToken\":\"" + refresh + "\"}}", null);
        assertEquals(200, rotated.statusCode());
        String newAccess = field(rotated.body(), "accessToken");
        assertEquals(200, call("POST", "/api/v1/auth/logout", null, newAccess).statusCode());
        assertEquals(401, call("GET", "/api/v1/me", null, newAccess).statusCode());
    }
}

