package com.shoecommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "security.login-rate-limit.pair-limit=5","security.login-rate-limit.source-limit=30"})
@ActiveProfiles("test")
@Import(LoginRateLimitExternalIT.TestClockConfiguration.class)
@EnabledIfEnvironmentVariable(named="SPRING_DATASOURCE_URL",matches=".+")
class LoginRateLimitExternalIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired LoginRateLimiterTest.MutableClock clock;
    private HttpClient client;
    @BeforeEach void browser() {
        clock.at.updateAndGet(at->at.plus(Duration.ofDays(1)));
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    }
    @Test void countsSuccessAndFailureOncePreservesCsrfAndIgnoresForwardedAddress() throws Exception {
        String login="throttle-"+UUID.randomUUID()+"@example.test",password="Test-Only-Password-42";
        Timestamp now=Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES(?,?,?,'ENABLED',1,0,?,?)",UUID.randomUUID(),login,passwords.encode(password),now,now);
        assertThat(post(login,password,false,"203.0.113.1").statusCode()).isEqualTo(403);
        for(int i=0;i<5;i++) assertThat(post(i%2==0?login:" "+login.toUpperCase(java.util.Locale.ROOT)+" ",i==1?password:"wrong",true,"203.0.113."+i).statusCode()).isEqualTo(i==1?200:401);
        var denied=post(login,password,true,"198.51.100.99");
        assertThat(denied.statusCode()).isEqualTo(429);
        assertThat(denied.headers().firstValue("Retry-After")).contains("900");
        assertThat(json.readTree(denied.body()).get("code").asString()).isEqualTo("AUTH_RATE_LIMITED");
        assertThat(denied.body()).doesNotContain(login,password);
        clock.at.updateAndGet(at->at.plus(Duration.ofMinutes(15)));
        assertThat(post(login,password,true,"198.51.100.99").statusCode()).isEqualTo(200);
    }
    @Test void nonexistentAccountsHaveSamePairLimitAndSourceLimitStopsUsernameRotation() throws Exception {
        String missing="missing-"+UUID.randomUUID();
        for(int i=0;i<5;i++) assertThat(post(missing,"wrong",true,"203.0.113."+i).statusCode()).isEqualTo(401);
        var denied=post(missing,"wrong",true,"203.0.113.99");
        assertThat(denied.statusCode()).isEqualTo(429);
        assertThat(denied.headers().firstValue("Retry-After")).contains("900");
        for(int i=0;i<25;i++) assertThat(post("missing-"+i,"wrong",true,"198.51.100."+i).statusCode()).isEqualTo(401);
        var sourceBlocked=post("another-missing","wrong",true,"192.0.2.1");
        assertThat(sourceBlocked.statusCode()).isEqualTo(429);
        assertThat(sourceBlocked.headers().firstValue("Retry-After")).contains("60");
        clock.at.updateAndGet(at->at.plusSeconds(60));
        assertThat(post("another-missing","wrong",true,"192.0.2.1").statusCode()).isEqualTo(401);
    }
    private HttpResponse<String> post(String login,String password,boolean csrf,String forwarded) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/login"))
                .header("Content-Type","application/x-www-form-urlencoded").header("X-Forwarded-For",forwarded);
        if(csrf) {
            var token=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
            var body=json.readTree(token.body());request.header(body.get("headerName").asString(),body.get("token").asString());
        }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString("username="+URLEncoder.encode(login,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8))).build(),HttpResponse.BodyHandlers.ofString());
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class TestClockConfiguration {
        @Bean @Primary LoginRateLimiterTest.MutableClock limiterClock(){return new LoginRateLimiterTest.MutableClock();}
    }
}
