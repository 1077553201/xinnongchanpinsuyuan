package org.nongchanpin.security;

public final class AccessContext {
    private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<>();
    private AccessContext() {}
    public static void set(Actor actor) { CURRENT.set(actor); }
    public static void clear() { CURRENT.remove(); }
    public static Actor require() {
        Actor actor = CURRENT.get();
        if (actor == null) throw new AccessException(401, "登录已失效，请重新登录。");
        return actor;
    }
    public static Long organization() { return require().isAdmin() ? null : require().getOrganizationId(); }
    public static void checkOrganization(Long id) {
        Actor actor = require();
        if (!actor.isAdmin() && (id == null || !id.equals(actor.getOrganizationId()))) {
            throw new AccessException(403, "无权访问其他机构的记录。");
        }
    }
    public static void write() { if (!require().canWrite()) throw new AccessException(403, "只读账号不能修改业务记录。"); }
    public static void admin() { if (!require().isAdmin()) throw new AccessException(403, "此操作仅限管理员。"); }
}
