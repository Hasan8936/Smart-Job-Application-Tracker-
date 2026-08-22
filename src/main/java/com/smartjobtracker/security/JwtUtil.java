package com.smartjobtracker.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.time.Instant;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

    @Value("${JWT_SECRET:secret123}")
    private String jwtSecret;

    @Value("${JWT_EXP_SECONDS:86400}")
    private Long jwtExpSeconds;

    public String generateToken(String subject) {
        Algorithm algo = Algorithm.HMAC256(jwtSecret.getBytes());
        Instant exp = Instant.now().plusSeconds(jwtExpSeconds);
        return JWT.create()
                .withSubject(subject)
                .withExpiresAt(Date.from(exp))
                .withIssuedAt(new Date())
                .sign(algo);
    }

    public String validateAndGetSubject(String token) {
        Algorithm algo = Algorithm.HMAC256(jwtSecret.getBytes());
        DecodedJWT jwt = JWT.require(algo).build().verify(token);
        return jwt.getSubject();
    }
}
