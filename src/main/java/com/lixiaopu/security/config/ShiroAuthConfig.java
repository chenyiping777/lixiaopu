package com.lixiaopu.security.config;

import com.lixiaopu.security.realm.AuthRealm;

import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShiroAuthConfig {
    @Bean public AuthRealm authRealm() {
        return new AuthRealm();
    }
    @Bean public DefaultSecurityManager securityManager(AuthRealm realm) {
        DefaultSecurityManager manager=new DefaultSecurityManager(realm);
        manager.setRememberMeManager(null);
        DefaultSessionStorageEvaluator evaluator=new DefaultSessionStorageEvaluator();
        evaluator.setSessionStorageEnabled(false);
        DefaultSubjectDAO subjectDAO=new DefaultSubjectDAO();
        subjectDAO.setSessionStorageEvaluator(evaluator);
        manager.setSubjectDAO(subjectDAO);
        return manager;
    }
}
