package com.defvul.passets.api.interceptor;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 说明:
 * 时间: 2019/12/17 10:14
 *
 * @author wimas
 */
@Component
public class SecretInterceptor implements HandlerInterceptor {

    @Value("${secret}")
    private String secret;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String secret = request.getHeader("X-Auth-Secret");
        boolean result = StringUtils.isNotBlank(secret) && this.secret.equals(secret);
        if (!result) {
            response.setStatus(403);
        }
        return result;
    }
}
