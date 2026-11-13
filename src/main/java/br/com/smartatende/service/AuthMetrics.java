package br.com.smartatende.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AuthMetrics {

    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Counter registerSuccess;

    public AuthMetrics(MeterRegistry meterRegistry) {
        this.loginSuccess = Counter.builder("smartatende.auth.login")
                .tag("result", "success")
                .description("Logins bem-sucedidos")
                .register(meterRegistry);
        this.loginFailure = Counter.builder("smartatende.auth.login")
                .tag("result", "failure")
                .description("Falhas de login")
                .register(meterRegistry);
        this.registerSuccess = Counter.builder("smartatende.auth.register")
                .tag("result", "success")
                .description("Cadastros bem-sucedidos")
                .register(meterRegistry);
    }

    public void recordLoginSuccess() {
        loginSuccess.increment();
    }

    public void recordLoginFailure() {
        loginFailure.increment();
    }

    public void recordRegisterSuccess() {
        registerSuccess.increment();
    }
}
