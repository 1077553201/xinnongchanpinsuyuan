package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.AccountEntity;

@Mapper
public interface AccountMapper {
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    AccountEntity byUsername(String username);
    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    AccountEntity byId(Long id);
    @Select("SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{id} AND r.status = 'ACTIVE'")
    List<String> roles(Long id);
    @Select("SELECT * FROM sys_user ORDER BY id DESC")
    List<AccountEntity> all();
    @Insert("INSERT INTO sys_user (organization_id, username, password_hash, display_name, status) "
            + "VALUES (#{organizationId}, #{username}, #{passwordHash}, #{displayName}, 'ACTIVE')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AccountEntity account);
    @Insert("INSERT INTO sys_user_role (user_id, role_id) SELECT #{userId}, id FROM sys_role "
            + "WHERE role_code = #{role} AND status = 'ACTIVE'")
    int grant(@Param("userId") Long userId, @Param("role") String role);
    @Update("UPDATE sys_user SET status = 'DISABLED' WHERE id = #{id}")
    int disable(Long id);
    @Update("UPDATE sys_user SET password_hash = #{hash} WHERE id = #{id}")
    int password(@Param("id") Long id, @Param("hash") String hash);
    @Update("UPDATE sys_user SET last_login_time = NOW() WHERE id = #{id}")
    int login(Long id);
    @Insert("INSERT INTO operation_audit (actor_id, actor_name, organization_id, action, target_type, target_id, detail) "
            + "VALUES (#{actorId}, #{actorName}, #{orgId}, #{action}, #{type}, #{id}, #{detail})")
    int audit(@Param("actorId") Long actorId, @Param("actorName") String actorName, @Param("orgId") Long orgId,
            @Param("action") String action, @Param("type") String type, @Param("id") Long id, @Param("detail") String detail);
}
