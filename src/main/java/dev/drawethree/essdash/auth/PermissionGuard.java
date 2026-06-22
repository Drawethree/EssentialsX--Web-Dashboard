package dev.drawethree.essdash.auth;

import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;

import java.util.Set;

public final class PermissionGuard {

    private PermissionGuard() {}

    public static void require(Context ctx, Permission permission) {
        String role = ctx.attribute("role");
        if ("ADMIN".equals(role)) return;

        @SuppressWarnings("unchecked")
        Set<Permission> perms = ctx.attribute("permissions");
        if (perms == null || !perms.contains(permission)) {
            throw new ForbiddenResponse("Missing permission: " + permission.name());
        }
    }

    public static void requireAdmin(Context ctx) {
        if (!"ADMIN".equals(ctx.<String>attribute("role"))) {
            throw new ForbiddenResponse("Admin access required");
        }
    }
}
