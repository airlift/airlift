package io.airlift.security.utils;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

public class SecurityUtil {
    public static final String REALM_IN_CHALLENGE = "X-Realm-In-Challenge";

    // TODO: convert the Shiro's Subject to javax.security.auth.Subject
    public static Subject getSubject()
    {
        return SecurityUtils.getSubject();
    }
}

